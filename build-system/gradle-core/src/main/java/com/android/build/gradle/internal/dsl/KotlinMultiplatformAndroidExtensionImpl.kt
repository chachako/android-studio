/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidTestConfiguration
import com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnDeviceConfiguration
import com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnJvmConfiguration
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreLocation
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.builder.core.BuilderConstants
import com.android.builder.core.LibraryRequest
import com.android.builder.core.ToolsRevisionUtils
import com.android.builder.signing.DefaultSigningConfig
import javax.inject.Inject

internal abstract class KotlinMultiplatformAndroidExtensionImpl @Inject @WithLazyInitialization("lazyInit") constructor(
    private val dslServices: DslServices,
    private val enablingTestOnJvmCallBack: (KotlinMultiplatformAndroidTestConfigurationImpl) -> Unit,
    private val enablingTestOnDeviceCallBack: (KotlinMultiplatformAndroidTestConfigurationImpl) -> Unit,
): KotlinMultiplatformAndroidExtension, Lockable {

    fun lazyInit() {
        buildToolsVersion = ToolsRevisionUtils.DEFAULT_BUILD_TOOLS_REVISION.toString()
        DefaultSigningConfig.DebugSigningConfig(
            getBuildService(
                dslServices.buildServiceRegistry,
                AndroidLocationsBuildService::class.java
            ).get().getDefaultDebugKeystoreLocation()
        ).copyToSigningConfig(signingConfig)
    }

    abstract val libraryRequests: MutableList<LibraryRequest>

    override fun useLibrary(name: String) {
        useLibrary(name, true)
    }

    override fun useLibrary(name: String, required: Boolean) {
        libraryRequests.add(LibraryRequest(name, required))
    }

    var signingConfig = dslServices.newDecoratedInstance(
        SigningConfig::class.java, BuilderConstants.DEBUG, dslServices
    )

    internal val minSdkVersion: MutableAndroidVersion
        get() = mutableMinSdk?.sanitize()?.let { MutableAndroidVersion(it.apiLevel, it.codename) }
            ?: MutableAndroidVersion(1)

    private var mutableMinSdk: MutableAndroidVersion? = null

    override var minSdk: Int?
        get() = mutableMinSdk?.api
        set(value) {
            val min =
                mutableMinSdk ?: MutableAndroidVersion(null, null).also {
                    mutableMinSdk = it
                }
            min.codename = null
            min.api = value
        }

    override var minSdkPreview: String?
        get() = mutableMinSdk?.codename
        set(value) {
            val min =
                mutableMinSdk ?: MutableAndroidVersion(null, null).also {
                    mutableMinSdk = it
                }
            min.codename = value
            min.api = null
        }

    override val testCoverage = dslServices.newInstance(JacocoOptions::class.java)

    internal var androidTestOnJvmConfiguration: KotlinMultiplatformAndroidTestOnJvmConfigurationImpl? = null
    internal var androidTestOnDeviceConfiguration: KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl? = null

    private fun withAndroidTest(
        compilationName: String,
        previousConfiguration: KotlinMultiplatformAndroidTestConfigurationImpl?,
        type: String
    ): KotlinMultiplatformAndroidTestConfiguration {
        previousConfiguration?.let {
            throw IllegalAccessException(
                "Android tests on $type has already been enabled, and a corresponding compilation " +
                        "(`${it.compilationName}`) has already been created. You can create only " +
                        "one component of type android tests on $type. Alternatively, you can " +
                        "specify a dependency from the default sourceSet " +
                        "(`${it.defaultSourceSetName}`) to another sourceSet and it will be " +
                        "included in the compilation."
            )
        }

        return when(type) {
            "jvm" -> dslServices.newDecoratedInstance(
                KotlinMultiplatformAndroidTestOnJvmConfigurationImpl::class.java, compilationName, dslServices
            )
            "device" -> dslServices.newDecoratedInstance(
                KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl::class.java, compilationName, dslServices
            )
            else -> throw IllegalArgumentException(
                "Invalid test compilation type. Supported types are: jvm and device"
            )
        }

    }

    override fun withAndroidTestOnJvm(
        compilationName: String,
        action: KotlinMultiplatformAndroidTestOnJvmConfiguration.() -> Unit
    ) {
        androidTestOnJvmConfiguration = withAndroidTest(
            compilationName,
            androidTestOnJvmConfiguration,
            "jvm"
        ) as KotlinMultiplatformAndroidTestOnJvmConfigurationImpl

        action(androidTestOnJvmConfiguration!!)

        enablingTestOnJvmCallBack(androidTestOnJvmConfiguration!!)
    }

    override fun withAndroidTestOnJvm() {
        androidTestOnJvmConfiguration = withAndroidTest(
            TEST_ON_JVM_DEFAULT_COMPILATION_NAME,
            androidTestOnJvmConfiguration,
            "jvm"
        ) as KotlinMultiplatformAndroidTestOnJvmConfigurationImpl

        enablingTestOnJvmCallBack(androidTestOnJvmConfiguration!!)
    }

    override fun withAndroidTestOnJvm(compilationName: String) {
        androidTestOnJvmConfiguration = withAndroidTest(
            compilationName,
            androidTestOnJvmConfiguration,
            "jvm"
        ) as KotlinMultiplatformAndroidTestOnJvmConfigurationImpl

        enablingTestOnJvmCallBack(androidTestOnJvmConfiguration!!)
    }

    override fun withAndroidTestOnJvm(action: KotlinMultiplatformAndroidTestOnJvmConfiguration.() -> Unit) {
        androidTestOnJvmConfiguration = withAndroidTest(
            TEST_ON_JVM_DEFAULT_COMPILATION_NAME,
            androidTestOnJvmConfiguration,
            "jvm"
        ) as KotlinMultiplatformAndroidTestOnJvmConfigurationImpl

        action(androidTestOnJvmConfiguration!!)

        enablingTestOnJvmCallBack(androidTestOnJvmConfiguration!!)
    }

    override fun withAndroidTestOnDevice(
        compilationName: String,
        action: KotlinMultiplatformAndroidTestOnDeviceConfiguration.() -> Unit
    ) {
        androidTestOnDeviceConfiguration = withAndroidTest(
            compilationName,
            androidTestOnDeviceConfiguration,
            "device"
        ) as KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl

        action(androidTestOnDeviceConfiguration!!)

        enablingTestOnDeviceCallBack(androidTestOnDeviceConfiguration!!)
    }

    override fun withAndroidTestOnDevice() {
        androidTestOnDeviceConfiguration = withAndroidTest(
            TEST_ON_DEVICE_DEFAULT_COMPILATION_NAME,
            androidTestOnDeviceConfiguration,
            "device"
        ) as KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl

        enablingTestOnDeviceCallBack(androidTestOnDeviceConfiguration!!)
    }

    override fun withAndroidTestOnDevice(compilationName: String) {
        androidTestOnDeviceConfiguration = withAndroidTest(
            compilationName,
            androidTestOnDeviceConfiguration,
            "device"
        ) as KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl

        enablingTestOnDeviceCallBack(androidTestOnDeviceConfiguration!!)
    }

    override fun withAndroidTestOnDevice(action: KotlinMultiplatformAndroidTestOnDeviceConfiguration.() -> Unit) {
        androidTestOnDeviceConfiguration = withAndroidTest(
            TEST_ON_DEVICE_DEFAULT_COMPILATION_NAME,
            androidTestOnDeviceConfiguration,
            "device"
        ) as KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl

        action(androidTestOnDeviceConfiguration!!)

        enablingTestOnDeviceCallBack(androidTestOnDeviceConfiguration!!)
    }

    companion object {
        const val TEST_ON_JVM_DEFAULT_COMPILATION_NAME = "testOnJvm"
        const val TEST_ON_DEVICE_DEFAULT_COMPILATION_NAME = "testOnDevice"
    }
}
