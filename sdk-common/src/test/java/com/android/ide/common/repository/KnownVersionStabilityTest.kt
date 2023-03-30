/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ide.common.repository

import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Version
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KnownVersionStabilityTest {

    @Test
    fun testComponentStability() {
        fun component(group: String, name: String, version: String = "1.0.0") =
            Component(group, name, Version.parse(version))
        assertThat(component("com.android.support", "appcompat-v7").stability)
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(component("androidx.appcompat", "appcompat").stability)
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(component("com.android.support", "support-annotations").stability)
          .isEqualTo(KnownVersionStability.STABLE)
        assertThat(component("androidx.annotation", "annotation").stability)
          .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(component("com.android.support", "design").stability)
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(component("com.google.android.material", "material").stability)
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(component("com.android.support.constraint", "constraint-layout").stability)
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(component("androidx.constraintlayout", "constraintlayout").stability)
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(component("com.google.firebase", "firebase-core", "14.3.1").stability)
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(component("com.google.firebase", "firebase-core", "15.0.1").stability)
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(component("com.google.android.gms", "play-services-ads", "14.3.1").stability)
            .isEqualTo(KnownVersionStability.INCOMPATIBLE)
        assertThat(component("com.google.android.gms", "play-services-ads", "15.0.1").stability)
            .isEqualTo(KnownVersionStability.SEMANTIC)

        assertThat(component("org.jetbrains.kotlin", "kotlin-stdlib").stability)
            .isEqualTo(KnownVersionStability.STABLE)
        assertThat(component("org.jetbrains.kotlin", "kotlin-reflect").stability)
            .isEqualTo(KnownVersionStability.INCREMENTAL)
    }

    @Test
    fun testExpiration() {
        assertThat(KnownVersionStability.INCOMPATIBLE.expiration(Version.parse("3.4.5")))
            .isEqualTo(Version.prefixInfimum("3.4.6"))
        assertThat(KnownVersionStability.INCREMENTAL.expiration(Version.parse("3.4.5")))
            .isEqualTo(Version.prefixInfimum("3.5"))
        assertThat(KnownVersionStability.SEMANTIC.expiration(Version.parse("3.4.5")))
            .isEqualTo(Version.prefixInfimum("4"))
        assertThat(KnownVersionStability.STABLE.expiration(Version.parse("3.4.5")))
            .isEqualTo(Version.prefixInfimum("${Int.MAX_VALUE}"))
    }
}
