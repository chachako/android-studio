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

package com.android.tools.preview.multipreview.testclasses

@DerivedAnnotation2Lvl1
fun method3() {

}

@DerivedAnnotation1Lvl2
fun method4() {

}

@BaseAnnotation("qwe", 3)
@DerivedAnnotation1Lvl2
fun method5() {

}

@DerivedAnnotation1Lvl2
@BaseAnnotation("asd", 4)
@BaseAnnotation("zxc", 5)
fun method6() {

}

annotation class NotPreviewAnnotation

@NotPreviewAnnotation
fun methodWithNonPreviewAnnotations() {

}
