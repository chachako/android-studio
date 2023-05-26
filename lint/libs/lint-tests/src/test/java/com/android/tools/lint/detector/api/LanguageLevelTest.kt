/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.lint.detector.api

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.client.api.UElementHandler
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiRecordHeader
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile

@Suppress("LintDocExample")
class LanguageLevelTest : AbstractCheckTest() {

  fun testJava() {
    // Regression test for b/283693337
    lint()
      .files(
        java("""
            record Person(String name, int age) {
            }
            """)
          .indented()
      )
      .javaLanguageLevel("17")
      .run()
      .expect(
        """
          src/Person.java:1: Warning: Java record found [_TestIssueId]
          record Person(String name, int age) {
                       ~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 1 warnings
          """
      )
  }

  fun testJavaInvalid() {
    // Regression test for b/283693337
    lint()
      .files(
        java("""
          record Person(String name, int age) {
          }
          """)
          .indented()
      )
      .javaLanguageLevel("11")
      .allowCompilationErrors()
      .run()
      .expectClean()
  }

  fun testKotlin() {
    // This isn't a particularly interesting test; it does use the language level setter,
    // but even though the code is using invalid code at that language level, Kotlin
    // doesn't seem to throw exceptions.
    lint()
      .files(
        kotlin(
            """
            typealias MyAlias = List<String>
            sealed interface Animal {
                fun makeSound()
            }
            class Dog : Animal {
                override fun makeSound() {
                    println("Woof!")
                }
            }
            """
          )
          .indented()
      )
      .kotlinLanguageLevel("1.0")
      .run()
      .expectClean()
  }

  override fun getDetector(): Detector = TestDetector()

  override fun getIssues(): List<Issue> = listOf(TEST_ISSUE)

  class TestDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
      return listOf(UFile::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
      return object : UElementHandler() {
        override fun visitFile(node: UFile) {
          val psiFile = node.sourcePsi
          if (psiFile is PsiJavaFile) {
            psiFile.accept(
              object : JavaRecursiveElementVisitor() {
                override fun visitRecordHeader(recordHeader: PsiRecordHeader) {
                  context.report(
                    TEST_ISSUE,
                    recordHeader,
                    context.getLocation(recordHeader),
                    "Java record found"
                  )
                }
              }
            )
          }
        }
      }
    }
  }

  companion object {
    val TEST_ISSUE =
      Issue.create(
        "_TestIssueId",
        "Not applicable",
        "Not applicable",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        Implementation(TestDetector::class.java, Scope.JAVA_FILE_SCOPE)
      )
  }
}
