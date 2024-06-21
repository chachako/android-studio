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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.bundle.Config
import com.android.testutils.apk.Zip
import com.google.common.truth.Truth.assertThat
import com.android.testutils.truth.PathSubject
import com.android.tools.build.bundletool.model.AndroidManifest.MODULE_TYPE_AI_VALUE
import com.android.tools.build.bundletool.model.AppBundle
import com.android.tools.build.bundletool.model.BundleModule
import com.android.tools.build.bundletool.model.BundleModuleName
import java.util.Optional
import java.util.zip.ZipFile
import org.junit.Rule
import org.junit.Test

class AiPackTest {

    private val packageName = "com.example.aipacktestapp"
    private val aiPackTestApp = MultiModuleTestProject.builder().apply {
        val app = MinimalSubProject.app("$packageName")
            .appendToBuild(
                // TODO(@hughed): split by model version
                """android.assetPacks = [
                    |':customModelInstallTime',
                    |':customModelFastFollow',
                    |':modelAdaptationOnDemand']""".trimMargin()
            )

        val customModelInstallTime = MinimalSubProject.aiPack()
            .appendToBuild(
                """aiPack {
                          |  packName = "customModelInstallTime"
                          |  dynamicDelivery {
                          |    deliveryType = "install-time"
                          |  }
                          |}""".trimMargin()
            )
            .withFile(
                "src/main/assets/customModel.tflite",
                """This is a custom model delivered at install time."""
            )

        val customModelFastFollow = MinimalSubProject.aiPack()
            .appendToBuild(
                """aiPack {
                          |  packName = "customModelFastFollow"
                          |  dynamicDelivery {
                          |    deliveryType = "fast-follow"
                          |  }
                          |}""".trimMargin()
            )
            .withFile(
                "src/main/assets/customModel.jax",
                """This is a custom model delivered after install time."""
            )

        val modelAdaptationOnDemand = MinimalSubProject.aiPack()
            .appendToBuild(
                """aiPack {
                          |  packName = "modelAdaptationOnDemand"
                          |  dynamicDelivery {
                          |    deliveryType = "on-demand"
                          |  }
                          |  modelDependency {
                          |    aiModelPackageName = "com.foundation.app"
                          |    aiModelName = "com.foundation.llm"
                          |  }
                          |}""".trimMargin()
            )
            .withFile(
                "src/main/assets/adaptation.lora",
                """This is an adaptation file delivered on-demand."""
            )

        subproject(":app", app)
        subproject(":customModelInstallTime", customModelInstallTime)
        subproject(":customModelFastFollow", customModelFastFollow)
        subproject(":modelAdaptationOnDemand", modelAdaptationOnDemand)
    }
        .build()

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(aiPackTestApp)
        .create()

    @Test
    fun buildDebugBundle() {
        project.executor().run(":app:bundleDebug")

        val bundleFile = project.locateBundleFileViaModel("debug", ":app")
        PathSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use { bundle ->
            val bundleContents = bundle.entries

            assertThat(bundleContents.map { it.toString() }).containsAtLeast(
                "/customModelInstallTime/assets/customModel.tflite",
                "/customModelInstallTime/manifest/AndroidManifest.xml",
                "/customModelInstallTime/assets.pb",
                "/customModelFastFollow/assets/customModel.jax",
                "/customModelFastFollow/manifest/AndroidManifest.xml",
                "/customModelFastFollow/assets.pb",
                "/modelAdaptationOnDemand/assets/adaptation.lora",
                "/modelAdaptationOnDemand/manifest/AndroidManifest.xml",
                "/modelAdaptationOnDemand/assets.pb",
            )
        }

        ZipFile(bundleFile).use { zip ->
            val appBundle = AppBundle.buildFromZip(zip)

            // TODO(@hughed): Assert that AI_MODEL_DIMENSION is set
            val splitsConfigBuilder = Config.SplitsConfig.newBuilder()
            assertThat(appBundle.bundleConfig.optimizations.splitsConfig)
                .isEqualTo(splitsConfigBuilder.build())

            // Bundletool treats AI packs as special types of asset packs.
            val moduleNames = appBundle.assetModules.keys.map { it.name }
            assertThat(moduleNames).containsExactly(
                "customModelInstallTime",
                "customModelFastFollow",
                "modelAdaptationOnDemand"
            )

            val customModelInstallTimeManifest =
                appBundle.assetModules[BundleModuleName.create("customModelInstallTime")]!!.androidManifest
            assertThat(customModelInstallTimeManifest.moduleType).isEqualTo(
                BundleModule.ModuleType.ASSET_MODULE
            )
            assertThat(customModelInstallTimeManifest.optionalModuleTypeAttributeValue).isEqualTo(
                Optional.of(MODULE_TYPE_AI_VALUE)
            )
            assertThat(customModelInstallTimeManifest.packageName).isEqualTo(packageName)
            assertThat(
                customModelInstallTimeManifest.manifestDeliveryElement.get()
                    .hasInstallTimeElement()
            )
                .isTrue()

            val customModelFastFollowManifest =
                appBundle.assetModules[BundleModuleName.create("customModelFastFollow")]!!.androidManifest
            assertThat(customModelFastFollowManifest.moduleType).isEqualTo(
                BundleModule.ModuleType.ASSET_MODULE
            )
            assertThat(customModelInstallTimeManifest.optionalModuleTypeAttributeValue).isEqualTo(
                Optional.of(MODULE_TYPE_AI_VALUE)
            )
            assertThat(customModelFastFollowManifest.packageName).isEqualTo(packageName)
            assertThat(
                customModelFastFollowManifest.manifestDeliveryElement.get()
                    .hasFastFollowElement()
            )
                .isTrue()

            val modelAdaptationOnDemandManifest =
                appBundle.assetModules[BundleModuleName.create("modelAdaptationOnDemand")]!!.androidManifest
            assertThat(modelAdaptationOnDemandManifest.moduleType).isEqualTo(
                BundleModule.ModuleType.ASSET_MODULE
            )
            assertThat(modelAdaptationOnDemandManifest.optionalModuleTypeAttributeValue).isEqualTo(
                Optional.of(MODULE_TYPE_AI_VALUE)
            )
            assertThat(modelAdaptationOnDemandManifest.packageName).isEqualTo(packageName)
            assertThat(
                modelAdaptationOnDemandManifest.manifestDeliveryElement.get()
                    .hasOnDemandElement()
            )
                .isTrue()
        }
    }
}
