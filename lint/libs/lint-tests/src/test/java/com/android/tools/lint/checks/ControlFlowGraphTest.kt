@file:Suppress("UNUSED_PARAMETER", "LiftReturnOrAssignment")

package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.BytecodeTestFile
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.compiled
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.parseFirst
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiMethod
import com.intellij.util.text.trimMiddle
import java.io.File
import java.util.Collections
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.psi.UElementWithLocation
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

private const val SHOW_DOT_ON_FAILURE = false
@Suppress("RedundantNullableReturnType", "RedundantSuppression")
private val UPDATE_IN_PLACE: String? = null

// Ignore warnings in test files
@Suppress(
  "KotlinConstantConditions",
  "CallToPrintStackTrace",
  "ConstantValue",
  "CatchMayIgnoreException",
  "EqualsBetweenInconvertibleTypes",
  "UnnecessarySemicolon",
  "SameParameterValue",
  "ConstantConditionIf",
  "Convert2Lambda",
  "TryFinallyCanBeTryWithResources",
  "DataFlowIssue",
  "UnnecessaryLabelOnBreakStatement",
  "UnusedLabel",
  "InfiniteLoopStatement",
  "ResultOfMethodCallIgnored",
  "TestFunctionName",
  "ConvertToStringTemplate"
)
class ControlFlowGraphTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun checkTryCatchJava() {
    val testFile =
      java(
          """
          package test.pkg;

          import android.app.Activity;
          import android.os.PowerManager.WakeLock;

          public class WakelockActivity extends Activity {
              void target(WakeLock lock) {
                  lock.acquire();
                  try {
                      randomCall();
                  } catch (Exception e) {
                      e.printStackTrace();
                  } finally {
                      lock.release();
                  }
              }

              static void randomCall() {
                  System.out.println("test");
              }
          }
          """
        )
        .indented()
    checkAstGraph(
      testFile,
      """
                CodeBlock:     ╭─ { lock.acquire….release(); } }
      QualifiedExpression:   ╭─╰→ lock.acquire()
                     Call:   ╰→╭─ lock.acquire()
                      Try:   ╭─╰→ try { randomCa…ck.release(); }
                CodeBlock:   ╰→╭─ { randomCall(); }
                     Call:   ╭─╰→ randomCall()                   ─╮─╮ RuntimeException Exception
              CatchClause:   │ ╭─ catch (Excepti…StackTrace(); } ←╯←╯
                CodeBlock: ╭─│ ╰→ { e.printStackTrace(); }
      QualifiedExpression: ╰→│ ╭─ e.printStackTrace()
                     Call: ╭─│ ╰→ e.printStackTrace()
                CodeBlock: ╰→╰→╭─ { lock.release(); }
      QualifiedExpression:   ╭─╰→ lock.release()
                     Call:   ╰→╭─ lock.release()
                               ╰→ *exit*
      """,
      canThrow = { _, method -> method.name == "randomCall" },
      expectedDotGraph =
        """
        digraph {
          labelloc="t"

          node [shape=box]
          subgraph cluster_instructions {
            penwidth=0;
            N00A [label="exit"]
            N00B [label="CodeBlock\n{ lock.acquire….release(); } }",shape=ellipse]
            N00C [label="QualifiedExpression\nlock.acquire()"]
            N00D [label="Call\nlock.acquire()"]
            N00E [label="Try\ntry { randomCa…ck.release(); }"]
            N00F [label="CodeBlock\n{ randomCall(); }"]
            N010 [label="Call\nrandomCall()"]
            N011 [label="CatchClause\ncatch (Excepti…StackTrace(); }"]
            N012 [label="CodeBlock\n{ e.printStackTrace(); }"]
            N013 [label="QualifiedExpression\ne.printStackTrace()"]
            N014 [label="Call\ne.printStackTrace()"]
            N015 [label="CodeBlock\n{ lock.release(); }"]
            N016 [label="QualifiedExpression\nlock.release()"]
            N017 [label="Call\nlock.release()"]

            N00B -> N00C [label=" then "]
            N00C -> N00D [label=" then "]
            N00D -> N00E [label=" then "]
            N00E -> N00F [label=" then "]
            N00F -> N010 [label=" then "]
            N010 -> N015 [label=" finally "]
            N010 -> N011 [label=" java.lang.RuntimeException ",style=dashed]
            N010 -> N011 [label=" java.lang.Exception ",style=dashed]
            N011 -> N012 [label=" catch "]
            N012 -> N013 [label=" then "]
            N013 -> N014 [label=" then "]
            N014 -> N015 [label=" finally "]
            N015 -> N016 [label=" then "]
            N016 -> N017 [label=" then "]
            N017 -> N00A [label=" exit "]
          }
        }
        """
    )
  }

  @Test
  fun checkTryCatchKotlin1a() {
    checkAstGraph(
      kotlin(
          """
          import android.app.Activity
          import android.os.PowerManager.WakeLock

          class WakelockActivity : Activity() {
              fun target(lock: WakeLock) {
                  lock.acquire()
                  try {
                      randomCall()
                  } catch (e: Exception) {
                      e.printStackTrace()
                  } finally {
                      lock.release()
                  }
              }

              fun randomCall() {
                  println("test")
              }
          }
          """
        )
        .indented(),
      """
                    Block:     ╭─ { lock.acquire…k.release() } }
      QualifiedExpression:   ╭─╰→ lock.acquire()
                 FuncCall:   ╰→╭─ acquire()
                      Try:   ╭─╰→ try { randomCa…ock.release() }
                Try Block:   ╰→╭─ { randomCall() }
                 FuncCall:   ╭─╰→ randomCall()                   ─╮ Exception
              CatchClause:   │ ╭─ catch (e: Exce…tStackTrace() } ←╯
                    Block: ╭─│ ╰→ { e.printStackTrace() }
      QualifiedExpression: ╰→│ ╭─ e.printStackTrace()
                 FuncCall: ╭─│ ╰→ printStackTrace()
            Finally Block: ╰→╰→╭─ { lock.release() }
      QualifiedExpression:   ╭─╰→ lock.release()
                 FuncCall:   ╰→╭─ release()
                               ╰→ *exit*
      """,
      canThrow = { _, method -> method.name == "randomCall" },
      expectedDotGraph =
        """
        digraph {
          labelloc="t"

          node [shape=box]
          subgraph cluster_instructions {
            penwidth=0;
            N00A [label="exit"]
            N00B [label="Block\n{ lock.acquire…k.release() } }",shape=ellipse]
            N00C [label="QualifiedExpression\nlock.acquire()"]
            N00D [label="FuncCall\nacquire()"]
            N00E [label="Try\ntry { randomCa…ock.release() }"]
            N00F [label="Try Block\n{ randomCall() }"]
            N010 [label="FuncCall\nrandomCall()"]
            N011 [label="CatchClause\ncatch (e: Exce…tStackTrace() }"]
            N012 [label="Block\n{ e.printStackTrace() }"]
            N013 [label="QualifiedExpression\ne.printStackTrace()"]
            N014 [label="FuncCall\nprintStackTrace()"]
            N015 [label="Finally Block\n{ lock.release() }"]
            N016 [label="QualifiedExpression\nlock.release()"]
            N017 [label="FuncCall\nrelease()"]

            N00B -> N00C [label=" then "]
            N00C -> N00D [label=" then "]
            N00D -> N00E [label=" then "]
            N00E -> N00F [label=" then "]
            N00F -> N010 [label=" then "]
            N010 -> N015 [label=" finally "]
            N010 -> N011 [label=" java.lang.Exception ",style=dashed]
            N011 -> N012 [label=" catch "]
            N012 -> N013 [label=" then "]
            N013 -> N014 [label=" then "]
            N014 -> N015 [label=" finally "]
            N015 -> N016 [label=" then "]
            N016 -> N017 [label=" then "]
            N017 -> N00A [label=" exit "]
          }
        }
        """
    )
  }

  @Test
  fun checkTryCatchKotlin1b() {
    // Like checkTryCatchKotlin1a, but removed the `finally`, and added an additional function call
    // for clarity.
    checkAstGraph(
      kotlin(
          """
          import android.app.Activity
          import android.os.PowerManager.WakeLock

          class WakelockActivity : Activity() {
              fun target(lock: WakeLock) {
                  lock.acquire()
                  try {
                      randomCall()
                  } catch (e: Exception) {
                      e.printStackTrace()
                  }
                  someOtherCall()
              }

              fun randomCall() {
                  println("test")
              }

              fun someOtherCall() {
                  println("test")
              }
          }
          """
        )
        .indented(),
      """
                    Block:     ╭─ { lock.acquire…meOtherCall() }
      QualifiedExpression:   ╭─╰→ lock.acquire()
                 FuncCall:   ╰→╭─ acquire()
                      Try:   ╭─╰→ try { randomCa…tStackTrace() }
                Try Block:   ╰→╭─ { randomCall() }
                 FuncCall:   ╭─╰→ randomCall()                   ─╮ Exception
              CatchClause:   │ ╭─ catch (e: Exce…tStackTrace() } ←╯
                    Block: ╭─│ ╰→ { e.printStackTrace() }
      QualifiedExpression: ╰→│ ╭─ e.printStackTrace()
                 FuncCall: ╭─│ ╰→ printStackTrace()
                 FuncCall: ╰→╰→╭─ someOtherCall()
                               ╰→ *exit*
      """,
      canThrow = { _, method -> method.name == "randomCall" },
    )
  }

  @Test
  fun checkTryCatchKotlin1c() {
    // Like checkTryCatchKotlin1b, but we've changed the catch type from Exception to
    // ArrayIndexOutOfBoundsException
    checkAstGraph(
      kotlin(
          """
          import android.app.Activity
          import android.os.PowerManager.WakeLock

          class WakelockActivity : Activity() {
              fun target(lock: WakeLock) {
                  lock.acquire()
                  try {
                      randomCall()
                  } catch (e: java.lang.ArrayIndexOutOfBoundsException) {
                      e.printStackTrace()
                  }
                  someOtherCall()
              }

              fun randomCall() {
                  println("test")
              }

              fun someOtherCall() {
                  println("test")
              }
          }
          """
        )
        .indented(),
      """
                    Block:     ╭─ { lock.acquire…meOtherCall() }
      QualifiedExpression:   ╭─╰→ lock.acquire()
                 FuncCall:   ╰→╭─ acquire()
                      Try:   ╭─╰→ try { randomCa…tStackTrace() }
                Try Block:   ╰→╭─ { randomCall() }
                 FuncCall:   ╭─╰→ randomCall()                   ─╮─╮ ArrayIndexOutOfBoundsException Exception
              CatchClause:   │ ╭─ catch (e: java…tStackTrace() } ←╯ ┆
                    Block: ╭─│ ╰→ { e.printStackTrace() }           ┆
      QualifiedExpression: ╰→│ ╭─ e.printStackTrace()               ┆
                 FuncCall: ╭─│ ╰→ printStackTrace()                 ┆
                 FuncCall: ╰→╰→╭─ someOtherCall()                   ┆
                               ╰→ *exit*                           ←╯
      """,
      canThrow = { _, method -> method.name == "randomCall" },
    )
  }

  @Test
  fun checkTryCatchKotlin1d() {
    // Like checkTryCatchKotlin1c, but here the randomCall method is annotated to
    // throw Throwable rather than Exception
    checkAstGraph(
      kotlin(
          """
          import android.app.Activity
          import android.os.PowerManager.WakeLock

          class WakelockActivity : Activity() {
              fun target(lock: WakeLock) {
                  lock.acquire()
                  try {
                      randomCall()
                  } catch (e: Exception) {
                      e.printStackTrace()
                  }
                  someOtherCall()
              }

              @Throws(Throwable::class)
              fun randomCall() {
                  println("test")
              }

              fun someOtherCall() {
                  println("test")
              }
          }
          """
        )
        .indented(),
      """
                    Block:     ╭─ { lock.acquire…meOtherCall() }
      QualifiedExpression:   ╭─╰→ lock.acquire()
                 FuncCall:   ╰→╭─ acquire()
                      Try:   ╭─╰→ try { randomCa…tStackTrace() }
                Try Block:   ╰→╭─ { randomCall() }
                 FuncCall:   ╭─╰→ randomCall()                   ─╮─╮ Exception Throwable
              CatchClause:   │ ╭─ catch (e: Exce…tStackTrace() } ←╯ ┆
                    Block: ╭─│ ╰→ { e.printStackTrace() }           ┆
      QualifiedExpression: ╰→│ ╭─ e.printStackTrace()               ┆
                 FuncCall: ╭─│ ╰→ printStackTrace()                 ┆
                 FuncCall: ╰→╰→╭─ someOtherCall()                   ┆
                               ╰→ *exit*                           ←╯
      """,
      canThrow = { _, method -> method.name == "randomCall" },
    )
  }

  @Test
  fun checkTryCatchKotlin2() {
    checkAstGraph(
      kotlin(
          """
          import java.io.FileNotFoundException
          import java.io.IOException

          fun target() {
              try {
                  randomCall1()
                  randomCall2()
                  randomCall3()
              } catch (e: java.lang.ArrayIndexOutOfBoundsException) {
                  never()
              } catch (e: FileNotFoundException) {
                  here()
              } catch (e: IOException) {
                  never()
              } finally {
                  always()
              }
              done()
          }
          @Throws(FileNotFoundException::class)
          fun randomCall1()

          @Throws(IOException::class)
          fun randomCall2()

          @Throws(Exception::class)
          fun randomCall3()
          """
        )
        .indented(),
      """
              Block:         ╭─ { try { random…ys() } done() }
                Try:       ╭─╰→ try { randomCa…ly { always() }
          Try Block:       ╰→╭─ { randomCall1(…randomCall3() }
           FuncCall:       ╭─╰→ randomCall1()                  ─╮ FileNotFoundException
           FuncCall:       ╰→╭─ randomCall2()                   ┆─╮─╮ FileNotFoundException IOException
           FuncCall:       ╭─╰→ randomCall3()                   ┆ ┆ ┆─╮─╮─╮─╮ ArrayIndexOutOfBoundsException FileNotFoundException
        CatchClause:       │ ╭─ catch (e: java…on) { never() }  ┆ ┆ ┆←╯ ┆ ┆ ┆
              Block:     ╭─│ ╰→ { never() }                     ┆ ┆ ┆   ┆ ┆ ┆
           FuncCall:     ╰→│ ╭─ never()                         ┆ ┆ ┆   ┆ ┆ ┆
        CatchClause:     ╭─│ │  catch (e: File…ion) { here() } ←╯←╯ ┆  ←╯ ┆ ┆
              Block:   ╭─╰→│ │  { here() }                          ┆     ┆ ┆
           FuncCall:   ╰→╭─│ │  here()                              ┆     ┆ ┆
        CatchClause:   ╭─│ │ │  catch (e: IOEx…on) { never() }     ←╯    ←╯ ┆
              Block: ╭─╰→│ │ │  { never() }                                 ┆
           FuncCall: ╰→╭─│ │ │  never()                                     ┆
      Finally Block: ╭─╰→╰→╰→╰→ { always() }                               ←╯
           FuncCall: ╰→      ╭─ always()                       ─╮ Exception
           FuncCall:       ╭─╰→ done()                          ┆
                           ╰→   *exit*                         ←╯
      """,
      canThrow = { _, method -> if (method.name.startsWith("randomCall")) true else null },
      expectedDotGraph =
        """
        digraph {
          labelloc="t"

          node [shape=box]
          subgraph cluster_instructions {
            penwidth=0;
            N00A [label="exit"]
            N00B [label="Block\n{ try { random…ys() } done() }",shape=ellipse]
            N00C [label="Try\ntry { randomCa…ly { always() }"]
            N00D [label="Try Block\n{ randomCall1(…randomCall3() }"]
            N00E [label="FuncCall\nrandomCall1()"]
            N00F [label="FuncCall\nrandomCall2()"]
            N010 [label="FuncCall\nrandomCall3()"]
            N011 [label="CatchClause\ncatch (e: java…on) { never() }"]
            N012 [label="Block\n{ never() }"]
            N013 [label="FuncCall\nnever()"]
            N014 [label="CatchClause\ncatch (e: File…ion) { here() }"]
            N015 [label="Block\n{ here() }"]
            N016 [label="FuncCall\nhere()"]
            N017 [label="CatchClause\ncatch (e: IOEx…on) { never() }"]
            N018 [label="Block\n{ never() }"]
            N019 [label="FuncCall\nnever()"]
            N01A [label="Finally Block\n{ always() }"]
            N01B [label="FuncCall\nalways()"]
            N01C [label="FuncCall\ndone()"]

            N00B -> N00C [label=" then "]
            N00C -> N00D [label=" then "]
            N00D -> N00E [label=" then "]
            N00E -> N00F [label=" s0 "]
            N00E -> N014 [label=" java.io.FileNotFoundException ",style=dashed]
            N00F -> N010 [label=" s0 "]
            N00F -> N014 [label=" java.io.FileNotFoundException ",style=dashed]
            N00F -> N017 [label=" java.io.IOException ",style=dashed]
            N010 -> N01A [label=" finally "]
            N010 -> N011 [label=" java.lang.ArrayIndexOutOfBoundsException ",style=dashed]
            N010 -> N014 [label=" java.io.FileNotFoundException ",style=dashed]
            N010 -> N017 [label=" java.io.IOException ",style=dashed]
            N010 -> N01A [label=" finally ",style=dashed]
            N011 -> N012 [label=" catch "]
            N012 -> N013 [label=" then "]
            N013 -> N01A [label=" finally "]
            N014 -> N015 [label=" catch "]
            N015 -> N016 [label=" then "]
            N016 -> N01A [label=" finally "]
            N017 -> N018 [label=" catch "]
            N018 -> N019 [label=" then "]
            N019 -> N01A [label=" finally "]
            N01A -> N01B [label=" then "]
            N01B -> N01C [label=" s0 "]
            N01B -> N00A [label=" java.lang.Exception ",style=dashed]
            N01C -> N00A [label=" exit "]
          }
        }
        """
    )
  }

  @Test
  fun checkTryCatchKotlin3() {
    // Checks that when we throw an exception of a certain subclass,
    // then a catch of a superclass will consume it.
    checkAstGraph(
      kotlin(
          """
          import java.io.FileNotFoundException
          import java.io.IOException

          fun target() {
              try {
                  randomCall1()
              } catch (e: IOException) {
                  sometimes()
              } catch (e: FileNotFoundException) {
                  never() // because above IOException will take these
              } finally {
                  always()
              }
              done()
          }
          @Throws(FileNotFoundException::class)
          fun randomCall1()
          """
        )
        .indented(),
      """
              Block:     ╭─ { try { random…ys() } done() }
                Try:   ╭─╰→ try { randomCa…ly { always() }
          Try Block:   ╰→╭─ { randomCall1() }
           FuncCall:   ╭─╰→ randomCall1()                  ─╮ FileNotFoundException
        CatchClause:   │ ╭─ catch (e: IOEx…{ sometimes() } ←╯
              Block: ╭─│ ╰→ { sometimes() }
           FuncCall: ╰→│ ╭─ sometimes()
      Finally Block: ╭─╰→╰→ { always() }
           FuncCall: ╰→  ╭─ always()
           FuncCall:   ╭─╰→ done()
                       ╰→   *exit*

        CatchClause:   ╭─ catch (e: File…ll take these }
              Block: ╭─╰→ { never() // b…ll take these }
           FuncCall: ╰→╭─ never()
      Finally Block: ╭─╰→ { always() }
           FuncCall: ╰→╭─ always()
           FuncCall: ╭─╰→ done()
                     ╰→   *exit*
      """,
      canThrow = { _, method -> if (method.name.startsWith("randomCall")) true else null },
    )
  }

  @Test
  fun checkTryCatchKotlin4() {
    // Test case from code review comment: "E.g. an inner FileNotFoundException
    // catch and an outer IOException catch could both have edges if the method
    // declares IOException (and assuming no other subsuming catch blocks),
    // whereas an inner IOException catch would subsume an outer
    // FileNotFoundException catch."
    checkAstGraph(
      kotlin(
          """
          import java.io.FileNotFoundException
          import java.io.IOException

          fun target() {
            try {
              try {
                randomCall1() // declares IOException, but can throw a subclass like FileNotFoundException
              } catch (e: FileNotFoundException) {
                sometimes()
              }
            } catch (e: IOException) {
              sometimes()
            }
          }
          @Throws(IOException::class)
          fun randomCall1() {}
          """
        )
        .indented(),
      """
            Block:       ╭─ { try { try { …sometimes() } }
              Try:     ╭─╰→ try { try { ra…{ sometimes() }
        Try Block:     ╰→╭─ { try { random…sometimes() } }
              Try:     ╭─╰→ try { randomCa…{ sometimes() }
        Try Block:     ╰→╭─ { randomCall1(…oundException }
         FuncCall:     ╭─╰→ randomCall1()                  ─╮─╮ FileNotFoundException IOException
      CatchClause:     │ ╭─ catch (e: File…{ sometimes() } ←╯ ┆
            Block:   ╭─│ ╰→ { sometimes() }                   ┆
         FuncCall:   ╰→│ ╭─ sometimes()                       ┆
      CatchClause:   ╭─│ │  catch (e: IOEx…{ sometimes() }   ←╯
            Block: ╭─╰→│ │  { sometimes() }
         FuncCall: ╰→╭─│ │  sometimes()
                     ╰→╰→╰→ *exit*
      """,
      canThrow = { _, method -> if (method.name.startsWith("randomCall")) true else null },
    )

    checkAstGraph(
      kotlin(
          """
          import java.io.FileNotFoundException
          import java.io.IOException

          fun target() {
            try {
              try {
                randomCall1()
              } catch (e: IOException) {
                sometimes()
              }
            } catch (e: FileNotFoundException) {
              // Never possible, always consumed in inner block
              never()
            }
          }
          @Throws(IOException::class)
          fun randomCall1() {}
          """
        )
        .indented(),
      """
            Block:     ╭─ { try { try { …ock never() } }
              Try:   ╭─╰→ try { try { ra…block never() }
        Try Block:   ╰→╭─ { try { random…sometimes() } }
              Try:   ╭─╰→ try { randomCa…{ sometimes() }
        Try Block:   ╰→╭─ { randomCall1() }
         FuncCall:   ╭─╰→ randomCall1()                  ─╮ IOException
      CatchClause:   │ ╭─ catch (e: IOEx…{ sometimes() } ←╯
            Block: ╭─│ ╰→ { sometimes() }
         FuncCall: ╰→│ ╭─ sometimes()
                     ╰→╰→ *exit*

      CatchClause:   ╭─ catch (e: File…block never() }
            Block: ╭─╰→ { // Never pos…block never() }
         FuncCall: ╰→╭─ never()
                     ╰→ *exit*
      """,
      canThrow = { _, method -> if (method.name.startsWith("randomCall")) true else null },
    )
  }

  @Test
  fun checkMultipleFinallyEdges() {
    // Here we want both an *exceptional* edge and a normal edge from randomCall1 into the finally
    // block
    checkAstGraph(
      kotlin(
          """
          import java.io.FileNotFoundException
          import java.io.IOException

          fun target() {
             try {
                 randomCall1()
             } finally {
                 cleanup()
             }
             next()
          }
          @Throws(FileNotFoundException::class)
          fun randomCall1() {}
          """
        )
        .indented(),
      """
              Block:   ╭─ { try { random…up() } next() }
                Try: ╭─╰→ try { randomCa…y { cleanup() }
          Try Block: ╰→╭─ { randomCall1() }
           FuncCall: ╭─╰→ randomCall1()                  ─╮ FileNotFoundException
      Finally Block: ╰→╭─ { cleanup() }                  ←╯
           FuncCall: ╭─╰→ cleanup()                      ─╮ FileNotFoundException
           FuncCall: ╰→╭─ next()                          ┆
                       ╰→ *exit*                         ←╯
      """,
      canThrow = { _, method -> if (method.name.startsWith("randomCall")) true else null },
      useGraph = { graph, start ->

        // Test the DFS methods to print out all exit paths here -- both with and
        // without followExceptionalFlow enabled.
        fun checkPaths(expected: String, followExceptionalFlow: Boolean) {
          val matches = mutableListOf<List<ControlFlowGraph<UElement>.Edge>>()

          val startNode = graph.getNode(start)!!
          graph.dfs(
            object : ControlFlowGraph.DfsRequest<UElement, Unit>(startNode, Unit) {
              override fun visitNode(
                node: ControlFlowGraph<UElement>.Node,
                path: List<ControlFlowGraph<UElement>.Edge>,
                status: Unit
              ) {
                if (node.isExit()) matches.add(path.toList())
                node.visit = 0
              }

              override val followExceptionalFlow: Boolean = followExceptionalFlow

              override fun consumesException(edge: ControlFlowGraph<UElement>.Edge): Boolean {
                val instruction = edge.to.instruction
                val parent = instruction.uastParent
                return parent is UTryExpression && parent.catchClauses.any { it == instruction }
              }
            }
          )

          assertEquals(
            expected.trimIndent().trim(),
            matches.joinToString("\n") { ControlFlowGraph.describePath(it) }.trim()
          )
        }

        checkPaths(
          """
          try → randomCall1() → finally → cleanup() → next() → exit
          try → randomCall1() → finally → cleanup() → java.io.FileNotFoundException exit
          try → randomCall1() → java.io.FileNotFoundException → cleanup() → next() → exit
          try → randomCall1() → java.io.FileNotFoundException → cleanup() → java.io.FileNotFoundException exit
          """,
          followExceptionalFlow = false
        )

        // Notice how the third path is no longer there: we do not flow to the "next" node via an
        // exception path
        checkPaths(
          """
          try → randomCall1() → finally → cleanup() → next() → exit
          try → randomCall1() → finally → cleanup() → java.io.FileNotFoundException exit
          try → randomCall1() → java.io.FileNotFoundException → cleanup() → java.io.FileNotFoundException exit
          """,
          followExceptionalFlow = true
        )
      }
    )
  }

  @Test
  fun checkExplicitThrow() {
    checkAstGraph(
      kotlin(
          """
          import java.io.FileNotFoundException
          import java.io.IOException

          fun target() {
              try {
                  if (something()) {
                      throw FileNotFoundException("No such file")
                  }
              } catch (e: java.lang.ArrayIndexOutOfBoundsException) {
                  never()
              } catch (e: IOException) {
                  here()
              } finally {
                  always()
              }
              done()
          }
          """
        )
        .indented(),
      """
              Block:     ╭─ { try { if (so…ys() } done() }
                Try:   ╭─╰→ try { if (some…ly { always() }
          Try Block:   ╰→╭─ { if (somethin…such file") } }
                 If:   ╭─╰→ if (something(…o such file") }
           FuncCall: ╭─╰→╭─ something()
         Then Block: │ ╭─╰→ { throw FileNo…o such file") }
           FuncCall: │ ╰→╭─ FileNotFoundEx…"No such file")
              Throw: │   ╰→ throw FileNotF…"No such file") ─╮ FileNotFoundException
        CatchClause: │   ╭─ catch (e: IOEx…ion) { here() } ←╯
              Block: │ ╭─╰→ { here() }
           FuncCall: │ ╰→╭─ here()
      Finally Block: ╰→╭─╰→ { always() }
           FuncCall:   ╰→╭─ always()
           FuncCall:   ╭─╰→ done()
                       ╰→   *exit*

        CatchClause:   ╭─ catch (e: java…on) { never() }
              Block: ╭─╰→ { never() }
           FuncCall: ╰→╭─ never()
      Finally Block: ╭─╰→ { always() }
           FuncCall: ╰→╭─ always()
           FuncCall: ╭─╰→ done()
                     ╰→   *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkTryUncaughtKotlin() {
    checkAstGraph(
      kotlin(
          """
          import android.app.Activity
          import android.os.PowerManager.WakeLock

          class WakelockActivity : Activity() {
              fun target(lock: WakeLock) {
                  lock.acquire()
                  try {
                      randomCall()
                  } finally {
                      lock.release()
                  }
                  next()
              }

              fun randomCall() {
                  println("test")
              }
          }
          """
        )
        .indented(),
      """
                    Block:   ╭─ { lock.acquire…se() } next() }
      QualifiedExpression: ╭─╰→ lock.acquire()
                 FuncCall: ╰→╭─ acquire()
                      Try: ╭─╰→ try { randomCa…ock.release() }
                Try Block: ╰→╭─ { randomCall() }
                 FuncCall: ╭─╰→ randomCall()                   ─╮ Exception
            Finally Block: ╰→╭─ { lock.release() }             ←╯
      QualifiedExpression: ╭─╰→ lock.release()
                 FuncCall: ╰→╭─ release()                      ─╮ Exception
                 FuncCall: ╭─╰→ next()                          ┆
                           ╰→   *exit*                         ←╯
      """,
      canThrow = { _, method -> method.name == "randomCall" }
    )
  }

  @Test
  fun checkTryUncaughtKotlin2() {
    checkAstGraph(
      kotlin(
          """
          import android.app.Activity
          import android.os.PowerManager.WakeLock

          class WakelockActivity : Activity() {
              fun target(lock: WakeLock) {
                try {
                    lock.acquire()
                    try {
                        randomCall()
                    } finally {
                        lock.release()
                    }
                    next()
                } catch (e: Throwable) {
                  catchBlock()
                } finally {
                  outerFinally()
                }
                after()
              }

              fun randomCall() {
                  println("test")
              }
          }
          """
        )
        .indented(),
      """
                    Block:     ╭─ { try { lock.a…y() } after() }
                      Try:   ╭─╰→ try { lock.acq…uterFinally() }
                Try Block:   ╰→╭─ { lock.acquire…se() } next() }
      QualifiedExpression:   ╭─╰→ lock.acquire()
                 FuncCall:   ╰→╭─ acquire()
                      Try:   ╭─╰→ try { randomCa…ock.release() }
                Try Block:   ╰→╭─ { randomCall() }
                 FuncCall:   ╭─╰→ randomCall()                   ─╮ Exception
            Finally Block:   ╰→╭─ { lock.release() }             ←╯
      QualifiedExpression:   ╭─╰→ lock.release()
                 FuncCall:   ╰→╭─ release()                      ─╮ Exception
                 FuncCall:   ╭─╰→ next()                          ┆
              CatchClause:   │ ╭─ catch (e: Thro… catchBlock() } ←╯
                    Block: ╭─│ ╰→ { catchBlock() }
                 FuncCall: ╰→│ ╭─ catchBlock()
            Finally Block: ╭─╰→╰→ { outerFinally() }
                 FuncCall: ╰→  ╭─ outerFinally()
                 FuncCall:   ╭─╰→ after()
                             ╰→   *exit*
      """,
      canThrow = { _, method -> method.name == "randomCall" }
    )
  }

  @Test
  fun checkTryFinallyKotlin() {
    // Try/finally (with no catch) -- make sure we also exit
    checkAstGraph(
      kotlin(
          """
          import android.app.Activity
          import android.os.PowerManager.WakeLock

          class WakelockActivity : Activity() {
              fun target(lock: WakeLock) {
                  lock.acquire()
                  try {
                      randomCall()
                  } finally {
                      lock.release()
                  }
              }

              fun randomCall() {
                  println("test")
              }
          }
          """
        )
        .indented(),
      """
                    Block:   ╭─ { lock.acquire…k.release() } }
      QualifiedExpression: ╭─╰→ lock.acquire()
                 FuncCall: ╰→╭─ acquire()
                      Try: ╭─╰→ try { randomCa…ock.release() }
                Try Block: ╰→╭─ { randomCall() }
                 FuncCall: ╭─╰→ randomCall()                   ─╮ Exception
            Finally Block: ╰→╭─ { lock.release() }             ←╯
      QualifiedExpression: ╭─╰→ lock.release()
                 FuncCall: ╰→╭─ release()                      ─╮ Exception
                             ╰→ *exit*                         ←╯
      """,
      canThrow = { _, method -> method.name == "randomCall" }
    )
  }

  @Test
  fun checkTryFinallyEmpty() {
    checkAstGraph(
      kotlin(
          """
          import android.app.Activity
          import android.os.PowerManager.WakeLock

          class WakelockActivity : Activity() {
              fun target(lock: WakeLock) {
                  try {
                      randomCall()
                  } finally {
                  }
                  next()
              }
              fun randomCall() { error("throws") }
          }
          """
        )
        .indented(),
      """
              Block:   ╭─ { try { random…ly { } next() }
                Try: ╭─╰→ try { randomCa…) } finally { }
          Try Block: ╰→╭─ { randomCall() }
           FuncCall: ╭─╰→ randomCall()                   ─╮ Exception
      Finally Block: ╰→╭─ { }                            ←╯─╮ Exception
           FuncCall: ╭─╰→ next()                            ┆
                     ╰→   *exit*                           ←╯
      """,
      canThrow = { _, method -> method.name == "randomCall" }
    )
  }

  @Test
  fun checkCallExceptionEdge1() {
    checkAstGraph(
      kotlin(
          """
          fun target() {
              var i = 0
              randomCall()
              i++
          }
          fun randomCall() { error("throws") }
          """
        )
        .indented(),
      """
              Block:   ╭─ { var i = 0 randomCall() i++ }
      LocalVariable: ╭─╰→ var i = 0
       Declarations: ╰→╭─ var i = 0
           FuncCall: ╭─╰→ randomCall()                   ─╮ Exception
            Postfix: ╰→╭─ i++                             ┆
                       ╰→ *exit*                         ←╯
      """
    )
  }

  @Test
  fun checkCallExceptionEdge2() {
    // From TransactionDetectorTest.testSimpleBlockingCall
    checkAstGraph(
      kotlin(
          """
          package test.pkg

          import android.os.Trace

          fun target() {
              Trace.beginSection("wrong")
              blockingCall()
              Trace.endSection()
          }

          fun blockingCall() { }
          """
        )
        .indented(),
      """
                    Block:   ╭─ { Trace.beginS….endSection() }
      QualifiedExpression: ╭─╰→ Trace.beginSection("wrong")
                 FuncCall: ╰→╭─ beginSection("wrong")
                 FuncCall: ╭─╰→ blockingCall()                 ─╮ Exception
      QualifiedExpression: ╰→╭─ Trace.endSection()              ┆
                 FuncCall: ╭─╰→ endSection()                    ┆
                           ╰→   *exit*                         ←╯
      """,
      canThrow = { _, method -> method.name == "blockingCall" }
    )
  }

  @Test
  fun checkCallExceptionFromCatch() {
    // From TransactionDetectorTest.testTryCatchVariations.wrong1
    checkAstGraph(
      kotlin(
          """
          fun target() {
              Trace.beginSection("wrong-1")
              try {
                blockingCall()
              } catch (e: Exception) {
                e.printStackTrace()
              } finally {
                // finally
              }
              // This is wrong for two reasons: 1) `e.printStackTrace()` could itself throw an exception, and
              // 2) we can't verify that all possible exceptions are caught. The only way to guarantee a
              // `Trace.endSection()` is called is by using a `finally` block.
              Trace.endSection()
          }
          fun blockingCall() { }
          """
        )
        .indented(),
      """
                    Block:     ╭─ { Trace.beginS….endSection() }
      QualifiedExpression:   ╭─╰→ Trace.beginSection("wrong-1")
                 FuncCall:   ╰→╭─ beginSection("wrong-1")
                      Try:   ╭─╰→ try { blocking… { // finally }
                Try Block:   ╰→╭─ { blockingCall() }
                 FuncCall:   ╭─╰→ blockingCall()                 ─╮ Exception
              CatchClause:   │ ╭─ catch (e: Exce…tStackTrace() } ←╯
                    Block: ╭─│ ╰→ { e.printStackTrace() }
      QualifiedExpression: ╰→│ ╭─ e.printStackTrace()
                 FuncCall: ╭─│ ╰→ printStackTrace()              ─╮ Exception
            Finally Block: ╰→╰→╭─ { // finally }                 ←╯─╮ Exception
      QualifiedExpression:   ╭─╰→ Trace.endSection()                ┆
                 FuncCall:   ╰→╭─ endSection()                      ┆
                               ╰→ *exit*                           ←╯
      """,
      canThrow = { _, method -> method.name == "blockingCall" || method.name == "printStackTrace" }
    )
  }

  @Test
  fun checkCallExceptionFromFinally() {
    // From TransactionDetectorTest.testTryCatchVariations.wrong4
    checkAstGraph(
      kotlin(
          """
          fun target() {
              try {
                Trace.beginSection("wrong-4")
              } finally {
                // The blocking call could throw an exception, and there is nothing here to catch it
                blockingCall()
                Trace.endSection()
              }
          }
          fun blockingCall() { }
          """
        )
        .indented(),
      """
                    Block:   ╭─ { try { Trace.…ndSection() } }
                      Try: ╭─╰→ try { Trace.be….endSection() }
                Try Block: ╰→╭─ { Trace.beginS…on("wrong-4") }
      QualifiedExpression: ╭─╰→ Trace.beginSection("wrong-4")
                 FuncCall: ╰→╭─ beginSection("wrong-4")
            Finally Block: ╭─╰→ { // The block….endSection() }
                 FuncCall: ╰→╭─ blockingCall()                 ─╮ Exception
      QualifiedExpression: ╭─╰→ Trace.endSection()              ┆
                 FuncCall: ╰→╭─ endSection()                    ┆
                             ╰→ *exit*                         ←╯
      """,
      canThrow = { _, method -> method.name == "blockingCall" }
    )
  }

  @Test
  fun checkNestedFinally() {
    // makes sure that if we have an exception thrown, there is a path through the
    // finally blocks that doesn't touch the non-finally blocks.
    checkAstGraph(
      kotlin(
          """
          fun target() {
              try {
                before()
                try {
                  blockingCall()
                } finally {
                  firstFinally1()
                  firstFinally2()
                }
                after()
              } finally {
                secondFinally1()
                secondFinally2()
              }
              done()
          }
          fun blockingCall() { }
          """
        )
        .indented(),
      """
              Block:   ╭─ { try { before…y2() } done() }
                Try: ╭─╰→ try { before()…ondFinally2() }
          Try Block: ╰→╭─ { before() try…2() } after() }
           FuncCall: ╭─╰→ before()
                Try: ╰→╭─ try { blocking…rstFinally2() }
          Try Block: ╭─╰→ { blockingCall() }
           FuncCall: ╰→╭─ blockingCall()                 ─╮ Exception
      Finally Block: ╭─╰→ { firstFinally…rstFinally2() } ←╯
           FuncCall: ╰→╭─ firstFinally1()
           FuncCall: ╭─╰→ firstFinally2()                ─╮ Exception
           FuncCall: ╰→╭─ after()                         ┆
      Finally Block: ╭─╰→ { secondFinall…ondFinally2() } ←╯
           FuncCall: ╰→╭─ secondFinally1()
           FuncCall: ╭─╰→ secondFinally2()               ─╮ Exception
           FuncCall: ╰→╭─ done()                          ┆
                       ╰→ *exit*                         ←╯
      """,
      canThrow = { _, method -> method.name == "blockingCall" },
      useGraph = { graph, start ->
        // Check some path computations
        checkGraphPath(
          graph,
          start,
          targetNode = { call ->
            call is UCallExpression && call.methodIdentifier?.name == "before"
          },
          expected = "try → before()",
        )
        checkGraphPath(
          graph,
          start,
          targetNode = { call ->
            call is UCallExpression && call.methodIdentifier?.name == "secondFinally2"
          },
          expected =
            "try → before() → try → blockingCall() → finally → firstFinally1() → firstFinally2() → after() → finally → secondFinally1() → secondFinally2()",
        )
      }
    )
  }

  @Test
  fun checkIfElse() {
    checkAstGraph(
      java(
          """
          package test.pkg;

          import android.app.Activity;
          import android.os.PowerManager.WakeLock;

          public class WakelockActivity extends Activity {
              void target(WakeLock lock) {
                  lock.acquire();
                  if (getTaskId() == 50) {
                      randomCall1();
                      randomCall2();
                  } else {
                      lock.release();
                  }
              }
          }
          """
        )
        .indented(),
      """
                CodeBlock:     ╭─ { lock.acquire….release(); } }
      QualifiedExpression:   ╭─╰→ lock.acquire()
                     Call:   ╰→╭─ lock.acquire()
                       If:   ╭─╰→ if (getTaskId(…ck.release(); }
                   Binary:   ╰→╭─ getTaskId() == 50
                     Call: ╭─╭─╰→ getTaskId()
               Then Block: │ ╰→╭─ { randomCall1(…andomCall2(); }
                     Call: │ ╭─╰→ randomCall1()
                     Call: │ ╰→╭─ randomCall2()
               Else Block: ╰→╭─│  { lock.release(); }
      QualifiedExpression: ╭─╰→│  lock.release()
                     Call: ╰→╭─│  lock.release()
                             ╰→╰→ *exit*
      """,
      canThrow = { _, _ -> false },
      expectedDotGraph =
        """
        digraph {
          labelloc="t"

          node [shape=box]
          subgraph cluster_instructions {
            penwidth=0;
            N00A [label="exit"]
            N00B [label="CodeBlock\n{ lock.acquire….release(); } }",shape=ellipse]
            N00C [label="QualifiedExpression\nlock.acquire()"]
            N00D [label="Call\nlock.acquire()"]
            N00E [label="If\nif (getTaskId(…ck.release(); }"]
            N00F [label="Binary\ngetTaskId() == 50"]
            N010 [label="Call\ngetTaskId()"]
            N011 [label="Then Block\n{ randomCall1(…andomCall2(); }"]
            N012 [label="Call\nrandomCall1()"]
            N013 [label="Call\nrandomCall2()"]
            N014 [label="Else Block\n{ lock.release(); }"]
            N015 [label="QualifiedExpression\nlock.release()"]
            N016 [label="Call\nlock.release()"]

            N00B -> N00C [label=" then "]
            N00C -> N00D [label=" then "]
            N00D -> N00E [label=" then "]
            N00E -> N00F [label=" then "]
            N00F -> N010 [label=" then "]
            N010 -> N011 [label=" then "]
            N010 -> N014 [label=" else "]
            N011 -> N012 [label=" then "]
            N012 -> N013 [label=" then "]
            N013 -> N00A [label=" exit "]
            N014 -> N015 [label=" then "]
            N015 -> N016 [label=" then "]
            N016 -> N00A [label=" exit "]
          }
        }
        """
    )
  }

  @Test
  fun checkNullGuard() {
    val testFile =
      java(
          """
          package test.pkg;

          import android.app.Activity;
          import android.os.PowerManager.WakeLock;

          public class WakelockActivity extends Activity {
              public void target(WakeLock lock) {
                  lock.acquire();
                  if (lock != null) {
                      lock.release();
                  } else {
                      int i = 0;
                  }
              }
          }
          """
        )
        .indented()

    checkAstGraph(
      testFile,
      """
                CodeBlock:     ╭─ { lock.acquire… int i = 0; } }
      QualifiedExpression:   ╭─╰→ lock.acquire()
                     Call:   ╰→╭─ lock.acquire()
                       If:   ╭─╰→ if (lock != nu… { int i = 0; }
                   Binary: ╭─╰→╭─ lock != null
               Then Block: │ ╭─╰→ { lock.release(); }
      QualifiedExpression: │ ╰→╭─ lock.release()
                     Call: │ ╭─╰→ lock.release()
               Else Block: ╰→│ ╭─ { int i = 0; }
            LocalVariable: ╭─│ ╰→ int i = 0;
             Declarations: ╰→│ ╭─ var i: int = 0
                             ╰→╰→ *exit*
      """,
      canThrow = { _, _ -> false }
    )

    // Prune graph to only follow then-branch
    checkAstGraph(
      testFile,
      """
                CodeBlock:   ╭─ { lock.acquire… int i = 0; } }
      QualifiedExpression: ╭─╰→ lock.acquire()
                     Call: ╰→╭─ lock.acquire()
                       If: ╭─╰→ if (lock != nu… { int i = 0; }
                   Binary: ╰→╭─ lock != null
               Then Block: ╭─╰→ { lock.release(); }
      QualifiedExpression: ╰→╭─ lock.release()
                     Call: ╭─╰→ lock.release()
                           ╰→   *exit*
      """,
      canThrow = { _, _ -> false },
      checkBranchPaths = { ControlFlowGraph.FollowBranch.THEN }
    )

    // Prune graph to only follow else-branch
    checkAstGraph(
      testFile,
      """
                CodeBlock:   ╭─ { lock.acquire… int i = 0; } }
      QualifiedExpression: ╭─╰→ lock.acquire()
                     Call: ╰→╭─ lock.acquire()
                       If: ╭─╰→ if (lock != nu… { int i = 0; }
                   Binary: ╰→╭─ lock != null
               Else Block: ╭─╰→ { int i = 0; }
            LocalVariable: ╰→╭─ int i = 0;
             Declarations: ╭─╰→ var i: int = 0
                           ╰→   *exit*
      """,
      canThrow = { _, _ -> false },
      checkBranchPaths = { ControlFlowGraph.FollowBranch.ELSE }
    )
  }

  @Test
  fun checkNullGuardWhenStatement() {
    val testFile =
      kotlin(
          """
        import android.os.PowerManager.WakeLock
        fun target(lock: WakeLock?) {
            lock?.acquire()
            when {
                lock != null -> lock.release()
                else -> {
                  var i = 0
                }
            }
        }
        """
        )
        .indented()

    checkAstGraph(
      testFile,
      """
                     Block:         ╭─ { lock?.acquir…var i = 0 } } }
      SafeQuali…Expression:     ╭─╭─╰→ lock?.acquire()
                  FuncCall:     │ ╰→╭─ acquire()
                    Switch:     ╰→╭─╰→ when { lock !=…{ var i = 0 } }
                    Binary:     ╭─╰→╭─ lock != null
           SwitchEntryBody:     │ ╭─╰→ lock != null -> lock.release()
           SwitchEntryBody: ╭→  │ │ ╭─ yield lock.release()
       QualifiedExpression: │ ╭─│ ╰→│  lock.release()
                  FuncCall: ╰─╰→│   │  release()
           SwitchEntryBody:     ╰→╭─│  else -> { var i = 0 }
           SwitchEntryBody: ╭→  ╭─│ │  yield var i: int = 0
             LocalVariable: │ ╭─│ ╰→│  var i = 0
              Declarations: ╰─╰→│   │  var i = 0
                                ╰→  ╰→ *exit*
      """,
      canThrow = { _, _ -> false }
    )

    // Prune graph to only follow then-branch
    checkAstGraph(
      testFile,
      """
                     Block:       ╭─ { lock?.acquir…var i = 0 } } }
      SafeQuali…Expression:   ╭─╭─╰→ lock?.acquire()
                  FuncCall:   │ ╰→╭─ acquire()
                    Switch:   ╰→╭─╰→ when { lock !=…{ var i = 0 } }
                    Binary:     ╰→╭─ lock != null
           SwitchEntryBody:     ╭─╰→ lock != null -> lock.release()
           SwitchEntryBody: ╭→  │ ╭─ yield lock.release()
       QualifiedExpression: │ ╭─╰→│  lock.release()
                  FuncCall: ╰─╰→  │  release()
                                  ╰→ *exit*
      """,
      canThrow = { _, _ -> false },
      checkBranchPaths = { ControlFlowGraph.FollowBranch.THEN }
    )

    // Prune graph to only follow else-branch
    checkAstGraph(
      testFile,
      """
                     Block:       ╭─ { lock?.acquir…var i = 0 } } }
      SafeQuali…Expression:   ╭─╭─╰→ lock?.acquire()
                  FuncCall:   │ ╰→╭─ acquire()
                    Switch:   ╰→╭─╰→ when { lock !=…{ var i = 0 } }
           SwitchEntryBody:     ╰→╭─ else -> { var i = 0 }
           SwitchEntryBody: ╭→  ╭─│  yield var i: int = 0
             LocalVariable: │ ╭─│ ╰→ var i = 0
              Declarations: ╰─╰→│    var i = 0
                                ╰→   *exit*
      """,
      canThrow = { _, _ -> false },
      checkBranchPaths = { ControlFlowGraph.FollowBranch.ELSE }
    )
  }

  @Test
  fun checkAlwaysTrueOrFalse() {
    checkAstGraph(
      kotlin(
          """
          fun target() {
              if (true)
                 always()
              else
                  never()
              if (false)
                  never()
              else
                  always()
          }
          """
        )
        .indented(),
      """
         Block:   ╭─ { if (true) al…else always() }
            If: ╭─╰→ if (true) alwa…() else never()
      FuncCall: ╰→╭─ always()
            If: ╭─╰→ if (false) nev…) else always()
      FuncCall: ╰→╭─ always()
                  ╰→ *exit*
      """,
      canThrow = { _, _ -> false },
    )
  }

  @Test
  fun checkExceptionsForImplicitCalls() {
    // Make sure we add exception edges for various implicit calls in Kotlin
    checkAstGraph(
      kotlin(
          """
          package test.pkg

          import java.math.BigInteger

          fun target(b1: BigInteger, b2: BigInteger, list: MutableList<Int>) {
              try {
                  b1 + b2 // operator overloading (.plus)
                  list[0] // operator overloading (.get)
                  list[1] = 1 // operator overloading (.set)
                  !b1 // operator overloading (.not)
                  3 in list // operator overloading (.contains)
                  // Do we worry about really exceptional cases like division by zero? Does ASM?
              } catch (e: Exception) {
                  e.printStackTrace()
              }
          }
          """
        )
        .indented(),
      """
                    Block:     ╭─ { try { b1 + b…tackTrace() } }
                      Try:   ╭─╰→ try { b1 + b2 …tStackTrace() }
                Try Block:   ╰→╭─ { b1 + b2 // o…ro? Does ASM? }
                   Binary:   ╭─╰→ b1 + b2                        ─╮ Exception
              ArrayAccess:   ╰→╭─ list[0]                         ┆─╮ Exception
                   Binary:   ╭─╰→ list[1] = 1                     ┆ ┆─╮ Exception
              ArrayAccess:   ╰→╭─ list[1]                         ┆ ┆ ┆
                   Prefix:   ╭─╰→ !b1                             ┆ ┆ ┆─╮ Exception
                   Binary:   ╰→╭─ 3 in list                       ┆ ┆ ┆ ┆─╮ Exception
              CatchClause:   ╭─│  catch (e: Exce…tStackTrace() } ←╯←╯←╯←╯←╯
                    Block: ╭─╰→│  { e.printStackTrace() }
      QualifiedExpression: ╰→╭─│  e.printStackTrace()
                 FuncCall: ╭─╰→│  printStackTrace()
                           ╰→  ╰→ *exit*
      """,
      useGraph = { graph, start ->
        checkGraphPath(
          graph,
          start,
          targetNode = { call ->
            call is UCallExpression && call.methodIdentifier?.name == "printStackTrace"
          },
          expected = "try → + → = → ! → in → catch → printStackTrace()",
        )
      }
    )
  }

  @Test
  fun checkExceptionsForImplicitPropertyCalls() {
    // Make sure we add exception edges for implicit calls related to properties
    checkAstGraph(
      kotlin(
          """
          class MyTest {
            var foo: Int = 1
            var bar: Int get() = 1
              set(value) { }
          }

          fun target(test: MyTest) {
            with (test) {
              val notCall2 = foo
              val call2 = bar
            }
          }
          """
        )
        .indented(),
      """
                Block:   ╭─ { with (test) …call2 = bar } }
             FuncCall: ╭─╰→ with (test) { …l call2 = bar } ─╮ Exception
               Lambda: ╰→╭─ { val notCall2…l call2 = bar }  ┆
                 Body: ╭─╰→ val notCall2 =…val call2 = bar  ┆
        LocalVariable: ╰→╭─ val notCall2 = foo              ┆
         Declarations: ╭─╰→ val notCall2 = foo              ┆
            SimpleRef: ╰→╭─ bar                             ┆─╮ Exception
        LocalVariable: ╭─╰→ val call2 = bar                 ┆ ┆
         Declarations: ╰→╭─ val call2 = bar                 ┆ ┆
      Implicit Return: ╭─╰→ return var call2: int = bar     ┆ ┆
                       ╰→   *exit*                         ←╯←╯
      """,
      canThrow = { _, _ -> true }
    )
  }

  @Test
  fun checkExceptionsForImplicitPropertyCalls2() {
    // Make sure we add exception edges for implicit calls related to properties
    checkAstGraph(
      kotlin(
          """
          class MyTest {
            var foo: Int = 1
            var bar: Int get() = 1
              set(value) { }
            val next: MyTest get() = MyTest()
            val lazyValue: String by lazy {
              "Hello"
            }
          }

          fun target(test: MyTest) {
            val notCall1 = test.foo
            val call1 = test.bar
            val call3 = test.next.next
            test.foo = 2
            test.bar = 2
            test.lazyValue
          }
          """
        )
        .indented(),
      """
                    Block:   ╭─ { val notCall1…est.lazyValue }
      QualifiedExpression: ╭─╰→ test.foo
            LocalVariable: ╰→╭─ val notCall1 = test.foo
             Declarations: ╭─╰→ val notCall1 = test.foo
      QualifiedExpression: ╰→╭─ test.bar
                SimpleRef: ╭─╰→ bar                            ─╮ Exception
            LocalVariable: ╰→╭─ val call1 = test.bar            ┆
             Declarations: ╭─╰→ val call1 = test.bar            ┆
      QualifiedExpression: ╰→╭─ test.next.next                  ┆
      QualifiedExpression: ╭─╰→ test.next                       ┆
                SimpleRef: ╰→╭─ next                            ┆─╮ Exception
                SimpleRef: ╭─╰→ next                            ┆ ┆─╮ Exception
            LocalVariable: ╰→╭─ val call3 = test.next.next      ┆ ┆ ┆
             Declarations: ╭─╰→ val call3 = test.next.next      ┆ ┆ ┆
                   Binary: ╰→╭─ test.foo = 2                    ┆ ┆ ┆
      QualifiedExpression: ╭─╰→ test.foo                        ┆ ┆ ┆
                   Binary: ╰→╭─ test.bar = 2                    ┆ ┆ ┆
      QualifiedExpression: ╭─╰→ test.bar                        ┆ ┆ ┆
                SimpleRef: ╰→╭─ bar                             ┆ ┆ ┆─╮ Exception
      QualifiedExpression: ╭─╰→ test.lazyValue                  ┆ ┆ ┆ ┆
                SimpleRef: ╰→╭─ lazyValue                       ┆ ┆ ┆ ┆─╮ Exception
                             ╰→ *exit*                         ←╯←╯←╯←╯←╯
      """,
      canThrow = { _, _ -> true }
    )
  }

  @Test
  fun checkForLoop() {
    checkAstGraph(
      java(
          """
          package test.pkg;

          public class Test {
            public void target() {
                for (int i = 0; i < 10; i++) {
                  if (i % 2 == 1) {
                    break;
                  }
                  if (i % 2 == 0) {
                    continue;
                  }
                  if (something())
                    print(i);
                  else
                    print(j);
                }
                print("done");
            }
          }
          """
        )
        .indented(),
      """
          CodeBlock:               ╭─ { for (int i =…rint("done"); }
                For:             ╭─╰→ for (int i = 0…lse print(j); }
      LocalVariable:             ╰→╭─ int i = 0;
       Declarations:             ╭─╰→ var i: int = 0
             Binary:         ╭→╭─╰→╭─ i < 10
            Postfix: ╭→╭→╭→  ╰─│   │  i++
              Block: │ │ │     │ ╭─╰→ { if (i % 2 ==…lse print(j); }
                 If: │ │ │     │ ╰→╭─ if (i % 2 == 1) { break; }
             Binary: │ │ │     │ ╭─╰→ i % 2 == 1
             Binary: │ │ │   ╭─│ ╰→╭─ i % 2
         Then Block: │ │ │   │ │ ╭─╰→ { break; }
              Break: │ │ │   │ │ ╰→╭─ break;
                 If: │ │ │   ╰→│ ╭─│  if (i % 2 == 0) { continue; }
             Binary: │ │ │   ╭─│ ╰→│  i % 2 == 0
             Binary: │ │ │ ╭─╰→│ ╭─│  i % 2
         Then Block: │ │ │ │ ╭─│ ╰→│  { continue; }
           Continue: │ │ ╰─│ ╰→│   │  continue;
                 If: │ │   ╰→  │ ╭─│  if (something(… else print(j);
               Call: │ │   ╭─╭─│ ╰→│  something()
               Call: │ ╰─  │ ╰→│   │  print(i)
               Call: ╰─    ╰→  │   │  print(j)
               Call:           ╰→╭─╰→ print("done")
                                 ╰→   *exit*
      """,
      canThrow = { _, _ -> false },
      useGraph = { graph, start ->
        checkGraphPath(
          graph,
          start,
          targetNode = { call ->
            call is UCallExpression && call.methodIdentifier?.name == "print"
          },
          expected = "for → < → if → === → % → then → break → print()",
        )
        checkGraphPath(
          graph,
          start,
          targetNode = { node -> node is UBreakExpression },
          expected = "for → < → if → === → % → then → break",
        )
      }
    )
  }

  @Test
  fun checkForEachLoop() {
    // Misc test from TraceSectionDetectorTest.testOkLoop which
    // wasn't handled correctly; finishing the try block wouldn't
    // return out of the for loop.
    checkAstGraph(
      kotlin(
          """
          package test.pkg

          import android.os.Trace

          fun target() {
            for (i in 0..10) {
               nested()
            }
            next()
          }
          """
        )
        .indented(),
      """
         Block:       ╭─ { for (i in 0.…ed() } next() }
       ForEach: ╭→╭─╭─╰→ for (i in 0..10) { nested() }
         Block: │ │ ╰→╭─ { nested() }
      FuncCall: ╰─│   ╰→ nested()
      FuncCall:   ╰→  ╭─ next()
                      ╰→ *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkForEachLoopWithBreak() {
    checkAstGraph(
      java(
          """
          package test.pkg;

          public class Test {
            public void target(List<String> list) {
              for (String s : list) {
                if (s.length() > 5) {
                  break;
                }
                println("looping");
              }
              println("Done");
            }
          }
          """
        )
        .indented(),
      """
                CodeBlock:         ╭─ { for (String …ntln("Done"); }
                  ForEach: ╭→  ╭─╭─╰→ for (String s …n("looping"); }
                    Block: │   │ ╰→╭─ { if (s.length…n("looping"); }
                       If: │   │ ╭─╰→ if (s.length() > 5) { break; }
                   Binary: │   │ ╰→╭─ s.length() > 5
      QualifiedExpression: │   │ ╭─╰→ s.length()
                     Call: │ ╭─│ ╰→╭─ s.length()
               Then Block: │ │ │ ╭─╰→ { break; }
                    Break: │ │ │ ╰→╭─ break;
                     Call: ╰─╰→│   │  println("looping")
                     Call:     ╰→╭─╰→ println("Done")
                                 ╰→   *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkDoWhileLoop() {
    checkAstGraph(
      java(
          """
          package test.pkg;

          public class Test {
            public void target(int i, int j) {
              do {
                i++;
                if (i > j / 2) break;
                if (i == 2) continue;
                j++;
              } while (i < 2 * j);
              println("Done");
            }
          }
          """
        )
        .indented(),
      """
      CodeBlock:         ╭─ { do { i++; if…ntln("Done"); }
        DoWhile: ╭→    ╭─╰→ do { i++; if (…le (i < 2 * j);
          Block: │     ╰→╭─ { i++; if (i >…ontinue; j++; }
        Postfix: │     ╭─╰→ i++
             If: │     ╰→╭─ if (i > j / 2) break;
         Binary: │     ╭─╰→ i > j / 2
         Binary: │   ╭─╰→╭─ j / 2
          Break: │   │ ╭─╰→ break;
             If: │   ╰→│ ╭─ if (i == 2) continue;
         Binary: │ ╭─╭─│ ╰→ i == 2
       Continue: │ │ ╰→│ ╭─ continue;
        Postfix: │ ╰→╭─│ │  j++
         Binary: │ ╭─╰→│ ╰→ i < 2 * j
         Binary: ╰─╰→  │ ╭─ 2 * j
           Call:     ╭─╰→╰→ println("Done")
                     ╰→     *exit*
       """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkWhileLoop() {
    checkAstGraph(
      java(
          """
          package test.pkg;

          public class Test {
            public void target(int i, int j) {
              while (i < j) {
                if (i > j / 2) break;
                j++;
              }
              println("Done");
            }
          }
          """
        )
        .indented(),
      """
      CodeBlock:         ╭─ { while (i < j…ntln("Done"); }
          While: ╭→  ╭─╭─╰→ while (i < j) …) break; j++; }
          Block: │   │ ╰→╭─ { if (i > j / 2) break; j++; }
             If: │   │ ╭─╰→ if (i > j / 2) break;
         Binary: │   │ ╰→╭─ i > j / 2
         Binary: │ ╭─│ ╭─╰→ j / 2
          Break: │ │ │ ╰→╭─ break;
        Postfix: ╰─╰→│   │  j++
           Call:     ╰→╭─╰→ println("Done")
                       ╰→   *exit*
       """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkJavaSwitch() {
    checkAstGraph(
      java(
          """
          package test.pkg;

          public class Test {
            public void target(int value) {
                switch (value) {
                  case 0: System.out.println("Zero");
                  // fallthrough
                  case 1: {
                    System.out.println("One");
                    break;
                  }
                  case 2:
                  case 3: {
                    System.out.println("Two or Three");
                    // fallthrough
                  }
                  default: System.out.println("Something else");
                }
            }
          }
          """
        )
        .indented(),
      """
                CodeBlock:         ╭─ { switch (valu…ing else"); } }
                   Switch: ╭─╭─╭─╭─╰→ switch (value)…thing else"); }
          SwitchEntryBody: │ │ │ ╰→╭─ case 0:
      QualifiedExpression: │ │ │ ╭─╰→ System.out.println("Zero")
      QualifiedExpression: │ │ │ ╰→╭─ System.out
                     Call: │ │ │ ╭─╰→ System.out.println("Zero")
          SwitchEntryBody: │ │ ╰→╰→╭─ case 1:
                    Block: │ │   ╭─╰→ { System.out.p…One"); break; }
      QualifiedExpression: │ │   ╰→╭─ System.out.println("One")
      QualifiedExpression: │ │   ╭─╰→ System.out
                     Call: │ │   ╰→╭─ System.out.println("One")
                    Break: │ │   ╭─╰→ break;
          SwitchEntryBody: │ ╰→  │ ╭─ case 2:
                    Block: │   ╭─│ ╰→ { System.out.p…/ fallthrough }
      QualifiedExpression: │   ╰→│ ╭─ System.out.pri…"Two or Three")
      QualifiedExpression: │   ╭─│ ╰→ System.out
                     Call: │   ╰→│ ╭─ System.out.pri…"Two or Three")
              DefaultCase: ╰→  ╭─│ │  default:
          SwitchEntryBody:   ╭─╰→│ ╰→ default:
      QualifiedExpression:   ╰→  │ ╭─ System.out.pri…omething else")
      QualifiedExpression:     ╭─│ ╰→ System.out
                     Call:     ╰→│ ╭─ System.out.pri…omething else")
                                 ╰→╰→ *exit*
      """,
      // keep graph simpler for test
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkKotlinWhenStatementWithSubject() {
    checkAstGraph(
      kotlin(
          """
          package test.pkg

          fun target(list: List<String>) {
            when (list) {
              is ArrayDeque,
              is ArrayList -> {
                println("array")
              }
              is MutableList -> {
                println("mutable")
              }
              else -> {
                println("Something else")
              }
            }
            println("after")
          }
          """
        )
        .indented(),
      """
                 Block:         ╭─ { when (list) …ntln("after") }
                Switch:       ╭─╰→ when (list) { …hing else") } }
       SwitchEntryBody:   ╭→  │ ╭─ is ArrayDeque,…ntln("array") }
               TypeRef:   │ ╭─╰→│  ArrayDeque
      BinaryExWithType:   │ ╰→╭─│  is ArrayDeque
               TypeRef:   │ ╭─╰→│  ArrayList
      BinaryExWithType:   ╰─╰→╭─│  is ArrayList
       SwitchEntryBody:   ╭→╭─│ │  yield println("array")
              FuncCall:   ╰─│ │ ╰→ println("array")
       SwitchEntryBody: ╭→  │ │ ╭─ is MutableList…ln("mutable") }
               TypeRef: │ ╭─│ ╰→│  MutableList
      BinaryExWithType: ╰─╰→│ ╭─│  is MutableList
       SwitchEntryBody: ╭→╭─│ │ │  yield println("mutable")
              FuncCall: ╰─│ │ │ ╰→ println("mutable")
       SwitchEntryBody:   │ │ ╰→╭─ else -> { prin…ething else") }
       SwitchEntryBody: ╭→│ │ ╭─│  yield println(…omething else")
              FuncCall: ╰─│ │ │ ╰→ println("Something else")
              FuncCall:   ╰→╰→╰→╭─ println("after")
                                ╰→ *exit*
      """,
      canThrow = { _, _ -> false },
      useGraph = { graph, start ->
        checkGraphPath(
          graph,
          start,
          targetNode = { call ->
            call is UCallExpression && call.methodIdentifier?.name == "println"
          },
          expected = "when → println()",
        )
      }
    )
  }

  @Test
  fun checkKotlinWhenStatementWithoutSubject() {
    checkAstGraph(
      kotlin(
          """
          fun target(list: List<String>) {
            when {
              list is ArrayDeque ||
              list is ArrayList -> {
                println("array")
              }
              list.toString().contains(",") -> {
                println("mutable")
              }
              list.isEmpty() -> println("empty")
              else -> {
                println("Something else")
              }
            }
            println("after")
          }
          """
        )
        .indented(),
      """
                    Block:           ╭─ { when { list …ntln("after") }
                   Switch:         ╭─╰→ when { list is…hing else") } }
          SwitchEntryBody: ╭→  ╭→  │ ╭─ list is ArrayD…ntln("array") }
                   Binary: │   │ ╭─╰→│  list is ArrayD…st is ArrayList
                  TypeRef: │   │ ╰→╭─│  ArrayDeque
                TypeCheck: │ ╭─╰─╭─╰→│  list is ArrayDeque
                  TypeRef: │ │   ╰→╭─│  ArrayList
                TypeCheck: ╰─│   ╭─╰→│  list is ArrayList
          SwitchEntryBody:   │ ╭→│ ╭─│  yield println("array")
                 FuncCall:   │ ╰─│ │ ╰→ println("array")
          SwitchEntryBody: ╭→│   │ │ ╭─ list.toString(…ln("mutable") }
      QualifiedExpression: │ ╰→╭─╰→│ │  list.toString().contains(",")
      QualifiedExpression: │   ╰→╭─│ │  list.toString()
                 FuncCall: │   ╭─╰→│ │  toString()
                 FuncCall: ╰─  ╰→╭─│ │  contains(",")
          SwitchEntryBody:   ╭→╭─│ │ │  yield println("mutable")
                 FuncCall:   ╰─│ │ │ ╰→ println("mutable")
      QualifiedExpression:     │ ╰→│ ╭─ list.isEmpty()
                 FuncCall:   ╭─│ ╭─│ ╰→ isEmpty()
          SwitchEntryBody:   │ │ ╰→│ ╭─ list.isEmpty()…rintln("empty")
          SwitchEntryBody: ╭→│ │ ╭─│ │  yield println("empty")
                 FuncCall: ╰─│ │ │ │ ╰→ println("empty")
          SwitchEntryBody:   ╰→│ │ │ ╭─ else -> { prin…ething else") }
          SwitchEntryBody: ╭→╭─│ │ │ │  yield println(…omething else")
                 FuncCall: ╰─│ │ │ │ ╰→ println("Something else")
                 FuncCall:   ╰→╰→╰→╰→╭─ println("after")
                                     ╰→ *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkKotlinShortCircuitEvaluation() {
    checkAstGraph(
      kotlin(
          """
          package test.pkg
          fun target(list1: List<String>, list2: List<String>) {
            val x = list1.isEmpty() || list2.contains("1")
          }
          """
        )
        .indented(),
      """
                    Block:     ╭─ { val x = list…contains("1") }
                   Binary:   ╭─╰→ list1.isEmpty(…2.contains("1")
      QualifiedExpression:   ╰→╭─ list1.isEmpty()
                 FuncCall: ╭─╭─╰→ isEmpty()
      QualifiedExpression: │ ╰→╭─ list2.contains("1")
                 FuncCall: │ ╭─╰→ contains("1")
            LocalVariable: ╰→╰→╭─ val x = list1.…2.contains("1")
             Declarations:   ╭─╰→ val x = list1.…2.contains("1")
                             ╰→   *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkKotlinSafeCalls() {
    // Bug: we have an extra arrow from reversed() directly to toString here
    // which isn't possible at runtime.
    checkAstGraph(
      kotlin(
          """
          package test.pkg
          fun target(list: List<String>) {
              outer()?.middle()?.inner()?.ref
              after()

          }
          """
        )
        .indented(),
      """
                     Block:       ╭─ { outer()?.mid…?.ref after() }
      SafeQuali…Expression:     ╭─╰→ outer()?.middl…)?.inner()?.ref
      SafeQuali…Expression:     ╰→╭─ outer()?.middle()?.inner()
      SafeQuali…Expression:     ╭─╰→ outer()?.middle()
                  FuncCall:   ╭─╰→╭─ outer()
                  FuncCall: ╭─│ ╭─╰→ middle()
                  FuncCall: │ │ ╰→╭─ inner()
                  FuncCall: ╰→╰→╭─╰→ after()
                                ╰→   *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkSafeMethodCalls() {
    // Checks that we avoid reporting method calls that should be safe (only accessing simple
    // members)
    val testFile: TestFile =
      kotlin(
          """
          package test.pkg

          import java.io.FileNotFoundException

          fun target() {
            empty()
            simple1(1,2)
            simple2(listOf("hello", "world"))
            simple3(listOf())
            unsafe1()
            unsafe2()
            unsafe3()
          }

          fun empty() {
          }

          fun simple1(a: Int, b: Int): Int {
             return a + b * 3
          }

          fun simple2(condition: Boolean, list: List<String>?): Any {
             var x = 1
             val y = "hello"
             if ((condition)) {
                 return -1
             }
             return list!!
          }

          fun simple3(list: List<String>?): Boolean {
              return list?.size
          }

          fun unsafe1() {
              "string with ${"$"}{toString()} inside"
          }

          fun unsafe2() {
              val list = emptyList()
          }

          @Throws(java.lang.Exception::class)
          fun unsafe3() {
              throw FileNotFoundException()
          }
          """
        )
        .indented()

    checkAstGraph(
      testFile,
      """
         Block:   ╭─ { empty() simp…2() unsafe3() }
      FuncCall: ╭─╰→ empty()
      FuncCall: ╰→╭─ simple1(1,2)
      FuncCall: ╭─╰→ simple2(listOf…llo", "world"))
      FuncCall: ╰→╭─ listOf("hello", "world")
      FuncCall: ╭─╰→ simple3(listOf())
      FuncCall: ╰→╭─ listOf()
      FuncCall: ╭─╰→ unsafe1()                      ─╮ Exception
      FuncCall: ╰→╭─ unsafe2()                       ┆─╮ Exception
      FuncCall: ╭─╰→ unsafe3()                       ┆ ┆─╮ Exception
                ╰→   *exit*                         ←╯←╯←╯
      """,
      strict = false
    )

    // Strict mode: no longer trusts the throws clauses of functions,
    // and defaults to Throwable instead of Exception.
    checkAstGraph(
      testFile,
      """
         Block:   ╭─ { empty() simp…2() unsafe3() }
      FuncCall: ╭─╰→ empty()
      FuncCall: ╰→╭─ simple1(1,2)
      FuncCall: ╭─╰→ simple2(listOf…llo", "world"))
      FuncCall: ╰→╭─ listOf("hello", "world")
      FuncCall: ╭─╰→ simple3(listOf())
      FuncCall: ╰→╭─ listOf()
      FuncCall: ╭─╰→ unsafe1()                      ─╮ Throwable
      FuncCall: ╰→╭─ unsafe2()                       ┆─╮ Throwable
      FuncCall: ╭─╰→ unsafe3()                       ┆ ┆─╮ Exception
                ╰→   *exit*                         ←╯←╯←╯
      """,
      strict = true
    )
  }

  @Test
  fun checkErrorAndTodo1() {
    checkAstGraph(
      kotlin(
          """
          package test.pkg
          fun target(list: List<String>, condition: Boolean) {
              if (condition) {
                  error("Exit")
              } else {
                  if (condition) {
                      TODO()
                  } else {
                      throw RuntimeException("Exit")
                  }
              }
          }
          """
        )
        .indented(),
      """
           Block:     ╭─ { if (conditio…n("Exit") } } }
              If: ╭─╭─╰→ if (condition)…ion("Exit") } }
      Then Block: │ ╰→╭─ { error("Exit") }
        FuncCall: │   ╰→ error("Exit")                  ─╮ Exception
      Else Block: ╰→  ╭─ { if (conditio…ion("Exit") } }  ┆
              If: ╭─╭─╰→ if (condition)…ption("Exit") }  ┆
      Then Block: │ ╰→╭─ { TODO() }                      ┆
        FuncCall: │   ╰→ TODO()                          ┆─╮ Exception
      Else Block: ╰→  ╭─ { throw Runtim…ption("Exit") }  ┆ ┆
        FuncCall:   ╭─╰→ RuntimeException("Exit")        ┆ ┆
           Throw:   ╰→   throw RuntimeException("Exit")  ┆ ┆─╮ RuntimeException
                         *exit*                         ←╯←╯←╯
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkErrorAndTodo2() {
    checkAstGraph(
      kotlin(
          """
          package test.pkg
          fun target(condition: Boolean) {
              try {
                if (condition) {
                  error("Exit")
                }
              } catch (e: Exception) {
              }
              next()
          }
          """
        )
        .indented(),
      """
            Block:     ╭─ { try { if (co…n) { } next() }
              Try:   ╭─╰→ try { if (cond… Exception) { }
        Try Block:   ╰→╭─ { if (conditio…ror("Exit") } }
               If: ╭─╭─╰→ if (condition)…error("Exit") }
       Then Block: │ ╰→╭─ { error("Exit") }
         FuncCall: │   ╰→ error("Exit")                  ─╮ Exception
      CatchClause: │   ╭─ catch (e: Exception) { }       ←╯
            Block: │ ╭─╰→ { }
         FuncCall: ╰→╰→╭─ next()
                       ╰→ *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkElvis1() {
    checkAstGraph(
      kotlin(
          """
          package test.pkg
          fun target(createList: ()->List<String>?) {
              val list = createList()
                  ?: emptyList()
          }
          """
        )
        .indented(),
      """
                Block:     ╭─ { val list = c…: emptyList() }
      ElvisExpression:   ╭─╰→ createList() ?: emptyList()
             FuncCall:   ╰→╭─ createList()
        LocalVariable:   ╭─╰→ var ＄temp = invoke()
         Declarations:   ╰→╭─ var ＄temp = invoke()
         ElvisIfCheck:   ╭─╰→ if (var＄temp !…lse emptyList()
       ElvisNullCheck: ╭─╰→╭─ <synthetic>
             FuncCall: │ ╭─╰→ emptyList()
        LocalVariable: ╰→╰→╭─ val list = cre… ?: emptyList()
         Declarations:   ╭─╰→ val list = cre… ?: emptyList()
                         ╰→   *exit*
      """,
      canThrow = { _, _ -> false },
      dfsOrder = true
    )
  }

  @Test
  fun checkElvis2() {
    checkAstGraph(
      kotlin(
          """
          package test.pkg
          fun target(a: List<String>?) {
              val list = a
                  ?: mutableListOf()
                  ?: emptyList()
          }
          """
        )
        .indented(),
      """
                Block:     ╭─ { val list = a…: emptyList() }
      ElvisExpression:   ╭─╰→ a ?: mutableLi… ?: emptyList()
      ElvisExpression:   ╰→╭─ a ?: mutableListOf()
        LocalVariable:   ╭─╰→ var ＄temp = a
         Declarations:   ╰→╭─ var ＄temp = a
         ElvisIfCheck:   ╭─╰→ if (var＄temp !…mutableListOf()
       ElvisNullCheck: ╭─╰→╭─ <synthetic>
             FuncCall: │ ╭─╰→ mutableListOf()
        LocalVariable: ╰→╰→╭─ var ＄temp = el…tableListOf() }
         Declarations:   ╭─╰→ var ＄temp = el…tableListOf() }
         ElvisIfCheck:   ╰→╭─ if (var＄temp !…lse emptyList()
       ElvisNullCheck: ╭─╭─╰→ <synthetic>
             FuncCall: │ ╰→╭─ emptyList()
        LocalVariable: ╰→╭─╰→ val list = a ?… ?: emptyList()
         Declarations:   ╰→╭─ val list = a ?… ?: emptyList()
                           ╰→ *exit*
      """,
      canThrow = { _, _ -> false },
      dfsOrder = true
    )
  }

  @Test
  fun checkKotlinSubstitutionStrings() {
    checkAstGraph(
      kotlin(
          "" +
            "" +
            "package test.pkg\n" +
            "import android.os.PowerManager.WakeLock\n" +
            "fun target(lock: WakeLock) {\n" +
            "    val x = \"Acquired: \${lock.acquire()}. Released: \${lock.release()}\"\n" +
            "}\n"
        )
        .indented(),
      """
                    Block:   ╭─ { val x = "Acq…k.release()}" }
      QualifiedExpression: ╭─╰→ lock.acquire()
                 FuncCall: ╰→╭─ acquire()
      QualifiedExpression: ╭─╰→ lock.release()
                 FuncCall: ╰→╭─ release()
           StringTemplate: ╭─╰→ "Acquired: ${"$"}{l…ock.release()}"
            LocalVariable: ╰→╭─ val x = "Acqui…ock.release()}"
             Declarations: ╭─╰→ val x = "Acqui…ock.release()}"
                           ╰→   *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkJavaTernaryExpression() {
    checkAstGraph(
      java(
          """
          public class Test {
            public void target() {
              boolean x = equals(5) ? foo() : bar();
            }
          }
          """
        )
        .indented(),
      """
          CodeBlock:     ╭─ { boolean x = …oo() : bar(); }
          TernaryIf:   ╭─╰→ equals(5) ? foo() : bar()
               Call: ╭─╰→╭─ equals(5)
               Call: │ ╭─╰→ foo()
               Call: ╰→│ ╭─ bar()
      LocalVariable: ╭─╰→╰→ boolean x = eq… foo() : bar();
       Declarations: ╰→  ╭─ var x: boolean…oo()) : (bar())
                         ╰→ *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkReturnNodes() {
    // From TraceSectionDetectorTest.testNestedLogic
    checkAstGraph(
      kotlin(
          """
          package test.pkg

          import android.os.Trace

          fun target(a: Int, b: Int, c: Int) {
            try {
              Trace.beginSection("wrong-1")
              if (a == b) {
                Trace.beginSection("wrong-1")
                if (a == c) {
                  Trace.beginSection("ok")
                  return
                }
                Trace.endSection()
              }
            } finally {
              Trace.endSection()
            }
          }

          fun blockingCall() { }
          """
        )
        .indented(),
      """
                    Block:       ╭─ { try { Trace.…ndSection() } }
                      Try:     ╭─╰→ try { Trace.be….endSection() }
                Try Block:     ╰→╭─ { Trace.beginS…ndSection() } }
      QualifiedExpression:     ╭─╰→ Trace.beginSection("wrong-1")
                 FuncCall:     ╰→╭─ beginSection("wrong-1")
                       If:     ╭─╰→ if (a == b) { ….endSection() }
                   Binary:   ╭─╰→╭─ a == b
               Then Block:   │ ╭─╰→ { Trace.beginS….endSection() }
      QualifiedExpression:   │ ╰→╭─ Trace.beginSection("wrong-1")
                 FuncCall:   │ ╭─╰→ beginSection("wrong-1")
                       If:   │ ╰→╭─ if (a == c) { …("ok") return }
                   Binary: ╭─│ ╭─╰→ a == c
               Then Block: │ │ ╰→╭─ { Trace.beginS…("ok") return }
      QualifiedExpression: │ │ ╭─╰→ Trace.beginSection("ok")
                 FuncCall: │ │ ╰→╭─ beginSection("ok")
                   Return: │ │   ╰→ return                         ─╮ finally
      QualifiedExpression: ╰→│   ╭─ Trace.endSection()              ┆
                 FuncCall:   │ ╭─╰→ endSection()                    ┆
            Finally Block:   ╰→╰→╭─ { Trace.endSection() }         ←╯
      QualifiedExpression:     ╭─╰→ Trace.endSection()
                 FuncCall:     ╰→╭─ endSection()                   ─╮ finally
                                 ╰→ *exit*                         ←╯
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkReturnCatch() {
    // Make sure the return target here doesn't jump across a finally-tag
    checkAstGraph(
      java(
          """
          import java.io.ByteArrayOutputStream;
          import java.io.File;
          import java.io.FileOutputStream;
          import java.io.IOException;

          class Test {
             public static void target(File f) {
                  FileOutputStream fo = null;
                  try {
                      fo = new FileOutputStream(f);
                  } catch (IOException e) {
                      return;
                  } finally {
                      try { fo.close(); } catch (Exception e) { }
                  }
                  after();
              }
          }
          """
        )
        .indented(),
      """
                CodeBlock:     ╭─ { FileOutputSt… } } after(); }
            LocalVariable:   ╭─╰→ FileOutputStream fo = null;
             Declarations:   ╰→╭─ var fo: java.i…utStream = null
                      Try:   ╭─╰→ try { fo = new…eption e) { } }
                CodeBlock:   ╰→╭─ { fo = new Fil…putStream(f); }
               Assignment:   ╭─╰→ fo = new FileOutputStream(f)
         ConstructorUCall:   ╰→╭─ new FileOutputStream(f)        ─╮ FileNotFoundException
              CatchClause:   ╭─│  catch (IOExcep… e) { return; } ←╯
                CodeBlock: ╭─╰→│  { return; }
                   Return: ╰→  │  return;                        ─╮ finally
                CodeBlock:   ╭─╰→ { try { fo.clo…eption e) { } } ←╯
                      Try:   ╰→╭─ try { fo.close…xception e) { }
                CodeBlock:   ╭─╰→ { fo.close(); }
      QualifiedExpression:   ╰→╭─ fo.close()
                     Call:   ╭─╰→ fo.close()                     ─╮─╮ IOException finally
              CatchClause:   │ ╭─ catch (Exception e) { }        ←╯ ┆
                CodeBlock: ╭─│ ╰→ { }                            ─╮ ┆ finally
                     Call: ╰→╰→╭─ after()                         ┆ ┆
                               ╰→ *exit*                         ←╯←╯
      """,
    )
  }

  @Test
  fun checkLabeledBreakViaFinally() {
    // Make sure a labeled break jumps via intermediate finally statements
    checkAstGraph(
      java(
          """
          class Test {
            public static void target(boolean condition) {
              myloop1:
              while (true) {
                try {
                  myloop2:
                  for (int i = 0; i < 10; i++) {
                    try {
                      if (condition) {
                        break myloop1;
                      }
                      if (i == 5) {
                        break myloop2;
                      }
                    } finally {
                      here();
                    }
                  }
                } finally {
                  here2();
                }
              }
              after();
            }
          }
          """
        )
        .indented(),
      """
          CodeBlock:             ╭─ { myloop1: whi… } } after(); }
            Labeled:           ╭─╰→ myloop1: while… { here2(); } }
              While: ╭→      ╭─╰→╭─ while (true) {… { here2(); } }
              Block: │       │ ╭─╰→ { try { myloop… { here2(); } }
                Try: │       │ ╰→╭─ try { myloop2:…ly { here2(); }
          CodeBlock: │       │ ╭─╰→ { myloop2: for…{ here(); } } }
            Labeled: │       │ ╰→╭─ myloop2: for (…y { here(); } }
                For: │       │ ╭─╰→ for (int i = 0…y { here(); } }
       Declarations: │     ╭→│ │ ╭─ var i: int = 0
      LocalVariable: │     ╰─│ ╰→│  int i = 0;
             Binary: │   ╭→╭─│ ╭─╰→ i < 10
            Postfix: │ ╭→╰─│ │ │    i++
              Block: │ │   │ │ ╰→╭─ { try { if (co…y { here(); } }
                Try: │ │   │ │ ╭─╰→ try { if (cond…lly { here(); }
          CodeBlock: │ │   │ │ ╰→╭─ { if (conditio…ak myloop2; } }
                 If: │ │ ╭─│ │ ╭─╰→ if (condition)…reak myloop1; }
         Then Block: │ │ │ │ │ ╰→╭─ { break myloop1; }
              Break: │ │ │ │ │   ╰→ break myloop1;                 ─╮ finally
                 If: │ │ ╰→│ │   ╭─ if (i == 5) { break myloop2; }  ┆
             Binary: │ │ ╭─│ │ ╭─╰→ i == 5                          ┆
         Then Block: │ │ │ │ │ ╰→╭─ { break myloop2; }              ┆
              Break: │ │ │ │ │   ╰→ break myloop2;                  ┆─╮ finally
          CodeBlock: │ │ ╰→│ │   ╭─ { here(); }                    ←╯←╯
               Call: │ ╰─  │ │   ╰→ here()                         ─╮ finally
          CodeBlock: │     ╰→│   ╭─ { here2(); }                   ←╯
               Call: ╰─      │   ╰→ here2()                        ─╮ finally
               Call:         ╰→  ╭─ after()                         ┆
                                 ╰→ *exit*                         ←╯
      """,
    )
  }

  @Test
  fun checkLabeledExpression() {
    checkAstGraph(
      java(
          """
          class Test {
            public static void target(boolean condition) {
              myloop1:
              while (true) {
                test();
              }
            }
          }
          """
        )
        .indented(),
      """
      CodeBlock:       ╭─ { myloop1: whi…) { test(); } }
        Labeled:     ╭─╰→ myloop1: while…ue) { test(); }
          While: ╭→╭─╰→╭─ while (true) { test(); }
          Block: │ │ ╭─╰→ { test(); }
           Call: ╰─│ ╰→   test()
                   ╰→     *exit*
      """,
    )
  }

  @Test
  fun checkTryFinallyWithoutCatch() {
    // From TraceSectionDetectorTest.testWrongLoop
    checkAstGraph(
      kotlin(
          """
          package test.pkg

          import android.os.Trace

          fun target() {
              Trace.beginSection("wrong")
              for (i in 0..10) {
                try {
                  Trace.beginSection("ok")
                  blockingCall()
                } finally {
                  Trace.endSection()
                }
              }
              Trace.endSection()
          }

          fun blockingCall() { }
          """
        )
        .indented(),
      """
                    Block:       ╭─ { Trace.beginS….endSection() }
      QualifiedExpression:     ╭─╰→ Trace.beginSection("wrong")
                 FuncCall:     ╰→╭─ beginSection("wrong")
                  ForEach: ╭→╭─╭─╰→ for (i in 0..1…ndSection() } }
                    Block: │ │ ╰→╭─ { try { Trace.…ndSection() } }
                      Try: │ │ ╭─╰→ try { Trace.be….endSection() }
                Try Block: │ │ ╰→╭─ { Trace.beginS…lockingCall() }
      QualifiedExpression: │ │ ╭─╰→ Trace.beginSection("ok")
                 FuncCall: │ │ ╰→╭─ beginSection("ok")
                 FuncCall: │ │ ╭─╰→ blockingCall()                 ─╮ Exception
            Finally Block: │ │ ╰→╭─ { Trace.endSection() }         ←╯
      QualifiedExpression: │ │ ╭─╰→ Trace.endSection()
                 FuncCall: ╰─│ ╰→   endSection()                   ─╮ Exception
      QualifiedExpression:   ╰→  ╭─ Trace.endSection()              ┆
                 FuncCall:     ╭─╰→ endSection()                    ┆
                               ╰→   *exit*                         ←╯
      """,
      canThrow = { _, method -> method.name == "blockingCall" }
    )
  }

  @Test
  fun checkKotlinScopingFunctions() {
    checkAstGraph(
      kotlin(
          """
          fun target(list: MutableList<String>) {
            list.let {
              println(it)
            }
            list.apply {
              clear()
            }
            with (list) {
              clear()
            }
            list?.let {
              println(it)
            }
            next()
          }
          """
        )
        .indented(),
      """
                     Block:     ╭─ { list.let { p…(it) } next() }
       QualifiedExpression:   ╭─╰→ list.let { println(it) }
                  FuncCall:   ╰→╭─ let { println(it) }
                    Lambda:   ╭─╰→ { println(it) }
                      Body:   ╰→╭─ println(it)
                  FuncCall:   ╭─╰→ println(it)
           Implicit Return:   ╰→╭─ return println(it)
       QualifiedExpression:   ╭─╰→ list.apply { clear() }
                  FuncCall:   ╰→╭─ apply { clear() }
                    Lambda:   ╭─╰→ { clear() }
                      Body:   ╰→╭─ clear()
                  FuncCall:   ╭─╰→ clear()
           Implicit Return:   ╰→╭─ return clear()
                  FuncCall:   ╭─╰→ with (list) { clear() }
                    Lambda:   ╰→╭─ { clear() }
                      Body:   ╭─╰→ clear()
                  FuncCall:   ╰→╭─ clear()
           Implicit Return:   ╭─╰→ return clear()
      SafeQuali…Expression: ╭─╰→╭─ list?.let { println(it) }
                  FuncCall: │ ╭─╰→ let { println(it) }
                    Lambda: │ ╰→╭─ { println(it) }
                      Body: │ ╭─╰→ println(it)
                  FuncCall: │ ╰→╭─ println(it)
           Implicit Return: │ ╭─╰→ return println(it)
                  FuncCall: ╰→╰→╭─ next()
                                ╰→ *exit*
      """,
      canThrow = { _, method ->
        if (method.name == "println" || method.name == "clear" || method.name == "next") false
        else null
      }
    )
  }

  @Test
  fun checkKotlinScopingFlow() {
    // This scenario at some point had a weird edge pointing out of the implicit return
    // in the first block pointing straight past the second let into the final next statement.
    // This only happens when the method bodies are identical. (This happened when using
    // a LinkedHashMap instead of an IdentityHashMap for the control flow graph node map
    // where in the below code, the implicit return element from the first lambda is
    // considered equal to the implicit return from the second.)
    checkAstGraph(
      kotlin(
          """
          fun target(list: MutableList<String>) {
            list.let {
              println(it)
            }
            list.let {
              println(it)
            }
            next()
          }
          """
        )
        .indented(),
      """
                    Block:   ╭─ { list.let { p…(it) } next() }
      QualifiedExpression: ╭─╰→ list.let { println(it) }
                 FuncCall: ╰→╭─ let { println(it) }
                   Lambda: ╭─╰→ { println(it) }
                     Body: ╰→╭─ println(it)
                 FuncCall: ╭─╰→ println(it)
          Implicit Return: ╰→╭─ return println(it)
      QualifiedExpression: ╭─╰→ list.let { println(it) }
                 FuncCall: ╰→╭─ let { println(it) }
                   Lambda: ╭─╰→ { println(it) }
                     Body: ╰→╭─ println(it)
                 FuncCall: ╭─╰→ println(it)
          Implicit Return: ╰→╭─ return println(it)
                 FuncCall: ╭─╰→ next()
                           ╰→   *exit*
      """,
      canThrow = { _, method ->
        val name = method.name
        if (name == "println" || name == "clear" || name == "next" || name == "print") false
        else null
      }
    )
  }

  @Test
  fun checkKotlinMethodReferences() {
    checkAstGraph(
      kotlin(
          """
          fun target(list: MutableList<String>) {
            list.let(::println)
            list?.let(::print)
            next()
          }
          """
        )
        .indented(),
      """
                     Block:     ╭─ { list.let(::p…print) next() }
       QualifiedExpression:   ╭─╰→ list.let(::println)
                  FuncCall:   ╰→╭─ let(::println)
               CallableRef:   ╭─╰→ ::println
      SafeQuali…Expression: ╭─╰→╭─ list?.let(::print)
                  FuncCall: │ ╭─╰→ let(::print)
               CallableRef: │ ╰→╭─ ::print
                  FuncCall: ╰→╭─╰→ next()
                              ╰→   *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkJavaLambdas() {
    checkAstGraph(
      java(
          """
          import java.util.List;
          public class Test {
            public void target(List<String> list) {
                list.stream().filter(s -> s.contains("?"));
            }
          }
          """
        )
        .indented(),
      """
                CodeBlock:       ╭─ { list.stream(…ntains("?")); }
      QualifiedExpression:     ╭─╰→ list.stream().….contains("?"))
      QualifiedExpression:     ╰→╭─ list.stream()
                     Call:     ╭─╰→ list.stream()
                     Call: ╭→╭─╰→╭─ list.stream().….contains("?"))
                   Lambda: │ │ ╭─╰→ s -> s.contains("?")
           Implicit Block: │ │ ╰→╭─ { return s.contains("?") }
      QualifiedExpression: │ │ ╭─╰→ s.contains("?")
                     Call: │ │ ╰→╭─ s.contains("?")
          Implicit Return: ╰─│   ╰→ return s.contains("?")
                             ╰→     *exit*
      """,
      canThrow = { _, _ -> false },
      dfsOrder = true
    )
  }

  @Test
  fun checkKotlinLambdas() {
    val testFile =
      kotlin(
          """
          fun target(list: List<String>) {
            var i = 0
            val foo = list.filter { it.contains("?") }
            next()
          }
          """
        )
        .indented()

    // No lambda connections
    checkAstGraph(
      testFile,
      """
                    Block:   ╭─ { var i = 0 va…"?") } next() }
            LocalVariable: ╭─╰→ var i = 0
             Declarations: ╰→╭─ var i = 0
      QualifiedExpression: ╭─╰→ list.filter { …contains("?") }
                 FuncCall: ╰→╭─ filter { it.contains("?") }
            LocalVariable: ╭─╰→ val foo = list…contains("?") }
             Declarations: ╰→╭─ val foo = list…contains("?") }
                 FuncCall: ╭─╰→ next()
                           ╰→   *exit*
      """,
      callLambdaParameters = false,
      canThrow = { _, _ -> false }
    )

    // Connect lambdas:
    checkAstGraph(
      testFile,
      """
                    Block:         ╭─ { var i = 0 va…"?") } next() }
            LocalVariable:       ╭─╰→ var i = 0
             Declarations:       ╰→╭─ var i = 0
      QualifiedExpression:       ╭─╰→ list.filter { …contains("?") }
                 FuncCall:   ╭→╭─╰→╭─ filter { it.contains("?") }
                   Lambda:   │ │ ╭─╰→ { it.contains("?") }
                     Body:   │ │ ╰→╭─ it.contains("?")
          Implicit Return: ╭→╰─│   │  return it.contains("?")
      QualifiedExpression: │   │ ╭─╰→ it.contains("?")
                 FuncCall: ╰─  │ ╰→   contains("?")
            LocalVariable:     ╰→  ╭─ val foo = list…contains("?") }
             Declarations:       ╭─╰→ val foo = list…contains("?") }
                 FuncCall:       ╰→╭─ next()
                                   ╰→ *exit*
      """,
      callLambdaParameters = true,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkKotlinLambdasLabeledReturns() {
    checkAstGraph(
      kotlin(
          """
          fun target(list: List<String>) {
            val foo = list.filter {
              if (it.length == 0) return@filter true
              if (it.length == 1) return
              it.contains("?")
            }
            next()
          }
          """
        )
        .indented(),
      """
                    Block:           ╭─ { val foo = li…"?") } next() }
            LocalVariable:     ╭→  ╭─│  val foo = list…contains("?") }
             Declarations:     │ ╭─╰→│  val foo = list…contains("?") }
      QualifiedExpression:     │ │ ╭─╰→ list.filter { …contains("?") }
                 FuncCall: ╭→╭→╰─│ ╰→╭─ filter { if (i…contains("?") }
                   Lambda: │ │   │ ╭─╰→ { if (it.lengt…contains("?") }
                     Body: │ │   │ ╰→╭─ if (it.length …t.contains("?")
                       If: │ │   │ ╭─╰→ if (it.length …urn@filter true
                   Binary: │ │   │ ╰→╭─ it.length == 0
      QualifiedExpression: │ │ ╭─│ ╭─╰→ it.length
                   Return: │ ╰─│ │ ╰→   return@filter true
                       If: │   ╰→│   ╭─ if (it.length == 1) return
                   Binary: │     │ ╭─╰→ it.length == 1
      QualifiedExpression: │   ╭─│ ╰→╭─ it.length
                   Return: │   │ │ ╭─╰→ return
          Implicit Return: ╰─╭→│ │ │    return it.contains("?")
      QualifiedExpression:   │ ╰→│ │ ╭─ it.contains("?")
                 FuncCall:   ╰─  │ │ ╰→ contains("?")
                 FuncCall:       ╰→│ ╭─ next()
                                   ╰→╰→ *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkKotlinMultipleLambdas() {
    checkAstGraph(
      kotlin(
          """
          fun target(list: List<String>) {
            val foo = foo({ i-> firstLambda() }, { j-> secondLambda(j) }) { k -> thirdLambda(k) }
            next()
          }
          """
        )
        .indented(),
      """
                Block:               ╭─ { val foo = fo…a(k) } next() }
             FuncCall: ╭→╭→╭→╭─╭─╭─╭─╰→ foo({ i-> firs…hirdLambda(k) }
               Lambda: │ │ │ │ │ │ ╰→╭─ { i-> firstLambda() }
                 Body: │ │ │ │ │ │ ╭─╰→ firstLambda()
             FuncCall: │ │ │ │ │ │ ╰→╭─ firstLambda()
      Implicit Return: │ │ ╰─│ │ │   ╰→ return <anonymous class>()
               Lambda: │ │   │ │ ╰→  ╭─ { j-> secondLambda(j) }
                 Body: │ │   │ │   ╭─╰→ secondLambda(j)
             FuncCall: │ │   │ │   ╰→╭─ secondLambda(j)
      Implicit Return: │ ╰─  │ │     ╰→ return <anonymous class>(j)
               Lambda: │     │ ╰→    ╭─ { k -> thirdLambda(k) }
                 Body: │     │     ╭─╰→ thirdLambda(k)
             FuncCall: │     │     ╰→╭─ thirdLambda(k)
      Implicit Return: ╰─    │       ╰→ return <anonymous class>(k)
        LocalVariable:       ╰→      ╭─ val foo = foo(…hirdLambda(k) }
         Declarations:             ╭─╰→ val foo = foo(…hirdLambda(k) }
             FuncCall:             ╰→╭─ next()
                                     ╰→ *exit*
      """,
      canThrow = { _, _ -> false },
      dfsOrder = true
    )

    // In strict mode, lambdas are not connected
    checkAstGraph(
      kotlin(
          """
          fun target(list: List<String>) {
            val foo = foo({ i-> firstLambda() }, { j-> secondLambda(j) }) { k -> thirdLambda(k) }
            next()
          }
          """
        )
        .indented(),
      """
              Block:   ╭─ { val foo = fo…a(k) } next() }
           FuncCall: ╭─╰→ foo({ i-> firs…hirdLambda(k) }
      LocalVariable: ╰→╭─ val foo = foo(…hirdLambda(k) }
       Declarations: ╭─╰→ val foo = foo(…hirdLambda(k) }
           FuncCall: ╰→╭─ next()
                       ╰→ *exit*
      """,
      canThrow = { _, _ -> false },
      // Strict mode: don't connect lambdas
      strict = true
    )
  }

  @Test
  fun checkLambdaVariable() {
    // Make sure we don't treat this as visited on its own
    checkAstGraph(
      kotlin(
          """
          fun target(i: Int) {
            val y = { s: Int -> println(s) }
          }
          """
        )
        .indented(),
      // Two clusters: the method execution, and the lambda block (which
      // is not reachable)
      """
                Block:   ╭─ { val y = { s:… println(s) } }
        LocalVariable: ╭─╰→ val y = { s: I…-> println(s) }
         Declarations: ╰→╭─ val y = { s: I…-> println(s) }
                         ╰→ *exit*

               Lambda:   ╭─ { s: Int -> println(s) }
                 Body: ╭─╰→ println(s)
             FuncCall: ╰→╭─ println(s)
      Implicit Return:   ╰→ return println(s)
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkLocalFunctions() {
    checkAstGraph(
      kotlin(
          """
          fun target(i: Int) {
            fun localFun() {
              if (i == 0) {
                return
              }
              println("hello")
            }
            if (i < 5) {
              localFun()
              nonLocalFun()
            }
            next()
          }
          fun nonLocalFun() { }
          """
        )
        .indented(),
      """
                    Block:         ╭─ { fun localFun…un() } next() }
            LocalFunction:       ╭─╰→ fun localFun()…ntln("hello") }
             Declarations:       ╰→╭─ fun localFun()…ntln("hello") }
                       If:       ╭─╰→ if (i < 5) { l…nonLocalFun() }
                   Binary:     ╭─╰→╭─ i < 5
               Then Block:     │ ╭─╰→ { localFun() nonLocalFun() }
                 FuncCall:     │ ╰→╭─ localFun()
      LocalFunctionLambda:     │ ╭─╰→ fun localFun()…ntln("hello") }
                    Block:     │ ╰→╭─ { if (i == 0) …ntln("hello") }
                       If:     │ ╭─╰→ if (i == 0) { return }
                   Binary:   ╭─│ ╰→╭─ i == 0
               Then Block:   │ │ ╭─╰→ { return }
                   Return:   │ │ ╰→╭─ return
                 FuncCall: ╭→│ │ ╭─╰→ nonLocalFun()
                 FuncCall: │ │ ╰→╰→╭─ next()
                 FuncCall: ╰─╰→    │  println("hello")
                                   ╰→ *exit*
      """,
      canThrow = { _, _ -> false },
      dfsOrder = true
    )
  }

  @Test
  fun checkKotlinLocalLambdaInvocation() {
    checkAstGraph(
      kotlin(
          """
          fun target(i: Int) {
            val y = { s: Int -> println(s) }
            val z = { a: Int, b: Int, c: Int -> print(a+b+c) }
            y(42)
            z(1,2,3)
            next()
          }
          """
        )
        .indented(),
      """
                Block:   ╭─ { val y = { s:…1,2,3) next() }
        LocalVariable: ╭─╰→ val y = { s: I…-> println(s) }
         Declarations: ╰→╭─ val y = { s: I…-> println(s) }
        LocalVariable: ╭─╰→ val z = { a: I… print(a+b+c) }
         Declarations: ╰→╭─ val z = { a: I… print(a+b+c) }
             FuncCall: ╭─╰→ y(42)
               Lambda: ╰→╭─ { s: Int -> println(s) }
                 Body: ╭─╰→ println(s)
             FuncCall: ╰→╭─ println(s)
      Implicit Return: ╭─╰→ return println(s)
             FuncCall: ╰→╭─ z(1,2,3)
               Lambda: ╭─╰→ { a: Int, b: I… print(a+b+c) }
                 Body: ╰→╭─ print(a+b+c)
             FuncCall: ╭─╰→ print(a+b+c)
               Binary: ╰→╭─ a+b+c
               Binary: ╭─╰→ a+b
      Implicit Return: ╰→╭─ return print(a + b + c)
             FuncCall: ╭─╰→ next()
                       ╰→   *exit*
      """,
      canThrow = { _, _ -> false },
      callLambdaParameters = false,
      dfsOrder = true
    )
  }

  @Test
  fun checkJavaLocalLambdaInvocation() {
    checkAstGraph(
      java(
          """
          import java.util.function.Predicate;
          class Test {
            public void target() {
              Runnable runnable = () -> System.out.println("Hello from a lambda!");
              runnable.run();
              Predicate<Integer> isEven = number -> number % 2 == 0;
              isEven.test(4);
            }
          }
          """
        )
        .indented(),
      """
                CodeBlock:   ╭─ { Runnable run…Even.test(4); }
            LocalVariable: ╭─╰→ Runnable runna…om a lambda!");
             Declarations: ╰→╭─ var runnable: …a lambda!") } }
      QualifiedExpression: ╭─╰→ runnable.run()
                     Call: ╰→╭─ runnable.run()
                   Lambda: ╭─╰→ () -> System.o…rom a lambda!")
           Implicit Block: ╰→╭─ { return Syste…m a lambda!") }
      QualifiedExpression: ╭─╰→ System.out.pri…rom a lambda!")
      QualifiedExpression: ╰→╭─ System.out
                     Call: ╭─╰→ System.out.pri…rom a lambda!")
          Implicit Return: ╰→╭─ return System.…rom a lambda!")
            LocalVariable: ╭─╰→ Predicate<Inte…umber % 2 == 0;
             Declarations: ╰→╭─ var isEven: ja…r % 2 === 0 } }
      QualifiedExpression: ╭─╰→ isEven.test(4)
                     Call: ╰→╭─ isEven.test(4)
                   Lambda: ╭─╰→ number -> number % 2 == 0
           Implicit Block: ╰→╭─ { return number % 2 === 0 }
                   Binary: ╭─╰→ number % 2 == 0
                   Binary: ╰→╭─ number % 2
          Implicit Return: ╭─╰→ return number % 2 === 0
                           ╰→   *exit*
      """,
      canThrow = { _, _ -> false },
      // Here we have direct invocation of lambdas, so the
      // call graph should unconditionally add these edges on its own:
      callLambdaParameters = false,
      dfsOrder = true
    )
  }

  @Test
  fun checkJavaAnonymousInnerClassInvocation() {
    checkAstGraph(
      java(
          """
          class Test {
            public void target() {
              Runnable runnable = new Runnable() {
                @Override
                public void run() {
                  System.out.println("Hello from an inner class!");
                }
              };
              runnable.run();
            }
          }
          """
        )
        .indented(),
      """
                CodeBlock:   ╭─ { Runnable run…nnable.run(); }
            LocalVariable: ╭─╰→ Runnable runna… class!"); } };
             Declarations: ╰→╭─ var runnable: …r class!"); } }
      QualifiedExpression: ╭─╰→ runnable.run()
                     Call: ╰→╭─ runnable.run()
            ObjectLiteral: ╭─╰→ new Runnable()…r class!"); } }
                CodeBlock: ╰→╭─ { System.out.p…ner class!"); }
      QualifiedExpression: ╭─╰→ System.out.pri… inner class!")
      QualifiedExpression: ╰→╭─ System.out
                     Call: ╭─╰→ System.out.pri… inner class!")
                           ╰→   *exit*
      """,
      canThrow = { _, _ -> false },
      callLambdaParameters = false,
      dfsOrder = true
    )
  }

  @Test
  fun checkLocalCallableReferenceInvocation() {
    checkAstGraph(
      kotlin(
          """
          fun target(i: Int) {
            fun localFun() {
              println("hello")
            }
            val x = ::localFun
            x()
            next()
          }
          """
        )
        .indented(),
      """
                    Block:       ╭─ { fun localFun…un x() next() }
            LocalFunction:     ╭─╰→ fun localFun()…ntln("hello") }
             Declarations:     ╰→╭─ fun localFun()…ntln("hello") }
      LocalFunctionLambda: ╭→  ╭─│  fun localFun()…ntln("hello") }
                    Block: │ ╭─╰→│  { println("hello") }
                 FuncCall: │ ╰→╭─│  println("hello")
              CallableRef: │ ╭─│ ╰→ ::localFun
            LocalVariable: │ ╰→│ ╭─ val x = ::localFun
             Declarations: │ ╭─│ ╰→ val x = ::localFun
                 FuncCall: ╰─╰→│    x()
                 FuncCall:     ╰→╭─ next()
                                 ╰→ *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkJavaSynchronized() {
    checkAstGraph(
      java(
          """
          public class Test {
            public void target() {
                synchronized (getLock()) {
                  synced();
                }
                after();
            }
          }
          """
        )
        .indented(),
      """
         CodeBlock:   ╭─ { synchronized…); } after(); }
      Synchronized: ╭─╰→ synchronized (…) { synced(); }
              Call: ╰→╭─ getLock()
              Call: ╭─╰→ synced()
              Call: ╰→╭─ after()
                      ╰→ *exit*
      """,
      canThrow = { _, _ -> false }
    )
  }

  @Test
  fun checkCompose() {
    val testFile =
      kotlin(
          """
          package androidx.compose.runtime
          annotation class Composable

          @Composable
          fun MyApplicationTheme(
              onClick: () -> Unit = {},
              content: @Composable () -> Unit = {}
          ) {
          }

          @Composable
          fun target() {
             MyApplicationTheme(onClick = {
               println("Click")
             })

             MyApplicationTheme {
                for (i in 0 until 10) {
                    Text(text = "Hello " + i)
                }
             }
          }
          """
        )
        .indented()

    checkAstGraph(
      testFile,
      """
                Block:       ╭─ { MyApplicatio…lo " + i) } } }
             FuncCall:     ╭─╰→ MyApplicationT…tln("Click") })
             FuncCall:     ╰→╭─ MyApplicationT…ello " + i) } }
               Lambda:     ╭─╰→ { for (i in 0 …ello " + i) } }
                 Body:     ╰→╭─ for (i in 0 un…"Hello " + i) }
              ForEach: ╭→╭─╭─╰→ for (i in 0 un…"Hello " + i) }
      Implicit Return: │ │ ╰→╭─ return for (i …"Hello " + i) }
                Block: │ ╰→╭─│  { Text(text = "Hello " + i) }
             FuncCall: │ ╭─╰→│  Text(text = "Hello " + i)
               Binary: ╰─╰→  │  "Hello " + i
                             ╰→ *exit*
      """,
      canThrow = { _, _ -> false },
      callLambdaParameters = false
    )

    // With lambda call connections we'll also include the onClick handler
    checkAstGraph(
      testFile,
      """
                Block:       ╭─ { MyApplicatio…lo " + i) } } }
             FuncCall: ╭→╭─╭─╰→ MyApplicationT…tln("Click") })
               Lambda: │ │ ╰→╭─ { println("Click") }
                 Body: │ │ ╭─╰→ println("Click")
      Implicit Return: ╰─│ │ ╭→ return println("Click")
             FuncCall:   │ ╰→╰─ println("Click")
             FuncCall:   ╰→  ╭─ MyApplicationT…ello " + i) } }
               Lambda:     ╭─╰→ { for (i in 0 …ello " + i) } }
                 Body:     ╰→╭─ for (i in 0 un…"Hello " + i) }
              ForEach: ╭→╭─╭─╰→ for (i in 0 un…"Hello " + i) }
      Implicit Return: │ │ ╰→╭─ return for (i …"Hello " + i) }
                Block: │ ╰→╭─│  { Text(text = "Hello " + i) }
             FuncCall: │ ╭─╰→│  Text(text = "Hello " + i)
               Binary: ╰─╰→  │  "Hello " + i
                             ╰→ *exit*
      """,
      canThrow = { _, _ -> false },
      callLambdaParameters = true
    )
  }

  @Test
  fun testByteCode() {
    val testFile: TestFile =
      compiled(
        "bin/classes",
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.annotation.SuppressLint;\n" +
            "import android.app.Activity;\n" +
            "import android.os.PowerManager;\n" +
            "import android.os.PowerManager.WakeLock;;\n" +
            "\n" +
            "public class WakelockActivity6 extends Activity {\n" +
            "    void wrongFlow1() {\n" +
            "        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);\n" +
            "        PowerManager.WakeLock lock =\n" +
            "                manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, \"Test\");\n" +
            "        lock.acquire();\n" +
            "        if (getTaskId() == 50) {\n" +
            "            randomCall();\n" +
            "        } else {\n" +
            "            lock.release(); // Wrong\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    void wrongFlow2(PowerManager.WakeLock lock) {\n" +
            "        lock.acquire();\n" +
            "        if (getTaskId() == 50) {\n" +
            "            randomCall();\n" +
            "        } else {\n" +
            "            lock.release(); // Wrong\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    void okFlow1(WakeLock lock) {\n" +
            "        lock.acquire();\n" +
            "        try {\n" +
            "            randomCall();\n" +
            "        } catch (Exception e) {\n" +
            "            e.printStackTrace();\n" +
            "        } finally {\n" +
            "            lock.release(); // OK\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public void checkNullGuard(WakeLock lock) {\n" +
            "        lock.acquire();\n" +
            "        if (lock != null) {\n" +
            "            lock.release(); // OK\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    @SuppressLint(\"Wakelock\")\n" +
            "    public void checkDisabled1(PowerManager.WakeLock lock) {\n" +
            "        lock.acquire();\n" +
            "        randomCall();\n" +
            "        lock.release(); // Wrong, but disabled\n" +
            "    }\n" +
            "\n" +
            "    void wrongFlow3(WakeLock lock) {\n" +
            "        int id = getTaskId();\n" +
            "        lock.acquire();\n" +
            "        if (id < 50) {\n" +
            "            System.out.println(1);\n" +
            "        } else {\n" +
            "            System.out.println(2);\n" +
            "        }\n" +
            "        lock.release(); // Wrong\n" +
            "    }\n" +
            "\n" +
            "    static void randomCall() {\n" +
            "        System.out.println(\"test\");\n" +
            "    }\n" +
            "}\n"
        ),
        0x6a6b5888,
        "test/pkg/WakelockActivity6.class:" +
          "H4sIAAAAAAAAAIVVXVcTVxTdExImGYavIFiRKqChIUFGQaUaUWkQjYaPGpZ9" +
          "6NMwuSuOGWbSmQnob+if6OqLz/Yh7epD26c+9DfZru57IQHUtcys3HPvuefu" +
          "vc+5J5N//vv9DwAreG5gDEUdC2lcM5DFog4rjesGUrhhYBJL0rksh5tyeUvH" +
          "bQMmVjL4GncM3EUpjXvSruq4r+OBhv57ru/G9zX05edfaEiWg7rQMFx1fbHV" +
          "3t8T4a6959FjHIaB39jwgsMbGgZrse00N+2W2tTx8PT+kob0d3ZTVAOnqcGs" +
          "+L4Iy54dRSLSkMtXbb8eBm7dCiJrJzgU4abt2w0RXu2eKUkdetBUXDrWNAw5" +
          "L4XT3Gp73uO2Hda7jnU3kvR1Crr4vO3H7r6o+Adu5NK55vtBbMdu4JN0psdp" +
          "99xWrd1qhSKKmGlc0pA6sL22OJbuKeknKS1zERIj2C/bnsdFLWiHjthwZWUm" +
          "uifWnNg9cOM3txdf2Qe2iXMY13C+S+0Efiz82CpL+zomY0tmb+IblE+FfVAV" +
          "3siuiGIT63hkYgPjJh7jiYm8nFUkwZgkszzbb1iPXjuiJZMz8RTjOp6ZqGJT" +
          "x5aJbewQKlZQ2/hWw6ScW61mw/pIvoZzvXq1WlbXr2H6c1d3Rs3uyzA4PGqe" +
          "kYaIa2+iWOzXRHjgOnTN5asnobU4dP1Gaf6Ua3vvlXDkxQz44vAEfyVf+dS5" +
          "z/YUO8p2fmi7IakzVLNrR81KXXV9hXuh8IQdycZvETFW7b0b2lLoyCk6lQIP" +
          "BW3e3/iREDewdo7OhMLeL3VLcNZNCgXs+byFfEV2+PinCvACM/ztjkF+0tBk" +
          "C3Gc4OpLWo02VfgV2jtO2DMc+5Uzze8XvdAW+vgAhUK2r4PkX0hVi8lsfwf6" +
          "5kIH6UIHmZGln5D5DcZb6HQNSLyEwptVWAafAQzxzZHFIDGHcBnDyGEU8xR3" +
          "gREmEv8ipyOlY1JyXcSlY/anREpIPcWPuYpnuCb41pJjkqtB6h/jG+s8phR+" +
          "EokxiXu5h/sjbVIqlLiEk1hvMSFTGjyaG1vSXvvzHbNPQlf6pZ3kqSGF3mVe" +
          "ZHYSO8ly68xrAHOMmGWOVxiXY8wco3OMmGPEV0rRAPq2dJjPdEzLos/0hN2l" +
          "lQlLFeni3x9mmVWqCxyLrO0CNS0qvD5oJk/OEiehcK4f45inEjy56COYJY7L" +
          "lHyTcm5RrExMw1V+c99DiygXlNtVtkc8WcScvIZVCTs1svQzBn/BULKDYVaM" +
          "sxRnZxVfIgH4h5HCHWSY3jBKVLyKaTwg1UOlXkfiPS5o/Uwp32u8nNIPZAib" +
          "Helg9ES+obbKDF/nbF5xFdjUcocvI3JNYfR/L6FM7+cGAAA="
      )

    checkCompiledGraph(
      testFile as BytecodeTestFile,
      """
            ╭─ LABEL1
          ╭─╰→ LINE 10
          ╰→╭─ ALOAD: Var 0
          ╭─╰→ LDC: Load constant 'power'
          ╰→╭─ INVOKEVIRTUAL: Call WakelockActivity6.getSystemService()
          ╭─╰→ CHECKCAST: Type PowerManager
          ╰→╭─ ASTORE: Var 1
          ╭─╰→ LABEL2
          ╰→╭─ LINE 11
          ╭─╰→ ALOAD: Var 1
          ╰→╭─ ICONST_1 (InsnNode)
          ╭─╰→ LDC: Load constant 'Test'
          ╰→╭─ LABEL3
          ╭─╰→ LINE 12
          ╰→╭─ INVOKEVIRTUAL: Call PowerManager.newWakeLock()
          ╭─╰→ ASTORE: Var 2
          ╰→╭─ LABEL4
          ╭─╰→ LINE 13
          ╰→╭─ ALOAD: Var 2
          ╭─╰→ INVOKEVIRTUAL: Call PowerManager${"$"}WakeLock.acquire()
          ╰→╭─ LABEL5
          ╭─╰→ LINE 14
          ╰→╭─ ALOAD: Var 0
          ╭─╰→ INVOKEVIRTUAL: Call WakelockActivity6.getTaskId()
          ╰→╭─ BIPUSH (IntInsnNode)
        ╭─╭─╰→ IF_ICMPNE (JumpInsnNode)
        │ ╰→╭─ LABEL6
        │ ╭─╰→ LINE 15
        │ ╰→╭─ INVOKESTATIC: Call WakelockActivity6.randomCall()
        │ ╭─╰→ GOTO (JumpInsnNode)
        ╰→│ ╭─ LABEL7
        ╭─│ ╰→ LINE 17
        ╰→│ ╭─ F_NEW (FrameNode)
        ╭─│ ╰→ ALOAD: Var 2
        ╰→│ ╭─ INVOKEVIRTUAL: Call PowerManager${"$"}WakeLock.release()
        ╭─╰→╰→ LABEL8
        ╰→  ╭─ LINE 19
          ╭─╰→ F_NEW (FrameNode)
          ╰→   RETURN (InsnNode)
      """
        .trimIndent(),
      methodName = "wrongFlow1"
    )
  }

  // --------------------------------------------------------------------------
  // Unit test infrastructure below this point
  // --------------------------------------------------------------------------

  private fun checkCompiledGraph(
    testFile: BytecodeTestFile,
    expected: String,
    methodName: String = "target"
  ) {
    fun getOpcodeString(opcode: Int): String? {
      try {
        val c = Class.forName("org.objectweb.asm.Opcodes")
        for (f in c.declaredFields.reversed()) { // opcodes are at the end
          val value = f.get(null) as? Int ?: continue
          if (value == opcode) {
            return f.name
          }
        }
      } catch (t: Throwable) {
        // debug not installed: just do toString() on the instructions
      }

      return null
    }

    fun AbstractInsnNode.describe(
      method: MethodNode? = null,
      source: String? = null,
      labelKeys: Map<Label, String>? = null
    ): String {
      val opcode = getOpcodeString(opcode) ?: "OPCODE $opcode"
      return when (this) {
        is MethodInsnNode -> "$opcode: Call ${owner.substringAfterLast('/')}.${name}()"
        is TypeInsnNode -> "$opcode: Type ${desc.substringAfterLast('/')}"
        is LineNumberNode -> {
          var lineContents = ""
          if (source != null) {
            // Remove "'s from the source since they can't appear in the dot file (find out how to
            // escape)
            lineContents = source.split("\n")[line - 1].trim().replace('"', '\u02DD')
          }
          "LINE $line${if (lineContents.isNotBlank()) ": \u02DD$lineContents\u02DD" else ""}"
        }
        is VarInsnNode -> {
          var name = ""
          val index = `var`
          if (method != null) {
            val localVariables = method.localVariables
            if (index < localVariables.size) {
              val varNode = localVariables[index]
              name = varNode.name
            }
          }
          "$opcode: Var $index${if (name.isNotEmpty()) ": $name" else ""}"
        }
        is LabelNode -> {
          if (labelKeys != null) {
            labelKeys[label] ?: "Label $label"
          } else {
            "Label $label"
          }
        }
        is LdcInsnNode -> {
          val cst = cst
          "$opcode: Load constant ${if (cst is String) "'${cst.replace('"', '\'')}'" else cst}"
        }
        else -> opcode + " (${javaClass.simpleName})"
      }
    }

    for (file in testFile.getBytecodeFiles()) {
      val targetPath = file.targetPath
      if (!targetPath.endsWith(SdkConstants.DOT_CLASS) || targetPath.contains("$")) {
        continue
      }
      val bytes = (file as TestFile.BinaryTestFile).binaryContents
      val reader = ClassReader(bytes)
      val classNode = ClassNode(ASM9)
      reader.accept(classNode, 0)

      for (method in classNode.methods) {
        if (method.name == methodName) {
          val labels = method.instructions.filterIsInstance<LabelNode>().map { it.label }
          val labelKeys = LinkedHashMap<Label, String>()
          for (i in labels.indices) {
            val label = labels[i]
            labelKeys[label] = "LABEL${i + 1}"
          }

          val graph =
            ControlFlowGraph.create(
              classNode,
              method,
            )
          val actual =
            graph
              .prettyPrintGraph(
                method.instructions.get(0),
                nodeTypeString = { node ->
                  node.instruction.describe(method, labelKeys = labelKeys)
                },
                sourceString = { null },
                method.instructions.mapNotNull { graph.getNode(it) },
                filter = { true }
              )
              .trimEnd()

          val cleanExpected = expected.trimIndent().trimEnd()
          if (cleanExpected != actual) {
            if (SHOW_DOT_ON_FAILURE) {
              graph.show()
            }
            assertEquals(expected, actual)
          }

          return
        }
      }
    }
    fail("Couldn't find $methodName")
  }

  private fun checkAstGraph(
    testFile: TestFile,
    expected: String,
    printGraph:
      (
        JavaContext, ControlFlowGraph<UElement>, UMethod, List<ControlFlowGraph<UElement>.Node>
      ) -> String? =
      { _, _, _, _ ->
        null
      },
    canThrow: ((UElement, PsiMethod) -> Boolean?)? = null,
    checkBranchPaths: ((conditional: UExpression) -> ControlFlowGraph.FollowBranch)? = null,
    strict: Boolean = false,
    callLambdaParameters: Boolean = !strict,
    methodName: String = "target",
    expectedDotGraph: String? = null,
    useGraph: ((ControlFlowGraph<UElement>, UExpression) -> Unit)? = null,
    dfsOrder: Boolean = false
  ) {
    val (context, disposable) =
      parseFirst(
        temporaryFolder = temporaryFolder,
        sdkHome = TestUtils.getSdk().toFile(),
        testFiles = arrayOf(testFile)
      )

    fun JavaContext.findTarget(): UMethod {
      var method: UMethod? = null
      uastFile?.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (node.name == methodName) {
              method = node
            }
            return super.visitMethod(node)
          }
        }
      )
      return method ?: error("Couldn't find method $methodName in ${testFile.contents}")
    }

    val renderNode: (ControlFlowGraph<UElement>.Node) -> String = { node ->
      if (node.isExit()) "exit"
      else ("${node.typeString()}\n${node.sourceString().trimMiddle(30)}").trim()
    }
    val renderEdge:
      (ControlFlowGraph<UElement>.Node, ControlFlowGraph<UElement>.Edge, Int) -> String =
      { from, edge, index ->
        if (edge.label != null) {
          edge.label!!
        } else if (from.isLinear()) {
          "then"
        } else {
          "s$index"
        }
      }
    val method = context.findTarget()
    val graph =
      ControlFlowGraph.create(
        method,
        builder =
          object :
            ControlFlowGraph.Companion.Builder(
              strict = strict,
              trackCallThrows = true,
              callLambdaParameters = callLambdaParameters
            ) {
            override fun checkBranchPaths(conditional: UExpression): ControlFlowGraph.FollowBranch {
              return checkBranchPaths?.invoke(conditional) ?: super.checkBranchPaths(conditional)
            }

            override fun canThrow(reference: UElement, method: PsiMethod): Boolean {
              return canThrow?.invoke(reference, method) ?: super.canThrow(reference, method)
            }
          }
      )

    val start = method.uastBody!!

    val clusters = getClusters(graph, method)

    val rendered =
      clusters.joinToString("\n") { (entry, list) ->
        val instructions = getInstructionOrder(graph, list, entry, method, dfsOrder)

        printGraph.invoke(context, graph, method, instructions)
          ?: graph.prettyPrintGraph(
            entry.instruction,
            nodeTypeString = { node -> if (node.isExit()) "" else node.typeString() },
            sourceString = { node -> if (node.isExit()) "*exit*" else node.sourceString() },
            instructions
          )
      }

    val cleanExpected = expected.trimIndent().trimEnd()
    val actual = rendered.trimIndent().trimEnd()
    if (cleanExpected != actual) {
      if (UPDATE_IN_PLACE != null) {
        // Try to directly rewrite the test source to have the actual rendering.
        // Useful when making changes to the format so that you don't have to manually
        // update all the existing test cases.
        val sourceFile = File(UPDATE_IN_PLACE)
        if (sourceFile.isFile) {
          val text = sourceFile.readText()
          val testName = Thread.currentThread().stackTrace[3].methodName
          val methodOffset = text.indexOf("fun $testName(")
          if (methodOffset != -1) {
            var startMarker = "\n      \"\"\"\n"
            var startMarkerOffset = text.indexOf(startMarker, methodOffset)
            if (startMarkerOffset == -1) {
              startMarker = "\n      \"\"\"\n"
              startMarkerOffset = text.indexOf(startMarker, methodOffset)
            }
            if (startMarkerOffset != -1) {
              val startOffset = startMarkerOffset + startMarker.length
              val stringEndOffset = text.indexOf("\"\"\"", startOffset)
              if (stringEndOffset != -1) {
                val endOffset = text.lastIndexOf("\n", stringEndOffset)
                if (endOffset != -1) {
                  val nextMethod = text.indexOf(" @Test\n", methodOffset)
                  if (nextMethod != -1 && nextMethod < startOffset) {
                    error("Unexpectedly start was in next method")
                  }
                  val newStringContents =
                    actual.replace("\$", "\${\"\$\"}").split("\n").joinToString("\n") {
                      "      $it"
                    }
                  val newSource =
                    text.substring(0, startOffset) + newStringContents + text.substring(endOffset)
                  sourceFile.writeText(newSource)
                }
              }
            }
          }
        }
      }
      if (SHOW_DOT_ON_FAILURE) {
        graph.show(start, method, renderNode = renderNode, renderEdge = renderEdge)
      }
      assertEquals(cleanExpected, actual)
    }

    if (expectedDotGraph != null) {
      assertEquals(
        expectedDotGraph.trimIndent().trimEnd(),
        graph
          .toDot(start, renderNode = renderNode, renderEdge = renderEdge)
          .split("\n")
          .joinToString("\n") { it.trimEnd() }
          .trimEnd()
      )
    }

    useGraph?.invoke(graph, start)

    Disposer.dispose(disposable)
  }

  private fun checkGraphPath(
    graph: ControlFlowGraph<UElement>,
    start: UExpression,
    targetNode: (UElement) -> Boolean,
    expected: String
  ) {
    var foundPath: List<ControlFlowGraph<UElement>.Edge> = emptyList()

    val startNode = graph.getNode(start)!!
    graph.dfs(
      object : ControlFlowGraph.DfsRequest<UElement, Boolean>(startNode, false) {
        override fun visitNode(
          node: ControlFlowGraph<UElement>.Node,
          path: List<ControlFlowGraph<UElement>.Edge>,
          status: Boolean
        ): Boolean {
          val instruction = node.instruction
          return if (targetNode(instruction)) {
            foundPath = path.toList()
            true
          } else false
        }

        override fun isDone(status: Boolean): Boolean = status
      }
    )

    assertEquals(expected, ControlFlowGraph.describePath(foundPath))
  }

  /** Clusters the graph into entry points and nodes reachable from each entry point */
  private fun getClusters(
    graph: ControlFlowGraph<UElement>,
    method: UMethod
  ): List<Pair<ControlFlowGraph<UElement>.Node, List<ControlFlowGraph<UElement>.Node>>> {
    val entryPoints = graph.getEntryPoints()
    val clusters =
      mutableListOf<Pair<ControlFlowGraph<UElement>.Node, List<ControlFlowGraph<UElement>.Node>>>()
    for (entry in entryPoints) {
      val reaches = mutableListOf<ControlFlowGraph<UElement>.Node>()
      graph.dfs(
        object : ControlFlowGraph.DfsRequest<UElement, Unit>(entry, Unit) {
          override fun visitNode(
            node: ControlFlowGraph<UElement>.Node,
            path: List<ControlFlowGraph<UElement>.Edge>,
            status: Unit
          ) {
            reaches.add(node)
          }
        }
      )
      clusters.add(Pair(entry, reaches))
    }

    return clusters
  }

  /**
   * Returns the nodes in suitable instruction order. This is generally the program order (unless
   * [dfsOrder] is set to true, in which it's the depth-first-search traversal order starting from
   * [start]) but is tweaked a bit to for example generally follow the flow order for instructions
   * on the same line, etc.
   */
  private fun getInstructionOrder(
    graph: ControlFlowGraph<UElement>,
    nodes: List<ControlFlowGraph<UElement>.Node>,
    start: ControlFlowGraph<UElement>.Node,
    method: UMethod,
    dfsOrder: Boolean
  ): List<ControlFlowGraph<UElement>.Node> {
    if (dfsOrder) {
      fun dfs(startNode: ControlFlowGraph<UElement>.Node): List<ControlFlowGraph<UElement>.Node> {
        val visited = mutableSetOf<ControlFlowGraph<UElement>.Node>()
        val result = mutableListOf<ControlFlowGraph<UElement>.Node>()

        fun dfs(
          node: ControlFlowGraph<UElement>.Node,
          visited: MutableSet<ControlFlowGraph<UElement>.Node>
        ) {
          if (!visited.add(node)) {
            return
          }
          result.add(node)
          // successors and exceptions separately?
          val neighbors = node.successors + node.exceptions
          for (neighbor in neighbors) {
            dfs(neighbor.to, visited)
          }
        }

        dfs(startNode, visited)

        return result
      }
      return dfs(start)
    }

    // Assign the id's in source order
    var next = 0
    val nodeOrder = LinkedHashMap<ControlFlowGraph<UElement>.Node, Int>()
    val sourceOffsets = LinkedHashMap<ControlFlowGraph<UElement>.Node, Segment>()

    val nodeMap = mutableMapOf<UElement, ControlFlowGraph<UElement>.Node>()
    for (node in nodes) {
      nodeMap[node.instruction] = node
    }

    method.accept(
      object : AbstractUastVisitor() {
        override fun visitElement(
          @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") element: UElement
        ): Boolean {
          val node = graph.getNode(element) ?: nodeMap[element]
          if (node != null) {
            nodeOrder[node] = next++
            val segment = (element as? UElementWithLocation) ?: element.sourcePsi?.textRange
            if (segment != null) {
              sourceOffsets[node] = segment
            } else if (element is UReturnExpression && element.returnExpression != null) {
              // Special workaround for KotlinUImplicitReturnExpression created by
              // UAST for lambdas:
              element.returnExpression?.sourcePsi?.textRange?.let { sourceOffsets[node] = it }
            } else if (element is UDeclarationsExpression && element.declarations.size == 1) {
              // Special workaround for JavaUDeclarationsExpression created by
              // UAST; the nested declaration statement has the relevant source offset.
              element.declarations[0].sourcePsi?.textRange?.let { sourceOffsets[node] = it }
            }
          }
          return super.visitElement(element)
        }
      }
    )
    // Any unexpected elements
    for (node in nodes) {
      if (nodeOrder[node] == null) {
        nodeOrder[node] = next++
      }
    }
    // place exit last
    graph.getNode(method)?.let { nodeOrder[it] = next }

    /** Are these two nodes fully on the same source code line? */
    val source = method.sourcePsi!!.containingFile.text
    fun sameLine(
      o1: ControlFlowGraph<UElement>.Node,
      o2: ControlFlowGraph<UElement>.Node
    ): Boolean {
      val source1 = sourceOffsets[o1]
      val source2 = sourceOffsets[o2]
      if (source1 != null && source2 != null) {
        val startOffset = min(source1.startOffset, source2.startOffset)
        val endOffset = max(source1.endOffset, source2.endOffset)
        for (i in startOffset until endOffset) {
          if (source[i] == '\n') {
            return false
          }
        }
        return true
      }
      return false
    }

    val instructionOrder: MutableList<ControlFlowGraph<UElement>.Node> = nodes.toMutableList()
    Collections.sort(
      instructionOrder,
      object : Comparator<ControlFlowGraph<UElement>.Node> {
        override fun compare(
          o1: ControlFlowGraph<UElement>.Node,
          o2: ControlFlowGraph<UElement>.Node
        ): Int {
          val segment1 = sourceOffsets[o1]
          val segment2 = sourceOffsets[o2]
          if (segment1 != null && segment2 != null) {
            if (sameLine(o1, o2) || segment1 == segment2) {
              // For nearby nodes (on the same line), bias towards the flow direction.
              // With this, we'll convert the code "var i = 0; randomCall()" from
              //               Block:     ╭─ { var i = 0 randomCall() i++ }
              //       Declarations: ╭→╭─│  var i = 0
              //      LocalVariable: ╰─│ ╰→ var i = 0
              //           FuncCall:   ╰→   randomCall()
              // into
              //              Block:   ╭─ { var i = 0 randomCall() i++ }
              //      LocalVariable: ╭─╰→ var i = 0
              //       Declarations: ╰→╭─ var i = 0
              //           FuncCall:   ╰→ randomCall()
              // (in other words, we've reordered the Declarations and LocalVariable
              // nodes; even though Declarations comes first in UAST iteration, the graph
              // flow is more natural this way.
              val forward = o1.flowsTo(o2)
              val backward = o2.flowsTo(o1)
              if (forward && !backward) {
                return -1
              } else if (backward && !forward) {
                return 1
              }
            }

            val startDelta = segment1.startOffset - segment2.startOffset
            if (startDelta != 0) {
              return startDelta
            }
            val endDelta = segment2.endOffset - segment1.endOffset
            if (endDelta != 0) {
              return endDelta
            }
          }
          return nodeOrder[o1]!! - nodeOrder[o2]!!
        }
      }
    )

    return instructionOrder
  }
}

/** On a Mac or Linux, renders the graph to PNG and opens it. */
fun <T> ControlFlowGraph<T>.show(
  start: T? = null,
  end: T? = null,
  reuseFile: Boolean = false,
  defaultName: String = "testcase",
  // Can use "sfdp", "neato", "circo", "osage", "patchwork", "twopi", etc here to
  // use different graphviz algorithms.
  algorithm: String = "dot",
  renderNode: (ControlFlowGraph<T>.Node) -> String = { node -> node.instruction.toString() },
  renderEdge: (ControlFlowGraph<T>.Node, ControlFlowGraph<T>.Edge, Int) -> String =
    { _, edge, index ->
      edge.label ?: "s${index}"
    }
) {
  val dotPath = "/opt/homebrew/bin/dot"
  if (!File(dotPath).isFile) {
    error("Didn't find $dotPath; make sure graphviz is installed.")
  }
  val dotFile = File.createTempFile("mydot", ".dot")
  var pngFile = File("")
  if (reuseFile) {
    pngFile = File("/tmp/$defaultName.png")
    if (pngFile.isFile) {
      pngFile.delete()
    }
  } else {
    for (i in 1 until 1000) {
      pngFile = File("/tmp/$defaultName$i.png")
      if (!pngFile.isFile) {
        break
      }
    }
  }

  val dot = toDot(start, end, renderNode = renderNode, renderEdge = renderEdge)
  dotFile.writeText(dot)
  Runtime.getRuntime()
    .exec("$dotPath -K$algorithm -Tpng -o${pngFile.path} ${dotFile.path}")
    .waitFor()
  Runtime.getRuntime().exec("/usr/bin/open ${pngFile.path}").waitFor()
  dotFile.delete()
}

/** Creates the type string for a control graph node. */
fun ControlFlowGraph<UElement>.Node.typeString(): String {
  val node = this
  val element = node.instruction

  if (isExit()) {
    // Special exit marker. Maybe use the UFile instead to make it even more obvious?
    return ""
  }

  val simpleName = element.javaClass.simpleName
  when (simpleName) {
    "KotlinStringTemplateUPolyadicExpression" -> return "StringTemplate"
    "KotlinUElvisExpression" -> return "ElvisExpression"
    "KotlinUSafeQualifiedExpression" -> return "SafeQualifiedExpression"
    "" -> {
      // Anonymous inner class
      val inner = element.javaClass.name.substringAfterLast('.')
      if (
        inner.startsWith("JavaUSwitchEntry\$body\$") ||
          inner.startsWith("KotlinUSwitchEntry\$body\$")
      ) {
        return "SwitchEntryBody"
      } else if (inner.startsWith("ElvisExpressionKt\$createNotEqWithNullExpression\$")) {
        return "ElvisNullCheck"
      } else if (inner.startsWith("ElvisExpressionKt\$createElvisExpressions\$ifExpression\$")) {
        return "ElvisIfCheck"
      } else {
        println("What now?")
      }
    }
    "JavaImplicitUBlockExpression" -> return "Implicit Block"
    "JavaImplicitUReturnExpression",
    "KotlinUImplicitReturnExpression" -> return "Implicit Return"
    "JavaUQualifiedReferenceExpression",
    "JavaUCompositeQualifiedExpression",
    "KotlinUQualifiedReferenceExpression" -> return "QualifiedExpression"
    "KotlinLocalFunctionUVariable" -> return "LocalFunction"
    "KotlinLocalFunctionULambdaExpression" -> return "LocalFunctionLambda"
  }
  val type =
    simpleName
      .removePrefix("Kotlin")
      .removePrefix("Java")
      .removePrefix("Custom")
      .removePrefix("U")
      .replace("Reference", "Ref")
      .removeSuffix("Expression")
      .replace("Expression", "Ex")
      .replace("Function", "Func")
      .replace("Qualified", "Qlf")
      .let {
        when (it) {
          "AnnotatedLocalVariable" -> "LocalVariable"
          "FuncCallEx" -> "FuncCall"
          "DeclarationsEx" -> "Declaration"
          else -> it
        }
      }

  if (type == "Block") {
    when (val parent = element.uastParent) {
      is UTryExpression ->
        when (element) {
          parent.finallyClause -> return "Finally Block"
          parent.tryClause -> return "Try Block"
          else -> return "Catch Block"
        }
      is UIfExpression ->
        when (element) {
          parent.thenExpression -> return "Then Block"
          parent.elseExpression -> return "Else Block"
        }
    }
  }

  return type
}

/** Produce a source snippet for a given control flow graph node */
fun ControlFlowGraph<UElement>.Node.sourceString(): String {
  return if (!this.isExit()) {
    val sourcePsi = instruction.sourcePsi
    val text =
      if (sourcePsi == null) {
        val source = instruction.asSourceString()
        // Example: var var116517f9: java.util.List<? extends java.lang.String> =
        // list?.reversed()?.asReversed()
        if (source.startsWith("var var") && source.contains("=")) {
          // safe call expression
          "var ＄temp =" + source.substringAfter("=")
        } else if (source.startsWith("var") && !source.startsWith("var ") && source.contains(" ")) {
          // elvis expression
          "＄temp ".substringAfter(" ")
        } else if (source.startsWith("if (var")) {
          // Example: if (var3feb8ee7 != null) var3feb8ee7 else mutableListOf()
          val start = "if (var".length
          var end = start + 1
          // See psi/UastKotlinPsiVariable.kt
          // name = "var" + Integer.toHexString(declaration.getHashCode()),
          while (end < source.length && source[end].isLetterOrDigit()) {
            end++
          }
          return source.replace(source.substring(start, end), "＄temp")
        } else {
          instruction.asSourceString()
        }
      } else {
        sourcePsi.text
      }
    text.replace(Regex("\\s+"), " ").replace("\n", "\\n").ifBlank { "<synthetic>" }
  } else {
    "*exit*"
  }
}

/**
 * Print out the flow control graph in ASCII.
 *
 * This isn't as nice as a dot-representation, but the goal is to have it in a pretty compact yet
 * readable format such that it can be used in unit tests (to more directly test the operation of
 * the control flow graph construction than testing it indirectly via detector behaviors).
 */
fun <T> ControlFlowGraph<T>.prettyPrintGraph(
  start: T,
  nodeTypeString: (ControlFlowGraph<T>.Node) -> String,
  sourceString: (ControlFlowGraph<T>.Node) -> String?,
  nodes: List<ControlFlowGraph<T>.Node> = getAllNodes().toList(),
  filter: (ControlFlowGraph<T>.Node) -> Boolean = { true }
): String {
  val sb = StringBuilder()
  val ids = LinkedHashMap<ControlFlowGraph<T>.Node, String>()

  // Assign id's in source visit order
  var nextId = 1
  val width = floor(log10(nodes.size.toDouble()) + 1).toInt()

  fun assignId(graphNode: ControlFlowGraph<T>.Node?) {
    graphNode ?: return
    val id = if (graphNode.isExit()) nodes.size else nextId++
    ids[graphNode] = String.format(Locale.US, "N%0${width}d", id)
  }

  for (instruction in nodes) {
    assignId(instruction)
  }

  // Add any remaining nodes not part of a visit
  for (node in nodes) {
    if (ids[node] == null) {
      assignId(node)
    }
  }

  // Compute edge data
  val entries = ids.entries
  val sortedNodes: List<MutableMap.MutableEntry<ControlFlowGraph<T>.Node, String>> =
    entries.sortedBy { ids[it.key]!! }

  fun indexOf(target: ControlFlowGraph<T>.Node): Int {
    for (i in sortedNodes.indices) {
      if (target == sortedNodes[i].key) {
        return i
      }
    }
    return -1
  }

  val normalArrows = Arrows(entries.size)
  for ((node, _) in ids) {
    val fromIndex = indexOf(node)
    for (edge in node.successors.sortedBy { ids[it.to]!! }) {
      val toIndex = indexOf(edge.to)
      normalArrows.drawEdge(fromIndex, toIndex, rhs = false)
    }
  }

  val exceptionArrows = Arrows(entries.size)
  for ((node, _) in ids) {
    val fromIndex = indexOf(node)
    val exceptions = node.exceptions.sortedBy { ids[it.to] }
    for (edge in exceptions) {
      val toIndex = indexOf(edge.to)
      exceptionArrows.drawEdge(fromIndex, toIndex, dashed = true, rhs = true)
    }
  }
  for ((node, _) in ids) {
    val fromIndex = indexOf(node)
    val exceptions = node.exceptions.sortedBy { ids[it.to] }
    for (edge in exceptions) {
      val label = edge.label ?: continue
      // Not drawing labels on the left; doesn't fit. More important
      // for exceptional edges anyway.
      exceptionArrows.drawLabel(fromIndex, label.substringAfterLast('.'))
    }
  }

  val arrowLinesLhs = normalArrows.toLines()
  val arrowLinesRhs = exceptionArrows.toLines()

  var index = 0
  for ((node, _) in sortedNodes) {
    val type = nodeTypeString(node)
    val source = sourceString(node)
    if (source != null) {
      sb.append(String.format(Locale.US, "%20s", type.trimMiddle(20)))
      if (node.isExit()) {
        sb.append("  ")
      } else {
        sb.append(": ")
      }
      sb.append(arrowLinesLhs[index]).append(' ')
      val snippet = source.trimMiddle(30)
      val suffix = arrowLinesRhs[index]
      if (suffix.isNotBlank()) {
        sb.append(String.format(Locale.US, "%-31s", snippet))
        sb.append(suffix.trimEnd())
      } else {
        sb.append(snippet.trimEnd())
      }
    } else {
      sb.append(arrowLinesLhs[index]).append(' ')
      sb.append(type)
      sb.append(arrowLinesRhs[index].trimEnd())
    }

    sb.append("\n")
    index++
  }

  return sb.toString()
}

/** Draws ASCII arrow art */
private class Arrows(val size: Int) {
  private val spacing = 2 // Distance between each arrow column
  private val padding = 30 // extra space for labels etc
  private val display: Array<StringBuilder> =
    Array(size) { StringBuilder(" ".repeat(spacing * size + padding)) }

  fun drawEdge(
    fromIndex: Int,
    toIndex: Int,
    forward: Boolean = true,
    dashed: Boolean = false,
    rhs: Boolean = true
  ) {
    if (fromIndex > toIndex) {
      // Swap from/to such that we always draw downwards, but flip arrow directions too
      drawEdge(
        fromIndex = toIndex,
        toIndex = fromIndex,
        forward = !forward,
        dashed = dashed,
        rhs = rhs
      )
      return
    }
    val increment = if (rhs) spacing else -spacing
    var columnIndex = if (rhs) 1 else spacing * size - 2
    while (true) {
      var available = true
      for (row in fromIndex..toIndex) {
        if (display[row][columnIndex] != ' ') {
          available = false
          break
        }
      }
      if (available) {
        break
      } else {
        columnIndex += increment
      }
    }

    if (fromIndex == toIndex) {
      display[fromIndex][columnIndex] = '↺'
      return
    }

    val sideDelta = if (rhs) -1 else 1
    val fromArrow = if (forward) '─' else if (rhs) '←' else '→'
    val toArrow = if (!forward) '─' else if (rhs) '←' else '→'
    display[fromIndex][columnIndex] = if (rhs) '╮' else '╭'
    display[fromIndex][columnIndex + sideDelta] = fromArrow
    for (row in fromIndex + 1 until toIndex) {
      display[row][columnIndex] = if (dashed) '┆' else '│'
    }
    display[toIndex][columnIndex] = if (rhs) '╯' else '╰'
    display[toIndex][columnIndex + sideDelta] = toArrow
  }

  fun drawLabel(row: Int, label: String) {
    val line = display[row]
    var end = line.length - 1
    while (end >= 0 && line[end] == ' ') {
      end--
    }
    end += spacing
    for (c in label) {
      if (end == line.length) {
        break
      }
      line[end++] = c
    }
  }

  fun toLines(): List<String> {
    var left = display[0].length - 1
    var right = 0
    for (builder in display) {
      for (i in builder.indices) {
        if (builder[i] != ' ') {
          left = minOf(left, i)
          break
        }
      }
      for (i in builder.length - 1 downTo 0) {
        if (builder[i] != ' ') {
          right = maxOf(right, i + 1)
          break
        }
      }
    }

    left = minOf(left, right)

    return display.map { it.substring(left, right) }
  }
}
