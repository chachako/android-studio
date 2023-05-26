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
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.google.api.services.testing.Testing
import com.google.api.services.testing.Testing.Projects.TestMatrices
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.TestEnvironmentCatalog
import com.google.api.services.testing.model.TestMatrix
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

class TestingManagerTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    lateinit var testClient: Testing

    @Mock
    lateinit var testMatrixClient: TestMatrices

    @Mock
    lateinit var mockMatrixCreate: TestMatrices.Create

    @Mock
    lateinit var mockMatrixGet: TestMatrices.Get

    @Mock
    lateinit var mockCatalog: Testing.TestEnvironmentCatalog

    @Mock
    lateinit var mockCatalogGet: Testing.TestEnvironmentCatalog.Get

    @Before
    fun setup() {
        `when`(mockCatalog.get(any())).thenReturn(mockCatalogGet)
        testMatrixClient.apply {
            `when`(get(any(), any())).thenReturn(mockMatrixGet)
            `when`(create(any(), any())).thenReturn(mockMatrixCreate)
        }
        val projects = mock<Testing.Projects>().apply {
            `when`(testMatrices()).thenReturn(testMatrixClient)
        }
        testClient.apply {
            `when`(projects()).thenReturn(projects)
            `when`(testEnvironmentCatalog()).thenReturn(mockCatalog)
        }
    }

    @Test
    fun test_createTextMatrixRun() {
        val manager = TestingManager(testClient)

        val mockMatrix: TestMatrix = mock()
        `when`(mockMatrixCreate.execute()).thenReturn(mockMatrix)

        val result = manager.createTestMatrixRun(
            "my_project",
            mockMatrix,
            "aabbcc"
        )

        assertThat(result).isSameInstanceAs(mockMatrix)

        verify(testMatrixClient).create("my_project", mockMatrix)

        inOrder(mockMatrixCreate).also {
            it.verify(mockMatrixCreate).setRequestId(eq("aabbcc"))
            it.verify(mockMatrixCreate).execute()
        }

        verifyNoMoreInteractions(testMatrixClient)
        verifyNoMoreInteractions(mockMatrixCreate)
    }

    @Test
    fun test_getTextMatrix_idVersion() {
        val manager = TestingManager(testClient)

        val mockMatrix: TestMatrix = mock()

        `when`(mockMatrixGet.execute()).thenReturn(mockMatrix)

        val result = manager.getTestMatrix("big_project", "some_long_matrix_id")

        assertThat(result).isSameInstanceAs(mockMatrix)

        verify(testMatrixClient).get("big_project", "some_long_matrix_id")
        verify(mockMatrixGet).execute()

        verifyNoMoreInteractions(testMatrixClient)
        verifyNoMoreInteractions(mockMatrixGet)
    }

    @Test
    fun test_getTextMatrix_matrixVersion() {
        val manager = TestingManager(testClient)

        val mockMatrix: TestMatrix = mock()

        `when`(mockMatrix.testMatrixId).thenReturn("id_from_matrix")

        `when`(mockMatrixGet.execute()).thenReturn(mockMatrix)

        val result = manager.getTestMatrix("big_project", mockMatrix)

        assertThat(result).isSameInstanceAs(mockMatrix)

        verify(testMatrixClient).get("big_project", "id_from_matrix")
        verify(mockMatrixGet).execute()

        verifyNoMoreInteractions(testMatrixClient)
        verifyNoMoreInteractions(mockMatrixGet)
    }

    @Test
    fun test_catalog() {
        val manager = TestingManager(testClient)

        val testEnvironment: TestEnvironmentCatalog = mock()

        val androidCatalog: AndroidDeviceCatalog = mock<AndroidDeviceCatalog>().also {
            `when`(testEnvironment.androidDeviceCatalog).thenReturn(it)
        }

        `when`(mockCatalogGet.execute()).thenReturn(testEnvironment)

        val result = manager.catalog("project_id_here")

        assertThat(result).isSameInstanceAs(androidCatalog)

        verify(mockCatalog).get("ANDROID")

        inOrder(mockCatalogGet).also {
            it.verify(mockCatalogGet).setProjectId("project_id_here")
            it.verify(mockCatalogGet).execute()
        }

        verifyNoMoreInteractions(mockCatalog)
        verifyNoMoreInteractions(mockCatalogGet)
    }
}
