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

/**
 * A method parameter represented by the parameter-value pairs of the parameters of its annotation.
 *
 * We assume that each parameter that has to be supplied (there might be parameters that do not
 * need to be supplied, e.g. Composable synthetic parameters) is annotated by an annotation that
 * with its parameters is specifying how to instantiate the annotated parameter.
 */
data class ParameterRepresentation(val annotationParameters: Map<String, Any?>)

/**
 * Represent a method where [methodFqn] is a fully qualified name of the method and [parameters] is
 * a list of [ParameterRepresentation]s.
 */
data class MethodRepresentation(
  val methodFqn: String,
  val parameters: List<ParameterRepresentation>
)
