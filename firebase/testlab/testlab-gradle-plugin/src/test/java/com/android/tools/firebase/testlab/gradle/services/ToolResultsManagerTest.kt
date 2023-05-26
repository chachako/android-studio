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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.HttpResponse
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.util.DateTime
import com.google.api.services.testing.model.ToolResultsStep
import com.google.api.services.toolresults.ToolResults
import com.google.api.services.toolresults.ToolResults.Projects.Histories.Executions.Steps.Thumbnails
import com.google.api.services.toolresults.model.History
import com.google.api.services.toolresults.model.ListHistoriesResponse
import com.google.api.services.toolresults.model.ListStepThumbnailsResponse
import com.google.api.services.toolresults.model.ProjectSettings
import com.google.api.services.toolresults.model.Step
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * Unit tests for [ToolResultsManager]
 */
class ToolResultsManagerTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock
    lateinit var toolResultsClient: ToolResults

    @Mock
    lateinit var mockProjects: ToolResults.Projects

    @Mock
    lateinit var mockSettings: ToolResults.Projects.InitializeSettings

    @Mock
    lateinit var mockHistories: ToolResults.Projects.Histories

    @Mock
    lateinit var mockHistoryList: ToolResults.Projects.Histories.List

    @Mock
    lateinit var mockHistoryCreate: ToolResults.Projects.Histories.Create

    @Mock
    lateinit var mockSteps: ToolResults.Projects.Histories.Executions.Steps

    @Mock
    lateinit var mockStepsGet: ToolResults.Projects.Histories.Executions.Steps.Get

    @Mock
    lateinit var mockThumbnails: Thumbnails

    @Mock
    lateinit var mockThumbnailsList: Thumbnails.List

    @Mock
    lateinit var requestFactory: HttpRequestFactory

    @Mock
    lateinit var objectParser: JsonObjectParser

    lateinit var manager: ToolResultsManager

    private fun requestInfo() = ToolResultsManager.RequestInfo(
        "project_id",
        "history_id",
        "execution_id",
        "step_id"
    )

    @Before
    fun setup() {
        `when`(mockThumbnails.list(any(), any(), any(), any())).thenReturn(mockThumbnailsList)
        mockSteps.apply {
            `when`(thumbnails()).thenReturn(mockThumbnails)
            `when`(get(any(), any(), any(), any())).thenReturn(mockStepsGet)
        }
        val mockExecutions = mock<ToolResults.Projects.Histories.Executions>().apply {
            `when`(steps()).thenReturn(mockSteps)
        }
        mockHistories.apply {
            `when`(executions()).thenReturn(mockExecutions)
            `when`(list(any())).thenReturn(mockHistoryList)
            `when`(create(any(), any())).thenReturn(mockHistoryCreate)
        }
        mockProjects.apply {
            `when`(histories()).thenReturn(mockHistories)
            `when`(initializeSettings(any())).thenReturn(mockSettings)
        }
        `when`(toolResultsClient.projects()).thenReturn(mockProjects)

        manager = ToolResultsManager(
            toolResultsClient,
            requestFactory,
            objectParser
        )
    }

    @Test
    fun test_initializeSettings() {
        val settings = mock<ProjectSettings>().also {
            `when`(mockSettings.execute()).thenReturn(it)
        }

        val result = manager.initializeSettings("project_here")

        assertThat(result).isSameInstanceAs(settings)

        verify(mockProjects).initializeSettings("project_here")
        verify(mockSettings).execute()

        verifyNoMoreInteractions(mockProjects)
        verifyNoMoreInteractions(mockSettings)
    }

    @Test
    fun test_getOrCreateHistory_returnExistingHistory() {
        val mockResult = mock<History>().apply {
            `when`(historyId).thenReturn("history id")
        }
        val mockListResponse = mock<ListHistoriesResponse>().apply {
            `when`(histories).thenReturn(listOf(mockResult))
        }
        mockHistoryList.apply {
            `when`(execute()).thenReturn(mockListResponse)
        }

        val result = manager.getOrCreateHistory("this_project", "test_history")

        assertThat(result).isEqualTo("history id")

        verify(mockHistories).list("this_project")

        inOrder(mockHistoryList).also {
            it.verify(mockHistoryList).setFilterByName("test_history")
            it.verify(mockHistoryList).execute()
        }

        verifyNoMoreInteractions(mockHistories)
        verifyNoMoreInteractions(mockHistoryList)
    }

    @Test
    fun test_getOrCreateHistory_createHistoryWhenNoneExist() {
        val mockListResponse = mock<ListHistoriesResponse>().apply {
            `when`(histories).thenReturn(listOf())
        }
        mockHistoryList.apply {
            `when`(execute()).thenReturn(mockListResponse)
        }

        val mockResponse = mock<History>().apply {
            `when`(historyId).thenReturn("other history id")
        }
        mockHistoryCreate.apply {
            `when`(execute()).thenReturn(mockResponse)
        }

        val result = manager.getOrCreateHistory("my_project", "my_other_history")

        assertThat(result).isEqualTo("other history id")

        inOrder(mockHistories).also {
            it.verify(mockHistories).list("my_project")
            it.verify(mockHistories).create(
                eq("my_project"),
                argThat {
                    it.name == "my_other_history" && it.displayName == it.name
                })
        }

        inOrder(mockHistoryList).also {
            it.verify(mockHistoryList).setFilterByName("my_other_history")
            it.verify(mockHistoryList).execute()
        }

        inOrder(mockHistoryCreate).also {
            it.verify(mockHistoryCreate).setRequestId(any())
            it.verify(mockHistoryCreate).execute()
        }

        verifyNoMoreInteractions(mockHistories)
        verifyNoMoreInteractions(mockHistoryList)
        verifyNoMoreInteractions(mockHistoryCreate)
    }

    @Test
    fun test_requestStep() {
        val step: Step = mock()
        `when`(mockStepsGet.execute()).thenReturn(step)

        val result = manager.requestStep(requestInfo())

        assertThat(result).isSameInstanceAs(step)

        verify(mockSteps).get("project_id", "history_id", "execution_id", "step_id")
        verify(mockStepsGet).execute()

        verifyNoMoreInteractions(mockSteps)
        verifyNoMoreInteractions(mockStepsGet)
    }

    @Test
    fun test_requestThumbnails() {
        val thumbnails: ListStepThumbnailsResponse = mock()
        `when`(mockThumbnailsList.execute()).thenReturn(thumbnails)

        val result = manager.requestThumbnails(requestInfo())

        assertThat(result).isSameInstanceAs(thumbnails)

        verify(mockThumbnails).list("project_id", "history_id", "execution_id", "step_id")
        verify(mockThumbnailsList).execute()

        verifyNoMoreInteractions(mockThumbnails)
        verifyNoMoreInteractions(mockThumbnailsList)
    }

    @Test
    fun test_requestTestCases() {
        val testCases = mock<ToolResultsManager.TestCases>()
        `when`(objectParser.parseAndClose<ToolResultsManager.TestCases>(any(), any(), any()))
            .thenReturn(testCases)

        val file = temporaryFolderRule.newFile()
        val inputStream = FileInputStream(file)
        val response = mock<HttpResponse>().apply {
            `when`(content).thenReturn(inputStream)
        }
        val request = mock<HttpRequest>().apply {
            `when`(execute()).thenReturn(response)
            `when`(requestFactory.buildGetRequest(any())).thenReturn(this)
        }

        val result = manager.requestTestCases(requestInfo())

        assertThat(result).isSameInstanceAs(testCases)

        verify(requestFactory).buildGetRequest(
            eq(
                GenericUrl(
                    "https://toolResults.googleapis.com/toolresults/v1beta3/projects/project_id/" +
                            "histories/history_id/executions/execution_id/steps/step_id/testCases"
                )
            )
        )

        inOrder(request).also {
            it.verify(request).setParser(objectParser)
            it.verify(request).execute()
        }

        verify(objectParser).parseAndClose<ToolResultsManager.TestCases>(
            eq(inputStream),
            eq(StandardCharsets.UTF_8),
            eq(ToolResultsManager.TestCases::class.java)
        )

        verifyNoMoreInteractions(requestFactory)
        verifyNoMoreInteractions(request)
        verifyNoMoreInteractions(objectParser)
    }

    @Test
    fun testStatic_requestFrom() {
        val resultStep = mock<ToolResultsStep>().apply {
            `when`(projectId).thenReturn("some_id")
            `when`(historyId).thenReturn("another_id")
            `when`(executionId).thenReturn("yet_another_id")
            `when`(stepId).thenReturn("one_more_for_good_measure")
        }
        val result = ToolResultsManager.requestFrom(resultStep)

        assertThat(result.projectId).isEqualTo("some_id")
        assertThat(result.historyId).isEqualTo("another_id")
        assertThat(result.executionId).isEqualTo("yet_another_id")
        assertThat(result.stepId).isEqualTo("one_more_for_good_measure")
    }
}
