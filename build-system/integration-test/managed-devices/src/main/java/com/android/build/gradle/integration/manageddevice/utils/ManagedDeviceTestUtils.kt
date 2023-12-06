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
                            apiLevel = ${System.getProperty("sdk.repo.sysimage.apiLevel")}
                            systemImageSource = "${System.getProperty("sdk.repo.sysimage.source")}"
                            require64Bit = true
                        }
                    }
                }
            }
        }
    """)
}
