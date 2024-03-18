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
        private const val GRADLE_BUILD_PROFILE_SPAN_CLASS_NAME = "com.google.wireless.android.sdk.stats.GradleBuildProfileSpan"
        private const val GRADLE_BUILD_PROFILE_SPAN_BUILDER_CLASS_NAME = "com.google.wireless.android.sdk.stats.GradleBuildProfileSpan\$Builder"
        private const val ANDROID_STUDIO_EVENT_CLASS_NAME = "com.google.wireless.android.sdk.stats.AndroidStudioEvent"
        private const val ANDROID_STUDIO_EVENT_BUILDER_CLASS_NAME = "com.google.wireless.android.sdk.stats.AndroidStudioEvent\$Builder"
        private val clock: Clock = Clock.systemDefaultZone()
    }

    private val recordEventFunc: (AndroidStudioEvent.Builder) -> Unit by lazy {
        for (registration in buildServiceRegistry.registrations) {
            if (registration.name.startsWith(ANALYTICS_CLASS_NAME)) {
                val service = registration.service.get()
                val analyticsServiceClass = service.javaClass.classLoader.loadClass(
                        ANALYTICS_CLASS_NAME)
                if (analyticsServiceClass.isInstance(service)) {
                    // AndroidStudioEvent needs to be created via reflection because
                    // the protobuf message version loaded in this class loader can be different
                    // between screenshot plugin and other applied Android plugins.
                    val eventClass = service.javaClass.classLoader.loadClass(
                            ANDROID_STUDIO_EVENT_CLASS_NAME)
                    val parseFromMethod = eventClass.getMethod("parseFrom", ByteArray::class.java)
                    val toBuilderMethod = eventClass.getMethod("toBuilder")
                    val eventBuilderClass = service.javaClass.classLoader.loadClass(
                            ANDROID_STUDIO_EVENT_BUILDER_CLASS_NAME)
                    val recordEventMethod = analyticsServiceClass
                            .getMethod("recordEvent", eventBuilderClass)
                    return@lazy { event ->
                        recordEventMethod(
                                service,
                                toBuilderMethod(parseFromMethod(null, event.build().toByteArray())))
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
                    // GradleBuildProfileSpan needs to be created via reflection because
                    // the protobuf message version loaded in this class loader can be different
                    // between screenshot plugin and other applied Android plugins.
                    val profileSpanClass = service.javaClass.classLoader.loadClass(
                            GRADLE_BUILD_PROFILE_SPAN_CLASS_NAME)
                    val parseFromMethod = profileSpanClass.getMethod(
                            "parseFrom", ByteArray::class.java)
                    val toBuilderMethod = profileSpanClass.getMethod("toBuilder")
                    val profileSpanBuilderClass = service.javaClass.classLoader.loadClass(
                            GRADLE_BUILD_PROFILE_SPAN_BUILDER_CLASS_NAME)
                    val registerSpanMethod = analyticsServiceClass
                            .getMethod(
                                    "registerSpan",
                                    String::class.java,
                                    profileSpanBuilderClass)
                    return@lazy { taskPath, span ->
                        registerSpanMethod(
                                service,
                                taskPath,
                                toBuilderMethod(parseFromMethod(null, span.build().toByteArray())))
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
