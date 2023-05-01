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

package com.android.tools.firebase.testlab.gradle.services.testrunner

import com.android.build.api.instrumentation.StaticTestData
import com.android.tools.firebase.testlab.gradle.FixtureImpl
import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import com.android.tools.firebase.testlab.gradle.services.toUrl
import com.android.tools.firebase.testlab.gradle.services.storage.TestRunStorage
import com.google.api.client.json.GenericJson
import com.google.api.client.util.Key
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.testing.model.AndroidDevice
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.AndroidDeviceList
import com.google.api.services.testing.model.AndroidInstrumentationTest
import com.google.api.services.testing.model.AndroidModel
import com.google.api.services.testing.model.ClientInfo
import com.google.api.services.testing.model.DeviceFile
import com.google.api.services.testing.model.EnvironmentMatrix
import com.google.api.services.testing.model.GoogleCloudStorage
import com.google.api.services.testing.model.RegularFile
import com.google.api.services.testing.model.ResultStorage
import com.google.api.services.testing.model.TestExecution
import com.google.api.services.testing.model.TestMatrix
import com.google.api.services.testing.model.TestSetup
import com.google.api.services.testing.model.TestSpecification
import com.google.api.services.testing.model.ToolResultsHistory
import java.io.File

class TestMatrixGenerator(private val projectSettings: ProjectSettings) {

    companion object {
        private const val STUB_APP_NAME: String = "androidx.test.services"

        const val INSTRUMENTATION_TEST_SHARD_FIELD = "shardingOption"
        const val TEST_MATRIX_FLAKY_TEST_ATTEMPTS_FIELD = "flakyTestAttempts"
        const val TEST_MATRIX_FAIL_FAST_FIELD = "failFast"
    }

    class UniformSharding: GenericJson() {
        @Key var numShards: Int? = null
    }

    class SmartSharding: GenericJson() {
        @Key var targetedShardDuration: String? = null
    }

    class ShardingOption: GenericJson() {
        @Key var uniformSharding: UniformSharding? = null

        @Key var smartSharding: SmartSharding? = null
    }

    fun createTestMatrix(
        device: TestDeviceData,
        testData: StaticTestData,
        testRunStorage: TestRunStorage,
        testApkObject: StorageObject,
        testedApkObject: StorageObject,
        usingStubApkId: Boolean
    ): TestMatrix =
        TestMatrix().apply {
            projectId = projectSettings.name
            clientInfo = ClientInfo().apply {
                name = TestLabBuildService.CLIENT_APPLICATION_NAME
            }
            testSpecification = TestSpecification().apply {
                testSetup = TestSetup().apply {
                    set("dontAutograntPermissions", projectSettings.grantedPermissions ==
                            FixtureImpl.GrantedPermissions.NONE.name)
                    projectSettings.networkProfile?.apply {
                        networkProfile = this
                    }
                    filesToPush = mutableListOf()
                    projectSettings.extraDeviceFiles.forEach { (onDevicePath, filePath) ->
                        val gcsFilePath = if (filePath.startsWith("gs://")) {
                            filePath
                        } else {
                            val file = File(filePath)
                            check(file.exists()) { "$filePath doesn't exist." }
                            check(file.isFile) { "$filePath must be file." }
                            testRunStorage.uploadToStorage(file).toUrl()
                        }
                        filesToPush.add(DeviceFile().apply {
                            regularFile = RegularFile().apply {
                                content = com.google.api.services.testing.model.FileReference().apply {
                                    gcsPath = gcsFilePath
                                }
                                devicePath = onDevicePath
                            }
                        })
                    }

                    directoriesToPull = projectSettings.directoriesToPull
                }
                androidInstrumentationTest = AndroidInstrumentationTest().apply {
                    testApk = com.google.api.services.testing.model.FileReference().apply {
                        gcsPath = testApkObject.toUrl()
                    }
                    appApk = com.google.api.services.testing.model.FileReference().apply {
                        gcsPath = testedApkObject.toUrl()
                    }
                    appPackageId = if (usingStubApkId) {
                        STUB_APP_NAME
                    } else {
                        testData.testedApplicationId
                    }
                    testPackageId = testData.applicationId
                    testRunnerClass = testData.instrumentationRunner

                    if(projectSettings.useOrchestrator) {
                        orchestratorOption = "USE_ORCHESTRATOR"
                    }

                    createShardingOption()?.also { sharding ->
                        this.set(INSTRUMENTATION_TEST_SHARD_FIELD, sharding)
                    }
                }
                environmentMatrix = EnvironmentMatrix().apply {
                    androidDeviceList = AndroidDeviceList().apply {
                        androidDevices = listOf(
                            AndroidDevice().apply {
                                androidModelId = device.deviceId
                                androidVersionId = device.apiLevel.toString()
                                locale = device.locale.toString()
                                orientation = device.orientation.toString().lowercase()
                            }
                        )
                    }
                }
                resultStorage = ResultStorage().apply {
                    googleCloudStorage = GoogleCloudStorage().apply {
                        gcsPath = testRunStorage.resultStoragePath
                    }
                    toolResultsHistory = ToolResultsHistory().apply {
                        projectId = projectSettings.name
                        this.historyId = testRunStorage.historyId
                    }
                }
                testTimeout = "${projectSettings.ftlTimeoutSeconds}s"
                disablePerformanceMetrics = !projectSettings.performanceMetrics
                disableVideoRecording = !projectSettings.videoRecording
            }
            set(TEST_MATRIX_FLAKY_TEST_ATTEMPTS_FIELD, projectSettings.maxTestReruns)
            set(TEST_MATRIX_FAIL_FAST_FIELD, projectSettings.failFast)
        }

    private fun createShardingOption(): ShardingOption? {
        val numUniformShards = projectSettings.numUniformShards
        val targetShardDuration = projectSettings.targetedShardDurationSeconds
        return when {
            numUniformShards != 0 && targetShardDuration != 0 -> {
                error("""
                Only one sharding option should be set for "numUniformShards" or
                "targetedShardDurationMinutes" in firebaseTestLab.testOptions.execution.
            """.trimIndent())

            }
            numUniformShards != 0 -> ShardingOption().apply {
                uniformSharding = UniformSharding().apply {
                    numShards = numUniformShards
                }
            }
            targetShardDuration != 0 -> ShardingOption().apply {
                smartSharding = SmartSharding().apply {
                    targetedShardDuration = "${targetShardDuration}s"
                }
            }
            else -> null
        }
    }
}
