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

import com.android.build.api.variant.DeviceTestBuilder;
import com.android.build.api.variant.PropertyAccessNotAllowedException;
import com.android.build.gradle.internal.services.VariantBuilderServices;
import com.android.build.gradle.options.ProjectOptions;
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
        Mockito.when(variantBuilderServices.getProjectOptions()).thenReturn(projectOptions);

        DeviceTestBuilder tested = new DeviceTestBuilderImpl(variantBuilderServices, false);
        tested.getEnableMultiDex();
    }
}
