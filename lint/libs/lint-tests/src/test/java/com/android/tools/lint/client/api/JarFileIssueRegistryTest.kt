/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.lint.client.api

import com.android.testutils.TestUtils
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.MainTest
import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.describeApi
import com.google.common.io.ByteStreams
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.nio.file.Files
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JarFileIssueRegistryTest : AbstractCheckTest() {
  override fun lint(): TestLintTask = TestLintTask.lint().sdkHome(TestUtils.getSdk().toFile())

  fun testError() {
    val loggedWarnings = StringWriter()
    val client = createClient(loggedWarnings)
    getSingleRegistry(client, File("bogus"))
    assertThat(loggedWarnings.toString())
      .contains("Could not load custom lint check jar files: bogus")
  }

  fun testCached() {
    val targetDir = TestUtils.createTempDirDeletedOnExit().toFile()
    val file1 = base64gzip("lint.jar", CustomRuleTest.LINT_JAR_BASE64_GZIP).createFile(targetDir)
    val file2 = jar("unsupported/lint.jar").createFile(targetDir)
    val file3 = jar("unsupported.jar").createFile(targetDir)
    assertTrue(file1.path, file1.exists())
    val loggedWarnings = StringWriter()
    val client = createClient(loggedWarnings)
    val registry1 = getSingleRegistry(client, file1) ?: fail()
    val registry2 = getSingleRegistry(client, File(file1.path)) ?: fail()
    assertSame(registry1, registry2)
    val registry3 = getSingleRegistry(client, file2)
    assertThat(registry3).isNull()
    val registry4 = getSingleRegistry(client, file3)
    assertThat(registry4).isNull()

    assertEquals(1, registry1.issues.size)
    assertEquals("UnitTestAppCompatMethod", registry1.issues[0].id)

    // Access detector state. On Java 7/8 this will access the detector class after
    // the jar loader has been closed; this tests that we still have valid classes.
    val detector = registry1.issues[0].implementation.detectorClass.newInstance()
    val applicableCallNames = detector.getApplicableCallNames()
    assertNotNull(applicableCallNames)
    assertTrue(applicableCallNames!!.contains("getActionBar"))

    assertEquals(
      "Custom lint rule jar " +
        file2.path +
        " does not contain a valid " +
        "registry manifest key (Lint-Registry-v2).\n" +
        "Either the custom jar is invalid, or it uses an outdated API not " +
        "supported this lint client\n",
      loggedWarnings.toString()
    )

    // Make sure we handle up to date checks properly too
    val composite = CompositeIssueRegistry(listOf(registry1, registry2))
    assertThat(composite.isUpToDate).isTrue()

    assertThat(registry1.isUpToDate).isTrue()
    file1.setLastModified(file1.lastModified() + 2000)
    assertThat(registry1.isUpToDate).isFalse()
    assertThat(composite.isUpToDate).isFalse()
  }

  fun testDeduplicate() {
    val targetDir = TestUtils.createTempDirDeletedOnExit().toFile()
    val file1 = base64gzip("lint1.jar", CustomRuleTest.LINT_JAR_BASE64_GZIP).createFile(targetDir)
    val file2 = base64gzip("lint2.jar", CustomRuleTest.LINT_JAR_BASE64_GZIP).createFile(targetDir)
    assertTrue(file1.path, file1.exists())
    assertTrue(file2.path, file2.exists())

    val loggedWarnings = StringWriter()
    val client = createClient(loggedWarnings)

    val registries = JarFileIssueRegistry.get(client, listOf(file1, file2))
    // Only *one* registry should have been computed, since the two provide the same lint
    // class names!
    assertThat(registries.size).isEqualTo(1)
  }

  fun testGetDefaultIdentifier() {
    val targetDir = TestUtils.createTempDirDeletedOnExit().toFile()
    val file1 = base64gzip("lint1.jar", CustomRuleTest.LINT_JAR_BASE64_GZIP).createFile(targetDir)
    assertTrue(file1.path, file1.exists())

    val loggedWarnings = StringWriter()
    val client = createClient(loggedWarnings)

    val registry = JarFileIssueRegistry.get(client, listOf(file1)).first()
    val vendor = registry.vendor
    assertNotNull(vendor)
    assertEquals("android.support.v7.lint.appcompat", vendor.identifier)
    assertEquals(
      "Android Open Source Project (android.support.v7.lint.appcompat)",
      vendor.vendorName
    )
    assertEquals("https://issuetracker.google.com/issues/new?component=192731", vendor.feedbackUrl)
  }

  override fun getDetector(): Detector? {
    fail("Not used in this test")
    return null
  }

  private fun getSingleRegistry(client: LintClient, file: File): JarFileIssueRegistry? {
    val list = listOf(file)
    val registries = JarFileIssueRegistry.get(client, list)
    return if (registries.size == 1) registries[0] else null
  }

  private fun createClient(loggedWarnings: StringWriter): LintClient {
    return object : TestLintClient() {
      override fun log(exception: Throwable?, format: String?, vararg args: Any) {
        if (format != null) {
          loggedWarnings.append(String.format(format, *args) + '\n')
        }
      }

      override fun log(
        severity: Severity,
        exception: Throwable?,
        format: String?,
        vararg args: Any
      ) {
        if (format != null) {
          loggedWarnings.append(String.format(format, *args) + '\n')
        }
      }
    }
  }

  private fun fail(): Nothing {
    error("Test failed")
  }

  fun testNewerLintOk() {
    // Tests what happens when the loaded lint rule was compiled with
    // a newer version of the lint apis than the current host, but the
    // API accesses all seem to be okay
    val root = Files.createTempDirectory("lintjar").toFile()

    lint()
      .files(
        *lintApiStubs,
        bytecode(
          "lint.jar",
          source(
            "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
            "test.pkg.MyIssueRegistry"
          ),
          0x70522285
        ),
        bytecode(
          "lint.jar",
          kotlin(
              """
                    package test.pkg
                    import com.android.tools.lint.client.api.*
                    import com.android.tools.lint.detector.api.*
                    import java.util.EnumSet

                    class MyIssueRegistry : IssueRegistry() {
                        override val issues: List<Issue> = emptyList()
                        override val api: Int = 10000
                        override val minApi: Int = 7
                        override val vendor: Vendor = Vendor(
                            vendorName = "Android Open Source Project: Lint Unit Tests",
                            contact = "/dev/null"
                        )
                    }
                    """
            )
            .indented(),
          0x4f058bc1,
          """
                    test/pkg/MyIssueRegistry.class:
                    H4sIAAAAAAAAAJ1UW08bRxT+Zn1bb0xYHELAhMYkbWKckDX0HhNSAkm7qqEV
                    pFYrnhZ76o693kU7Y1f0iV/RH1D1sQ+tVJSqlSqUx/yoqmfXSwEDEunDzplz
                    +c75Zs6cff3Pn38DWITNMKm4VNZup2Wt79lS9vgmbwmpgr0MGIPV8LuW4zUD
                    XzQt5fuutFzhKavhCk7C2RXWECjBkBahSTKYtbbTd6yeEq5VI3+V4fGQaal2
                    QYUmV7yh/OC4RnWZ8HdqftCy2lztBI7wJCE9XzlK+LTf8NVGz3UpKtviyo5J
                    5EtzZ2k8OWN8MyI5pJDOQkOOIam+E1SnULvoJqlegsAMjO47TdRWQiVRmrNz
                    MDEWphknR1d4kSMkvx7tc5gYuCfJ3ede0w8YyhcRPdGTehQbX0Q9Bj6gM18a
                    mkMB02Hpt6j0kvCEWo4o13MoYtZAErcZih1fEZ4eievSHUVNWD3ef64yeJs4
                    8O6u2gvvOIe7MAy8g3sMpctSyWCOuK8MIotf7HKvuOX3ggYvfhn4bSr1qFgj
                    YPEr4lh8QS2QOu5TVWpc3/LoPeiYZ/ihNOi363gta0sFwmtV/5/FrsWnbve7
                    FhXmgee41hr/1um5apWOrYJe+F7WnaDDg+rgxiwDZVQYxo7A61w5TUc51COt
                    20/QNLJwSYaLRradyIZwoYliHTI1Fxh+PtwvGdqkNvh0+kydZIJkMbaljnyT
                    h/uLWoU9Tb36Ka2Z2ua4mSholeTXr35cI4tuHO4XknrKTG8WzExBzyfzWiVb
                    0cmdPHYb5hXC5c7iRgg3bl4lx+hphGmOhVwXGRYu8dqG5wQM999gEGm6h2bt
                    YUdR77dEy3NUL+AM05s9T4kut72+kGLH5SvHfwya3VW/SUGj9IL4Rq+7w4MX
                    DsVQ3prfcNy6E4hQj43G4OE9F6EyFSeun0mLBZqbZNhN5MP/BGlPw7Yig1WS
                    aTqkTjIfDn8kJ2JJI0e+kzEpklkY9A7WSLNIY6G1/DtGfo1SPouDQcmf05ob
                    BOAqRkkOyl45lZLOhmu0Die8/stQQv2chAw3zgVPDYOz54JvYoaihsG3ho9i
                    nAM+fQT6B8UsvqdSYdx8+SXulP9A6QAj5bF75gGul83MAabKf6H8Tf4BY/mH
                    Zpq9xMIBbv32X8G7BB8kTlO7MnRXOt1PFpNEYoa0WUQ/rROE5mNCGj6N1hV8
                    RrJNvkUi++42Ejbes/G+jQ/w4ZH2kY2P8Yi2qG6DSSzh8TbyEqMSyxKzEqbE
                    Exlarkmkov0nElkJQ2JC4oZEQWJa4qbEzL/b/KBivgcAAA==
                    """,
          """
                    META-INF/main.kotlin_module:
                    H4sIAAAAAAAAAGNgYGBmYGBgBGIWIGYCYgYuYy7F5PxcvcS8lKL8zBS9kvz8
                    nGK9nMy8Er3knMxUIJVYkCnE5wxmxxeXlCYVe5coMWgxAAANsEImTQAAAA==
                    """
        )
      )
      .testModes(TestMode.DEFAULT)
      .createProjects(root)

    val lintJar = File(root, "app/lint.jar")
    assertTrue(lintJar.exists())

    lint()
      .files(
        source( // instead of xml: not valid XML below
            "res/values/strings.xml",
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources/>
                """
          )
          .indented()
      )
      .clientFactory { createGlobalLintJarClient(lintJar) }
      .testModes(TestMode.DEFAULT)
      .allowObsoleteLintChecks(false)
      .issueIds("MyIssueId")
      .run()
      .expectClean()
  }

  @Suppress("NullableProblems", "ConstantConditions")
  fun testAndroidXOk() {
    // Tests what happens when the loaded lint rule was compiled with
    // a newer version of the lint apis than the current host, but the
    // API accesses all seem to be okay
    val root = Files.createTempDirectory("lintjar").toFile()

    lint()
      .files(
        *lintApiStubs,
        bytecode(
          "lint.jar",
          source(
            "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
            "androidx.annotation.experimental.lint.ExperimentalIssueRegistry"
          ),
          0x7ca072f0
        ),
        bytecode(
          "lint.jar",
          java(
              """
                    package androidx.annotation.experimental.lint;
                    import com.android.tools.lint.client.api.*;
                    import com.android.tools.lint.detector.api.*;
                    import java.util.EnumSet;
                    import java.util.List;
                    import java.util.Collections;

                    public class ExperimentalIssueRegistry extends IssueRegistry {
                        @Override public List<Issue> getIssues() { return Collections.emptyList(); }
                    }
                    """
            )
            .indented(),
          0xb1bcd1d5,
          """
                androidx/annotation/experimental/lint/ExperimentalIssueRegistry.class:
                H4sIAAAAAAAAAHWQv07DMBDGP/dfmlAoFMoESGwtAx4ZWpWhAgkpYqCI3U2s
                ysixq8RB7VvBBGLgAXgoxCVUKgLVw9k+/e777u7z6/0DwAUOA9TQDrCLPQ8d
                D/sMjaEyyo0Yqr3+A0NtbGPJ0A6Vkbd5MpXpvZhqyvgz6W6yLJcZQ6fXDx/F
                k+C5U5qHKnMDAiZqZoTLU4Iv/wHDMLIJFyZOrYq5s1ZnXCvjeCydjJxNuZgr
                XhoMRqQWTGyeRvJaFd4nV4u5TFUijRO6ZO7kjETT5Xnh0kIdDQ8HLXQRkPnK
                ZUF2xjrhlDVc/lL4Md6oycA39BppRfi603VFdz3t2GpNE5EpbcqXydwtiwXg
                FFVafnEqYEXHFD36HdPN6K6fvYK90IOhSbFRJpsUfQRUUqBHK7T2hsrzH9LH
                Vindot92+dr5BoPJPOP3AQAA
                """
        )
      )
      .testModes(TestMode.DEFAULT)
      .createProjects(root)

    val lintJar = File(root, "app/lint.jar")
    assertTrue(lintJar.exists())

    lint()
      .files(
        source( // instead of xml: not valid XML below
            "res/values/strings.xml",
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources/>
                """
          )
          .indented()
      )
      .clientFactory { createGlobalLintJarClient(lintJar) }
      .testModes(TestMode.DEFAULT)
      .allowObsoleteLintChecks(false)
      .issueIds("MyIssueId")
      .run()
      .expectClean()
  }

  fun testInvalidPackaging() {
    val root = Files.createTempDirectory("lintjar").toFile()

    lint()
      .files(
        *lintApiStubs,
        bytecode(
          "lint.jar",
          source(
            "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
            "test.pkg.MyIssueRegistry"
          ),
          0x70522285
        ),
        bytecode(
          "lint.jar",
          kotlin(
              """
                    package com.android.something
                    class InReservedPackage {
                    }
                    """
            )
            .indented(),
          0x711b4bf6,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3AZcylmJyfq5eYl1KUn5miV5Kfn1Os
                l5OZV6KXnJOZCqQSCzKF+JzB7PjiktKkYu8SJQYtBgDDO/ZuTQAAAA==
                """,
          """
                com/android/something/InReservedPackage.class:
                H4sIAAAAAAAAAI2RPUsDQRCG39mYi55R43f8wNaPwlOxUwQVhED8QCWN1eZu
                0TW5XbjdBMv8Fv+BlWAhwdIfJc6ddjZu8TDvO8PM7O7n19s7gH2sEtZjm0bS
                JJnVSeRsqvyDNvdRw1wrp7K+Sq5k3JH3qgIi1B5lX0ZdyQWX7UcV+wpKhOBQ
                G+2PCKWNzVYVZQQhRlAhjHAvR9hs/nPGAWG62bG+q010rrxMpJfsibRf4nUp
                R5lAHbaedK52OEp2CWvDQRiKughFjaPhoD4c7IkdOil/PAeiJvKqPeIOmPsz
                c7vjedFTmyjCVFMbddFL2yq7le0uOzNNG8tuS2Y6179meGN7WazOdC6WrnvG
                61S1tNOcPTbGeum1NQ67EPwO+eGl82dhLrKKCs132XrF6AsHAnVmUJgCS8zq
                TwHGEBbecsEFrBTfRhjnXPUOpQYmGphkYipHrYFpzNyBHGYxx3mH0GHeIfgG
                eDuJRfMBAAA=
                """
        ),
        bytecode(
          "lint.jar",
          kotlin(
              """
                    package test.pkg
                    import com.android.tools.lint.client.api.*
                    import com.android.tools.lint.detector.api.*
                    import java.util.EnumSet

                    class MyIssueRegistry : IssueRegistry() {
                        override val issues: List<Issue> = emptyList()
                        override val api: Int = 11
                        override val vendor: Vendor = Vendor(
                            vendorName = "Android Open Source Project: Lint Unit Tests",
                            contact = "/dev/null"
                        )
                    }
                    """
            )
            .indented(),
          0x6017198f,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3AZcylmJyfq5eYl1KUn5miV5Kfn1Os
                l5OZV6KXnJOZCqQSCzKF+JzB7PjiktKkYu8SJQYtBgDDO/ZuTQAAAA==
                """,
          """
                test/pkg/MyIssueRegistry.class:
                H4sIAAAAAAAAAKVVTW8bRRh+Zm2v147bbNwSXLfAltDguGnHCd91mpKmQrLq
                pFVSLFBOG3twJ17vWjtji3DKnTs/gDMHkIiKQEJRj/wo1Hdsl6ROK0I57Lzz
                fn/P/vX3738C+BDrDAUtlOa9Tptv7NeU6ost0ZZKx/tpMAbejLrcD1txJFtc
                R1GgeCBDzZuBFAT8nuQTSgkGe0WGUq8yJEoLjRxSsLNIIs3gdSJN6mQ0CERT
                yyhUfP34fl+nkWHIiG5P79fJHkO+tFDf8wc+72sZcEOr5jCFXBZZnCNP0jhX
                DO5pqWm4GViYoTAoTAZWy+ECLhraGwylf0+sIcJWFKfxJsPi2kjSe9ATobcd
                9eOm8B7G0R5Ffsurk6L3JaXsPaJaKgeXKAneEgMe9oPAwWWG70qjAAM/bPNt
                HcuwXX09Sq0+LuLeoMvJsYhDP+D3xDd+P9DrVEUd95s6ijf8uCPi6qgBb2VR
                wNtUr8EwJ4Zy/az5UyU9XDVVe5chqR9Lqnax/qqhqVLmbaFr47bcOdW+lVc5
                bgktTNzHM1VdJWtz9Shu8z2hd2Nf0rj4YRhpfzQ6m5HepAKTlE0+10yTaeJq
                oxAa40wXKYQz58pw+3/GO/O8PRtC+y1f+0SzuoMErRszR4oGsUOkb6XBKnRr
                LTF8f3RQyloFa/Q59LkOwQRBb0xLPOcVjg6WrQq7m3r6o2251tZFN1G0Ksmv
                nv5wjyhO9uigmHRSrr1VdNNFJ5/MW5VMxSF28piddadIL0d6515knHenTUjL
                DEtnqNpk68Fw/T/Ui9Z7YnxudmjnM9uyHfq6HwuGy1v9UMuuqIUDqeRuINaO
                +0/juB61SGiaFlBs9ru7In7kkwzZrUdNP2j4sTT4mJgd7e0X0iCXxoYbp8xi
                iWY9Sc1JIG8eEcJWCLNwA7cJ2pRkiWDePCZDSOtBtJO8JMHUEFslrIcM3YD5
                8hM45d9w/hD5sjt1iNnyHyh8nS8ylr/i2uwJ3jnE3C9DX3eMPOkZm/R00pPn
                YIbwWZhNzqBIlCsErxH/c5LKjTzgPaKYNswTJTH0zgkzvFT5V+R//se4PSSm
                TyinxsqjZN9/ISGGBZTH6Zw0OPvThEHnJQZpKLBIUpPKc5PRZF6ifDIKC2vD
                s4q7BHeIepPk+A4SNVRqWKITy+b4oEb/to9IQOFjfLIDV+GawqcKtsIFhc8U
                FhTKCtPD+y2FeYWcgqdwVeG6wuIzYkwLpiEHAAA=
                """
        )
      )
      .testModes(TestMode.DEFAULT)
      .createProjects(root)

    val lintJar = File(root, "app/lint.jar")
    assertTrue(lintJar.exists())

    lint()
      .files(
        source("res/values/strings.xml", """
                <resources/>
                """)
          .indented()
      )
      .clientFactory { createGlobalLintJarClient(lintJar) }
      .testModes(TestMode.DEFAULT)
      .allowObsoleteLintChecks(false)
      .issueIds("MyIssueId")
      .run()
      .expectClean()
  }

  fun testNewerLintBroken() {
    // Tests what happens when the loaded lint rule was compiled with
    // a newer version of the lint apis than the current host, and
    // referencing some unknown APIs
    val root = Files.createTempDirectory("lintjar").toFile()

    lint()
      .files(
        *lintApiStubs,
        bytecode(
          "lint.jar",
          source(
            "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
            "test.pkg.MyIssueRegistry"
          ),
          0x70522285
        ),
        bytecode(
          "lint.jar",
          kotlin(
              """
                    package test.pkg
                    import com.android.tools.lint.client.api.*
                    import com.android.tools.lint.detector.api.*
                    import java.util.EnumSet

                    class MyIssueRegistry : IssueRegistry() {
                        override val issues: List<Issue> = listOf(issue)
                        override val api: Int = 10000
                        override val minApi: Int = 7
                        override val vendor: Vendor = Vendor(
                            vendorName = "Android Open Source Project: Lint Unit Tests",
                            contact = "/dev/null"
                        )
                    }

                    class MyDetector : Detector()
                    private val issue =
                        Issue.create(id = "_TestIssueId", briefDescription = "Desc", explanation = "Desc", implementation = Implementation(
                              MyDetector::class.java,
                              Scope.JAVA_FILE_SCOPE
                            )
                        )
                    """
            )
            .indented(),
          0x402ebf07,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJ2KM3AZcylmJyfq5eYl1KUn5miV5Kfn1Os
                l5OZV6KXnJOZCqQSCzKF+JzB7PjiktKkYu8SLlkujpLU4hK9gux0IUHfSs/i
                4tLUoNT0zOKSokrvEiUGLQYAcc+hwWwAAAA=
                """,
          """
                test/pkg/MyDetector.class:
                H4sIAAAAAAAA/5VRy04CMRQ9HWDQEeWhKL72agwF405j4iMmJIMLNWxYFabB
                BpiSaTGy41v8A1cmLgxx6UcZ74wkbtzYpCf3nHt7X/38ensHcIwdhlUrjeWj
                fo83J1fSyq7VURaM4bCrh1yEQaRVwK3WA8MHKrQ8mAdxMVL890WKwT1VobJn
                DKm9/VYOGbge0sgypO2DMgxl/49aJwxFv68t5eZNaUUgrCDNGT6mqEUWA2Vg
                fZKeVMxqZAV1ht3Z1POcipPc2bQymx45NXaR+Xh2nYITBx0xVP3/TEF1S81J
                w5ixvJU9ZWw0qfYt9X+pA8mQ91Uob8bDjozuRWdASsnXXTFoiUjFfC56d3oc
                deW1isnm7Ti0aihbyijynoehtsIqHRrU4dB64kPzxdsi3CDGEw5kDl6x8EKG
                gwqhm4gFbBLmfgKwCC/xbyW4ju3kVxmWyJdrI9XAcgMrhMjHUGigiFIbzGAV
                a+Q38AzKBu43siYapRICAAA=
                """,
          """
                test/pkg/MyIssueRegistry.class:
                H4sIAAAAAAAA/6VVW28bRRT+Zn1bu26yNikkTkPd1rS2m2ad9AZ1mpImFEzt
                BCXFAuVpY0/NxOvdaGdsUR5QfgU/APHIA0hErUBCUR/5UYizXoekdiwoPOyc
                OWfO/Xwz+8efv/4O4DY2GKYVl8rcb7fM2vOKlF2+xVtCKu95DIzBbLgd03Ka
                niuapnJdW5q2cJTZsAUnYu0Lc8goxBBdFo5QKwyhfKGeRATRBMKIMWTGxXqi
                YogzpKxGg0uZa3HVP87tM9zMF6pjkmhyxRvK9U7SKCdxDskEEjjPkG27ivSo
                BNsmPeE60lw72fsxJylZmzLYfMaQy1f3rJ5l2pbTMjd390itXAhEXSVss0p6
                5D+FdAIG3iJL4YeUDMao1gW8HYeGd6gHlBwDqyQxg4wvmyXLjnBW90USc4Ho
                XYb8Pze6zp2m68WQZZhfDTSzm/vcyW67Xa/Bs595rp/0/WyVDLOf0wiyT6nf
                UscVhjh1q2c6XdvWkWP45nSx28oTTqv83ySV6qDNe72OSYG551i2uc6fWV1b
                rVGfldf1h1SzvDb3ygEgriVwGdepEb1+TQzFcTMeqZ+aW0DR79oNhrD6StAA
                MtVxwCpT5cdoIsV0fmSiDA9HhMtvgLgV8nC16notc4+rXc8ShDLLcVxlBYjb
                cNUGdZ20opTIqg8GuhaVIK9aHwfBvj5oxfx4wI82g+HB/8w9dTy/GldW01IW
                ybROL0TvA/MXurasTaKvhc+VaNdcZPjh6CCf0Ka14NPpM3SiIaLZgSxyfDZ9
                dLCkldijyKvvo5qhbU0ZoYxWCn/x6rt1kuiJo4NMWI8Y0a2MEcvo6XBaK8VL
                Oh2HT44TxjmyS47anSe7KWOCDiZftzCMlJ/rEsPiv2jnMGjAcOMNGknIGgLe
                QlvRWLdFy7FU1+MMs1tdR4kOrzg9IcWuzVdPQEJAXnObpDRJV5dvdDu73Htq
                kQ75rboNy65bnvD5gTAR3PjHwmdmBo7rI26xSLckTFMLI+2/SMR9TJyG9/EJ
                0SgVuUQ07b9MfTo3oHTB6Oy0ToRopM9ViPsWcdoRGoovoBdfYuIlpg4xXUxd
                Nw5xsWjEDnGp+Bsuf5m+ylj6PSPKXiB/iPmf+9E/pbVIT7TvnX4LmIBO2cUx
                S7Isvd9XiOaQxDWiJuk8Ic1kEA83seADk+QlhPq5mMT5Z5HiL5j+6e8A0b4w
                dso4MjAOWrD4WnkMt+hvyEYcXvxxyKF+hkOGO2caXxo2jp9pfBf3SGvYeH64
                lMQZxqdL0FDtr49RI2qR9APSu7+DUAXlCpZpxQN/WangIT7cAZNYxaMdpCUW
                JNYkohIzEusStyRuS1zo7z+SMCVKEnMSdyQKEkWJuxL3/gKjcYhbRAgAAA==
                """,
          """
                test/pkg/MyIssueRegistryKt.class:
                H4sIAAAAAAAA/51UW08TQRT+TlvoxQqlKkJFvFVtVVxA8FY0wQJxtaKxplF5
                INPt2Axsd5vdKbFvxFf/hb9A8UGjiSE8+mv8BcaztRGiPhRmkzlnzn7n23PZ
                Od9/fvkGYAZ3CBktfW001+vGo7bp+y35VNaVr732Qx0FEVJrYkMYtnDqxuPq
                mrTYGiYMCcuSvp+tS91xyjYJE7l8yXIbhnBqnqtqhnZd2zds5WijJjV7up4h
                msroOBQIfSpQCJf34ZVEFLE4QogTYnMWw5S+Swjn8hVCvmeeKA4T4kW30RSO
                ch3CbO8xZP+4cTSDSMUxgCFCcvUZF7KDMGsxHCFEFqRvxXCMMN0TeaNpy4Z0
                tNDMHcVxwpE9vVnoYqMY7THTsuU2OdMThMEH85X51SWztLhaLj5+ssjMpU5b
                W1rZxqLTapSl5mxOYjyOMZwi9M91K3sxV9r9AYq28P3Cf1zzlSTO4GwCIzhH
                uHaAWkZxnjBgeVJoma3JV6Jla6K3uQP1ZU/IZe0pp96bZf9dOihLkbOsu167
                YPYEL8sN6SndLrzc8717DJbC+V879piKrm0zURCrWfr7Khf2c2GTuIhcAheQ
                J5wruV7dWJO66gnl+MzguL8r4hvLrl5u2TZf8KHSuquZzngktagJLdgWamyE
                efRQsEUJtB4oIba/VoE2yVptijC+vdmf2N5MhEZCZwdS25uZ0CQ933kT2XnX
                H2J7gJompP8aWVfXNeHE05ajVUOazobyVdWW87vB8aUsujWeOYMl5cjlVqMq
                vWeCMYRE2W15llxSwWG0y1H5hwFTPHwiQQbIBLOI5U0+9bO8HGSSGkVf53SL
                T2MsgxXZQuJ9x+d2FxvoMRxCsou8z5MkWLktpNNH08Pp4a8YeZHObOH0J2SJ
                wvwQDQ3+oM+49BGJD3/YBhDULc3+R3lPc3SFIA7cwBzLKUZd4YgmVhA2cdWE
                wTsmTX4xbeIaZlZAPmZxfQUhH1EfsV8QdIFtHAYAAA==
                """
        ),
        bytecode(
          "lint.jar",
          kotlin(
              """
                package test.pkg
                class Helper : com.android.tools.lint.detector.api.DeletedInterface {
                }
                """
            )
            .indented(),
          0x8bb07491,
          """
                    test/pkg/Helper.class:
                    H4sIAAAAAAAAAJ1Qy04bMRQ9nrxgSJvwDqUUlsACQ4TY8JCgCHWkAFKpsmHl
                    zBgwmdjR2GGdb+EPWCGxQBHLfhTieiibLpHlY59zr61zz9/Xp2cATaww1Jy0
                    jve71/yXTPsyq4Ax1G/FneCp0Nf8vHMrY1dBgWE7Nj0udJIZlXBnTGp5qrTj
                    iXTUYjIu+oofy5RoEmknsysRywpKDOU9pZU7YCisrrWrqGAsRBHjDEV3oyzD
                    ZOs/F7te6xpH//NT6UQinCAt6N0VyDjzUPQQkNbJNXggk6xLUrLFsDQahmHQ
                    CPI9GjZGw2awyY5KL/floO7fJU2GndZnZiIn4+8+N7qOhvhpEklJtpSWZ4Ne
                    R2Z/RCclZaplYpG2RaY8/yeGF2aQxfJEebLwe6Cd6sm2soqqh1obJ5wy2mIL
                    AWVE2fmBaVFohIvEeM6B0vojwgefAr4TlnMxwBJh9b0BE3Tz2o8cv2GZzn2q
                    faHa10sUItQi1CNMYuqDTUeYwSxdMXcJZjGPBpUsqhYLFmNv+k/OETwCAAA=
                    """
        ),
      )
      .testModes(TestMode.DEFAULT)
      .createProjects(root)

    val lintJar = File(root, "app/lint.jar")
    assertTrue(lintJar.exists())

    lint()
      .files(
        source( // instead of xml: not valid XML below
            "res/values/strings.xml",
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources/>
                """
          )
          .indented()
      )
      .clientFactory { createGlobalLintJarClient(lintJar) }
      .testModes(TestMode.DEFAULT)
      .allowObsoleteLintChecks(false)
      .issueIds("MyIssueId")
      .run()
      // Note how the "This affects the following lint checks"
      // list is empty below; that's because we're passing in
      // an issue registry which doesn't actually have any valid
      // issues for it. This won't be the case in a real issue registry.
      // Actually listing the issue id's is tested in a different
      // test below (search for "This affects".)
      .expectContains(
        """
                lint.jar: Warning: Requires newer lint; these checks will be skipped!

                Lint found an issue registry (test.pkg.MyIssueRegistry)
                which was compiled against a newer version of lint
                than this one. This is usually fine, but not in this
                case; some basic verification shows that the lint
                check jar references (for example) the following API
                which is not valid in the version of lint which is running:
                com.android.tools.lint.detector.api.DeletedInterface
                (Referenced from test/pkg/Helper.class)

                Therefore, this lint check library is not included
                in analysis. This affects the following lint checks:
                _TestIssueId

                To use this lint check, upgrade to a more recent version
                of lint.

                Version of Lint API this lint check is using is 10000.
                The Lint API version currently running is $CURRENT_API (${describeApi(CURRENT_API)}). [ObsoleteLintCustomCheck]
                0 errors, 1 warnings"""
      )

    // Also make sure we can handle issue registries without a manifest: b/280305856.
    // We'll just rewrite the above lint.jar file and filter out the manifest file.
    // This test would fail with the NPE from b/280305856 until the corresponding fix.
    val lintJarWithoutManifest = File(root, "app/lint2.jar")
    val zos = ZipOutputStream(FileOutputStream(lintJarWithoutManifest))
    JarFile(lintJar).use { jarFile ->
      for (entry in jarFile.entries()) {
        val name = entry.name
        if (name == "META-INF/MANIFEST.MF") {
          continue
        }
        zos.putNextEntry(ZipEntry(name))
        if (!entry.isDirectory) {
          jarFile.getInputStream(entry).use { stream -> zos.write(ByteStreams.toByteArray(stream)) }
        }
        zos.closeEntry()
      }
    }
    zos.close()

    lint()
      .files(
        source( // instead of xml: not valid XML below
            "res/values/strings.xml",
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources/>
                """
          )
          .indented()
      )
      .clientFactory { createGlobalLintJarClient(lintJarWithoutManifest) }
      .testModes(TestMode.DEFAULT)
      .allowObsoleteLintChecks(false)
      .issueIds("MyIssueId")
      .run()
      .expectContains("Lint found an issue registry (test.pkg.MyIssueRegistry)")

    val dir = File(root, "test2")
    lint()
      .files(
        source( // instead of xml: not valid XML below
            "res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources/>
            """
          )
          .indented()
      )
      .createProjects(dir)

    MainTest.checkDriver(
      "No issues found.",
      "",
      LintCliFlags.ERRNO_SUCCESS,
      arrayOf(
        "--lint-rule-jars",
        lintJar.path,
        dir.path,
        "--XskipJarVerification",
        "--disable",
        "_TestIssueId"
      ),
      null,
      null
    )
  }

  fun testFragment150Broken() {
    // Tests what happens when we have a lint check that (a) has the
    // same API level as the current API level, but (b) contains a
    // validation error, and (c) that is the known fragment library
    // error where we suggest a specific solution.

    val root = Files.createTempDirectory("lintjar-current").toFile()

    lint()
      .files(
        jar(
          "lint.jar",
          source(
            "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
            "androidx.fragment.lint.FragmentIssueRegistry"
          ),
          bytes(
            "androidx/fragment/lint/FragmentIssueRegistry.class",
            byteArrayOf(
              /*
              This class file was created using the following sources, and
              then afterwards identifying the byte which sets the API level
              and then dynamically referencing CURRENT_API instead such that
              this test continues to use the same API level whenever that version
              changes.
                  *lintApiStubs,
                  kotlin(
                      """
                      // Just a stub
                      package org.jetbrains.uast
                      open class UClass
                      """
                  ).indented(),
                  kotlin(
                      """
                      // Just a stub
                      package org.jetbrains.uast.kotlin
                      import org.jetbrains.kotlin.psi.KtClassOrObject
                      import org.jetbrains.uast.UClass
                      class KotlinUClass : UClass() {
                          val ktClass: KtClassOrObject
                              get() = TODO()
                      }
                      """
                  ).indented(),
                  kotlin(
                      """
                      // Just a stub
                      package org.jetbrains.kotlin.psi
                      class KtClassOrObject
                      """
                  ).indented(),
                  bytecode(
                      "lint.jar",
                      source(
                          "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
                          "androidx.fragment.lint.FragmentIssueRegistry"
                      ),
                      0x6be562c7
                  ),
                  bytecode(
                      "lint.jar",
                      kotlin(
                          """
                          package androidx.fragment.lint
                          import com.android.tools.lint.client.api.*
                          import com.android.tools.lint.detector.api.*
                          import java.util.EnumSet
                          import org.jetbrains.uast.kotlin.KotlinUClass
                          import org.jetbrains.uast.UClass

                          class FragmentIssueRegistry : IssueRegistry() {
                              override val issues: List<Issue> = emptyList()
                              //override val api: Int = $CURRENT_API
                              override val api: Int = 0x12345678
                              override val minApi: Int = 10
                              override val vendor: Vendor = Vendor(
                                  vendorName = "Android Open Source Project: Lint Unit Tests",
                                  contact = "/dev/null"
                              )
                              fun visitClass(node: UClass) {
                                  val ktClass = (node as? KotlinUClass)?.ktClass
                              }
                          }
                          """
                      ).indented(),

               */
              -54,
              -2,
              -70,
              -66,
              0,
              0,
              0,
              52,
              0,
              95,
              1,
              0,
              44,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              120,
              47,
              102,
              114,
              97,
              103,
              109,
              101,
              110,
              116,
              47,
              108,
              105,
              110,
              116,
              47,
              70,
              114,
              97,
              103,
              109,
              101,
              110,
              116,
              73,
              115,
              115,
              117,
              101,
              82,
              101,
              103,
              105,
              115,
              116,
              114,
              121,
              7,
              0,
              1,
              1,
              0,
              47,
              99,
              111,
              109,
              47,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              47,
              116,
              111,
              111,
              108,
              115,
              47,
              108,
              105,
              110,
              116,
              47,
              99,
              108,
              105,
              101,
              110,
              116,
              47,
              97,
              112,
              105,
              47,
              73,
              115,
              115,
              117,
              101,
              82,
              101,
              103,
              105,
              115,
              116,
              114,
              121,
              7,
              0,
              3,
              1,
              0,
              6,
              60,
              105,
              110,
              105,
              116,
              62,
              1,
              0,
              3,
              40,
              41,
              86,
              12,
              0,
              5,
              0,
              6,
              10,
              0,
              4,
              0,
              7,
              1,
              0,
              32,
              107,
              111,
              116,
              108,
              105,
              110,
              47,
              99,
              111,
              108,
              108,
              101,
              99,
              116,
              105,
              111,
              110,
              115,
              47,
              67,
              111,
              108,
              108,
              101,
              99,
              116,
              105,
              111,
              110,
              115,
              75,
              116,
              7,
              0,
              9,
              1,
              0,
              9,
              101,
              109,
              112,
              116,
              121,
              76,
              105,
              115,
              116,
              1,
              0,
              18,
              40,
              41,
              76,
              106,
              97,
              118,
              97,
              47,
              117,
              116,
              105,
              108,
              47,
              76,
              105,
              115,
              116,
              59,
              12,
              0,
              11,
              0,
              12,
              10,
              0,
              10,
              0,
              13,
              1,
              0,
              6,
              105,
              115,
              115,
              117,
              101,
              115,
              1,
              0,
              16,
              76,
              106,
              97,
              118,
              97,
              47,
              117,
              116,
              105,
              108,
              47,
              76,
              105,
              115,
              116,
              59,
              12,
              0,
              15,
              0,
              16,
              9,
              0,
              2,
              0,
              17,
              3,
              0,
              0,
              0,
              CURRENT_API.toByte(),
              1,
              0,
              3,
              97,
              112,
              105,
              1,
              0,
              1,
              73,
              12,
              0,
              20,
              0,
              21,
              9,
              0,
              2,
              0,
              22,
              1,
              0,
              6,
              109,
              105,
              110,
              65,
              112,
              105,
              12,
              0,
              24,
              0,
              21,
              9,
              0,
              2,
              0,
              25,
              1,
              0,
              40,
              99,
              111,
              109,
              47,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              47,
              116,
              111,
              111,
              108,
              115,
              47,
              108,
              105,
              110,
              116,
              47,
              99,
              108,
              105,
              101,
              110,
              116,
              47,
              97,
              112,
              105,
              47,
              86,
              101,
              110,
              100,
              111,
              114,
              7,
              0,
              27,
              1,
              0,
              44,
              65,
              110,
              100,
              114,
              111,
              105,
              100,
              32,
              79,
              112,
              101,
              110,
              32,
              83,
              111,
              117,
              114,
              99,
              101,
              32,
              80,
              114,
              111,
              106,
              101,
              99,
              116,
              58,
              32,
              76,
              105,
              110,
              116,
              32,
              85,
              110,
              105,
              116,
              32,
              84,
              101,
              115,
              116,
              115,
              8,
              0,
              29,
              1,
              0,
              9,
              47,
              100,
              101,
              118,
              47,
              110,
              117,
              108,
              108,
              8,
              0,
              31,
              1,
              0,
              122,
              40,
              76,
              106,
              97,
              118,
              97,
              47,
              108,
              97,
              110,
              103,
              47,
              83,
              116,
              114,
              105,
              110,
              103,
              59,
              76,
              106,
              97,
              118,
              97,
              47,
              108,
              97,
              110,
              103,
              47,
              83,
              116,
              114,
              105,
              110,
              103,
              59,
              76,
              106,
              97,
              118,
              97,
              47,
              108,
              97,
              110,
              103,
              47,
              83,
              116,
              114,
              105,
              110,
              103,
              59,
              76,
              106,
              97,
              118,
              97,
              47,
              108,
              97,
              110,
              103,
              47,
              83,
              116,
              114,
              105,
              110,
              103,
              59,
              73,
              76,
              107,
              111,
              116,
              108,
              105,
              110,
              47,
              106,
              118,
              109,
              47,
              105,
              110,
              116,
              101,
              114,
              110,
              97,
              108,
              47,
              68,
              101,
              102,
              97,
              117,
              108,
              116,
              67,
              111,
              110,
              115,
              116,
              114,
              117,
              99,
              116,
              111,
              114,
              77,
              97,
              114,
              107,
              101,
              114,
              59,
              41,
              86,
              12,
              0,
              5,
              0,
              33,
              10,
              0,
              28,
              0,
              34,
              1,
              0,
              6,
              118,
              101,
              110,
              100,
              111,
              114,
              1,
              0,
              42,
              76,
              99,
              111,
              109,
              47,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              47,
              116,
              111,
              111,
              108,
              115,
              47,
              108,
              105,
              110,
              116,
              47,
              99,
              108,
              105,
              101,
              110,
              116,
              47,
              97,
              112,
              105,
              47,
              86,
              101,
              110,
              100,
              111,
              114,
              59,
              12,
              0,
              36,
              0,
              37,
              9,
              0,
              2,
              0,
              38,
              1,
              0,
              4,
              116,
              104,
              105,
              115,
              1,
              0,
              46,
              76,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              120,
              47,
              102,
              114,
              97,
              103,
              109,
              101,
              110,
              116,
              47,
              108,
              105,
              110,
              116,
              47,
              70,
              114,
              97,
              103,
              109,
              101,
              110,
              116,
              73,
              115,
              115,
              117,
              101,
              82,
              101,
              103,
              105,
              115,
              116,
              114,
              121,
              59,
              1,
              0,
              9,
              103,
              101,
              116,
              73,
              115,
              115,
              117,
              101,
              115,
              1,
              0,
              63,
              40,
              41,
              76,
              106,
              97,
              118,
              97,
              47,
              117,
              116,
              105,
              108,
              47,
              76,
              105,
              115,
              116,
              60,
              76,
              99,
              111,
              109,
              47,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              47,
              116,
              111,
              111,
              108,
              115,
              47,
              108,
              105,
              110,
              116,
              47,
              100,
              101,
              116,
              101,
              99,
              116,
              111,
              114,
              47,
              97,
              112,
              105,
              47,
              73,
              115,
              115,
              117,
              101,
              59,
              62,
              59,
              1,
              0,
              35,
              76,
              111,
              114,
              103,
              47,
              106,
              101,
              116,
              98,
              114,
              97,
              105,
              110,
              115,
              47,
              97,
              110,
              110,
              111,
              116,
              97,
              116,
              105,
              111,
              110,
              115,
              47,
              78,
              111,
              116,
              78,
              117,
              108,
              108,
              59,
              1,
              0,
              6,
              103,
              101,
              116,
              65,
              112,
              105,
              1,
              0,
              3,
              40,
              41,
              73,
              1,
              0,
              9,
              103,
              101,
              116,
              77,
              105,
              110,
              65,
              112,
              105,
              1,
              0,
              9,
              103,
              101,
              116,
              86,
              101,
              110,
              100,
              111,
              114,
              1,
              0,
              44,
              40,
              41,
              76,
              99,
              111,
              109,
              47,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              47,
              116,
              111,
              111,
              108,
              115,
              47,
              108,
              105,
              110,
              116,
              47,
              99,
              108,
              105,
              101,
              110,
              116,
              47,
              97,
              112,
              105,
              47,
              86,
              101,
              110,
              100,
              111,
              114,
              59,
              1,
              0,
              10,
              118,
              105,
              115,
              105,
              116,
              67,
              108,
              97,
              115,
              115,
              1,
              0,
              30,
              40,
              76,
              111,
              114,
              103,
              47,
              106,
              101,
              116,
              98,
              114,
              97,
              105,
              110,
              115,
              47,
              117,
              97,
              115,
              116,
              47,
              85,
              67,
              108,
              97,
              115,
              115,
              59,
              41,
              86,
              1,
              0,
              4,
              110,
              111,
              100,
              101,
              8,
              0,
              52,
              1,
              0,
              30,
              107,
              111,
              116,
              108,
              105,
              110,
              47,
              106,
              118,
              109,
              47,
              105,
              110,
              116,
              101,
              114,
              110,
              97,
              108,
              47,
              73,
              110,
              116,
              114,
              105,
              110,
              115,
              105,
              99,
              115,
              7,
              0,
              54,
              1,
              0,
              21,
              99,
              104,
              101,
              99,
              107,
              78,
              111,
              116,
              78,
              117,
              108,
              108,
              80,
              97,
              114,
              97,
              109,
              101,
              116,
              101,
              114,
              1,
              0,
              39,
              40,
              76,
              106,
              97,
              118,
              97,
              47,
              108,
              97,
              110,
              103,
              47,
              79,
              98,
              106,
              101,
              99,
              116,
              59,
              76,
              106,
              97,
              118,
              97,
              47,
              108,
              97,
              110,
              103,
              47,
              83,
              116,
              114,
              105,
              110,
              103,
              59,
              41,
              86,
              12,
              0,
              56,
              0,
              57,
              10,
              0,
              55,
              0,
              58,
              1,
              0,
              38,
              111,
              114,
              103,
              47,
              106,
              101,
              116,
              98,
              114,
              97,
              105,
              110,
              115,
              47,
              117,
              97,
              115,
              116,
              47,
              107,
              111,
              116,
              108,
              105,
              110,
              47,
              75,
              111,
              116,
              108,
              105,
              110,
              85,
              67,
              108,
              97,
              115,
              115,
              7,
              0,
              60,
              1,
              0,
              10,
              103,
              101,
              116,
              75,
              116,
              67,
              108,
              97,
              115,
              115,
              1,
              0,
              44,
              40,
              41,
              76,
              111,
              114,
              103,
              47,
              106,
              101,
              116,
              98,
              114,
              97,
              105,
              110,
              115,
              47,
              107,
              111,
              116,
              108,
              105,
              110,
              47,
              112,
              115,
              105,
              47,
              75,
              116,
              67,
              108,
              97,
              115,
              115,
              79,
              114,
              79,
              98,
              106,
              101,
              99,
              116,
              59,
              12,
              0,
              62,
              0,
              63,
              10,
              0,
              61,
              0,
              64,
              1,
              0,
              7,
              107,
              116,
              67,
              108,
              97,
              115,
              115,
              1,
              0,
              42,
              76,
              111,
              114,
              103,
              47,
              106,
              101,
              116,
              98,
              114,
              97,
              105,
              110,
              115,
              47,
              107,
              111,
              116,
              108,
              105,
              110,
              47,
              112,
              115,
              105,
              47,
              75,
              116,
              67,
              108,
              97,
              115,
              115,
              79,
              114,
              79,
              98,
              106,
              101,
              99,
              116,
              59,
              1,
              0,
              27,
              76,
              111,
              114,
              103,
              47,
              106,
              101,
              116,
              98,
              114,
              97,
              105,
              110,
              115,
              47,
              117,
              97,
              115,
              116,
              47,
              85,
              67,
              108,
              97,
              115,
              115,
              59,
              1,
              0,
              40,
              111,
              114,
              103,
              47,
              106,
              101,
              116,
              98,
              114,
              97,
              105,
              110,
              115,
              47,
              107,
              111,
              116,
              108,
              105,
              110,
              47,
              112,
              115,
              105,
              47,
              75,
              116,
              67,
              108,
              97,
              115,
              115,
              79,
              114,
              79,
              98,
              106,
              101,
              99,
              116,
              7,
              0,
              69,
              1,
              0,
              61,
              76,
              106,
              97,
              118,
              97,
              47,
              117,
              116,
              105,
              108,
              47,
              76,
              105,
              115,
              116,
              60,
              76,
              99,
              111,
              109,
              47,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              47,
              116,
              111,
              111,
              108,
              115,
              47,
              108,
              105,
              110,
              116,
              47,
              100,
              101,
              116,
              101,
              99,
              116,
              111,
              114,
              47,
              97,
              112,
              105,
              47,
              73,
              115,
              115,
              117,
              101,
              59,
              62,
              59,
              1,
              0,
              17,
              76,
              107,
              111,
              116,
              108,
              105,
              110,
              47,
              77,
              101,
              116,
              97,
              100,
              97,
              116,
              97,
              59,
              1,
              0,
              2,
              109,
              118,
              3,
              0,
              0,
              0,
              1,
              3,
              0,
              0,
              0,
              7,
              1,
              0,
              1,
              107,
              1,
              0,
              2,
              120,
              105,
              3,
              0,
              0,
              0,
              48,
              1,
              0,
              2,
              100,
              49,
              1,
              0,
              -65,
              -64,
              -128,
              50,
              10,
              2,
              24,
              2,
              10,
              2,
              24,
              2,
              10,
              2,
              8,
              2,
              10,
              2,
              16,
              8,
              10,
              2,
              8,
              3,
              10,
              2,
              16,
              32,
              10,
              2,
              24,
              2,
              10,
              2,
              8,
              5,
              10,
              2,
              24,
              2,
              10,
              2,
              8,
              3,
              10,
              2,
              16,
              2,
              10,
              -64,
              -128,
              10,
              2,
              24,
              2,
              24,
              -64,
              -128,
              50,
              2,
              48,
              1,
              66,
              5,
              -62,
              -94,
              6,
              2,
              16,
              2,
              74,
              14,
              16,
              18,
              26,
              2,
              48,
              19,
              50,
              6,
              16,
              20,
              26,
              2,
              48,
              21,
              82,
              20,
              16,
              3,
              26,
              2,
              48,
              4,
              88,
              -62,
              -106,
              68,
              -62,
              -94,
              6,
              8,
              10,
              -64,
              -128,
              26,
              4,
              8,
              5,
              16,
              6,
              82,
              26,
              16,
              7,
              26,
              8,
              18,
              4,
              18,
              2,
              48,
              9,
              48,
              8,
              88,
              -62,
              -106,
              4,
              -62,
              -94,
              6,
              8,
              10,
              -64,
              -128,
              26,
              4,
              8,
              10,
              16,
              11,
              82,
              20,
              16,
              12,
              26,
              2,
              48,
              4,
              88,
              -62,
              -106,
              68,
              -62,
              -94,
              6,
              8,
              10,
              -64,
              -128,
              26,
              4,
              8,
              13,
              16,
              6,
              82,
              20,
              16,
              14,
              26,
              2,
              48,
              15,
              88,
              -62,
              -106,
              4,
              -62,
              -94,
              6,
              8,
              10,
              -64,
              -128,
              26,
              4,
              8,
              16,
              16,
              17,
              1,
              0,
              2,
              100,
              50,
              1,
              0,
              49,
              76,
              99,
              111,
              109,
              47,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              47,
              116,
              111,
              111,
              108,
              115,
              47,
              108,
              105,
              110,
              116,
              47,
              99,
              108,
              105,
              101,
              110,
              116,
              47,
              97,
              112,
              105,
              47,
              73,
              115,
              115,
              117,
              101,
              82,
              101,
              103,
              105,
              115,
              116,
              114,
              121,
              59,
              1,
              0,
              0,
              1,
              0,
              43,
              76,
              99,
              111,
              109,
              47,
              97,
              110,
              100,
              114,
              111,
              105,
              100,
              47,
              116,
              111,
              111,
              108,
              115,
              47,
              108,
              105,
              110,
              116,
              47,
              100,
              101,
              116,
              101,
              99,
              116,
              111,
              114,
              47,
              97,
              112,
              105,
              47,
              73,
              115,
              115,
              117,
              101,
              59,
              1,
              0,
              24,
              70,
              114,
              97,
              103,
              109,
              101,
              110,
              116,
              73,
              115,
              115,
              117,
              101,
              82,
              101,
              103,
              105,
              115,
              116,
              114,
              121,
              46,
              107,
              116,
              1,
              0,
              9,
              83,
              105,
              103,
              110,
              97,
              116,
              117,
              114,
              101,
              1,
              0,
              27,
              82,
              117,
              110,
              116,
              105,
              109,
              101,
              73,
              110,
              118,
              105,
              115,
              105,
              98,
              108,
              101,
              65,
              110,
              110,
              111,
              116,
              97,
              116,
              105,
              111,
              110,
              115,
              1,
              0,
              4,
              67,
              111,
              100,
              101,
              1,
              0,
              15,
              76,
              105,
              110,
              101,
              78,
              117,
              109,
              98,
              101,
              114,
              84,
              97,
              98,
              108,
              101,
              1,
              0,
              18,
              76,
              111,
              99,
              97,
              108,
              86,
              97,
              114,
              105,
              97,
              98,
              108,
              101,
              84,
              97,
              98,
              108,
              101,
              1,
              0,
              13,
              83,
              116,
              97,
              99,
              107,
              77,
              97,
              112,
              84,
              97,
              98,
              108,
              101,
              1,
              0,
              36,
              82,
              117,
              110,
              116,
              105,
              109,
              101,
              73,
              110,
              118,
              105,
              115,
              105,
              98,
              108,
              101,
              80,
              97,
              114,
              97,
              109,
              101,
              116,
              101,
              114,
              65,
              110,
              110,
              111,
              116,
              97,
              116,
              105,
              111,
              110,
              115,
              1,
              0,
              10,
              83,
              111,
              117,
              114,
              99,
              101,
              70,
              105,
              108,
              101,
              1,
              0,
              25,
              82,
              117,
              110,
              116,
              105,
              109,
              101,
              86,
              105,
              115,
              105,
              98,
              108,
              101,
              65,
              110,
              110,
              111,
              116,
              97,
              116,
              105,
              111,
              110,
              115,
              0,
              49,
              0,
              2,
              0,
              4,
              0,
              0,
              0,
              4,
              0,
              18,
              0,
              15,
              0,
              16,
              0,
              2,
              0,
              86,
              0,
              0,
              0,
              2,
              0,
              71,
              0,
              87,
              0,
              0,
              0,
              6,
              0,
              1,
              0,
              44,
              0,
              0,
              0,
              18,
              0,
              20,
              0,
              21,
              0,
              0,
              0,
              18,
              0,
              24,
              0,
              21,
              0,
              0,
              0,
              18,
              0,
              36,
              0,
              37,
              0,
              1,
              0,
              87,
              0,
              0,
              0,
              6,
              0,
              1,
              0,
              44,
              0,
              0,
              0,
              6,
              0,
              1,
              0,
              5,
              0,
              6,
              0,
              1,
              0,
              88,
              0,
              0,
              0,
              122,
              0,
              9,
              0,
              1,
              0,
              0,
              0,
              44,
              42,
              -73,
              0,
              8,
              42,
              -72,
              0,
              14,
              -75,
              0,
              18,
              42,
              18,
              19,
              -75,
              0,
              23,
              42,
              16,
              10,
              -75,
              0,
              26,
              42,
              -69,
              0,
              28,
              89,
              18,
              30,
              1,
              1,
              18,
              32,
              16,
              6,
              1,
              -73,
              0,
              35,
              -75,
              0,
              39,
              -79,
              0,
              0,
              0,
              2,
              0,
              89,
              0,
              0,
              0,
              42,
              0,
              10,
              0,
              0,
              0,
              8,
              0,
              4,
              0,
              9,
              0,
              11,
              0,
              11,
              0,
              17,
              0,
              12,
              0,
              23,
              0,
              13,
              0,
              28,
              0,
              14,
              0,
              30,
              0,
              13,
              0,
              32,
              0,
              15,
              0,
              34,
              0,
              13,
              0,
              43,
              0,
              8,
              0,
              90,
              0,
              0,
              0,
              12,
              0,
              1,
              0,
              0,
              0,
              44,
              0,
              40,
              0,
              41,
              0,
              0,
              0,
              1,
              0,
              42,
              0,
              12,
              0,
              3,
              0,
              88,
              0,
              0,
              0,
              47,
              0,
              1,
              0,
              1,
              0,
              0,
              0,
              5,
              42,
              -76,
              0,
              18,
              -80,
              0,
              0,
              0,
              2,
              0,
              89,
              0,
              0,
              0,
              6,
              0,
              1,
              0,
              0,
              0,
              9,
              0,
              90,
              0,
              0,
              0,
              12,
              0,
              1,
              0,
              0,
              0,
              5,
              0,
              40,
              0,
              41,
              0,
              0,
              0,
              86,
              0,
              0,
              0,
              2,
              0,
              43,
              0,
              87,
              0,
              0,
              0,
              6,
              0,
              1,
              0,
              44,
              0,
              0,
              0,
              1,
              0,
              45,
              0,
              46,
              0,
              1,
              0,
              88,
              0,
              0,
              0,
              47,
              0,
              1,
              0,
              1,
              0,
              0,
              0,
              5,
              42,
              -76,
              0,
              23,
              -84,
              0,
              0,
              0,
              2,
              0,
              89,
              0,
              0,
              0,
              6,
              0,
              1,
              0,
              0,
              0,
              11,
              0,
              90,
              0,
              0,
              0,
              12,
              0,
              1,
              0,
              0,
              0,
              5,
              0,
              40,
              0,
              41,
              0,
              0,
              0,
              1,
              0,
              47,
              0,
              46,
              0,
              1,
              0,
              88,
              0,
              0,
              0,
              47,
              0,
              1,
              0,
              1,
              0,
              0,
              0,
              5,
              42,
              -76,
              0,
              26,
              -84,
              0,
              0,
              0,
              2,
              0,
              89,
              0,
              0,
              0,
              6,
              0,
              1,
              0,
              0,
              0,
              12,
              0,
              90,
              0,
              0,
              0,
              12,
              0,
              1,
              0,
              0,
              0,
              5,
              0,
              40,
              0,
              41,
              0,
              0,
              0,
              1,
              0,
              48,
              0,
              49,
              0,
              2,
              0,
              88,
              0,
              0,
              0,
              47,
              0,
              1,
              0,
              1,
              0,
              0,
              0,
              5,
              42,
              -76,
              0,
              39,
              -80,
              0,
              0,
              0,
              2,
              0,
              89,
              0,
              0,
              0,
              6,
              0,
              1,
              0,
              0,
              0,
              13,
              0,
              90,
              0,
              0,
              0,
              12,
              0,
              1,
              0,
              0,
              0,
              5,
              0,
              40,
              0,
              41,
              0,
              0,
              0,
              87,
              0,
              0,
              0,
              6,
              0,
              1,
              0,
              44,
              0,
              0,
              0,
              17,
              0,
              50,
              0,
              51,
              0,
              2,
              0,
              88,
              0,
              0,
              0,
              122,
              0,
              2,
              0,
              3,
              0,
              0,
              0,
              35,
              43,
              18,
              53,
              -72,
              0,
              59,
              43,
              -63,
              0,
              61,
              -103,
              0,
              10,
              43,
              -64,
              0,
              61,
              -89,
              0,
              4,
              1,
              89,
              -58,
              0,
              9,
              -74,
              0,
              65,
              -89,
              0,
              5,
              87,
              1,
              77,
              -79,
              0,
              0,
              0,
              3,
              0,
              91,
              0,
              0,
              0,
              15,
              0,
              4,
              20,
              64,
              7,
              0,
              61,
              73,
              7,
              0,
              61,
              65,
              7,
              0,
              70,
              0,
              89,
              0,
              0,
              0,
              10,
              0,
              2,
              0,
              6,
              0,
              18,
              0,
              34,
              0,
              19,
              0,
              90,
              0,
              0,
              0,
              32,
              0,
              3,
              0,
              34,
              0,
              1,
              0,
              66,
              0,
              67,
              0,
              2,
              0,
              0,
              0,
              35,
              0,
              40,
              0,
              41,
              0,
              0,
              0,
              0,
              0,
              35,
              0,
              52,
              0,
              68,
              0,
              1,
              0,
              92,
              0,
              0,
              0,
              7,
              1,
              0,
              1,
              0,
              44,
              0,
              0,
              0,
              2,
              0,
              93,
              0,
              0,
              0,
              2,
              0,
              85,
              0,
              94,
              0,
              0,
              0,
              109,
              0,
              1,
              0,
              72,
              0,
              5,
              0,
              73,
              91,
              0,
              3,
              73,
              0,
              74,
              73,
              0,
              75,
              73,
              0,
              74,
              0,
              76,
              73,
              0,
              74,
              0,
              77,
              73,
              0,
              78,
              0,
              79,
              91,
              0,
              1,
              115,
              0,
              80,
              0,
              81,
              91,
              0,
              22,
              115,
              0,
              41,
              115,
              0,
              82,
              115,
              0,
              6,
              115,
              0,
              20,
              115,
              0,
              83,
              115,
              0,
              45,
              115,
              0,
              46,
              115,
              0,
              15,
              115,
              0,
              83,
              115,
              0,
              84,
              115,
              0,
              42,
              115,
              0,
              12,
              115,
              0,
              24,
              115,
              0,
              47,
              115,
              0,
              36,
              115,
              0,
              37,
              115,
              0,
              48,
              115,
              0,
              49,
              115,
              0,
              50,
              115,
              0,
              83,
              115,
              0,
              52,
              115,
              0,
              68,
            )
          )
        )
      )
      .testModes(TestMode.DEFAULT)
      .createProjects(root)

    val lintJar = File(root, "app/lint.jar")
    assertTrue(lintJar.exists())

    lint()
      .files(
        source( // instead of xml: not valid XML below
            "res/values/strings.xml",
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources/>
                """
          )
          .indented()
      )
      .clientFactory { createGlobalLintJarClient(lintJar, LintClient.CLIENT_GRADLE) }
      .testModes(TestMode.DEFAULT)
      .allowObsoleteLintChecks(false)
      .issueIds("MyIssueId")
      .run()
      // Note how the "This affects the following lint checks"
      // list is empty below; that's because we're passing in
      // an issue registry which doesn't actually have any valid
      // issues for it. This won't be the case in a real issue registry.
      // Actually listing the issue id's is tested in a different
      // test below (search for "This affects".)
      .expectContains(
        """
                Lint found an issue registry (androidx.fragment.lint.FragmentIssueRegistry)
                which contains some references to invalid API:
                org.jetbrains.uast.kotlin.KotlinUClass: org.jetbrains.kotlin.psi.KtClassOrObject getKtClass()
                (Referenced from androidx/fragment/lint/FragmentIssueRegistry.class)

                Therefore, this lint check library is not included
                in analysis. This affects the following lint checks:


                This is a known bug which is already fixed in
                `androidx.fragment:fragment:1.5.1` and later; update
                to that version. If you are not directly depending
                on this library but picking it up via a transitive
                dependency, explicitly add
                implementation 'androidx.fragment:fragment:1.5.1'
                (or later) to your build.gradle dependency block. [ObsoleteLintCustomCheck]
                0 errors, 1 warnings
                """
      )
  }

  fun testIncompatibleRegistry() {
    val root = Files.createTempDirectory("lintjar").toFile()

    lint()
      .files(
        *lintApiStubs,
        bytecode(
          "lint.jar",
          source(
            "META-INF/services/com.android.tools.lint.client.api.IssueRegistry",
            "test.pkg.MyIssueRegistry"
          ),
          0x70522285
        ),
        bytecode(
          "lint.jar",
          kotlin(
              """
                    package test.pkg
                    import com.android.tools.lint.detector.api.*
                    class Incompatible1 : DeletedInterface {
                    }
                    """
            )
            .indented(),
          0x3a35c31e,
          """
                    test/pkg/Incompatible1.class:
                    H4sIAAAAAAAAAJ1QTW8TMRSct5uPsgSaFkhToJQj7aFuIsSFD6kFIa20gFRQ
                    Lj05u6a42djR2sk5v4V/wAmJA4p67I+qeN7CAYkTWnnsmXn2vnmXVz9+Ahji
                    MaHnlfNiNjkTqcntdCa9Hpdq0AYRuudyIUUpzZn4MD5XuW8jJjzlMiFNUVld
                    CG9t6USpjReF8lxiKyFnWrxRJdMiNV5Vn2Wu2mgSWi+00f4VIX6yN+qgjbUE
                    DdwgNPwX7Qj97N/NPCdsZBPr+TfinfKykF6yFk0XMcegAI0AEWvjWkMA7pUm
                    LBUDws5qmSRRP6rXatlfLYfRIR03L762om64VwwJz7L/icaddP9q92DiOdJr
                    WyjCeqaNej+fjlX1SbJJ2MxsLsuRrHTgv8Xko51XuXqrA9k+mRuvp2qkXXjv
                    yBjr+WlrHAaIeGI8yZCbPx4h4wNmouZAc/87km9hGHjI2KrFGDuMnesC3ORT
                    8B/VeB+7vL9k7xZ7t08Rp1hPORI2sPmH3UlxF/f4iN4pyGELfbYcOg7bDmu/
                    ANjxTV9RAgAA
                    """,
        ),
        bytecode(
          "lint.jar",
          kotlin(
              """
                    package test.pkg
                    import com.android.tools.lint.client.api.*
                    import com.android.tools.lint.detector.api.*
                    import java.util.EnumSet

                    class MyDetector : Detector(), OtherFileScanner {
                        override fun getApplicableFiles(): EnumSet<Scope> {
                            return EnumSet.of(Scope.OTHER)
                        }

                        override fun run(context: Context) {
                            context.report(ISSUE, Location.create(context.file), "My message")
                        }

                        companion object {
                            @JvmField
                            val ISSUE = Issue.create(
                                id = "MyIssueId",
                                briefDescription = "My Summary",
                                explanation = "My full explanation.",
                                category = Category.LINT,
                                priority = 10,
                                severity = Severity.WARNING,
                                implementation = Implementation(MyDetector::class.java, EnumSet.of(Scope.OTHER))
                            )
                        }
                    }
                    class MyIssueRegistry : IssueRegistry() {
                        override val issues: List<Issue> = listOf(MyDetector.ISSUE)
                        override val api: Int = 9
                        override val minApi: Int = 7
                        override val vendor: Vendor = Vendor(
                            vendorName = "Android Open Source Project: Lint Unit Tests",
                            contact = "/dev/null"
                        )
                    }
                    """
            )
            .indented(),
          0xe2369de6,
          """
                test/pkg/MyDetector.class:
                H4sIAAAAAAAAAJ1W6VLbVhT+rgDLFg5xTEIgK00gMSGxDFmaxjQtIRBEDaSY
                kFLapsIWRqDFlWQKXem+L8/QJ2hIJ2GamQ7Tn32FvkZ/d3qubHZPR854fJej
                e8757lnvX//+/geAXvzM0OxpricXFwvy6MptzdNynu2IYAwXc7Ypq1besfW8
                7Nm24cqGbnlyvnJIVou6vM1Rx3AlCMe4N685Q7qhZXOqZWnE2cAQL2hef7Fo
                6Dl11tD4V5fhSKIrs6AuqXLJ0w150CqZWc1LM9yqRu/LBFGezdlFLX2ThJzN
                2E5BXtC8WUfVLZc4LdtTPd2m9ZjtjZUMg051BRYqopGhYXxyeHCCobsGMFEc
                QFMEURxkaPKvZahWwb+WiEMMh/ZdVUQzg2DPMXQmMrs50tUsFsURtEg4jKMM
                RyvEpD2X8PUnfchdYbQxnFq0PYIpLyyZMqHVHEs1ZMXyHDKQnnNFHGc4npvX
                cosVCw0uFx3NdcloU6pR0hjO7wQ0PrtAl03voGS5qEK6ayqKkzgl4QROM9R7
                8zr3dqZKIJIL6pySxZBKBDLpgE2wlz3SwCDmypswOkn6Tth3VUc1ic2J4nwZ
                RoJ8p2Sz9waD+k5x3RL33QV0RyDgYsB0ydg5P8ZEJBkiA7ZZVC3aMlwPpHWT
                vWOLkyCk0BOBjF6CXoOJRFwh089RplHYlV2k2zLPPBJ5Dc9HcBXXGa49GywR
                NxhCOUdTPZJ/M7FbQVdNlyU8fXhRQho3GaTRlXaTQk4taGG8TNAdrWg7Xkde
                m1NLhsfwd22BUoOzawO9P+qD8RNlSF9OK/vziGfNLQxI5JjbDJdquiY5o0+3
                dI8sWJfggu5gWEI9FJ53O7J+ZMkc0jUjTwzhvpxRYTldLTV3xCAVqU0ho5qn
                5lVPJZpgLtVRk2F8qOeDQLRZnwY+UMNgi0TK9zDMbayek4RWYfsf3l5vrEpC
                rDz5u9aN1fbGXiHF6C/cYI23Gv78JSTE6kaaYvXHwvH6uJAKpRqGW0ZiMfGY
                kAr3hmIRmqXhFq6N8iQZyHY7atC1QAx7OxsxNldtYGA4+b8WFfEWQ08iU60e
                3y5HOnnW9ZwS5xtVnUXSVnbr2xIeQOVVYZQXplzAHuaHuAhK1qvBU2JPGSpE
                MId5qmujK/5nJR/GQjllsyXTVJ2VMAyGw7Sfoyrcri0XKb79dEmGYQUsoQNU
                Ugq2syKiSAUso4xNBnXoJieBdeBG8A68gDqz2pLm6B7pXKK+cr9/YkwZuxNU
                7SYzqV3GSgTv4j2G3kBGNouGZmqWV+kZH+xpsAOG6rrpKiFWDoWPJHyIjxku
                P4NDRXzC8E+iShULQqnJG0ptVgwWnrssF7DhbPb0Pnwm4VN8znBgOzmTixQs
                xydKlqebmmIt6a5OL9X+7VcjBeOAnacEOkg1XBsrmbOaM8lfs/S65V3BmFId
                ne8rxEhWL1Dwlxxad+yVu/VC2aUgqvCy4nudv4+lrF1ycv5rmaGtImJqHzD0
                UB2op5oTormNv1ho/RUVYIn2r/gziE4Fo0IP0cxpnEdEmIr117S7QTOVLsTW
                ENtAfB2t0/Fj62j/lVd2fFPhA53/lsZo+Syewxmav/PPRHZJpyc4OojKZU+R
                HoHmU93xc+vo6l7DpTVc7v4NLzzGS/F+FguzdQw+3NLEkYfAH5EHfG0tZe6K
                Nr7qxBBp+J7WIttSSE2PRq7wMpgPsOHCE4zslltGGNpxi4aK3DAyWwJMNPqa
                rj7Fg2n2BLOPkF+DHl+Mm3F7DaWYtIb3n+LD6biwZa8nWH2MLx7h0kMfDVd4
                jozSiENoQpyM1UzjYfodwVG6UhuNJ9CKJH2vww8+PEbFXSBXteFHH/SX+Inm
                14g+RjDHZ1Cn4K6CVxVMILu5m1RwD1O0xP0ZMJeOT88g6uKMi9ddzLgYdiG6
                eMNFk4uzLt500eliyEXqP03yDqIoDgAA
                """,
          """
                test/pkg/MyDetector＄Companion.class:
                H4sIAAAAAAAAAJVSTU8UQRB93bNfjIsM4AegiB+ooAkNnDQYEl00mWTBBHRj
                wsH07rbY7Ew3me4l8bYn/R/+A08mHsyGoz/KWDMsciEmXqq63qtX1VXdv37/
                +AlgHUsM8145L456B2L705byquNtttiw6ZE02poqGEN0KI+lSKQ5EK/bh5RR
                RcBQeaaN9psMwdJyq44yKiFKqDKU/EftGBaa/6y8wbC21OxZn2gjDo9ToY1X
                mZGJ2FIfZD/xDWucz/q5altmPZVtLLdC8LzD9GLnnHyfFizDyv9VY5g8E2wr
                L7vSS8J4ehzQalhuSrnhhLULDLmhwVmPoO4aw+ZwEIV8hoc8Gg5CXuN5UDv5
                HMwMB+t8lb2o1vjJ1wqP+G4UBXN8tfSk8u7kSynHwuEgr7LOqBfK8d7e25cM
                j5sdmwppupnVXeGtTZyg+3nRHa1PyCMtYuf6iq46fcF+q7jFMPZ3yQzj59xK
                z9PjNGxXMUw0tVE7/bStsjeynRAy1bQdmbRkpvN4BNZjY1TWSKRzip403LP9
                rKNe6Zyb3e0br1PV0k5T8nNjrJeemjqs0TOV8o2R5/nPoBnvUiTI08AoP/qO
                2reCvke2UoATWCRbP03AGEIgotXg0kj8lDwfiesXi6+dJpyKi9M4LhMf4D5F
                YVFgAbcxiweF/g4ekm8QPkG50T6CGJMxpmJM48pZdDWmutfpiJl9MEfiuX2U
                HUKHGw4Vh5sO838A/VBd0VIDAAA=
                """,
          """
                test/pkg/MyIssueRegistry.class:
                H4sIAAAAAAAAAJ1UXU8bVxA9d/3tGFgMIWCS4IQ0MU6aBfodk6QEknYbAxUk
                qBVPC964C+tdtPfaEn3iV/QHVH3sQ1sVpWqlCuUxP6rqWXspYINE+rB37p2Z
                M3PuzOx9+8+ffwOYxYrAqLKlMnZ36sbSnill0161645UwV4KQsDY8huG5dUC
                36kZyvddabiOp4wt17EprF3H6ALFBJJOqJICenXballGUzmuUaW9IvCwSzVX
                PSdDzVb2lvKD4xyVR8RPVv2gbmzbajOwHE8S6fnKUo7P/bKvlpuuS69M3VZm
                RCJfmuql8bhH+W5EckggmYGGnEBcfecwT6F6XiWZL0awgDBZHFKbDw+x0pSZ
                g47BMMwwDQ3HaxtC8kvtfQ4jHfMozS3bq/mBQPk8oid6st72jQqxHgHv8c4X
                huZQwHiY+jpTzzmeox61Ka/nUMSNLOK4KTB04saLUZlSuCWQMNfWXj4VuPsO
                Rc3hNu5k8B5KAsUdX9GP0+e69Gt3d+F4/1ylUCYvl+VdeSVwq9Rppmt5dWNl
                c5tulZ6m53AP72dxF/cFShetQwrTLNx8x7O4smt7xTW/GWzZxa8DP8zzoFgl
                sPiSBSq+YDVkGrMsOy/YMjwOYxofCnx/kt+aChyvXvl/GrMaVWa71TCY2A48
                yzUW7VdW01ULLI0KmmFdl6xgxw4qnXZ9nMUMPhEYPAIv2cqqWcrigGiNVoxP
                gQiXeLho1G22dQgX/s5ih6rajMBPh/ulrDaqdb40Pz1NGaMsRrrEkW30cH9W
                mxZPEm9+TGq6tjqsxwradPybNz8sUpPOHu4X4umEnlwt6KlCOh/Pa9OZ6TTN
                8WNzVr9EXK4X10fcsN5Pw8BphK4PhlzZgZkLjHr3TwqBvuNBvr+j2Mg1p+5Z
                qhnYAuOrTU85Ddv0Wo50Nl17/vjt4Suw4NfoNMBxsJebjU07eGHRh+9P1d+y
                3HUrcMJzpMx2puiZEx7GosDrPWHZN42/GluDfPji8PRl2COkYFImyThNmQ+f
                kbYciSR/XtpO+iQoM8iyqV/xZPAkQm35d/T90g75PHIGrqHKNddxQD8GKDtp
                L50KybthiGt3wMs/dwW8fkZAgStngse6wRNngq+SpdYDnui+SvEM8Okr8DWL
                WOyxPqGfUX6NyfJvmPoDxgH6ynrmAJfLeuoAY+W/MPNt/gMh8h/pSfEanx5g
                4tf/Ut5mAJBZyC5Ofv28+DD5F5hkgmkmcROlU5SMiJKGpfb6BZYpt2n7jHQf
                bCBmomJizsRDPDo6PTbxOea5xZMNCIkFLG4gLzEg8VTihoQu8UyGmiGJRHt/
                RyIjkZUYkbgiUZAYl7gqce1fPTVL2woIAAA=
                """,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGIWIGYCYgYuYy7F5PxcvcS8lKL8zBS9kvz8
                nGK9nMy8Er3knMxUIJVYkCnE5wxmxxeXlCYVe5coMWgxAAANsEImTQAAAA==
                """
        )
      )
      .testModes(TestMode.DEFAULT)
      .createProjects(root)

    val lintJar = File(root, "app/lint.jar")
    assertTrue(lintJar.exists())

    lint()
      .files(
        source( // instead of xml: not valid XML below
            "res/values/strings.xml",
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name">LibraryProject</string>
                <<<<<<< HEAD
                    <string name="string1">String 1</string>
                =======
                    <string name="string2">String 2</string>
                >>>>>>> branch-a
                    <string name="string3">String 3</string>

                </resources>
                """
          )
          .indented(),
        // Reference <MyIssueId> from a lint.xml file; this would normally result
        // in an UnknownIssueId error, but since it's referenced from a rejected
        // issue registry, we want to make sure we *don't* flag this
        xml(
            "lint.xml",
            """
                <lint>
                    <issue id="MyIssueId" severity="error" />
                </lint>
                """
          )
          .indented(),
      )
      .clientFactory { createGlobalLintJarClient(lintJar) }
      .testModes(TestMode.DEFAULT)
      .allowObsoleteLintChecks(false)
      .issueIds("MyIssueId")
      .run()
      .expectContains(
        """
                lint.jar: Warning: Library lint checks out of date;
                these checks will be skipped!

                Lint found an issue registry (test.pkg.MyIssueRegistry)
                which was compiled against an older version of lint
                than this one. This is usually fine, but not in this
                case; some basic verification shows that the lint
                check jar references (for example) the following API
                which is no longer valid in this version of lint:
                com.android.tools.lint.detector.api.DeletedInterface
                (Referenced from test/pkg/Incompatible1.class)

                Therefore, this lint check library is not included
                in analysis. This affects the following lint checks:
                MyIssueId

                Recompile the checks against the latest version, or if
                this is a check bundled with a third-party library, see
                if there is a more recent version available.

                Version of Lint API this lint check is using is 9.
                The Lint API version currently running is $CURRENT_API (${describeApi(CURRENT_API)}). [ObsoleteLintCustomCheck]
                0 errors, 1 warnings"""
      )
      .expectMatches(
        """
                .*/app/lint\Q.jar: Warning: Library lint checks out of date;
                these checks will be skipped!

                Lint found an issue registry (test.pkg.MyIssueRegistry)
                which was compiled against an older version of lint
                than this one.\E"""
      )
  }

  companion object {
    val lintApiStubs =
      arrayOf<TestFile>(
        kotlin(
          "src/detector_stubs.kt",
          """
                @file:Suppress("unused", "UNUSED_PARAMETER", "PackageDirectoryMismatch")
                package com.android.tools.lint.detector.api
                import com.android.tools.lint.client.api.*
                import java.io.File
                import java.util.*
                enum class Severity { FATAL, ERROR, WARNING, INFORMATIONAL, IGNORE }
                data class Category  constructor(
                    val parent: Category?,
                    val name: String,
                    private val priority: Int
                ) {
                    companion object {
                        @JvmStatic fun create(name: String, priority: Int): Category =
                            Category(null, name, priority)
                        @JvmField val LINT = create("Lint", 110)
                    }
                }
                class LintFix
                open class Location protected constructor(
                    val file: File,
                    val start: Position?,
                    val end: Position?
                ) {
                    var message: String? = null
                    var clientData: Any? = null
                    open var visible = true
                    open var secondary: Location? = null
                    var source: Any? = null
                    fun isSelfExplanatory(): Boolean = false
                    fun isSingleLine(): Boolean = false
                    companion object {
                        @JvmStatic
                        fun create(file: File): Location = Location(file, null, null)
                    }
                }
                open class Context(
                    @JvmField val file: File,
                    private var contents: CharSequence? = null
                ) {
                    fun report(incident: Incident): Unit = error("Stub")
                    @JvmOverloads
                    open fun report(
                        issue: Issue,
                        location: Location,
                        message: String,
                        quickfixData: LintFix? = null
                    ) {
                        error("stub")
                    }
                }
                abstract class Detector : FileScanner {
                    open fun run(context: Context) {}
                    override fun beforeCheckFile(context: Context) { }
                    override fun afterCheckFile(context: Context) { }
                }
                interface FileScanner {
                    fun beforeCheckFile(context: Context)
                    fun afterCheckFile(context: Context)
                }
                class Implementation @SafeVarargs constructor(
                    detectorClass: Class<out Detector?>?,
                    scope: EnumSet<Scope>?,
                    vararg analysisScopes: EnumSet<Scope>?
                ) {
                    constructor(detectorClass: Class<out Detector?>?, scope: EnumSet<Scope>?) : this(
                        detectorClass,
                        scope,
                        Scope.EMPTY
                    )
                }
                class Incident(val issue: Issue, location: Location, message: String)
                class Issue {
                    companion object {
                        @JvmStatic
                        fun create(
                            id: String,
                            briefDescription: String,
                            explanation: String,
                            category: Category,
                            priority: Int,
                            severity: Severity,
                            implementation: Implementation
                        ): Issue {
                            error("Stub")
                        }

                        fun create(
                            id: String,
                            briefDescription: String,
                            explanation: String,
                            implementation: Implementation,
                            moreInfo: String? = null,
                            category: Category = Category.LINT,
                            priority: Int = 5,
                            severity: Severity = Severity.WARNING,
                            enabledByDefault: Boolean = true,
                            androidSpecific: Boolean? = null,
                            platforms: EnumSet<Platform>? = null,
                            suppressAnnotations: Collection<String>? = null
                        ): Issue {
                            error("Stub")
                        }
                    }
                }
                interface OtherFileScanner : FileScanner {
                    fun getApplicableFiles(): EnumSet<Scope>
                }
                enum class Platform {
                    ANDROID;
                    companion object {
                        @JvmField
                        val ANDROID_SET: EnumSet<Platform> = EnumSet.of(ANDROID)
                        @JvmField
                        val UNSPECIFIED: EnumSet<Platform> = EnumSet.noneOf(Platform::class.java)
                    }
                }
                abstract class Position {
                    abstract val line: Int
                    abstract val offset: Int
                    abstract val column: Int
                }
                enum class Scope {
                    RESOURCE_FILE, RESOURCE_FOLDER, ALL_RESOURCE_FILES, JAVA_FILE, CLASS_FILE,
                    MANIFEST, JAVA_LIBRARIES, OTHER;
                    companion object {
                        @JvmField val ALL: EnumSet<Scope> = EnumSet.allOf(Scope::class.java)
                        @JvmField val RESOURCE_FILE_SCOPE: EnumSet<Scope> = EnumSet.of(RESOURCE_FILE)
                        @JvmField val JAVA_FILE_SCOPE: EnumSet<Scope> = EnumSet.of(JAVA_FILE)
                        @JvmField val CLASS_FILE_SCOPE: EnumSet<Scope> = EnumSet.of(CLASS_FILE)
                        @JvmField val EMPTY: EnumSet<Scope> = EnumSet.noneOf(Scope::class.java)
                    }
                }
                """
        ),
        kotlin(
            "src/client_stubs.kt",
            """
                    @file:Suppress("unused")
                    package com.android.tools.lint.client.api
                    import com.android.tools.lint.detector.api.*
                    const val CURRENT_API = 11
                    data class Vendor
                    @JvmOverloads
                    constructor(
                        val vendorName: String? = null, val identifier: String? = null,
                        val feedbackUrl: String? = null, val contact: String? = null
                    )
                    abstract class IssueRegistry
                    protected constructor() {
                        open val api: Int = -1
                        open val minApi: Int
                            get() {
                                return api
                            }
                        abstract val issues: List<Issue>
                        open val vendor: Vendor? = null
                    }
                """
          )
          .indented(),
        // The following classes are classes or methods or fields which don't
        // exist. This is so that we can compile our custom lint jars with APIs
        // that look like lint APIs but aren't found by the verifier
        kotlin(
            """
                package com.android.tools.lint.detector.api
                interface DeletedInterface
                enum class TextFormat {
                    RAW, TEXT;
                    fun deleted() { }
                    @JvmField val deleted = 42
                }
                """
          )
          .indented()
      )
  }
}

fun createGlobalLintJarClient(lintJar: File, clientName: String? = null) =
  object :
    com.android.tools.lint.checks.infrastructure.TestLintClient(clientName ?: CLIENT_UNIT_TESTS) {
    override fun findGlobalRuleJars(driver: LintDriver?, warnDeprecated: Boolean): List<File> =
      listOf(lintJar)
    override fun findRuleJars(project: Project): List<File> = emptyList()
  }

fun createProjectLintJarClient(lintJar: File) =
  object : com.android.tools.lint.checks.infrastructure.TestLintClient() {
    override fun findGlobalRuleJars(driver: LintDriver?, warnDeprecated: Boolean): List<File> =
      emptyList()
    override fun findRuleJars(project: Project): List<File> = listOf(lintJar)
  }
