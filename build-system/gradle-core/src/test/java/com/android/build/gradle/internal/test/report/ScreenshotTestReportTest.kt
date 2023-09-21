/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.test.report

import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.io.Files
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

@RunWith(JUnit4::class)
class ScreenshotTestReportTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    private lateinit var resultsOutDir: File
    private lateinit var reportOutDir: File
    private lateinit var imagesOutDir: File
    private lateinit var golden: File
    private lateinit var diff: File
    private lateinit var actual: File

    @Before
    fun setupDirectory() {
        resultsOutDir = tempDirRule.newFolder()
        reportOutDir = tempDirRule.newFolder()
        imagesOutDir = tempDirRule.newFolder()
        golden = createTempImageFile(imagesOutDir, "golden")
        diff = createTempImageFile(imagesOutDir, "diff")
        actual = createTempImageFile(imagesOutDir, "actual")
    }

    private fun createTempImageFile(dir: File, name: String): File {
        val width = 5
        val height = 5
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g2d = image.createGraphics()
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, width, height)
        g2d.dispose()
        val imageFile = File("${dir.absolutePath}/$name.png")
        ImageIO.write(image, "png", imageFile)
        return imageFile
    }

    private fun createTestReportXmlFileRecordGolden() {
        val reportXml = File(resultsOutDir, "TEST-valid.xml")
        Files.asCharSink(reportXml, Charsets.UTF_8).write("""
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="com.example.myapplication.ExampleInstrumentedTest" tests="8" failures="1" errors="0" skipped="0" time="3.272" timestamp="2021-08-10T21:09:43" hostname="localhost">
              <properties>
                <property name="device" value="Previews" />
              </properties>
              <testcase name="useAppContext1" classname="com.example.myapplication.ExampleInstrumentedTest" time="3.272">
              <success>Reference Images saved</success>
              <images>
              <golden path="${golden.absolutePath}"/>
              </images>
              </testcase>
            </testsuite>
        """.trimIndent())
    }

    private fun createTestReportXmlFile() {
        val reportXml = File(resultsOutDir, "TEST-valid.xml")
        Files.asCharSink(reportXml, Charsets.UTF_8).write("""
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="com.example.myapplication.ExampleInstrumentedTest" tests="8" failures="1" errors="0" skipped="0" time="7.169" timestamp="2021-08-10T21:09:43" hostname="localhost">
              <properties>
                <property name="device" value="Previews" />
              </properties>
              <testcase name="useAppContext1" classname="com.example.myapplication.ExampleInstrumentedTest" time="3.272">
              <success>Reference Images saved</success>
              <images>
              <golden path="${golden.absolutePath}"/>
              <actual path="${actual.absolutePath}"/>
              <diff message="Images match"/>
              </images>
              </testcase>
              <testcase name="useAppContext2" classname="com.example.myapplication.ExampleInstrumentedTest" time="1.551">
              <failure>Images don't match</failure>
              <images>
              <golden path="${golden.absolutePath}"/>
              <actual path="${actual.absolutePath}"/>
              <diff path="${diff.absolutePath}"/>
              </images>
              </testcase>
              <testcase name="useAppContext3" classname="com.example.myapplication.ExampleInstrumentedTest" time="2.112">
              <failure>Images don't match</failure>
              <images>
              <golden path="${golden.absolutePath}"/>
              <actual path="${actual.absolutePath}"/>
              <diff message="Size Mismatch. Reference image: 5x5 Actual image: 4x5"/>
              </images>
              </testcase>
              <testcase name="useAppContext4" classname="com.example.myapplication.ExampleInstrumentedTest" time="0.234">
              <failure>No Reference Image</failure>
              <images>
              <golden message="Reference image does not exist"/>
              <actual path="${actual.absolutePath}"/>
              <diff message="No diff"/>
              </images>
              </testcase>
            </testsuite>
        """.trimIndent())
    }

    private fun createEmptyTestReportXmlFile() {
        val reportXml = File(resultsOutDir, "TEST-empty.xml")
        Files.asCharSink(reportXml, Charsets.UTF_8).write("""
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite tests="0" failures="0" errors="0" skipped="0" time="0.518" timestamp="2022-01-12T22:11:43" hostname="localhost">
              <properties>
                <property name="device" value="pixel3_1" />
                <property name="flavor" value="" />
                <property name="project" value=":app" />
              </properties>
            </testsuite>
        """.trimIndent())
    }

    private fun createTestReportXmlFileWithToolFailures() {
        val reportXml = File(resultsOutDir, "TEST-empty.xml")
        Files.asCharSink(reportXml, Charsets.UTF_8).write("""
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite tests="0" failures="0" errors="0" skipped="0" time="0.518" timestamp="2022-01-12T22:11:43" hostname="localhost">
              <properties>
                <property name="device" value="pixel3_1" />
                <property name="flavor" value="" />
                <property name="project" value=":app" />
              </properties>
              <system-err>PLATFORM ERROR</system-err>
            </testsuite>
        """.trimIndent())
    }

    @Test
    fun generateScreenshotReportRecordGolden() {
        createTestReportXmlFileRecordGolden()

        TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir).generateScreenshotTestReport(true)

        val indexHtml = File(reportOutDir, "index.html")
        assertThat(indexHtml).exists()
        assertThat(indexHtml).contains("""<div class="percent">100%</div>""")
        assertThat(indexHtml).contains("""<p>successful</p>""")

        val moduleHtml = File(reportOutDir, "com.example.myapplication.html")
        assertThat(moduleHtml).exists()

        val classHtml = File(reportOutDir, "com.example.myapplication.ExampleInstrumentedTest.html")
        assertThat(classHtml).exists()
        assertThat(classHtml).contains(golden.absolutePath)
    }

    @Test
    fun generateScreenshotReport() {
        createTestReportXmlFile()

        TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir).generateScreenshotTestReport(false)

        val indexHtml = File(reportOutDir, "index.html")
        assertThat(indexHtml).exists()
        assertThat(indexHtml).contains("""<div class="percent">25%</div>""")
        assertThat(indexHtml).contains("""<p>successful</p>""")

        val moduleHtml = File(reportOutDir, "com.example.myapplication.html")
        assertThat(moduleHtml).exists()

        val classHtml = File(reportOutDir, "com.example.myapplication.ExampleInstrumentedTest.html")
        assertThat(classHtml).exists()
        assertThat(classHtml).contains(golden.absolutePath)
        assertThat(classHtml).contains(actual.absolutePath)
        assertThat(classHtml).contains(diff.absolutePath)
        assertThat(classHtml).contains("Images match")
        assertThat(classHtml).contains("Reference image does not exist")
        assertThat(classHtml).contains("Size Mismatch")
        assertThat(classHtml).doesNotContain("Images don't match")

    }

    @Test
    fun shouldNotGenerateEmptyPackageReportForUnnamedTestSuite() {
        createEmptyTestReportXmlFile()

        TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir).generateScreenshotTestReport(false)

        val indexHtml = File(reportOutDir, "index.html")
        assertThat(indexHtml).exists()

        val packageHtml = File(reportOutDir, ".html")
        assertThat(packageHtml).doesNotExist()
    }

    @Test
    fun generateReportWithToolFailuresTab() {
        createTestReportXmlFileWithToolFailures()

        TestReport(ReportType.SINGLE_FLAVOR, resultsOutDir, reportOutDir).generateScreenshotTestReport(false)

        val indexHtml = File(reportOutDir, "index.html")
        assertThat(indexHtml).exists()
        assertThat(indexHtml).contains("<h2>Tool failures</h2>")
        assertThat(indexHtml).contains("PLATFORM ERROR")
    }
}
