/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.Packaging
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/** Test for the mutablitly of Packaging Options */
class PackagingTest {

    private lateinit var packaging: Packaging
    private val dslServices: DslServices = createDslServices()

    interface PackagingOptionsWrapper {
        val packaging: Packaging
    }

    @Before
    fun init() {
        packaging  = androidPluginDslDecorator.decorate(PackagingOptionsWrapper::class.java)
            .getDeclaredConstructor(DslServices::class.java)
            .newInstance(dslServices)
            .packaging
    }

    @Test
    fun `test defaults`() {
        assertThat(packaging.excludes)
            .containsExactly(
                "**/*.kotlin_metadata",
                "**/*~",
                "**/.*",
                "**/.*/**",
                "**/.svn/**",
                "**/CVS/**",
                "**/SCCS/**",
                "**/_*",
                "**/_*/**",
                "**/about.html",
                "**/overview.html",
                "**/package.html",
                "**/picasa.ini",
                "**/protobuf.meta",
                "**/thumbs.db",
                "/LICENSE",
                "/LICENSE.txt",
                "/META-INF/*.DSA",
                "/META-INF/*.EC",
                "/META-INF/*.RSA",
                "/META-INF/*.SF",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/MANIFEST.MF",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/com.android.tools/**",
                "/META-INF/maven/**",
                "/META-INF/proguard/*",
                "/NOTICE",
                "/NOTICE.txt"
            )
        assertThat(packaging.pickFirsts).isEmpty()
        assertThat(packaging.merges)
            .containsExactly(
                "/META-INF/services/**",
                "jacoco-agent.properties",
            )
        assertThat(packaging.doNotStrip).isEmpty()
    }

    @Test
    fun `test excludes mutations are possible`() {
        packaging.excludes.clear()
        assertThat(packaging.excludes).isEmpty()

        packaging.exclude("example1")
        packaging.excludes.add("example2")
        assertThat(packaging.excludes).containsExactly("example1", "example2")

        (packaging as com.android.build.gradle.internal.dsl.Packaging)
            .setExcludes(Sets.union(packaging.excludes, setOf("example3")))
        assertThat(packaging.excludes).containsExactly("example1", "example2", "example3")
    }

    @Test
    fun pickFirsts() {
        packaging.pickFirsts.clear()
        assertThat(packaging.pickFirsts).isEmpty()

        packaging.pickFirst("example1")
        packaging.pickFirsts.add("example2")
        assertThat(packaging.pickFirsts).containsExactly("example1", "example2")

        (packaging as com.android.build.gradle.internal.dsl.Packaging)
            .setPickFirsts(Sets.union(packaging.pickFirsts, setOf("example3")))
        assertThat(packaging.pickFirsts).containsExactly("example1", "example2", "example3");
    }

    @Test
    fun merges() {
        packaging.merges.clear()
        assertThat(packaging.merges).isEmpty()

        packaging.merge("example1")
        packaging.merges.add("example2")
        assertThat(packaging.merges).containsExactly("example1", "example2")

        (packaging as com.android.build.gradle.internal.dsl.Packaging)
            .setMerges(Sets.union(packaging.merges, setOf("example3")))
        assertThat(packaging.merges).containsExactly("example1", "example2", "example3");
    }

    @Test
    fun doNotStrip() {
        packaging.doNotStrip.clear()
        assertThat(packaging.doNotStrip).isEmpty()

        packaging.doNotStrip("example1")
        packaging.doNotStrip.add("example2")
        assertThat(packaging.doNotStrip).containsExactly("example1", "example2")

        (packaging as com.android.build.gradle.internal.dsl.Packaging)
            .setDoNotStrip(Sets.union(packaging.doNotStrip, setOf("example3")))
        assertThat(packaging.doNotStrip).containsExactly("example1", "example2", "example3");
    }
}
