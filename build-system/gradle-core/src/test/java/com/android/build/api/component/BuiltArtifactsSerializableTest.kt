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

package com.android.build.api.component

import com.android.ide.common.build.CommonBuiltArtifact
import com.android.ide.common.build.CommonBuiltArtifacts
import org.junit.Assert.fail
import org.junit.Test
import java.io.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KTypeProjection
import kotlin.reflect.jvm.javaType

class BuiltArtifactsSerializableTest {

    /**
     * This test is to ensure that all the types in the CommonBuiltArtifact and CommonBuiltArtifacts
     * are serializable.
     */
    @Test
    fun testBuiltArtifactsSerializable() {
        val commonBuiltArtifact = CommonBuiltArtifact::class
        val commonBuiltArtifacts = CommonBuiltArtifacts::class

        val classesToValidate = listOf(commonBuiltArtifact, commonBuiltArtifacts)
        val typesNotSerializable = mutableListOf<String>()
        classesToValidate.forEach { clazz ->
            // Iterate through each property of the class
            clazz.members.forEach { classProperty ->
                // Get the arguments of each property (e.g. `String` and `Int` in `Map<String, Int>`)
                val propertyArguments = classProperty.returnType.arguments
                if (propertyArguments.isEmpty()) {
                    val propertyType = classProperty.returnType.javaType
                    if (propertyType is Class<*> && !isPrimitiveClass(propertyType)) {
                        // Validate that the class is serializable
                        if (!Serializable::class.java.isAssignableFrom(propertyType)) {
                            typesNotSerializable.add("$propertyType in $clazz")
                        }
                    }
                }
                // Recursively validate the arguments of the property
                validateProperties(propertyArguments, typesNotSerializable, clazz)
            }
        }

        if (typesNotSerializable.isNotEmpty()) {
            val errorMessage =
                "The following types must be Serializable:\n\n${typesNotSerializable.joinToString("\n")}"
            fail(errorMessage)
        }
    }

    private fun validateProperties(
        properties: List<KTypeProjection>,
        typesNotSerializable: MutableList<String>,
        clazz: KClass<*>
    ) {
        properties.forEach { property ->
            val arguments = property.type?.arguments
            if (!arguments.isNullOrEmpty()) {
                validateProperties(arguments, typesNotSerializable, clazz)
            }
            val propertyType = property.type?.javaType
            if (propertyType != null && propertyType is Class<*> && !isPrimitiveClass(propertyType)) {
                if (!Serializable::class.java.isAssignableFrom(propertyType)) {
                    typesNotSerializable.add("$propertyType in $clazz")
                }
            }
        }
    }

    private fun isPrimitiveClass(type: Class<*>): Boolean {
        val primitiveWrapperTypes = setOf(
            "java.lang.Integer",
            "java.lang.Double",
            "java.lang.Boolean",
            "java.lang.Float",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Character"
        )
        return type.isPrimitive || type.name in primitiveWrapperTypes
    }
}
