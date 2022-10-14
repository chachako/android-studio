/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.helpers

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.use
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.getErrorLines
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultUastParserTest {
    @Test
    fun testUDeclarationsExpression() {
        listOf(
            java(
                """
                package test.pkg;
                class Test {
                  public void foo() {
                    System.out.println("foo");
                  }
                  public void bar() {
                    String bar = "bar";
                    System.out.println(bar);
                  }
                }
                """
            )
        ).use { context ->
            val file = context.uastFile!!
            var callLocation: Location? = null
            var declarationLocation: Location? = null

            file.accept(object : AbstractUastVisitor() {
                override fun visitMethod(node: UMethod): Boolean {
                    when (node.name) {
                        "foo" -> {
                            callLocation = context.getLocation(node.firstExpression)
                        }
                        "bar" -> {
                            declarationLocation = context.getLocation(node.firstExpression)
                        }
                    }
                    return true
                }
            })

            assertEquals(
                """
                System.out.println("foo");
                ~~~~~~~~~~~~~~~~~~~~~~~~~
                """.trimIndent(),
                callLocation?.getErrorLines { context.getContents() }?.trimIndent()
            )

            assertEquals(
                """
                String bar = "bar";
                ~~~~~~~~~~~~~~~~~~~
                """.trimIndent(),
                declarationLocation?.getErrorLines { context.getContents() }?.trimIndent()
            )
        }
    }

    private val UMethod.firstExpression: UExpression?
        get() = (this.uastBody as? UBlockExpression)?.expressions?.firstOrNull()
}
