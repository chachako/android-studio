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

package com.android.tools.firebase.testlab.gradle.services.testrunner

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TestResultsXmlHandlerTest {

    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    @Mock
    lateinit var device: TestDeviceData

    lateinit var xml: File

    @Before
    fun setup() {
        `when`(device.name).thenReturn("this_is_my_device")

        xml = temporaryFolderRule.newFile()
    }

    @Test
    fun test_updateXml() {
        xml.writeText(
            """
                <?xml version='1.0' encoding='UTF-8'?>
                <testsuite name='' hostname='localhost' tests='1' failures='0' skipped='0' errors='0' time='0.024' timestamp='2023-06-09T17:23:51'>
                  <properties />
                  <testcase name='testMethod' classname='package.name.ClassName' time='0.024' />
                </testsuite>
            """.trimIndent()
        )

        TestRunner.getDefaultHandler(device).updateXml(xml, "variant", "project")

        val expectedFile = temporaryFolderRule.newFile().apply {
            writeText(
                """
                    <?xml version="1.0" encoding="UTF-8" standalone="no"?><testsuite errors="0" failures="0" hostname="localhost" name="" skipped="0" tests="1" time="0.024" timestamp="2023-06-09T17:23:51">
                      <properties><property name="device" value="this_is_my_device"/><property name="flavor" value="variant"/><property name="project" value="project"/></properties>
                      <testcase classname="package.name.ClassName" name="testMethod" time="0.024"/>
                    </testsuite>
                """.trimIndent()
            )
        }

        assertThat(xml.readLines()).isEqualTo(expectedFile.readLines())
    }

}
