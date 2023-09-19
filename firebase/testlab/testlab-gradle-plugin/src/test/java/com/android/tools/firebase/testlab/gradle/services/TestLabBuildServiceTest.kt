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

package com.android.tools.firebase.testlab.gradle.services

import com.android.build.api.dsl.CommonExtension
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpTransport
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.common.truth.Truth.assertThat
import com.google.firebase.testlab.gradle.TestLabGradlePluginExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildServiceSpec
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.argThat
import org.mockito.Mockito.startsWith
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.util.logging.Level

/**
 * Unit tests for [TestLabBuildService].
 */
class TestLabBuildServiceTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var mockExtension: TestLabGradlePluginExtension

    @Mock
    lateinit var mockProviderFactory: ProviderFactory

    @Test
    fun registerIfAbsent() {
        val credentialFile = temporaryFolderRule.newFile("testCredentialFile").apply {
            writeText("""
                    {
                      "client_id": "test_client_id",
                      "client_secret": "test_client_secret",
                      "quota_project_id": "test_quota_project_id",
                      "refresh_token": "test_refresh_token",
                      "type": "authorized_user"
                    }
                """.trimIndent())
        }

        val credentialFileProvider = mock<Provider<File>>()
        `when`(mockProviderFactory.provider<File>(any()))
            .thenReturn(credentialFileProvider)
        val credentialFileRegularFile = mock<RegularFile>()
        `when`(credentialFileRegularFile.asFile).thenReturn(credentialFile)

        val mockProject = mock<Project>(withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))
        `when`(mockProject.path).thenReturn("mockProjectPath")
        `when`(mockProject.extensions.getByType(eq(TestLabGradlePluginExtension::class.java)))
                .thenReturn(mockExtension)
        `when`(mockProject.extensions.getByType(eq(CommonExtension::class.java)))
                .thenReturn(mock())
        `when`(mockProject.providers).thenReturn(mockProviderFactory)

        TestLabBuildService.RegistrationAction(mockProject).registerIfAbsent()

        lateinit var configAction: Action<in BuildServiceSpec<TestLabBuildService.Parameters>>
        verify(mockProject.gradle.sharedServices).registerIfAbsent(
            startsWith("com.android.tools.firebase.testlab.gradle.services.TestLabBuildService_"),
            eq(TestLabBuildService::class.java),
            argThat {
                configAction = it
                true
            }
        )

        val mockSpec = mock<BuildServiceSpec<TestLabBuildService.Parameters>>()
        val mockParams = mock<TestLabBuildService.Parameters>(
            withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))
        `when`(mockSpec.parameters).thenReturn(mockParams)
        `when`(mockParams.credentialFile.map<String>(any())).then {
            val file = it.getArgument<Transformer<String, RegularFile>>(0)
                .transform(credentialFileRegularFile)
            mock<Provider<String>>().apply {
                `when`(get()).thenReturn(file)
            }
        }

        configAction.execute(mockSpec)

        verify(mockParams.credentialFile).fileProvider(eq(credentialFileProvider))
    }

    @Test
    fun logLevelShouldBeWarning() {
        val credentialFile = temporaryFolderRule.newFile("testCredentialFile")
        val buildService = object: TestLabBuildService() {
            override fun getParameters(): Parameters {
                val params = mock<Parameters>(
                        withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS))
                `when`(params.credentialFile.get().asFile).thenReturn(credentialFile)
                return params
            }
        }
        assertThat(buildService.apiClientLogger.level).isEqualTo(Level.WARNING)
    }

    @Test
    fun catalog() {
        val testCredentialFile = temporaryFolderRule.newFile("testCredentialFile").apply {
            writeText("""
                {
                  "client_id": "test_client_id",
                  "client_secret": "test_client_secret",
                  "quota_project_id": "test_quota_project_id",
                  "refresh_token": "test_refresh_token",
                  "type": "authorized_user"
                }
            """.trimIndent())
        }
        val service = object: TestLabBuildService() {
            override val credential: GoogleCredential
                get() = mock()
            override val httpTransport: HttpTransport
                get() = MockHttpTransport.Builder().apply {
                    setLowLevelHttpResponse(MockLowLevelHttpResponse().apply {
                        setContent("""
                            {
                              "androidDeviceCatalog": {
                                "models": [
                                  {
                                    "id": "test_device_id"
                                  }
                                ]
                              }
                            }
                        """.trimIndent())
                    })
                }.build()
            override fun getParameters() = mock<Parameters>(
                withSettings().defaultAnswer(Answers.RETURNS_DEEP_STUBS)).apply {
                    val mockOffline: Property<Boolean> = mock()
                    `when`(mockOffline.get()).thenReturn(false)
                    `when`(offlineMode).thenReturn(mockOffline)
                    `when`(credentialFile.get().asFile).thenReturn(testCredentialFile)
                }
        }

        val catalog = service.catalog()
        assertThat(catalog.models).hasSize(1)
        assertThat(catalog.models[0].id).isEqualTo("test_device_id")
    }
}
