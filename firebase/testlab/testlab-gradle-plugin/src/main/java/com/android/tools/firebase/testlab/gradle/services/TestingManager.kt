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

import com.google.api.services.testing.Testing
import com.google.api.services.testing.Testing.Projects.TestMatrices
import com.google.api.services.testing.model.AndroidDeviceCatalog
import com.google.api.services.testing.model.TestMatrix

/**
 * Class to handle all [Testing] related requests made by the [TestLabBuildService]
 */
class TestingManager(
    private val testingClient: Testing
) {

    private val testMatricesClient: TestMatrices = testingClient.projects().testMatrices()

    fun createTestMatrixRun(
        projectName: String,
        testMatrix: TestMatrix,
        runRequestId: String
    ): TestMatrix =
        testMatricesClient.create(projectName, testMatrix).apply {
            this.requestId = runRequestId
        }.execute()

    fun getTestMatrix(
        projectName: String,
        testMatrix: TestMatrix
    ) = getTestMatrix(projectName, testMatrix.testMatrixId)

    fun getTestMatrix(
        projectName: String,
        testMatrixId: String
    ): TestMatrix =
        testMatricesClient.get(projectName, testMatrixId).execute()

    fun catalog(projectName: String): AndroidDeviceCatalog =
        testingClient.testEnvironmentCatalog().get("ANDROID").apply {
            projectId = projectName
        }.execute().androidDeviceCatalog
}
