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
package com.android.tools.lint.checks

import com.android.tools.lint.ProjectInitializerTest.Companion.project
import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.detector.api.Detector

class CommunicationDeviceDetectorTest : AbstractCheckTest() {

    override fun getDetector(): Detector = CommunicationDeviceDetector()

    fun testDocumentationExample() {
        lint().files(
            manifest().targetSdk(31),
            kotlin(
                """
                package test.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Test {
                  fun test() {
                    val manager = AudioManager()
                    manager.setCommunicationDevice(AudioDeviceInfo())
                  }
                }
                """
            ).indented(),
            audioManagerStub,
            audioDeviceInfoStub
        ).run().expect(
            """
            src/test/pkg/Test.kt:9: Warning: Must call clearCommunicationDevice() after setCommunicationDevice() [SetAndClearCommunicationDevice]
                manager.setCommunicationDevice(AudioDeviceInfo())
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
        """
        )
    }

    fun testTwoSetCalls() {
        lint().files(
            manifest().targetSdk(31),
            kotlin(
                """
                package test.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Test1 {
                  fun test() {
                    val manager = AudioManager()
                    manager.setCommunicationDevice(AudioDeviceInfo())
                  }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Test2 {
                  fun test() {
                    val manager = AudioManager()
                    manager.setCommunicationDevice(AudioDeviceInfo())
                  }
                }
                """
            ).indented(),
            audioManagerStub,
            audioDeviceInfoStub
        ).run().expect(
            """
            src/test/pkg/Test1.kt:9: Warning: Must call clearCommunicationDevice() after setCommunicationDevice() [SetAndClearCommunicationDevice]
                manager.setCommunicationDevice(AudioDeviceInfo())
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/Test2.kt:9: Warning: Must call clearCommunicationDevice() after setCommunicationDevice() [SetAndClearCommunicationDevice]
                manager.setCommunicationDevice(AudioDeviceInfo())
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
        """
        )
    }

    fun testSuppressed() {
        lint().files(
            manifest().targetSdk(31),
            kotlin(
                """
                package test.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Test {
                  @Suppress("SetAndClearCommunicationDevice")
                  fun test() {
                    val manager = AudioManager()
                    manager.setCommunicationDevice(AudioDeviceInfo())
                  }
                }
                """
            ).indented(),
            audioManagerStub,
            audioDeviceInfoStub
        ).run().expectClean()
    }

    fun testSetNoClearOldApk() {
        lint().files(
            manifest().targetSdk(30),
            kotlin(
                """
                package test.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Test {
                  fun test() {
                    val manager = AudioManager()
                    manager.setCommunicationDevice(AudioDeviceInfo())
                  }
                }
                """
            ).indented(),
            audioManagerStub,
            audioDeviceInfoStub
        ).run().expectClean()
    }

    fun testSetAndClearInSameFile() {
        lint().files(
            manifest().targetSdk(31),
            kotlin(
                """
                package test.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Test {
                  fun test() {
                    val manager = AudioManager()
                    manager.setCommunicationDevice(AudioDeviceInfo())
                    manager.clearCommunicationDevice()
                  }
                }
                """
            ).indented(),
            audioManagerStub,
            audioDeviceInfoStub
        ).run().expectClean()
    }

    fun testSetAndClearInSameModule() {
        lint().files(
            manifest().targetSdk(31),
            kotlin(
                """
                package test.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Set {
                  fun test() {
                    val manager = AudioManager()
                    manager.setCommunicationDevice(AudioDeviceInfo())
                  }
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Clear {
                  fun test() {
                    val manager = AudioManager()
                    manager.clearCommunicationDevice()
                  }
                }
                """
            ).indented(),
            audioManagerStub,
            audioDeviceInfoStub
        ).run().expectClean()
    }

    fun testSetAndClearInDifferentModules() {
        val project1 = project().files(
            manifest().targetSdk(31),
            kotlin(
                """
                package set.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Set {
                  fun test() {
                    val manager = AudioManager()
                    manager.setCommunicationDevice(AudioDeviceInfo())
                  }
                }
                """
            ).indented()
        ).dependsOn(mediaModuleStub)

        val project2 = project().files(
            manifest().targetSdk(31),
            kotlin(
                """
                package clear.pkg

                import android.media.AudioDeviceInfo
                import android.media.AudioManager

                class Clear {
                  fun test() {
                    val manager = AudioManager()
                    manager.clearCommunicationDevice()
                  }
                }
                """
            ).indented(),
        ).dependsOn(mediaModuleStub).dependsOn(project1)

        lint().projects(project1, project2, mediaModuleStub).run().expectClean()
    }
}

private val audioManagerStub: TestFile = java(
    """
    package android.media;

    public class AudioManager {
      public boolean setCommunicationDevice(@NonNull AudioDeviceInfo device) {}
      public void clearCommunicationDevice() {}
    }
    """
).indented()

private val audioDeviceInfoStub: TestFile = java(
    """
    package android.media;
    public class AudioDeviceInfo {}
    """
).indented()

private val mediaModuleStub: ProjectDescription = project().files(
    audioManagerStub, audioDeviceInfoStub
)
