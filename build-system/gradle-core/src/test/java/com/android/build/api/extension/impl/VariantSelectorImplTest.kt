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

package com.android.build.api.extension.impl

import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.LibraryVariantBuilder
import com.android.build.api.variant.VariantBuilder
import com.google.common.truth.Truth
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.regex.Pattern

/**
 * Tests for [VariantSelectorImpl]
 */
internal class VariantSelectorImplTest {

    @Test
    fun testAll() {
        val variantSelector = VariantSelectorImpl().all() as VariantSelectorImpl
        val variant = mock<VariantBuilder>()
        Truth.assertThat(variantSelector.appliesTo(variant)).isTrue()
    }

    @Test
    fun testWithName() {
        val variantSelector = VariantSelectorImpl()
                .withName(Pattern.compile("F.o")) as VariantSelectorImpl
        val variantFoo = mock<ApplicationVariantBuilder>()
        whenever(variantFoo.name).thenReturn("Foo")
        val variantFuo = mock<ApplicationVariantBuilder>()
        whenever(variantFuo.name).thenReturn("Fuo")
        val variantBar = mock<ApplicationVariantBuilder>()
        whenever(variantBar.name).thenReturn("Bar")
        Truth.assertThat(variantSelector.appliesTo(variantFoo)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(variantFuo)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(variantBar)).isFalse()
    }

    @Test
    fun testWithBuildType() {
        val variantSelector = VariantSelectorImpl()
                .withBuildType("debug") as VariantSelectorImpl
        val debugVariant1 = mock<ApplicationVariantBuilder>()
        whenever(debugVariant1.buildType).thenReturn("debug")
        val debugVariant2 = mock<ApplicationVariantBuilder>()
        whenever(debugVariant2.buildType).thenReturn("debug")
        val releaseVariant = mock<ApplicationVariantBuilder>()
        whenever(releaseVariant.buildType).thenReturn("release")
        Truth.assertThat(variantSelector.appliesTo(debugVariant1)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(debugVariant2)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(releaseVariant)).isFalse()
    }

    @Test
    fun testWithBuildTypeAndName() {
        val variantSelector = VariantSelectorImpl()
                .withBuildType("debug")
                .withName(Pattern.compile("F.o")) as VariantSelectorImpl
        val debugVariant1 = mock<ApplicationVariantBuilder>()
        whenever(debugVariant1.buildType).thenReturn("debug")
        whenever(debugVariant1.name).thenReturn("Foo")
        val debugVariant2 = mock<ApplicationVariantBuilder>()
        whenever(debugVariant2.buildType).thenReturn("debug")
        whenever(debugVariant2.name).thenReturn("Bar")
        val releaseVariant = mock<ApplicationVariantBuilder>()
        whenever(releaseVariant.buildType).thenReturn("release")
        whenever(releaseVariant.name).thenReturn("Foo")
        Truth.assertThat(variantSelector.appliesTo(debugVariant1)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(debugVariant2)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(releaseVariant)).isFalse()
    }

    @Test
    fun testWithProductFlavor() {
        val flavorAndDimensionVariantSelector = VariantSelectorImpl()
                .withFlavor("dim1" to "flavor1")
        val variantSelectorWithoutPair = VariantSelectorImpl()
                .withFlavor("dim3","flavor1")
        val flavor1Variant = mock<ApplicationVariantBuilder>()
        val flavor2variant = mock<ApplicationVariantBuilder>()
        val flavor1Dim3Variant = mock<ApplicationVariantBuilder>()

        whenever(flavor1Variant.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        whenever(flavor2variant.productFlavors).thenReturn(
                listOf("dim2" to "flavor2", "dim3" to "flavor3"))
        whenever(flavor1Dim3Variant.productFlavors).thenReturn(
                listOf("dim3" to "flavor1"))

        Truth.assertThat(flavorAndDimensionVariantSelector.appliesTo(flavor1Variant)).isTrue()
        Truth.assertThat(flavorAndDimensionVariantSelector.appliesTo(flavor2variant)).isFalse()

        Truth.assertThat(variantSelectorWithoutPair.appliesTo(flavor1Dim3Variant)).isTrue()
        Truth.assertThat(variantSelectorWithoutPair.appliesTo(flavor1Variant)).isFalse()
        Truth.assertThat(variantSelectorWithoutPair.appliesTo(flavor2variant)).isFalse()
    }

    @Test
    fun testWithBuildTypeAndFlavor() {
        val variantSelector = VariantSelectorImpl()
                .withFlavor("dim1" to "flavor1")
                .withBuildType("debug") as VariantSelectorImpl

        val applicationVariantBuilder1 = mock<ApplicationVariantBuilder>()
        whenever(applicationVariantBuilder1.buildType).thenReturn("debug")
        whenever(applicationVariantBuilder1.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        val applicationVariantBuilder2 = mock<ApplicationVariantBuilder>()
        whenever(applicationVariantBuilder2.buildType).thenReturn("release")
        whenever(applicationVariantBuilder2.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        val libraryVariantBuilder = mock<LibraryVariantBuilder>()
        whenever(libraryVariantBuilder.buildType).thenReturn("debug")
        whenever(libraryVariantBuilder.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))

        Truth.assertThat(variantSelector.appliesTo(applicationVariantBuilder1)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(applicationVariantBuilder2)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(libraryVariantBuilder)).isTrue()
    }

    @Test
    fun testWithBuildTypeAndFlavorAndName() {
        val variantSelector = VariantSelectorImpl()
                .withFlavor("dim1" to "flavor1")
                .withBuildType("debug")
                .withName(Pattern.compile("F.o")) as VariantSelectorImpl

        val variantFoo = mock<ApplicationVariantBuilder>()
        whenever(variantFoo.buildType).thenReturn("debug")
        whenever(variantFoo.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        whenever(variantFoo.name).thenReturn("Foo")

        val variantBar = mock<ApplicationVariantBuilder>()
        whenever(variantBar.buildType).thenReturn("debug")
        whenever(variantBar.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        whenever(variantBar.name).thenReturn("Bar")

        val variantFoo2 = mock<ApplicationVariantBuilder>()
        whenever(variantFoo2.buildType).thenReturn("release")
        whenever(variantFoo2.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        whenever(variantFoo2.name).thenReturn("Foo")

        val variantFoo3 = mock<ApplicationVariantBuilder>()
        whenever(variantFoo3.buildType).thenReturn("release")
        whenever(variantFoo3.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        whenever(variantFoo3.name).thenReturn("Foo")

        val libraryVariantBuilder = mock<LibraryVariantBuilder>()
        whenever(libraryVariantBuilder.buildType).thenReturn("debug")
        whenever(libraryVariantBuilder.productFlavors).thenReturn(
                listOf("dim1" to "flavor1", "dim2" to "flavor2"))
        whenever(libraryVariantBuilder.name).thenReturn("Foo")

        Truth.assertThat(variantSelector.appliesTo(variantFoo)).isTrue()
        Truth.assertThat(variantSelector.appliesTo(variantBar)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(variantFoo2)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(variantFoo3)).isFalse()
        Truth.assertThat(variantSelector.appliesTo(libraryVariantBuilder)).isTrue()
    }
}
