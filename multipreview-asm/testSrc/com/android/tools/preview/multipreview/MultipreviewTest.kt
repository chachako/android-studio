/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.preview.multipreview

import com.android.tools.preview.multipreview.testclasses.A
import com.android.tools.preview.multipreview.testclasses.B
import com.android.tools.preview.multipreview.testclasses.BaseAnnotation
import com.android.tools.preview.multipreview.testclasses.DerivedAnnotation1Lvl1
import com.android.tools.preview.multipreview.testclasses.DerivedAnnotation1Lvl2
import com.android.tools.preview.multipreview.testclasses.DerivedAnnotation2Lvl1
import com.android.tools.preview.multipreview.testclasses.NotPreviewAnnotation
import com.android.tools.preview.multipreview.testclasses.RecursiveAnnotation1
import com.android.tools.preview.multipreview.testclasses.RecursiveAnnotation2
import org.junit.Test
import org.objectweb.asm.Type
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals

private const val PKG = "com.android.tools.preview.multipreview.testclasses"
private const val BASE_ANNOTATION = "$PKG.BaseAnnotation"
private const val PARAMETER_ANNOTATION = "$PKG.ParamAnnotation"

class MultipreviewTest {

  @Test
  fun testNoParametersSingleAnnotation() {
    commonTestCase(
      listOf(
        Class.forName("$PKG.SimpleAnnotatedMethodKt")
      ),
      arrayOf(method("$PKG.SimpleAnnotatedMethodKt.simpleMethod")),
      listOf(arrayOf(BaseAnnotationRepresentation(emptyMap())))
    )
  }

  @Test
  fun testDuplicateAnnotation() {
    commonTestCase(
      listOf(
        Class.forName("$PKG.DuplicateAnnotatedMethodKt")
      ),
      arrayOf(method("$PKG.DuplicateAnnotatedMethodKt.duplicateAnnotated")),
      listOf(arrayOf(BaseAnnotationRepresentation(emptyMap())))
    )
  }

  @Test
  fun testRepeatedlyAnnotatedMethod() {
    commonTestCase(
      listOf(
        Class.forName("$PKG.RepeatedlyAnnotatedMethodKt")
      ),
      arrayOf(method("$PKG.RepeatedlyAnnotatedMethodKt.repeatedlyAnnotated")),
      listOf(
        arrayOf(
          BaseAnnotationRepresentation(emptyMap()),
          BaseAnnotationRepresentation(mapOf("paramName1" to "a")),
        )
      )
    )
  }

  @Test
  fun testSimpleMultipreviewAnnotation() {
    commonTestCase(
      listOf(
        DerivedAnnotation1Lvl1::class.java,
        Class.forName("$PKG.SimpleMultipreviewMethodKt")
      ),
      arrayOf(method("$PKG.SimpleMultipreviewMethodKt.simpleMultipreview")),
      listOf(
        arrayOf(
          BaseAnnotationRepresentation(emptyMap()),
          BaseAnnotationRepresentation(mapOf("paramName1" to "foo")),
        )
      )
    )
  }

  @Test
  fun testParameterizedMethod() {
    commonTestCase(
      listOf(
        Class.forName("$PKG.ParameterizedMethodsKt")
      ),
      arrayOf(
        method("$PKG.ParameterizedMethodsKt.parameterized"),
        method(
          "$PKG.ParameterizedMethodsKt.parameterized",
          listOf(
            mapOf("provider" to Type.getType(A::class.java))
          )
        ),
        method(
          "$PKG.ParameterizedMethodsKt.parameterized",
          listOf(
            mapOf("provider" to Type.getType(A::class.java)),
            mapOf("provider" to Type.getType(B::class.java), "param2" to "foo")
          )
        ),
      ),
      listOf(
        arrayOf(BaseAnnotationRepresentation(emptyMap())),
        arrayOf(BaseAnnotationRepresentation(emptyMap())),
        arrayOf(BaseAnnotationRepresentation(emptyMap())),
      )
    )
  }

  @Test
  fun testRecursiveAnnotationsDoNotLoopInfinitely() {
    val classStreams = listOf(
      RecursiveAnnotation1::class.java,
      RecursiveAnnotation2::class.java,
      Class.forName("$PKG.MethodWithRecursiveAnnotationKt")
    ).map { loadClassBytecode(it) }

    buildMultipreview(settings) { processor ->
      classStreams.forEach { processor.onClassBytecode(it) }
    }
  }

  @Test
  fun testComplexMultipreviewCase() {
    commonTestCase(
      listOf(
        BaseAnnotation::class.java,
        DerivedAnnotation1Lvl1::class.java,
        DerivedAnnotation1Lvl2::class.java,
        DerivedAnnotation2Lvl1::class.java,
        Class.forName("$PKG.AnnotatedMethods1Kt"),
        Class.forName("$PKG.AnnotatedMethods2Kt"),
        NotPreviewAnnotation::class.java

      ),
      arrayOf(
        method("$PKG.AnnotatedMethods1Kt.method1"),
        method("$PKG.AnnotatedMethods1Kt.method2"),
        method("$PKG.AnnotatedMethods2Kt.method3"),
        method("$PKG.AnnotatedMethods2Kt.method4"),
        method("$PKG.AnnotatedMethods2Kt.method5"),
        method("$PKG.AnnotatedMethods2Kt.method6"),
      ),
      listOf(
        arrayOf(BaseAnnotationRepresentation(emptyMap())),
        arrayOf(
          BaseAnnotationRepresentation(emptyMap()),
          BaseAnnotationRepresentation(mapOf("paramName1" to "foo"))
        ),
        arrayOf(
          BaseAnnotationRepresentation(mapOf("paramName2" to 42)),
          BaseAnnotationRepresentation(mapOf("paramName1" to "bar", "paramName2" to 1))
        ),
        arrayOf(
          BaseAnnotationRepresentation(emptyMap()),
          BaseAnnotationRepresentation(mapOf("paramName1" to "foo")),
          BaseAnnotationRepresentation(mapOf("paramName1" to "baz", "paramName2" to 2)),
        ),
        arrayOf(
          BaseAnnotationRepresentation(emptyMap()),
          BaseAnnotationRepresentation(mapOf("paramName1" to "foo")),
          BaseAnnotationRepresentation(mapOf("paramName1" to "baz", "paramName2" to 2)),
          BaseAnnotationRepresentation(mapOf("paramName1" to "qwe", "paramName2" to 3)),
        ),
        arrayOf(
          BaseAnnotationRepresentation(emptyMap()),
          BaseAnnotationRepresentation(mapOf("paramName1" to "foo")),
          BaseAnnotationRepresentation(mapOf("paramName1" to "asd", "paramName2" to 4)),
          BaseAnnotationRepresentation(mapOf("paramName1" to "baz", "paramName2" to 2)),
          BaseAnnotationRepresentation(mapOf("paramName1" to "zxc", "paramName2" to 5)),
        )
      )
    )
  }

  private fun commonTestCase(
    classes: List<Class<*>>,
    methods: Array<MethodRepresentation>,
    methodAnnotations: List<Array<BaseAnnotationRepresentation>>
  ) {
    val classesBytecode = classes.map { loadClassBytecode(it) }

    val multipreview = buildMultipreview(settings) { processor ->
      classesBytecode.forEach(processor::onClassBytecode)
    }

    val sortedMethods = multipreview.methods.sortedWith(MethodComparator).toTypedArray()

    assertArrayEquals(methods, sortedMethods)

    assertEquals("Unexpected amount of methods", methodAnnotations.size, sortedMethods.size)

    for (i in sortedMethods.indices) {
      assertArrayEquals(
        methodAnnotations[i],
        multipreview.getAnnotations(sortedMethods[i])
          .sortedWith(AnnotationComparator)
          .toTypedArray()
      )
    }
  }

  companion object {

    private val settings = MultipreviewSettings(BASE_ANNOTATION, PARAMETER_ANNOTATION)

    private fun compareMaps(m1: Map<String, Any?>, m2: Map<String, Any?>): Int {
      if (m1.size != m2.size) {
        return m1.size.compareTo(m2.size)
      }

      val sortedKeys1 = m1.keys.sorted()
      val sortedKeys2 = m2.keys.sorted()
      sortedKeys1.indices.forEach {
        if (sortedKeys1[it] != sortedKeys2[it]) {
          return sortedKeys1[it].compareTo(sortedKeys2[it])
        }
      }
      sortedKeys1.indices.forEach {
        val key = sortedKeys1[it]
        val v1 = m1[key]
        val v2 = m2[key]
        if (v1 != v2) {
          if (v1 == null) {
            return -1
          }
          return v1.toString().compareTo(v2.toString())
        }
      }

      return 0
    }

    private object MethodComparator : Comparator<MethodRepresentation> {

      override fun compare(m1: MethodRepresentation, m2: MethodRepresentation): Int {
        if (m1 === m2) {
          return 0
        }

        if (m1.methodFqn != m2.methodFqn) {
          return m1.methodFqn.compareTo(m2.methodFqn)
        }

        if (m1.parameters.size != m2.parameters.size) {
          return m1.parameters.size.compareTo(m2.parameters.size)
        }

        m1.parameters.indices.forEach {
          val c =
            compareMaps(
              m1.parameters[it].annotationParameters,
              m2.parameters[it].annotationParameters
            )

          if (c != 0) {
            return c
          }
        }

        return 0
      }
    }

    internal object AnnotationComparator : Comparator<BaseAnnotationRepresentation> {

      override fun compare(
        a1: BaseAnnotationRepresentation,
        a2: BaseAnnotationRepresentation,
      ): Int {
        if (a1 === a2) {
          return 0
        }

        return compareMaps(a1.parameters, a2.parameters)
      }
    }

    private fun loadClassBytecode(c: Class<*>): ByteArray {
      val className = "${Type.getInternalName(c)}.class"
      return c.classLoader.getResourceAsStream(className)!!.readAllBytes()
    }

    private fun method(
      name: String,
      params: List<Map<String, Any?>> = emptyList()
    ): MethodRepresentation = MethodRepresentation(name, params.map { ParameterRepresentation(it) })
  }
}
