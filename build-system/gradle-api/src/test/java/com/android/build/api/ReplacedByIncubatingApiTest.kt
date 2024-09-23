/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.api

import com.android.build.api.annotations.ReplacedByIncubating
import com.android.build.api.dsl.Lint
import com.android.testutils.ApiTester
import com.google.common.io.Resources
import com.google.common.reflect.ClassPath
import com.google.common.reflect.Invokable
import com.google.common.truth.Truth
import org.junit.Test
import java.io.InputStreamReader
import kotlin.test.fail

class ReplacedByIncubatingApiTest {

    private val snapshotFileUrl =
        Resources.getResource(DeprecatedApiTest::class.java, "incubating-api.txt")

    /**
     * When an incubating API is replacing an existing API, the existing API is not deprecated until
     * the incubating API becomes stable (otherwise, users have to choose between an incubating and a
     * deprecated API).
     *
     * Such replaced APIs are tracked with @ReplacedByIncubating to ensure they become deprecated the day the
     * incubating API graduates.
     *
     * Failing this test usually means that the [ReplacedByIncubating] annotation should be replaced
     * by a [Deprecated] and deprecated-api.txt should be updated.
     */
    @Test
    fun `ReplacedByIncubating references a bug which is present in incubating-api`() {
        val incubatingApis = InputStreamReader(snapshotFileUrl.openStream()).use {
            it.readText()
        }

        val classes = ClassPath.from(
                Lint::class.java.classLoader
                    ).getTopLevelClassesRecursive("com.android.build.api")
            .filter(::filterNonApiClasses)

        val apiTester = ApiTester(
                "ReplacedByIncubating Android Gradle Plugin API.",
                classes,
                ApiTester.Filter.REPLACED_BY_INCUBATING_ONLY,
                "",
                snapshotFileUrl,
                { content -> content },
                ApiTester.Flag.OMIT_HASH
        )

        classes.forEach { classInfo ->
            val apiClass = classInfo.load()
            val methods = apiClass.declaredMethods.map { m -> Invokable.from(m)}

            // first let's check if that class is annotated, if it is, no need to check any of its
            // methods and fields so we don't provide repeating error messages for one of them.
            findReplacedByIncubatingAnnotation(apiClass)?.let { replacedByIncubating ->
                checkAnnotation(replacedByIncubating, incubatingApis, "class ${apiClass.name}")
                // done with this type.
                return@forEach
            }
            apiTester.getApiElements(apiClass).forEach { apiElement ->
                methods.firstOrNull { ApiTester.getApiElement(it) == apiElement }?.let { method ->
                    findReplacedByIncubatingAnnotation(method)?.let { replacedByIncubating ->
                        checkAnnotation(replacedByIncubating, incubatingApis,"method $method")
                } ?:
                    // to keep things simple, this test is not handling constructors or fields.
                    // Since 99% of DSL and Variant API are interfaces, it should not be a problem
                    // but there might be cases in the future requiring to support more member types.
                    fail("$apiElement not found, please update the test to handle this type.")
                }

            }
        }
    }

    private fun checkAnnotation(
        replacedByIncubating: ReplacedByIncubating,
        incubatingApis: String,
        memberDescription: String,
    ) {
        Truth
            .assertWithMessage(
"""The $memberDescription
is annotated with @ReplacedByIncubating which mean it is scheduled to be replaced by an incubating API.

The incubating api was tracked by http://b/${replacedByIncubating.bugId}
Yet, there is no mention of that bug in the incubating-api.txt file.
Did you graduate the incubating API to stable and forgot to deprecate the annotated element ?

Original @ReplacedByIncubating annotation :
@ReplacedByIncubating(message = "${replacedByIncubating.message}", bugId = ${replacedByIncubating.bugId})
$memberDescription
"""
            )
            .that(incubatingApis)
            .contains("https://issuetracker.google.com/${replacedByIncubating.bugId}")
    }


    private fun findReplacedByIncubatingAnnotation(invokable: Invokable<*, *>): ReplacedByIncubating? =
        invokable.annotations.firstOrNull { annotation -> annotation is ReplacedByIncubating } as? ReplacedByIncubating
            ?: findReplacedByIncubatingAnnotation(invokable.declaringClass)

    private fun findReplacedByIncubatingAnnotation(clazz: Class<*>): ReplacedByIncubating? =
        clazz.annotations.firstOrNull { annotation: Annotation -> annotation is ReplacedByIncubating } as? ReplacedByIncubating


}
