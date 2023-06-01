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
import com.android.tools.firebase.testlab.gradle.services.storage.TestRunStorage
import com.android.tools.firebase.testlab.gradle.services.testrunner.TestMatrixGenerator.ShardingOption
import com.android.tools.firebase.testlab.gradle.services.testrunner.TestMatrixGenerator.SmartSharding
import com.android.tools.firebase.testlab.gradle.services.testrunner.TestMatrixGenerator.UniformSharding
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.firebase.testlab.gradle.ManagedDeviceImpl
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.testing.model.AndroidDevice
import com.google.api.services.testing.model.AndroidModel
import com.google.api.services.testing.model.DeviceFile
import com.google.api.services.testing.model.FileReference
import com.google.api.services.testing.model.RegularFile
import com.google.api.services.testing.model.TestMatrix
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.lang.IllegalStateException
import java.util.Locale

class TestMatrixGeneratorTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock
    lateinit var mockModel: AndroidModel

    @Mock
    lateinit var testData: StaticTestData

    @Mock
    lateinit var testRunStorage: TestRunStorage

    @Mock
    lateinit var testApk: StorageObject

    @Mock
    lateinit var testedApk: StorageObject

    lateinit var stubFile: File

    @Before
    fun setup() {
        stubFile = temporaryFolderRule.newFile("stub")

        testData.apply {
            `when`(testedApplicationId).thenReturn("app_under_test")
            `when`(applicationId).thenReturn("testing_apk")
            `when`(instrumentationRunner).thenReturn("instrumentation.class")
        }

        testRunStorage.apply {
            `when`(resultStoragePath).thenReturn("gs://bucket/run_id/results")
            `when`(historyId).thenReturn("clarified_history_value")
        }

        testApk.apply {
            `when`(bucket).thenReturn("testbucket")
            `when`(name).thenReturn("test/name")
        }

        testedApk.apply {
            `when`(bucket).thenReturn("testedbucket")
            `when`(name).thenReturn("tested/name")
        }
    }

    private fun getDeviceData(
        name: String = "dsl_name",
        deviceId: String = "ftl_device_id",
        apiLevel: Int = 29,
        locale: Locale = Locale.US,
        orientation: ManagedDeviceImpl.Orientation = ManagedDeviceImpl.Orientation.DEFAULT,
        extraDeviceFileUrls: Map<String, String> = mapOf()
    ) =
        TestDeviceData(
            name = name,
            deviceId = deviceId,
            apiLevel = apiLevel,
            locale = locale,
            orientation = orientation,
            ftlModel = mockModel,
            extraDeviceFileUrls = extraDeviceFileUrls
        )

    private fun getProjectSettings(
        name: String = "project_name",
        storageBucket: String = "bucket",
        testHistoryName: String? = null,
        grantedPermissions: String? = null,
        networkProfile: String? = null,
        directoriesToPull: List<String> = listOf(),
        useOrchestrator: Boolean = false,
        ftlTimeoutSeconds: Int = 900,
        performanceMetrics: Boolean = false,
        videoRecording: Boolean = false,
        maxTestReruns: Int = 0,
        failFast: Boolean = false,
        numUniformShards: Int = 0,
        targetedShardDurationSeconds: Int = 0
    ) =
        ProjectSettings(
            name = name,
            storageBucket = storageBucket,
            testHistoryName = testHistoryName,
            grantedPermissions = grantedPermissions,
            networkProfile = networkProfile,
            directoriesToPull = directoriesToPull,
            useOrchestrator = useOrchestrator,
            ftlTimeoutSeconds = ftlTimeoutSeconds,
            performanceMetrics = performanceMetrics,
            videoRecording = videoRecording,
            maxTestReruns = maxTestReruns,
            failFast = failFast,
            numUniformShards = numUniformShards,
            targetedShardDurationSeconds = targetedShardDurationSeconds,
            stubAppApk = stubFile
        )

    @Test
    fun test_createTestMatrix_basic() {

        val matrix = TestMatrixGenerator(getProjectSettings()).createTestMatrix(
            getDeviceData(),
            testData,
            testRunStorage,
            testApk,
            testedApk,
            false
        )

        verifyMatrix(matrix)
    }

    @Test
    fun test_createTestMatrix_addNumUniformShards() {
        val matrix = TestMatrixGenerator(
            getProjectSettings(numUniformShards = 5)
        ).createTestMatrix(
            getDeviceData(),
            testData,
            testRunStorage,
            testApk,
            testedApk,
            false
        )

        verifyMatrix(
            matrix,
            testSpecInstrumentationSharding = ShardingOption().apply {
                uniformSharding = UniformSharding().apply {
                    numShards = 5
                }
            }
        )
    }

    @Test
    fun test_createTestMatrix_addSmartSharding() {
        val matrix = TestMatrixGenerator(
            getProjectSettings(targetedShardDurationSeconds = 600)
        ).createTestMatrix(
            getDeviceData(),
            testData,
            testRunStorage,
            testApk,
            testedApk,
            false
        )

        verifyMatrix(
            matrix,
            testSpecInstrumentationSharding = ShardingOption().apply {
                smartSharding = SmartSharding().apply {
                    targetedShardDuration = "600s"
                }
            }
        )
    }

    @Test
    fun test_createTestMatrix_addingBothShardingMethodsFails() {
        val error = assertThrows(IllegalStateException::class.java) {
            TestMatrixGenerator(
                getProjectSettings(
                    targetedShardDurationSeconds = 600,
                    numUniformShards = 5
                )
            ).createTestMatrix(
                getDeviceData(),
                testData,
                testRunStorage,
                testApk,
                testedApk,
                false
            )
        }

        assertThat(error.message).isEqualTo(
            """
                Only one sharding option should be set for "numUniformShards" or
                "targetedShardDurationMinutes" in firebaseTestLab.testOptions.execution.
            """.trimIndent()
        )
    }

    @Test
    fun test_createTestMatrix_useStubApk() {
        testedApk.apply {
            `when`(name).thenReturn("stub")
            `when`(bucket).thenReturn("stub_bucket")
        }
        testData.apply {
            // when the apk is stubbed, it means there is no apk under test.
            `when`(testedApplicationId).thenReturn(null)
        }

        val matrix = TestMatrixGenerator(getProjectSettings())
            .createTestMatrix(
                getDeviceData(),
                testData,
                testRunStorage,
                testApk,
                testedApk,
                true
            )

        verifyMatrix(
            matrix,
            testSpecInstrumentationAppApk = "gs://stub_bucket/stub",
            testSpecInstrumentationAppPackageId = "androidx.test.services"
        )
    }

    @Test
    fun test_createTestMatrix_networkProfile() {
        val matrix = TestMatrixGenerator(
            getProjectSettings(networkProfile = "LTE")
        ).createTestMatrix(
            getDeviceData(),
            testData,
            testRunStorage,
            testApk,
            testedApk,
            false
        )

        verifyMatrix(
            matrix,
            testSpecSetupNetworkProfile = "LTE"
        )
    }

    @Test
    fun test_createTestMatrix_useOrchestrator() {
        val matrix = TestMatrixGenerator(
            getProjectSettings(useOrchestrator = true)
        ).createTestMatrix(
            getDeviceData(),
            testData,
            testRunStorage,
            testApk,
            testedApk,
            false
        )

        verifyMatrix(
            matrix,
            testSpecInstrumentationOrchestrator = "USE_ORCHESTRATOR"
        )
    }

    @Test
    fun test_createTestMatrix_runWithDifferentDevices() {
        TestMatrixGenerator(getProjectSettings()).apply {
            val matrix1 = createTestMatrix(
                getDeviceData(
                    deviceId = "hello",
                    apiLevel = 31,
                    locale = Locale.FRENCH,
                    orientation = ManagedDeviceImpl.Orientation.LANDSCAPE
                ),
                testData,
                testRunStorage,
                testApk,
                testedApk,
                false
            )

            verifyMatrix(
                matrix1,
                environmentDevices = listOf(
                    AndroidDevice().apply {
                        androidModelId = "hello"
                        androidVersionId = "31"
                        locale = "fr"
                        orientation = "landscape"
                    }
                )
            )

            val matrix2 = createTestMatrix(
                getDeviceData(
                    deviceId = "world",
                    apiLevel = 28,
                    locale = Locale.JAPANESE,
                    orientation = ManagedDeviceImpl.Orientation.PORTRAIT
                ),
                testData,
                testRunStorage,
                testApk,
                testedApk,
                false
            )

            verifyMatrix(
                matrix2,
                environmentDevices = listOf(
                    AndroidDevice().apply {
                        androidModelId = "world"
                        androidVersionId = "28"
                        locale = "ja"
                        orientation = "portrait"
                    }
                )
            )
        }
    }

    @Test
    fun test_createTestMatrix_extraDeviceUrls() {
        val matrix = TestMatrixGenerator(getProjectSettings())
            .createTestMatrix(
                getDeviceData(
                    extraDeviceFileUrls = mapOf(
                        "location/on/device" to "gs://link/to/storage",
                        "different/location/on/device" to "gs://same/storage/path",
                        "yet/another/location" to "gs://same/storage/path"
                    )
                ),
                testData,
                testRunStorage,
                testApk,
                testedApk,
                false
            )

        verifyMatrix(
            matrix,
            testSpecSetupFilesToPush = listOf(
                DeviceFile().apply {
                    regularFile = RegularFile().apply {
                        content = FileReference().apply {
                            gcsPath = "gs://link/to/storage"
                        }
                        devicePath = "location/on/device"
                    }
                },
                DeviceFile().apply {
                    regularFile = RegularFile().apply {
                        content = FileReference().apply {
                            gcsPath = "gs://same/storage/path"
                        }
                        devicePath = "different/location/on/device"
                    }
                },
                DeviceFile().apply {
                    regularFile = RegularFile().apply {
                        content = FileReference().apply {
                            gcsPath = "gs://same/storage/path"
                        }
                        devicePath = "yet/another/location"
                    }
                }
            )
        )
    }

    fun verifyMatrix(
        matrix: TestMatrix,
        projectId: String = "project_name",
        testSpecSetupDontAutograntPermissions: Boolean? = null,
        testSpecSetupNetworkProfile: String? = null,
        testSpecSetupFilesToPush: List<DeviceFile> = listOf(),
        testSpecSetupDirectoriesToPull: List<String> = listOf(),
        testSpecInstrumentationTestApk: String = "gs://testbucket/test/name",
        testSpecInstrumentationAppApk: String = "gs://testedbucket/tested/name",
        testSpecInstrumentationTestPackageId: String = "testing_apk",
        testSpecInstrumentationAppPackageId: String = "app_under_test",
        testSpecInstrumentationOrchestrator: String? = null,
        testSpecInstrumentationSharding: TestMatrixGenerator.ShardingOption? = null,
        testSpecTestTimeout: String = "900s",
        testSpecDisablePerformance: Boolean? = true,
        testSpecDisableVideoRecording: Boolean? = true,
        environmentDevices: List<AndroidDevice> = listOf(
            AndroidDevice().apply {
                androidModelId = "ftl_device_id"
                androidVersionId = "29"
                locale = "en_US"
                orientation = "default"
            }
        ),
        storageCloudStoragePath: String = "gs://bucket/run_id/results",
        storageHistoryName: String = "project_name",
        storageHistoryId: String = "clarified_history_value",
        flakyTestAttempts: Int = 0,
        failFast: Boolean = false
    ) {
        assertThat(matrix.projectId).isEqualTo(projectId)
        assertThat(matrix.clientInfo.name).isEqualTo("Firebase TestLab Gradle Plugin")

        matrix.testSpecification.also { testSpec ->
            testSpec.testSetup.also { setup ->
                assertThat(setup.get("dontAutograntPermssions"))
                    .isEqualTo(testSpecSetupDontAutograntPermissions)
                assertThat(setup.networkProfile)
                    .isEqualTo(testSpecSetupNetworkProfile)
                assertThat(setup.filesToPush)
                    .containsExactlyElementsIn(testSpecSetupFilesToPush)
                assertThat(setup.directoriesToPull)
                    .containsExactlyElementsIn(testSpecSetupDirectoriesToPull)
            }

            testSpec.androidInstrumentationTest.also { instrumentation ->
                assertThat(instrumentation.testApk.gcsPath)
                    .isEqualTo(testSpecInstrumentationTestApk)
                assertThat(instrumentation.appApk.gcsPath)
                    .isEqualTo(testSpecInstrumentationAppApk)
                assertThat(instrumentation.appPackageId)
                    .isEqualTo(testSpecInstrumentationAppPackageId)
                assertThat(instrumentation.testPackageId)
                    .isEqualTo(testSpecInstrumentationTestPackageId)
                assertThat(instrumentation.orchestratorOption)
                    .isEqualTo(testSpecInstrumentationOrchestrator)
                assertThat(instrumentation.get("shardingOption"))
                    .isEqualTo(testSpecInstrumentationSharding)
            }

            assertThat(testSpec.testTimeout).isEqualTo(testSpecTestTimeout)
            assertThat(testSpec.disablePerformanceMetrics).isEqualTo(testSpecDisablePerformance)
            assertThat(testSpec.disableVideoRecording).isEqualTo(testSpecDisableVideoRecording)
        }

        matrix.environmentMatrix.also { environment ->
            assertThat(environment.androidDeviceList.androidDevices)
                .containsExactlyElementsIn(environmentDevices)
        }

        matrix.resultStorage.also { storage ->
            assertThat(storage.googleCloudStorage.gcsPath)
                .isEqualTo(storageCloudStoragePath)
            assertThat(storage.toolResultsHistory.projectId)
                .isEqualTo(storageHistoryName)
            assertThat(storage.toolResultsHistory.historyId)
                .isEqualTo(storageHistoryId)
        }

        assertThat(matrix.get("flakyTestAttempts")).isEqualTo(flakyTestAttempts)
        assertThat(matrix.get("failFast")).isEqualTo(failFast)
    }
}
