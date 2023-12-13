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

package com.android.tools.preview.screenshot.services

import com.android.tools.analytics.CommonMetricsData
import com.android.tools.preview.screenshot.services.AnalyticsService.Params
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.TestRun
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry

/**
 * A build service to record analytics data for preview screenshot tests.
 *
 * This class delegates the implementation to AnalyticsService class of
 * the Android Gradle plugin's internal class, by using reflection.
 *
 * This class can be deleted once the preview screenshot plugin is merged
 * into the Android Gradle plugin.
 */
abstract class AnalyticsService : BuildService<Params> {

    interface Params : BuildServiceParameters {
        val androidGradlePluginVersion: Property<String>
    }

    companion object {
        private const val ANALYTICS_CLASS_NAME = "com.android.build.gradle.internal.profile.AnalyticsService"

        private fun getRecordEventFunc(registry: BuildServiceRegistry): (AndroidStudioEvent.Builder) -> Unit {
            for (registration in registry.registrations) {
                if (registration.name.startsWith(ANALYTICS_CLASS_NAME)) {
                    val service = registration.service.get()
                    val analyticsServiceClass = service.javaClass.classLoader.loadClass(
                            ANALYTICS_CLASS_NAME)
                    if (analyticsServiceClass.isInstance(service)) {
                        val recordEventMethod = analyticsServiceClass
                                .getMethod("recordEvent", AndroidStudioEvent.Builder::class.java)
                        return { event ->
                            recordEventMethod(service, event)
                        }
                    }
                }
            }
            return {}
        }
    }

    fun recordPreviewScreenshotTestRun(
            totalTestCount: Int,
            registry: BuildServiceRegistry) {
        val event = AndroidStudioEvent.newBuilder().apply {
            category = AndroidStudioEvent.EventCategory.TESTS
            kind = AndroidStudioEvent.EventKind.TEST_RUN
            javaProcessStats = CommonMetricsData.javaProcessStats
            jvmDetails = CommonMetricsData.jvmDetails
            productDetailsBuilder.apply {
                product = ProductDetails.ProductKind.GRADLE
                version = parameters.androidGradlePluginVersion.get()
                osArchitecture = CommonMetricsData.osArchitecture
            }

            testRunBuilder.apply {
                testInvocationType = TestRun.TestInvocationType.GRADLE_TEST
                testKind = TestRun.TestKind.PREVIEW_SCREENSHOT_TEST
                gradleVersion = parameters.androidGradlePluginVersion.get()
                numberOfTestsExecuted = totalTestCount
            }
        }
        getRecordEventFunc(registry)(event)
    }
}
