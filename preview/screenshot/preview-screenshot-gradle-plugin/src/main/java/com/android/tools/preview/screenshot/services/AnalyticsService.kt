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
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import com.google.wireless.android.sdk.stats.ProductDetails
import com.google.wireless.android.sdk.stats.TestRun
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * A build service to record analytics data for preview screenshot tests.
 *
 * This class delegates the implementation to AnalyticsService class of
 * the Android Gradle plugin's internal class, by using reflection.
 *
 * This class can be deleted once the preview screenshot plugin is merged
 * into the Android Gradle plugin. b/323040484.
 */
abstract class AnalyticsService : BuildService<Params> {

    interface Params : BuildServiceParameters {
        val androidGradlePluginVersion: Property<String>
    }

    @get:Inject
    abstract val buildServiceRegistry: BuildServiceRegistry

    companion object {
        private const val ANALYTICS_CLASS_NAME = "com.android.build.gradle.internal.profile.AnalyticsService"
        private val clock: Clock = Clock.systemDefaultZone()
    }

    private val recordEventFunc: (AndroidStudioEvent.Builder) -> Unit by lazy {
        for (registration in buildServiceRegistry.registrations) {
            if (registration.name.startsWith(ANALYTICS_CLASS_NAME)) {
                val service = registration.service.get()
                val analyticsServiceClass = service.javaClass.classLoader.loadClass(
                        ANALYTICS_CLASS_NAME)
                if (analyticsServiceClass.isInstance(service)) {
                    val recordEventMethod = analyticsServiceClass
                            .getMethod("recordEvent", AndroidStudioEvent.Builder::class.java)
                    return@lazy { event ->
                        recordEventMethod(service, event)
                    }
                }
            }
        }
        {}
    }

    private val registerSpanFunc: (String, GradleBuildProfileSpan.Builder) -> Unit by lazy {
        for (registration in buildServiceRegistry.registrations) {
            if (registration.name.startsWith(ANALYTICS_CLASS_NAME)) {
                val service = registration.service.get()
                val analyticsServiceClass = service.javaClass.classLoader.loadClass(
                        ANALYTICS_CLASS_NAME)
                if (analyticsServiceClass.isInstance(service)) {
                    val registerSpanMethod = analyticsServiceClass
                            .getMethod(
                                    "registerSpan",
                                    String::class.java,
                                    GradleBuildProfileSpan.Builder::class.java)
                    return@lazy { taskPath, span ->
                        registerSpanMethod(service, taskPath, span)
                    }
                }
            }
        }
        { _, _ -> }
    }

    fun recordPreviewScreenshotTestRun(totalTestCount: Int) {
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
        recordEventFunc(event)
    }

    fun recordTaskAction(taskPath: String, block: () -> Unit) {
        val before: Instant = clock.instant()
        block()
        val after: Instant = clock.instant()

        registerSpanFunc(
                taskPath,
                GradleBuildProfileSpan.newBuilder()
                        .setType(GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_ALL_PHASES)
                        .setThreadId(Thread.currentThread().id)
                        .setStartTimeInMs(before.toEpochMilli())
                        .setDurationInMs(Duration.between(before, after).toMillis()))
    }
}
