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
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.use
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.getErrorLines
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultUastParserTest {

  // Regression test from b/256872453
  @Test
  fun testNameLocationForDefaultSetterParameter() {
    listOf(
        kotlin(
          """
                package test.pkg

                class Test {
                  var variable = ""
                }
                """
        )
      )
      .use { context ->
        var propertyNameLocation: Location? = null
        var propertyLocation: Location? = null
        var setterParameterNameLocation: Location? = null
        var setterParameterLocation: Location? = null
        context.uastFile!!.accept(
          object : AbstractUastVisitor() {
            override fun visitVariable(node: UVariable): Boolean {
              if (node.name == "variable") {
                propertyNameLocation = context.getNameLocation(node)
                propertyLocation = context.getLocation(node as UElement)
              }
              return super.visitVariable(node)
            }

            override fun visitParameter(node: UParameter): Boolean {
              if (node.name == SpecialNames.IMPLICIT_SET_PARAMETER.asString()) {
                setterParameterNameLocation = context.getNameLocation(node)
                setterParameterLocation = context.getLocation(node as UElement)
              }

              return super.visitParameter(node)
            }
          }
        )

        assertEquals(
          """
                var variable = ""
                ~~~~~~~~~~~~~~~~~
                """
            .trimIndent(),
          propertyLocation!!.getErrorLines { context.getContents() }?.trimIndent(),
        )

        assertEquals(
          """
                var variable = ""
                    ~~~~~~~~
                """
            .trimIndent(),
          propertyNameLocation!!.getErrorLines { context.getContents() }?.trimIndent(),
        )

        assertNotNull(setterParameterNameLocation)
        // Position 0 (from empty range) may indicate that this name location is valid,
        // which is not true for synthetic, default setter parameter, since it doesn't exist.
        // Better to restore/stick to the old behavior: `null` position.
        assertNull(setterParameterNameLocation!!.start)

        assertEquals(
          """
                var variable = ""
                ~~~~~~~~~~~~~~~~~
                """
            .trimIndent(),
          setterParameterLocation!!.getErrorLines { context.getContents() }?.trimIndent(),
        )

        assertNull(
          setterParameterNameLocation!!.getErrorLines { context.getContents() }?.trimIndent()
        )
      }
  }

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
      )
      .use { context ->
        var callLocation: Location? = null
        var declarationLocation: Location? = null

        context.uastFile!!.accept(
          object : AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
              when (node.name) {
                "foo" -> {
                  callLocation = context.getLocation(node.firstExpression)
                }
                "bar" -> {
                  declarationLocation = context.getLocation(node.firstExpression)
                }
              }
              return super.visitMethod(node)
            }
          }
        )

        assertEquals(
          """
                System.out.println("foo");
                ~~~~~~~~~~~~~~~~~~~~~~~~~
                """
            .trimIndent(),
          callLocation?.getErrorLines { context.getContents() }?.trimIndent(),
        )

        assertEquals(
          """
                String bar = "bar";
                ~~~~~~~~~~~~~~~~~~~
                """
            .trimIndent(),
          declarationLocation?.getErrorLines { context.getContents() }?.trimIndent(),
        )
      }
  }

  private val UMethod.firstExpression: UExpression?
    get() = (this.uastBody as? UBlockExpression)?.expressions?.firstOrNull()
}
