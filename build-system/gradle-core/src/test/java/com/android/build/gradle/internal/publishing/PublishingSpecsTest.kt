/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.publishing

import com.android.build.gradle.internal.publishing.PublishingSpecs.Companion.getVariantPublishingSpec
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.builder.core.ComponentTypeImpl
import com.google.common.truth.Truth.assertThat
import org.gradle.api.attributes.LibraryElements
import org.junit.Test

class PublishingSpecsTest {

    @Test
    fun allComponentTypeExist() {
        for (type in ComponentTypeImpl.values()) {
            // TODO: to be fixed in following change
            if (type != ComponentTypeImpl.SCREENSHOT_TEST) {
                assertThat(PublishingSpecs.getVariantMap()).containsKey(type)
            }
        }
    }

    @Test
    fun `check output spec of CLASSES_DIR artifact type`() {
        val outputSpec = getVariantPublishingSpec(ComponentTypeImpl.LIBRARY).getSpec(
            AndroidArtifacts.ArtifactType.CLASSES_DIR,
            AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
        )
        checkNotNull(outputSpec)
        assertThat(outputSpec.artifactType).isEqualTo(AndroidArtifacts.ArtifactType.CLASSES_DIR)
        assertThat(outputSpec.publishedConfigTypes).containsExactly(AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS)
        assertThat(outputSpec.outputType).isEqualTo(InternalArtifactType.RUNTIME_LIBRARY_CLASSES_DIR)
        assertThat(outputSpec.libraryElements).isEqualTo(LibraryElements.CLASSES)
    }

    @Test
    fun `assert that library and test fixtures artifacts match`() {
        // the set of artifacts that are intentionally left out of test fixtures
        val testFixturesExcludedArtifacts = setOf(
            AndroidArtifacts.ArtifactType.AIDL,
            AndroidArtifacts.ArtifactType.ANDROID_TEST_LINT_MODEL,
            AndroidArtifacts.ArtifactType.ANDROID_TEST_LINT_PARTIAL_RESULTS,
            AndroidArtifacts.ArtifactType.ART_PROFILE,
            AndroidArtifacts.ArtifactType.JAVA_DOC_JAR,
            AndroidArtifacts.ArtifactType.JNI,
            AndroidArtifacts.ArtifactType.LINT,
            AndroidArtifacts.ArtifactType.LINT_MODEL,
            AndroidArtifacts.ArtifactType.LINT_MODEL_METADATA,
            AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS,
            AndroidArtifacts.ArtifactType.LINT_VITAL_LINT_MODEL,
            AndroidArtifacts.ArtifactType.LINT_VITAL_PARTIAL_RESULTS,
            AndroidArtifacts.ArtifactType.PREFAB_PACKAGE_CONFIGURATION,
            AndroidArtifacts.ArtifactType.PREFAB_PACKAGE,
            AndroidArtifacts.ArtifactType.RENDERSCRIPT,
            AndroidArtifacts.ArtifactType.R_CLASS_JAR,
            AndroidArtifacts.ArtifactType.SOURCES_JAR,
            AndroidArtifacts.ArtifactType.SUPPORTED_LOCALE_LIST,
            AndroidArtifacts.ArtifactType.TEST_FIXTURES_LINT_MODEL,
            AndroidArtifacts.ArtifactType.TEST_FIXTURES_LINT_PARTIAL_RESULTS,
            AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES,
            AndroidArtifacts.ArtifactType.UNIT_TEST_LINT_MODEL,
            AndroidArtifacts.ArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS,
        )

        val libraryOutputs = getVariantPublishingSpec(ComponentTypeImpl.LIBRARY).outputs
        val testFixturesOutputs = getVariantPublishingSpec(ComponentTypeImpl.TEST_FIXTURES).outputs
        assertThat(libraryOutputs.filterNot {
            testFixturesExcludedArtifacts.contains(it.artifactType)
        }).containsExactlyElementsIn(testFixturesOutputs)
    }
}
