package com.android.build.gradle.integration.manageddevice.utils

import com.android.build.gradle.integration.common.fixture.GradleTestProject

fun GradleTestProject.addManagedDevice(deviceName: String) {
    buildFile.appendText("""
        android {
            testOptions {
                managedDevices {
                    localDevices {
                        $deviceName {
                            device = "Pixel 2"
                            apiLevel = 29
                            systemImageSource = "aosp"
                            require64Bit = true
                        }
                    }
                }
            }
        }
    """)
}
