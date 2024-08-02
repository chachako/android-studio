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
package com.android.build.api.variant.impl;

import com.android.build.api.variant.AndroidVersion;
import com.android.build.api.variant.DeviceTestBuilder;
import com.android.build.api.variant.PropertyAccessNotAllowedException;
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo.DslDefinedDeviceTest;
import com.android.build.gradle.internal.services.VariantBuilderServices;
import com.android.build.gradle.options.ProjectOptions;

import com.google.common.collect.Lists;
import com.google.common.truth.Truth;

import org.junit.Test;
import org.mockito.Mockito;

/**
 * Tests written in Java to be able to test the getter for multidexEnabled which is a compilation
 * error in Kotlin.
 */
public class DeviceTestBuilderImplTest {

    @Test(expected = PropertyAccessNotAllowedException.class)
    public void testGetMultidexEnabled() {
        ProjectOptions projectOptions = Mockito.mock(ProjectOptions.class);
        VariantBuilderServices variantBuilderServices = Mockito.mock(VariantBuilderServices.class);
        GlobalVariantBuilderConfig global = Mockito.mock(GlobalVariantBuilderConfig.class);
        AndroidVersion androidVersion = Mockito.mock(AndroidVersion.class);

        Mockito.when(variantBuilderServices.getProjectOptions()).thenReturn(projectOptions);

        DslDefinedDeviceTest dslDefinedDeviceTest =
                new DslDefinedDeviceTest(DeviceTestBuilder.ANDROID_TEST_TYPE, true);

        DeviceTestBuilder builder =
                DeviceTestBuilderImpl.Companion.create(
                                Lists.newArrayList(dslDefinedDeviceTest),
                                variantBuilderServices,
                                global,
                                () -> androidVersion,
                                false)
                        .get(DeviceTestBuilder.ANDROID_TEST_TYPE);
        builder.getEnableMultiDex();
    }

    @Test
    public void testTargetSdkSetters() {
        ProjectOptions projectOptions = Mockito.mock(ProjectOptions.class);
        VariantBuilderServices variantBuilderServices = Mockito.mock(VariantBuilderServices.class);
        GlobalVariantBuilderConfig global = Mockito.mock(GlobalVariantBuilderConfig.class);
        AndroidVersion androidVersion = Mockito.mock(AndroidVersion.class);
        Mockito.when(variantBuilderServices.getProjectOptions()).thenReturn(projectOptions);

        DslDefinedDeviceTest dslDefinedDeviceTest =
                new DslDefinedDeviceTest(DeviceTestBuilder.ANDROID_TEST_TYPE, true);

        DeviceTestBuilder builder =
                DeviceTestBuilderImpl.Companion.create(
                                Lists.newArrayList(dslDefinedDeviceTest),
                                variantBuilderServices,
                                global,
                                () -> androidVersion,
                                false)
                        .get(DeviceTestBuilder.ANDROID_TEST_TYPE);

        Truth.assertThat(builder.getTargetSdk()).isNull();
        Truth.assertThat(builder.getTargetSdkPreview()).isNull();

        builder.setTargetSdk(43);
        Truth.assertThat(builder.getTargetSdk()).isEqualTo(43);
        Truth.assertThat(builder.getTargetSdkPreview()).isNull();

        builder.setTargetSdkPreview("M");
        Truth.assertThat(builder.getTargetSdk()).isNull();
        Truth.assertThat(builder.getTargetSdkPreview()).isEqualTo("M");

        builder.setTargetSdkPreview("N");
        Truth.assertThat(builder.getTargetSdk()).isNull();
        Truth.assertThat(builder.getTargetSdkPreview()).isEqualTo("N");

        builder.setTargetSdk(23);
        Truth.assertThat(builder.getTargetSdk()).isEqualTo(23);
        Truth.assertThat(builder.getTargetSdkPreview()).isNull();
    }
}
