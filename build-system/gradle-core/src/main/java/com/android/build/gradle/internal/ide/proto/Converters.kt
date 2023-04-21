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

package com.android.build.gradle.internal.ide.proto

import com.android.build.api.variant.impl.SigningConfigImpl
import com.android.builder.model.proto.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.proto.ide.AndroidVersion
import com.android.builder.model.proto.ide.SigningConfig
import com.android.builder.model.proto.ide.TestInfo
import java.io.File

internal fun<T, R> R.setIfNotNull(
    value: T?,
    setter: R.(T) -> Unit
): R {
    if (value != null) {
        setter(value)
    }
    return this
}

internal fun File.convert() = com.android.builder.model.proto.ide.File.newBuilder()
    .setAbsolutePath(absolutePath)
    .build()

internal fun com.android.builder.model.v2.ide.TestInfo.Execution.convert(): TestInfo.Execution =
    when (this) {
        com.android.builder.model.v2.ide.TestInfo.Execution.HOST -> TestInfo.Execution.HOST
        com.android.builder.model.v2.ide.TestInfo.Execution.ANDROID_TEST_ORCHESTRATOR-> TestInfo.Execution.ANDROID_TEST_ORCHESTRATOR
        com.android.builder.model.v2.ide.TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR -> TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR
    }

internal fun com.android.builder.model.v2.ide.TestInfo.convert() =
    TestInfo.newBuilder()
        .setAnimationsDisabled(animationsDisabled)
        .setIfNotNull(
            execution?.convert(),
            TestInfo.Builder::setExecution
        )
        .addAllAdditionalRuntimeApks(
            additionalRuntimeApks.map { it.convert() }
        )
        .setInstrumentedTestTaskName(
            instrumentedTestTaskName
        )
        .build()

internal fun com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.convert() =
    when (this) {
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS ->
            AndroidGradlePluginProjectFlags.BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.TEST_R_CLASS_CONSTANT_IDS  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.TEST_R_CLASS_CONSTANT_IDS
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.TRANSITIVE_R_CLASS
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.JETPACK_COMPOSE
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.ML_MODEL_BINDING
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.UNIFIED_TEST_PLATFORM  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.UNIFIED_TEST_PLATFORM
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.USE_ANDROID_X  ->
            AndroidGradlePluginProjectFlags.BooleanFlag.USE_ANDROID_X
        com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.ENABLE_VCS_INFO ->
            AndroidGradlePluginProjectFlags.BooleanFlag.ENABLE_VCS_INFO
    }

internal fun com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.convert() =
    AndroidGradlePluginProjectFlags.newBuilder()
        .addAllBooleanFlagValues(
            com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag.values()
                .map { flag ->
                    AndroidGradlePluginProjectFlags.BooleanFlagValue.newBuilder()
                        .setFlag(flag.convert())
                        .setValue(flag.getValue(this))
                        .build()
                }
        )
        .build()

internal fun com.android.build.api.variant.AndroidVersion.convert() =
    AndroidVersion.newBuilder()
        .setApiLevel(apiLevel)
        .setIfNotNull(codename, AndroidVersion.Builder::setCodename)


internal fun SigningConfigImpl.convert() =
    SigningConfig.newBuilder()
        .setIfNotNull(name, SigningConfig.Builder::setName)
        .setIfNotNull(storeFile.orNull?.convert(), SigningConfig.Builder::setStoreFile)
        .setIfNotNull(storePassword.orNull, SigningConfig.Builder::setStorePassword)
        .setIfNotNull(keyAlias.orNull, SigningConfig.Builder::setKeyAlias)
        .setIfNotNull(keyPassword.orNull, SigningConfig.Builder::setKeyPassword)
        .setEnableV1Signing(enableV1Signing.get())
        .setEnableV2Signing(enableV2Signing.get())
        .setEnableV3Signing(enableV3Signing.get())
        .setEnableV4Signing(enableV4Signing.get())
        .setIsSigningReady(isSigningReady())
