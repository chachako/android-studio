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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getNextInstruction
import com.android.tools.lint.detector.api.getPrevInstruction
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.AnalyzerException

/**
 * Checks for problems with wakelocks (such as failing to release them) which can lead to
 * unnecessary battery usage.
 */
class WakelockDetector : Detector(), ClassScanner, SourceCodeScanner {
  companion object {
    const val ANDROID_APP_ACTIVITY: String = "android.app.Activity"

    /** Problems using wakelocks */
    @JvmField
    val ISSUE: Issue =
      create(
        id = "Wakelock",
        briefDescription = "Incorrect `WakeLock` usage",
        explanation =
          """
          Failing to release a wakelock properly can keep the Android device in a high power mode, \
          which reduces battery life. There are several causes of this, such as releasing the wake \
          lock in `onDestroy()` instead of in `onPause()`, failing to call `release()` in all \
          possible code paths after an `acquire()`, and so on.

          NOTE: If you are using the lock just to keep the screen on, you should strongly consider \
          using `FLAG_KEEP_SCREEN_ON` instead. This window flag will be correctly managed by the \
          platform as the user moves between applications and doesn't require a special permission. \
          See https://developer.android.com/reference/android/view/WindowManager.LayoutParams.html#FLAG_KEEP_SCREEN_ON.
          """,
        category = Category.PERFORMANCE,
        priority = 9,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(WakelockDetector::class.java, Scope.CLASS_FILE_SCOPE)
      )

    /** Using non-timeout version of wakelock acquire */
    @JvmField
    val TIMEOUT: Issue =
      create(
        id = "WakelockTimeout",
        briefDescription = "Using wakeLock without timeout",
        explanation =
          """
          Wakelocks have two acquire methods: one with a timeout, and one without. You should \
          generally always use the one with a timeout. A typical timeout is 10 minutes. If the task \
          takes longer than it is critical that it happens (i.e. can't use `JobScheduler`) then \
          maybe they should consider a foreground service instead (which is a stronger run guarantee \
          and lets the user know something long/important is happening).
          """,
        category = Category.PERFORMANCE,
        priority = 9,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(WakelockDetector::class.java, Scope.JAVA_FILE_SCOPE)
      )

    private const val WAKELOCK_OWNER = "android/os/PowerManager\$WakeLock"
    private const val RELEASE_METHOD = "release"
    private const val ACQUIRE_METHOD = "acquire"
    private const val IS_HELD_METHOD = "isHeld"
    private const val POWER_MANAGER = "android/os/PowerManager"
    private const val NEW_WAKE_LOCK_METHOD = "newWakeLock"

    private const val SEEN_TARGET = 1
    private const val SEEN_BRANCH = 2
    private const val SEEN_EXCEPTION = 4
    private const val SEEN_RETURN = 8
  }

  override fun afterCheckRootProject(context: Context) {
    if (hasAcquire && !hasRelease && context.driver.phase == 1) {
      // Gather positions of the acquire calls
      context.driver.requestRepeat(this, Scope.CLASS_FILE_SCOPE)
    }
  }

  // ---- Implements ClassScanner ----

  /** Whether any `acquire()` calls have been encountered */
  private var hasAcquire = false

  /** Whether any `release()` calls have been encountered */
  private var hasRelease = false

  override fun getApplicableCallNames(): List<String> {
    return listOf(ACQUIRE_METHOD, RELEASE_METHOD, NEW_WAKE_LOCK_METHOD)
  }

  override fun checkCall(
    context: ClassContext,
    classNode: ClassNode,
    method: MethodNode,
    call: MethodInsnNode
  ) {
    if (!context.project.reportIssues) {
      // If this is a library project not being analyzed, ignore it
      return
    }

    if (call.owner == WAKELOCK_OWNER) {
      val name = call.name
      if (name == ACQUIRE_METHOD) {
        // acquire(long timeout) does not require a corresponding release
        if (call.desc == "(J)V") {
          return
        }
        hasAcquire = true

        if (context.driver.phase == 2) {
          assert(!hasRelease)
          context.report(
            ISSUE,
            method,
            call,
            context.getLocation(call),
            "Found a wakelock `acquire()` but no `release()` calls anywhere"
          )
        } else {
          assert(context.driver.phase == 1)
          // Perform flow analysis in this method to see if we're
          // performing an acquire/release block, where there are code paths
          // between the acquire and release which can result in the
          // release call not getting reached.
          checkFlow(context, classNode, method, call)
        }
      } else if (name == RELEASE_METHOD) {
        hasRelease = true

        // See if the release is happening in an onDestroy method, in an activity.
        if ("onDestroy" == method.name) {
          val psiClass = context.findPsiClass(classNode)
          val activityClass = context.findPsiClass(ANDROID_APP_ACTIVITY)
          if (
            psiClass != null && activityClass != null && psiClass.isInheritor(activityClass, true)
          ) {
            context.report(
              ISSUE,
              method,
              call,
              context.getLocation(call),
              "Wakelocks should be released in `onPause`, not `onDestroy`"
            )
          }
        }
      }
    } else if (call.owner == POWER_MANAGER) {
      if (call.name == NEW_WAKE_LOCK_METHOD) {
        val prev = getPrevInstruction(call)?.let { getPrevInstruction(it) } ?: return
        if (prev.opcode != Opcodes.LDC) {
          return
        }
        val ldc = prev as LdcInsnNode
        val constant = ldc.cst
        if (constant is Int) {
          // Constant values are copied into the bytecode so we have to compare
          // values; however, that means the values are part of the API
          val PARTIAL_WAKE_LOCK = 0x00000001
          val ACQUIRE_CAUSES_WAKEUP = 0x10000000
          val both = PARTIAL_WAKE_LOCK or ACQUIRE_CAUSES_WAKEUP
          if ((constant and both) == both) {
            context.report(
              ISSUE,
              method,
              call,
              context.getLocation(call),
              "Should not set both `PARTIAL_WAKE_LOCK` and `ACQUIRE_CAUSES_WAKEUP`. " +
                "If you do not want the screen to turn on, get rid of " +
                "`ACQUIRE_CAUSES_WAKEUP`"
            )
          }
        }
      }
    }
  }

  private fun checkFlow(
    context: ClassContext,
    classNode: ClassNode,
    method: MethodNode,
    acquire: MethodInsnNode
  ) {
    val instructions = method.instructions
    var release: MethodInsnNode? = null

    // Find release call
    var i = 0
    val n = instructions.size()
    while (i < n) {
      val instruction = instructions[i]
      val type = instruction.type
      if (type == AbstractInsnNode.METHOD_INSN) {
        val call = instruction as MethodInsnNode
        if (call.name == RELEASE_METHOD && call.owner == WAKELOCK_OWNER) {
          release = call
          break
        }
      }
      i++
    }

    if (release == null) {
      // Didn't find both acquire and release in this method; no point in doing
      // local flow analysis
      return
    }

    try {
      val graph = MyGraph()
      ControlFlowGraph.create(graph, classNode, method)

      val status = dfs(graph.getNode(acquire))
      if ((status and SEEN_RETURN) != 0) {
        val message =
          if ((status and SEEN_EXCEPTION) != 0) {
            "The `release()` call is not always reached (via exceptional flow)"
          } else {
            "The `release()` call is not always reached"
          }

        context.report(ISSUE, method, acquire, context.getLocation(release), message)
      }
    } catch (e: AnalyzerException) {
      context.log(e, null)
    }
  }

  /** TODO RENAME */
  private class MyGraph : ControlFlowGraph() {
    override fun add(from: AbstractInsnNode, to: AbstractInsnNode) {
      if (from.opcode == Opcodes.IFNULL) {
        val jump = from as JumpInsnNode
        if (jump.label === to) {
          // Skip jump targets on null if it's surrounding the release call
          //
          //  if (lock != null) {
          //      lock.release();
          //  }
          //
          // The above shouldn't be considered a scenario where release() may not
          // be called.
          var next = getNextInstruction(from)
          if (next != null && next.type == AbstractInsnNode.VAR_INSN) {
            next = getNextInstruction(next)
            if (next != null && next.type == AbstractInsnNode.METHOD_INSN) {
              val method = next as MethodInsnNode
              if (method.name == RELEASE_METHOD && method.owner == WAKELOCK_OWNER) {
                // This isn't entirely correct; this will also trigger
                // for "if (lock == null) { lock.release(); }" but that's
                // not likely (and caught by other null checking in tools)
                return
              }
            }
          }
        }
      } else if (from.opcode == Opcodes.IFEQ) {
        val jump = from as JumpInsnNode
        if (jump.label === to) {
          val prev = getPrevInstruction(from)
          if (prev != null && prev.type == AbstractInsnNode.METHOD_INSN) {
            val method = prev as MethodInsnNode
            if (method.name == IS_HELD_METHOD && method.owner == WAKELOCK_OWNER) {
              val next = getNextInstruction(from)
              if (next != null) {
                super.add(from, next)
                return
              }
            }
          }
        }
      }

      super.add(from, to)
    }
  }

  /**
   * Search from the given node towards the target; return false if we reach an exit point such as a
   * return or a call on the way there that is not within a try/catch clause.
   *
   * @param node the current node
   * @return true if the target was reached XXX RETURN VALUES ARE WRONG AS OF RIGHT NOW
   */
  protected fun dfs(node: ControlFlowGraph.Node): Int {
    val instruction = node.instruction
    if (instruction.type == AbstractInsnNode.JUMP_INSN) {
      val opcode = instruction.opcode
      if (
        opcode == Opcodes.RETURN ||
          opcode == Opcodes.ARETURN ||
          opcode == Opcodes.LRETURN ||
          opcode == Opcodes.IRETURN ||
          opcode == Opcodes.DRETURN ||
          opcode == Opcodes.FRETURN ||
          opcode == Opcodes.ATHROW
      ) {
        return SEEN_RETURN
      }
    }

    // There are no cycles, so no *NEED* for this, though it does avoid
    // researching shared labels. However, it makes debugging harder (no re-entry)
    // so this is only done when debugging is off
    if (node.visit != 0) {
      return 0
    }
    node.visit = 1

    // Look for the target. This is any method call node which is a release on the
    // lock (later also check it's the same instance, though that's harder).
    // This is because finally blocks tend to be inlined so from a single try/catch/finally
    // with a release() in the finally, the bytecode can contain multiple repeated
    // (inlined) release() calls.
    if (instruction.type == AbstractInsnNode.METHOD_INSN) {
      val method = instruction as MethodInsnNode
      if (method.name == RELEASE_METHOD && method.owner == WAKELOCK_OWNER) {
        return SEEN_TARGET
      } else if (method.name == ACQUIRE_METHOD && method.owner == WAKELOCK_OWNER) {
        // OK
      } else if (method.name == IS_HELD_METHOD && method.owner == WAKELOCK_OWNER) {
        // OK
      } else {
        // Some non acquire/release method call: if this is not associated with a
        // try-catch block, it would mean the exception would exit the method,
        // which would be an error
        if (node.exceptions.isEmpty()) {
          // Look up the corresponding frame, if any
          var curr = method.previous
          var foundFrame = false
          while (curr != null) {
            if (curr.type == AbstractInsnNode.FRAME) {
              foundFrame = true
              break
            }
            curr = curr.previous
          }

          if (!foundFrame) {
            return SEEN_RETURN
          }
        }
      }
    }

    // if (node.instruction is a call, and the call is not caught by
    // a try/catch block (provided the release is not inside the try/catch block)
    // then return false
    var status = 0

    var implicitReturn = true
    val successors: List<ControlFlowGraph.Node> = node.successors
    val exceptions: List<ControlFlowGraph.Node> = node.exceptions
    if (exceptions.isNotEmpty()) {
      implicitReturn = false
    }
    for (successor in exceptions) {
      status = dfs(successor) or status
      if ((status and SEEN_RETURN) != 0) {
        return status
      }
    }

    if (status != 0) {
      status = status or SEEN_EXCEPTION
    }

    if (successors.isNotEmpty()) {
      implicitReturn = false
      if (successors.size > 1) {
        status = status or SEEN_BRANCH
      }
    }
    for (successor in successors) {
      status = dfs(successor) or status
      if ((status and SEEN_RETURN) != 0) {
        return status
      }
    }

    if (implicitReturn) {
      status = status or SEEN_RETURN
    }

    return status
  }

  // Check for the non-timeout version of wakelock acquire
  override fun getApplicableMethodNames(): List<String> = listOf(ACQUIRE_METHOD)

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (node.valueArgumentCount > 0) {
      return
    }

    if (!context.evaluator.isMemberInClass(method, "android.os.PowerManager.WakeLock")) {
      return
    }

    val location = context.getLocation(node)
    val fix =
      fix()
        .name("Set timeout to 10 minutes")
        .replace()
        .pattern("acquire\\s*\\(()\\s*\\)")
        .with("10*60*1000L /*10 minutes*/")
        .build()

    context.report(
      TIMEOUT,
      node,
      location,
      "" +
        "Provide a timeout when requesting a wakelock with " +
        "`PowerManager.Wakelock.acquire(long timeout)`. This will ensure the OS will " +
        "cleanup any wakelocks that last longer than you intend, and will save your " +
        "user's battery.",
      fix
    )
  }
}
