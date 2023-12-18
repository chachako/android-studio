/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.checks

import com.google.common.collect.Maps
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.AnalyzerException
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue

/**
 * A [ControlFlowGraph] is a graph containing a node for each instruction in a method, and an edge
 * for each possible control flow; usually just "next" for the instruction following the current
 * instruction, but in the case of a branch such as an "if", multiple edges to each successive
 * location, or with a "goto", a single edge to the jumped-to instruction.
 *
 * It also adds edges for abnormal control flow, such as the possibility of a method call throwing a
 * runtime exception.
 */
open class ControlFlowGraph {
  /** Map from instructions to nodes */
  private lateinit var nodeMap: MutableMap<AbstractInsnNode, Node>

  private var methodNode: MethodNode? = null

  companion object {
    /**
     * Creates a new [ControlFlowGraph] and populates it with the flow control for the given method.
     * If the optional `initial` parameter is provided with an existing graph, then the graph is
     * simply populated, not created. This allows subclassing of the graph instance, if necessary.
     *
     * @param initial usually null, but can point to an existing instance of a [ ] in which that
     *   graph is reused (but populated with new edges)
     * @param classNode the class containing the method to be analyzed
     * @param method the method to be analyzed
     * @return a [ControlFlowGraph] with nodes for the control flow in the given method
     * @throws AnalyzerException if the underlying bytecode library is unable to analyze the method
     *   bytecode
     */
    @Throws(AnalyzerException::class)
    fun create(
      initial: ControlFlowGraph?,
      classNode: ClassNode,
      method: MethodNode
    ): ControlFlowGraph {
      val graph = initial ?: ControlFlowGraph()
      val instructions = method.instructions
      graph.nodeMap = Maps.newHashMapWithExpectedSize(instructions.size())
      graph.methodNode = method

      // Create a flow control graph using ASM5's analyzer. According to the ASM 4 guide
      // (download.forge.objectweb.org/asm/asm4-guide.pdf) there are faster ways to construct
      // it, but those require a lot more code.
      val interpreter = BasicInterpreter()
      val analyzer =
        object : Analyzer<BasicValue>(interpreter) {
          override fun newControlFlowEdge(insn: Int, successor: Int) {
            // Update the information as of whether the `this` object has been
            // initialized at the given instruction.
            val from = instructions[insn]
            val to = instructions[successor]
            graph.add(from, to)
          }

          override fun newControlFlowExceptionEdge(insn: Int, tcb: TryCatchBlockNode): Boolean {
            val from = instructions[insn]
            graph.exception(from, tcb)
            return super.newControlFlowExceptionEdge(insn, tcb)
          }

          override fun newControlFlowExceptionEdge(insn: Int, successor: Int): Boolean {
            val from = instructions[insn]
            val to = instructions[successor]
            graph.exception(from, to)
            return super.newControlFlowExceptionEdge(insn, successor)
          }
        }

      analyzer.analyze(classNode.name, method)
      return graph
    }
  }

  /**
   * A [Node] is a node in the control flow graph for a method, pointing to the instruction and its
   * possible successors
   */
  class Node
  /**
   * Constructs a new control graph node
   *
   * @param instruction the instruction to associate with this node
   */
  (
    /** The instruction */
    val instruction: AbstractInsnNode
  ) {
    /** Any normal successors (e.g. following instruction, or goto or conditional flow) */
    val successors: MutableList<Node> = ArrayList(2)

    /** Any abnormal successors (e.g. the handler to go to following an exception) */
    val exceptions: MutableList<Node> = ArrayList(1)

    /** A tag for use during depth-first-search iteration of the graph etc */
    var visit: Int = 0

    fun addSuccessor(node: Node) {
      if (!successors.contains(node)) {
        successors.add(node)
      }
    }

    fun addExceptionPath(node: Node) {
      if (!exceptions.contains(node)) {
        exceptions.add(node)
      }
    }
  }

  /** Adds an exception flow to this graph */
  protected open fun add(from: AbstractInsnNode, to: AbstractInsnNode) {
    getNode(from).addSuccessor(getNode(to))
  }

  /** Adds an exception flow to this graph */
  protected fun exception(from: AbstractInsnNode, to: AbstractInsnNode) {
    // For now, these edges appear useless; we also get more specific
    // information via the TryCatchBlockNode which we use instead.
    // getNode(from).addExceptionPath(getNode(to));
  }

  /** Adds an exception try block node to this graph */
  protected fun exception(from: AbstractInsnNode, tcb: TryCatchBlockNode) {
    // Add tcb's to all instructions in the range
    val start = tcb.start
    val end = tcb.end // exclusive

    // Add exception edges for all method calls in the range
    var curr: AbstractInsnNode? = start
    val handlerNode = getNode(tcb.handler)
    while (curr !== end && curr != null) {
      // A method can throw can exception, or a throw instruction directly
      if (
        curr.type == AbstractInsnNode.METHOD_INSN ||
          (curr.type == AbstractInsnNode.INSN && curr.opcode == Opcodes.ATHROW)
      ) {
        // Method call; add exception edge to handler
        if (tcb.type == null) {
          // finally block: not an exception path
          getNode(curr).addSuccessor(handlerNode)
        }
        getNode(curr).addExceptionPath(handlerNode)
      }
      curr = curr.next
    }
  }

  /**
   * Looks up (and if necessary) creates a graph node for the given instruction
   *
   * @param instruction the instruction
   * @return the control flow graph node corresponding to the given instruction
   */
  fun getNode(instruction: AbstractInsnNode): Node {
    var node = nodeMap[instruction]
    if (node == null) {
      node = Node(instruction)
      nodeMap[instruction] = node
    }

    return node
  }
}
