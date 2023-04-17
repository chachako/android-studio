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

package com.android.tools.firebase.testlab.gradle.services

import com.google.api.client.googleapis.util.Utils
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.GenericJson
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Key
import com.google.api.services.testing.model.ToolResultsStep
import com.google.api.services.toolresults.ToolResults
import com.google.api.services.toolresults.model.History
import com.google.api.services.toolresults.model.ListStepThumbnailsResponse
import com.google.api.services.toolresults.model.ProjectSettings
import com.google.api.services.toolresults.model.StackTrace
import com.google.api.services.toolresults.model.Step
import com.google.api.services.toolresults.model.ToolOutputReference
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * class to handle all [ToolResults] related requests made by the [TestLabBuildService]
 */
class ToolResultsManager (
    private val toolResultsClient: ToolResults,
    // TODO(b/???): remove once able to access
    // toolResultsClient.projects().histories().executions().steps().testCases().list()
    private val httpRequestFactory: HttpRequestFactory,
    private val parser: JsonObjectParser = JsonObjectParser(Utils.getDefaultJsonFactory())
) {
    data class RequestInfo(
        val projectId: String,
        val historyId: String,
        val executionId: String,
        val stepId: String
    )

    class TestCases : GenericJson() {
        @Key var testCases: List<TestCase>? = null
        @Key var nextPageToken: String? = null
    }

    class TestCase : GenericJson() {
        @Key var testCaseId: String? = null
        @Key var startTime: TimeStamp? = null
        @Key var endTime: TimeStamp? = null
        @Key var stackTraces: List<StackTrace>? = null
        @Key var status: String? = null
        @Key var testCaseReference: TestCaseReference? = null
        @Key var toolOutputs: List<ToolOutputReference>? = null
    }

    class TimeStamp : GenericJson() {
        @Key var seconds: String? = null
        @Key var nanos: Int? = null
    }

    class TestCaseReference : GenericJson() {
        @Key var name: String? = null
        @Key var className: String? = null
        @Key var testSuiteName: String? = null
    }

    companion object {
        fun requestFrom(resultsStep: ToolResultsStep) =
            RequestInfo(
                projectId = resultsStep.projectId,
                historyId = resultsStep.historyId,
                executionId = resultsStep.executionId,
                stepId = resultsStep.stepId
            )
    }

    fun initializeSettings(projectName: String): ProjectSettings =
        toolResultsClient.projects().initializeSettings(projectName).execute()

    fun getOrCreateHistory(projectName: String, testHistoryName: String): String {
        val historyList = toolResultsClient.projects().histories().list(projectName).apply {
            filterByName = testHistoryName
        }.execute()
        historyList?.histories?.firstOrNull()?.historyId?.let { return it }

        return toolResultsClient.projects().histories().create(
            projectName,
            History().apply {
                name = testHistoryName
                displayName = testHistoryName
            }).apply {
            requestId = UUID.randomUUID().toString()
        }.execute().historyId
    }

    fun requestStep(requestInfo: RequestInfo): Step =
        toolResultsClient.projects().histories().executions().steps().get(
            requestInfo.projectId,
            requestInfo.historyId,
            requestInfo.executionId,
            requestInfo.stepId
        ).execute()

    fun requestThumbnails(requestInfo: RequestInfo): ListStepThumbnailsResponse? =
        toolResultsClient.projects().histories().executions().steps().thumbnails().list(
            requestInfo.projectId,
            requestInfo.historyId,
            requestInfo.executionId,
            requestInfo.stepId
        ).execute()

    // Need the latest version of google-api-client to use
    // toolResultsClient.projects().histories().executions().steps().testCases().list().
    // Manually calling this API until this is available.
    fun requestTestCases(requestInfo: RequestInfo): TestCases =
        httpRequestFactory.buildGetRequest(
            GenericUrl(getTestCaseUrl(requestInfo))
        ).apply {
            setParser(parser)
        }.execute().content.use { response ->
            parser.parseAndClose<TestCases>(
                response, StandardCharsets.UTF_8, TestCases::class.java)
        }

    private fun getTestCaseUrl(requestInfo: RequestInfo): String =
        "https://toolResults.googleapis.com/toolresults/v1beta3/projects/${requestInfo.projectId}" +
                "/histories/${requestInfo.historyId}/executions/${requestInfo.executionId}/steps/" +
                "${requestInfo.stepId}/testCases"
}
