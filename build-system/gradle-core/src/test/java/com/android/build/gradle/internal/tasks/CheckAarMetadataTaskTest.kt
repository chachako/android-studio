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

package com.android.build.gradle.internal.tasks

import com.android.Version
import com.android.build.gradle.internal.fixtures.FakeArtifactCollection
import com.android.build.gradle.internal.fixtures.FakeComponentIdentifier
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeModuleComponentSelector
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeResolvedArtifactResult
import com.android.build.gradle.internal.fixtures.FakeResolvedComponentResult
import com.android.build.gradle.internal.fixtures.FakeResolvedDependencyResult
import com.android.build.gradle.internal.utils.DESUGAR_JDK_LIBS
import com.android.build.gradle.internal.utils.DESUGAR_JDK_LIBS_MINIMAL
import com.android.build.gradle.internal.utils.checkDesugarJdkVariant
import com.android.build.gradle.internal.utils.checkDesugarJdkVersion
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.inject.Inject

/**
 * Unit tests for [CheckAarMetadataTask].
 */
class CheckAarMetadataTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: CheckAarMetadataTask

    abstract class CheckAarMetadataTaskForTest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        CheckAarMetadataTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "checkAarMetadataTask",
            CheckAarMetadataTaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
    }

    @Test
    fun testPassing() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = true,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.disableCompileSdkChecks.set(false)
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.projectPath.set(":app")
        task.coreLibraryDesugaringEnabled.set(true)
        task.taskAction()
    }

    @Test
    fun testFailsOnAarFormatVersion() {
        val aarMetadataFile = temporaryFolder.newFile().also {
            writeAarMetadataFile(
                file = it,
                aarFormatVersion = "2.0",
                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                minCompileSdk = 28,
                minCompileSdkExtension = 0,
                minAgpVersion = "3.0.0",
                coreLibraryDesugaringEnabled = false,
            )
        }
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = aarMetadataFile,
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set("1.0")
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.disableCompileSdkChecks.set(false)
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.coreLibraryDesugaringEnabled.set(false)
        task.projectPath.set(":app")
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("""
                An issue was found when checking AAR metadata:

                  1.  Dependency 'displayName' has an aarFormatVersion value of
                      '2.0', which is not compatible with this version of the
                      Android Gradle plugin.

                      Please upgrade to a newer version of the Android Gradle plugin.
            """.trimIndent())
        }
    }

    @Test
    fun testFailsOnAarMetadataVersion() {
        val aarMetadataFile = temporaryFolder.newFile().also {
            writeAarMetadataFile(
                file = it,
                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                aarMetadataVersion = "2.0",
                minCompileSdk = 28,
                minCompileSdkExtension = 0,
                minAgpVersion = "3.0.0",
                coreLibraryDesugaringEnabled = false,
            )
        }
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = aarMetadataFile,
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set("1.0")
        task.compileSdkVersion.set("android-28")
        task.disableCompileSdkChecks.set(false)
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.coreLibraryDesugaringEnabled.set(false)
        task.projectPath.set(":app")
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("""
                An issue was found when checking AAR metadata:

                  1.  Dependency 'displayName' has an aarMetadataVersion value of
                      '2.0', which is not compatible with this version of the
                      Android Gradle plugin.

                      Please upgrade to a newer version of the Android Gradle plugin.
            """.trimIndent())
        }
    }

    @Test
    fun testFailsOnMinCompileSdkVersion() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-27")
        task.disableCompileSdkChecks.set(false)
        task.agpVersion.set("7.2.0")
        task.maxRecommendedStableCompileSdkVersionForThisAgp.set(30)
        task.coreLibraryDesugaringEnabled.set(false)
        task.projectPath.set(":app")
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                """
                    An issue was found when checking AAR metadata:

                      1.  Dependency 'displayName' requires libraries and applications that
                          depend on it to compile against version 28 or later of the
                          Android APIs.

                          :app is currently compiled against android-27.

                          Recommended action: Update this project to use a newer compileSdk
                          of at least 28, for example 30.

                          Note that updating a library or application's compileSdk (which
                          allows newer APIs to be used) can be done separately from updating
                          targetSdk (which opts the app in to new runtime behavior) and
                          minSdk (which determines which devices the app can be installed
                          on).
                """.trimIndent()
            )
        }
    }


    @Test
    fun testFailsOnMinCompileSdkVersionAboveMaxAgp() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 47,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-27")
        task.disableCompileSdkChecks.set(false)
        task.agpVersion.set("7.2.0")
        task.maxRecommendedStableCompileSdkVersionForThisAgp.set(30)
        task.coreLibraryDesugaringEnabled.set(false)
        task.projectPath.set(":app")
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("""
                An issue was found when checking AAR metadata:

                  1.  Dependency 'displayName' requires libraries and applications that
                      depend on it to compile against version 47 or later of the
                      Android APIs.

                      :app is currently compiled against android-27.

                      Also, the maximum recommended compile SDK version for Android Gradle
                      plugin 7.2.0 is 30.

                      Recommended action: Update this project's version of the Android Gradle
                      plugin to one that supports 47, then update this project to use
                      compileSdk of at least 47.

                      Note that updating a library or application's compileSdk (which
                      allows newer APIs to be used) can be done separately from updating
                      targetSdk (which opts the app in to new runtime behavior) and
                      minSdk (which determines which devices the app can be installed
                      on).
            """.trimIndent())
        }
    }

    @Test
    fun testFailsOnMinCompileSdkVersionAboveMaxAgp_unknownPreviewCompileSdk() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 47,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-Unknown")
        task.disableCompileSdkChecks.set(false)
        task.platformSdkApiLevel.set(46)
        task.agpVersion.set("7.2.0")
        task.maxRecommendedStableCompileSdkVersionForThisAgp.set(30)
        task.coreLibraryDesugaringEnabled.set(false)
        task.projectPath.set(":app")
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("""
                An issue was found when checking AAR metadata:

                  1.  Dependency 'displayName' requires libraries and applications that
                      depend on it to compile against version 47 or later of the
                      Android APIs.

                      :app is currently compiled against android-Unknown.

                      Also, the maximum recommended compile SDK version for Android Gradle
                      plugin 7.2.0 is 30.

                      Recommended action: Update this project's version of the Android Gradle
                      plugin to one that supports 47, then update this project to use
                      compileSdk of at least 47.

                      Note that updating a library or application's compileSdk (which
                      allows newer APIs to be used) can be done separately from updating
                      targetSdk (which opts the app in to new runtime behavior) and
                      minSdk (which determines which devices the app can be installed
                      on).
            """.trimIndent())
        }
    }

    @Test
    fun testPassing_unknownPreviewCompileSdk() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 47,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-Unknown")
        task.disableCompileSdkChecks.set(false)
        task.platformSdkApiLevel.set(47)
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.coreLibraryDesugaringEnabled.set(false)
        task.projectPath.set(":app")
        task.taskAction()
    }

    @Test
    fun testFailsOnMinAgpVersion() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.disableCompileSdkChecks.set(false)
        task.agpVersion.set("3.0.0-beta1")
        task.coreLibraryDesugaringEnabled.set(false)
        task.projectPath.set(":app")
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                """
                    An issue was found when checking AAR metadata:

                      1.  Dependency 'displayName' requires Android Gradle plugin 3.0.0 or higher.

                          This build currently uses Android Gradle plugin 3.0.0-beta1.
                """.trimIndent())
        }
    }

    @Test
    fun testPassingWithForceCompileSdkPreview() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                forceCompileSdkPreview = "Tiramisu",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-Tiramisu")
        task.disableCompileSdkChecks.set(false)
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.projectPath.set(":app")
        task.coreLibraryDesugaringEnabled.set(false)
        task.taskAction()
    }

    @Test
    fun testFailsOnForceCompileSdkPreview() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                forceCompileSdkPreview = "TiramisuPrivacySandbox",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.disableCompileSdkChecks.set(false)
        task.coreLibraryDesugaringEnabled.set(false)
        task.agpVersion.set("7.2.0")
        task.projectPath.set(":app")
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                """
                    An issue was found when checking AAR metadata:

                      1.  Dependency 'displayName' requires libraries and applications that
                          depend on it to compile against codename "TiramisuPrivacySandbox" of the
                          Android APIs.

                          :app is currently compiled against android-28.

                          Recommended action: Use a different version of dependency 'displayName',
                          or set compileSdkPreview to "TiramisuPrivacySandbox" in your build.gradle
                          file if you intend to experiment with that preview SDK.
                """.trimIndent()
            )
        }
    }

    @Test
    fun testPassingWithMinCompileSdkExtension_withSdkExtensionFromHash() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 1,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28-ext1")
        task.disableCompileSdkChecks.set(false)
        task.coreLibraryDesugaringEnabled.set(false)
        task.agpVersion.set("3.0.0")
        task.projectPath.set(":app")
        task.taskAction()
    }

    @Test
    fun testPassingWithMinCompileSdkExtension_withExplicitPlatformSdkExtension() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 1,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.disableCompileSdkChecks.set(false)
        task.coreLibraryDesugaringEnabled.set(false)
        task.platformSdkExtension.set(1)
        task.agpVersion.set("3.0.0")
        task.projectPath.set(":app")
        task.taskAction()
    }

    @Test
    fun testFailsOnMinCompileSdkExtension() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 1,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = false,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.disableCompileSdkChecks.set(false)
        task.coreLibraryDesugaringEnabled.set(false)
        task.agpVersion.set("3.0.0")
        task.projectPath.set(":app")
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                """
                    An issue was found when checking AAR metadata:

                      1.  Dependency 'displayName' requires libraries and applications that
                          depend on it to compile against an SDK with an extension level of
                          1 or higher.

                          Recommended action: Update this project to use a compileSdkExtension
                          value of at least 1.
                """.trimIndent())
        }
    }

    @Test
    fun testMultipleFailures() {
        val aarMetadataFile = temporaryFolder.newFile().also {
            writeAarMetadataFile(
                file = it,
                aarFormatVersion = "2.0",
                aarMetadataVersion = "2.0",
                minCompileSdk = 28,
                minCompileSdkExtension = 0,
                minAgpVersion = "3.0.0",
                coreLibraryDesugaringEnabled = false,
            )
        }
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = aarMetadataFile,
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-27")
        task.disableCompileSdkChecks.set(false)
        task.coreLibraryDesugaringEnabled.set(false)
        task.agpVersion.set("3.0.0-beta1")
        task.projectPath.set(":app")
        task.maxRecommendedStableCompileSdkVersionForThisAgp.set(30)
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("""
                4 issues were found when checking AAR metadata:

                  1.  Dependency 'displayName' has an aarFormatVersion value of
                      '2.0', which is not compatible with this version of the
                      Android Gradle plugin.

                      Please upgrade to a newer version of the Android Gradle plugin.

                  2.  Dependency 'displayName' has an aarMetadataVersion value of
                      '2.0', which is not compatible with this version of the
                      Android Gradle plugin.

                      Please upgrade to a newer version of the Android Gradle plugin.

                  3.  Dependency 'displayName' requires libraries and applications that
                      depend on it to compile against version 28 or later of the
                      Android APIs.

                      :app is currently compiled against android-27.

                      Recommended action: Update this project to use a newer compileSdk
                      of at least 28, for example 30.

                      Note that updating a library or application's compileSdk (which
                      allows newer APIs to be used) can be done separately from updating
                      targetSdk (which opts the app in to new runtime behavior) and
                      minSdk (which determines which devices the app can be installed
                      on).

                  4.  Dependency 'displayName' requires Android Gradle plugin 3.0.0 or higher.

                      This build currently uses Android Gradle plugin 3.0.0-beta1.
            """.trimIndent())
        }
    }

    @Test
    fun testFailsOnCoreLibraryDesugaring() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = true,
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.disableCompileSdkChecks.set(false)
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.projectPath.set(":app")
        task.coreLibraryDesugaringEnabled.set(false)
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("""
                An issue was found when checking AAR metadata:

                  1.  Dependency 'displayName' requires core library desugaring to be enabled
                      for :app.

                      See https://developer.android.com/studio/write/java8-support.html for more
                      details.
            """.trimIndent())
        }
    }

    @Test
    fun testFailsOnDesugarJdkLibId() {
        task.aarMetadataArtifacts =
            FakeArtifactCollection(
                mutableSetOf(
                    FakeResolvedArtifactResult(
                        file = temporaryFolder.newFile().also {
                            writeAarMetadataFile(
                                file = it,
                                aarFormatVersion = AarMetadataTask.AAR_FORMAT_VERSION,
                                aarMetadataVersion = AarMetadataTask.AAR_METADATA_VERSION,
                                minCompileSdk = 28,
                                minCompileSdkExtension = 0,
                                minAgpVersion = "3.0.0",
                                coreLibraryDesugaringEnabled = true,
                                desugarJdkLib = "com.android.tools:desugar_jdk_libs_nio:2.0.2"
                            )
                        },
                        identifier = FakeComponentIdentifier("displayName")
                    )
                )
            )
        task.aarFormatVersion.set(AarMetadataTask.AAR_FORMAT_VERSION)
        task.aarMetadataVersion.set(AarMetadataTask.AAR_METADATA_VERSION)
        task.compileSdkVersion.set("android-28")
        task.disableCompileSdkChecks.set(false)
        task.agpVersion.set(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        task.projectPath.set(":app")
        task.coreLibraryDesugaringEnabled.set(true)
        task.desugarJdkLibDependencyGraph.set(
            FakeResolvedComponentResult(
                dependencies = mutableSetOf(FakeResolvedDependencyResult(
                    requested = FakeModuleComponentSelector(
                        "com.android.tools", "desugar_jdk_libs", "1.1.5")
                    )
                )
            )
        )
        try {
            task.taskAction()
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("""
                2 issues were found when checking AAR metadata:

                  1.  Dependency 'displayName' requires desugar_jdk_libs flavor to be at least
                      desugar_jdk_libs_nio for :app, which is currently desugar_jdk_libs

                      See https://d.android.com/r/tools/api-desugaring-flavors
                      for more details.

                  2.  Dependency 'displayName' requires desugar_jdk_libs version to be
                      2.0.2 or above for :app, which is currently 1.1.5

                      See https://d.android.com/studio/build/library-desugaring for more
                      details.
            """.trimIndent())
        }
    }

    @Test
    fun testCompareDesugarJdkVariant() {
        val errorMessages = mutableListOf<String>()
        checkDesugarJdkVariant(
            variantFromAar = DESUGAR_JDK_LIBS_MINIMAL,
            variantFromConsumer = DESUGAR_JDK_LIBS,
            errorMessages = errorMessages,
            dependency = "",
            projectPath = ""
        )
        assertThat(errorMessages).isEmpty()
        checkDesugarJdkVariant(
            variantFromAar = DESUGAR_JDK_LIBS,
            variantFromConsumer = DESUGAR_JDK_LIBS_MINIMAL,
            errorMessages = errorMessages,
            dependency = "",
            projectPath = ""
        )
        assertThat(errorMessages).isNotEmpty()
    }

    @Test
    fun testCompareDesugarJdkVersion() {
        val errorMessages = mutableListOf<String>()
        checkDesugarJdkVersion(
            versionFromAar = "1.1.3",
            versionFromConsumer = "1.2.5",
            errorMessages = errorMessages,
            dependency = "",
            projectPath = ""
        )
        assertThat(errorMessages).isEmpty()
        checkDesugarJdkVersion(
            versionFromAar = "2.1.3",
            versionFromConsumer = "1.2.5",
            errorMessages = errorMessages,
            dependency = "",
            projectPath = ""
        )
        assertThat(errorMessages).isNotEmpty()
        errorMessages.clear()
        checkDesugarJdkVersion(
            versionFromAar = "2.1.3",
            versionFromConsumer = "2.2.5",
            errorMessages = errorMessages,
            dependency = "",
            projectPath = ""
        )
        assertThat(errorMessages).isEmpty()
    }
}
