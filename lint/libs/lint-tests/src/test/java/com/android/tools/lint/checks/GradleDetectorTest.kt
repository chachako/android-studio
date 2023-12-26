/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION
import com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.GoogleMavenRepository.Companion.MAVEN_GOOGLE_CACHE_DIR_KEY
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.testutils.TestUtils
import com.android.tools.lint.checks.GradleDetector.Companion.ACCIDENTAL_OCTAL
import com.android.tools.lint.checks.GradleDetector.Companion.AGP_DEPENDENCY
import com.android.tools.lint.checks.GradleDetector.Companion.ANNOTATION_PROCESSOR_ON_COMPILE_PATH
import com.android.tools.lint.checks.GradleDetector.Companion.BOM_WITHOUT_PLATFORM
import com.android.tools.lint.checks.GradleDetector.Companion.BUNDLED_GMS
import com.android.tools.lint.checks.GradleDetector.Companion.CHROMEOS_ABI_SUPPORT
import com.android.tools.lint.checks.GradleDetector.Companion.COMPATIBILITY
import com.android.tools.lint.checks.GradleDetector.Companion.DATA_BINDING_WITHOUT_KAPT
import com.android.tools.lint.checks.GradleDetector.Companion.DEPENDENCY
import com.android.tools.lint.checks.GradleDetector.Companion.DEPRECATED
import com.android.tools.lint.checks.GradleDetector.Companion.DEPRECATED_CONFIGURATION
import com.android.tools.lint.checks.GradleDetector.Companion.DEPRECATED_LIBRARY
import com.android.tools.lint.checks.GradleDetector.Companion.DEV_MODE_OBSOLETE
import com.android.tools.lint.checks.GradleDetector.Companion.DUPLICATE_CLASSES
import com.android.tools.lint.checks.GradleDetector.Companion.EXPIRED_TARGET_SDK_VERSION
import com.android.tools.lint.checks.GradleDetector.Companion.EXPIRING_TARGET_SDK_VERSION
import com.android.tools.lint.checks.GradleDetector.Companion.GRADLE_GETTER
import com.android.tools.lint.checks.GradleDetector.Companion.GRADLE_PLUGIN_COMPATIBILITY
import com.android.tools.lint.checks.GradleDetector.Companion.HIGH_APP_VERSION_CODE
import com.android.tools.lint.checks.GradleDetector.Companion.JAVA_PLUGIN_LANGUAGE_LEVEL
import com.android.tools.lint.checks.GradleDetector.Companion.JCENTER_REPOSITORY_OBSOLETE
import com.android.tools.lint.checks.GradleDetector.Companion.KAPT_USAGE_INSTEAD_OF_KSP
import com.android.tools.lint.checks.GradleDetector.Companion.KTX_EXTENSION_AVAILABLE
import com.android.tools.lint.checks.GradleDetector.Companion.LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8
import com.android.tools.lint.checks.GradleDetector.Companion.MIN_SDK_TOO_LOW
import com.android.tools.lint.checks.GradleDetector.Companion.NOT_INTERPOLATED
import com.android.tools.lint.checks.GradleDetector.Companion.PATH
import com.android.tools.lint.checks.GradleDetector.Companion.PLAY_SDK_INDEX_GENERIC_ISSUES
import com.android.tools.lint.checks.GradleDetector.Companion.PLAY_SDK_INDEX_NON_COMPLIANT
import com.android.tools.lint.checks.GradleDetector.Companion.PLUS
import com.android.tools.lint.checks.GradleDetector.Companion.REMOTE_VERSION
import com.android.tools.lint.checks.GradleDetector.Companion.RISKY_LIBRARY
import com.android.tools.lint.checks.GradleDetector.Companion.STRING_INTEGER
import com.android.tools.lint.checks.GradleDetector.Companion.SWITCH_TO_TOML
import com.android.tools.lint.checks.GradleDetector.Companion.TARGET_NEWER
import com.android.tools.lint.checks.GradleDetector.Companion.getNamedDependency
import com.android.tools.lint.checks.TargetSdkRequirements.MINIMUM_TARGET_SDK_VERSION
import com.android.tools.lint.checks.TargetSdkRequirements.MINIMUM_TARGET_SDK_VERSION_YEAR
import com.android.tools.lint.checks.TargetSdkRequirements.MINIMUM_WEAR_TARGET_SDK_VERSION
import com.android.tools.lint.checks.TargetSdkRequirements.PREVIOUS_MINIMUM_TARGET_SDK_VERSION
import com.android.tools.lint.checks.infrastructure.TestFiles.gradleToml
import com.android.tools.lint.checks.infrastructure.TestIssueRegistry
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.platformPath
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Scope
import com.android.utils.FileUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.function.Predicate
import java.util.zip.GZIPOutputStream
import junit.framework.TestCase
import org.junit.rules.TemporaryFolder

/**
 * NOTE: Many of these tests are duplicated in the Android Studio plugin to test the custom
 * GradleDetector subclass, LintIdeGradleDetector, which customizes some behavior to be based on top
 * of PSI rather than the Groovy parser.
 */
class GradleDetectorTest : AbstractCheckTest() {

  private val mDependencies =
    source(
      "build.gradle",
      "" +
        "apply plugin: 'android'\n" +
        "\n" +
        "android {\n" +
        "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
        "    buildToolsVersion \"19.0.0\"\n" +
        "\n" +
        "    defaultConfig {\n" +
        "        minSdkVersion 7\n" +
        "        targetSdkVersion 17\n" +
        "        versionCode 1\n" +
        "        versionName \"1.0\"\n" +
        "    }\n" +
        "\n" +
        "    productFlavors {\n" +
        "        free {\n" +
        "        }\n" +
        "        pro {\n" +
        "        }\n" +
        "    }\n" +
        "}\n" +
        "\n" +
        "dependencies {\n" +
        "    compile 'com.android.support:appcompat-v7:+'\n" +
        "    freeCompile 'com.google.guava:guava:11.0.2'\n" +
        "    compile 'com.android.support:appcompat-v7:13.0.0'\n" +
        "    compile 'com.google.android.support:wearable:1.2.0'\n" +
        "    compile 'com.android.support:multidex:1.0.0'\n" +
        "\n" +
        "    androidTestCompile 'com.android.support.test:runner:0.3'\n" +
        "}\n"
    )

  override fun tearDown() {
    super.tearDown()

    if (sdkRootDir != null) {
      deleteFile(sdkRootDir)
      sdkRootDir = null
    }
  }

  override fun lint(): TestLintTask {
    val task = super.lint()
    task.sdkHome(mockSupportLibraryInstallation)
    // The mock SDK installation isn't a full valid SDK
    task.requireCompileSdk(false)
    initializeNetworkMocksAndCaches(task)
    return task
  }

  fun testBasic() {
    val expected =
      "" +
        "build.gradle:1: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]\n" +
        "apply plugin: 'android'\n" +
        "~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:24: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 21.0 [GradleDependency]\n" +
        "    freeCompile 'com.google.guava:guava:11.0.2'\n" +
        "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:25: Warning: A newer version of com.android.support:appcompat-v7 than 13.0.0 is available: 25.3.1 [GradleDependency]\n" +
        "    compile 'com.android.support:appcompat-v7:13.0.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:26: Warning: A newer version of com.google.android.support:wearable than 1.2.0 is available: 1.3.0 [GradleDependency]\n" +
        "    compile 'com.google.android.support:wearable:1.2.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:27: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]\n" +
        "    compile 'com.android.support:multidex:1.0.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:29: Warning: A newer version of com.android.support.test:runner than 0.3 is available: 0.5 [GradleDependency]\n" +
        "    androidTestCompile 'com.android.support.test:runner:0.3'\n" +
        "                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:23: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:+) [GradleDynamicVersion]\n" +
        "    compile 'com.android.support:appcompat-v7:+'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 7 warnings\n"

    lint()
      .files(mDependencies)
      .issues(COMPATIBILITY, DEPRECATED, DEPENDENCY, PLUS)
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Autofix for build.gradle line 1: Replace with com.android.application:\n" +
          "@@ -1 +1\n" +
          "- apply plugin: 'android'\n" +
          "+ apply plugin: 'com.android.application'\n" +
          "Fix for build.gradle line 24: Change to 21.0:\n" +
          "@@ -24 +24\n" +
          "-     freeCompile 'com.google.guava:guava:11.0.2'\n" +
          "+     freeCompile 'com.google.guava:guava:21.0'\n" +
          "Fix for build.gradle line 25: Change to 25.3.1:\n" +
          "@@ -25 +25\n" +
          "-     compile 'com.android.support:appcompat-v7:13.0.0'\n" +
          "+     compile 'com.android.support:appcompat-v7:25.3.1'\n" +
          "Fix for build.gradle line 26: Change to 1.3.0:\n" +
          "@@ -26 +26\n" +
          "-     compile 'com.google.android.support:wearable:1.2.0'\n" +
          "+     compile 'com.google.android.support:wearable:1.3.0'\n" +
          "Fix for build.gradle line 27: Change to 1.0.1:\n" +
          "@@ -27 +27\n" +
          "-     compile 'com.android.support:multidex:1.0.0'\n" +
          "+     compile 'com.android.support:multidex:1.0.1'\n" +
          "Fix for build.gradle line 29: Change to 0.5:\n" +
          "@@ -29 +29\n" +
          "-     androidTestCompile 'com.android.support.test:runner:0.3'\n" +
          "+     androidTestCompile 'com.android.support.test:runner:0.5'\n" +
          "Data for build.gradle line 23:   coordinate : com.android.support:appcompat-v7:+\n" +
          "  revision : +"
      )
  }

  fun testTomlVersionCatalogFile() {
    // Tests that when using version catalogs, dependencies from maven.google.com works,
    // pinpoints problems in the .toml files and quickfixes work.
    lint()
      .files(
        gradleToml(
            """
                [versions]
                guavaVersion = "11.0.2"
                appCompatVersion="13.0.0"
                wearableVersion=" 1.2.0 "
                # Test comment suppression:
                #noinspection GradleDependency
                multi-dex="1.0.0"
                gradlePlugins-agp = "8.0.0"
                gradlePlugins-agp-alpha = "8.1.0-alpha01"
                gradlePlugins-agp-dev = "8.2.0-dev"
                gradlePlugins-crashlytics = "2.9.2"
                gradlePlugins-dependency-analysis = "1.0.0"

                [libraries]
                com-google-guava = { module = "com.google.guava:guava", version.ref = "guavaVersion"}
                appcompat = { module = "com.android.support:appcompat-v7", version.ref = "appCompatVersion" }
                wearable-support = { group = " com.google.android.support ", name =" wearable ", version.ref = " wearableVersion " }
                multidex-lib = { module = "com.android.support:multidex", version.ref = "multi-dex" }

                [bundles]
                misc = [
                    "com-google-guava",
                    "appcompat",
                ]

                [plugins]
                android-application = { id = "com.android.application", version.ref = "gradlePlugins-agp" }
                android-application2 = { id = "com.android.application", version.ref = "gradlePlugins-agp-alpha" }
                android-application3 = { id = "com.android.application", version.ref = "gradlePlugins-agp-dev" }
                crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "gradlePlugins-crashlytics" }
                dependency-analysis = { id = "com.autonomousapps.dependency-analysis", version.ref = "gradlePlugins-dependency-analysis" }
                """
          )
          .indented(),
        gradle(""),
        // Make sure that if we have multiple modules, we don't report issues from
        // the shared ../gradle project multiple times, once for each "including" project.
        gradle("../lib/build.gradle", ""),
      )
      .issues(AGP_DEPENDENCY, DEPENDENCY, REMOTE_VERSION)
      .sdkHome(mockSupportLibraryInstallation)
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.google.guava%22+AND+a:%22guava%22&core=gav&wt=json",
        ""
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.autonomousapps.dependency-analysis%22+AND+a:%22com.autonomousapps.dependency-analysis.gradle.plugin%22&core=gav&wt=json",
        ""
      )
      .networkData(
        "https://plugins.gradle.org/m2/com/autonomousapps/dependency-analysis/com.autonomousapps.dependency-analysis.gradle.plugin/maven-metadata.xml",
        // language=XML
        """
              <metadata>
                <groupId>com.autonomousapps.dependency-analysis</groupId>
                <artifactId>com.autonomousapps.dependency-analysis.gradle.plugin</artifactId>
                <version>1.20.0</version>
                <versioning>
                  <latest>1.20.0</latest>
                  <release>1.20.0</release>
                  <versions>
                    <version>0.1</version>
                    <version>0.10.0</version>
                    <version>0.11.0</version>
                    <version>0.11.1</version>
                    ... truncated ...
                    <version>1.18.0</version>
                    <version>1.19.0</version>
                    <version>1.2.0</version>
                    <version>1.2.1</version>
                    <version>1.20.0</version>
                    <version>1.3.0</version>
                    <version>1.9.0</version>
                  </versions>
                  <lastUpdated>20230421150929</lastUpdated>
                </versioning>
              </metadata>
              """
          .trimIndent()
      )
      .run()
      .expect(
        """
        ../gradle/libs.versions.toml:8: Warning: A newer version of com.android.application than 8.0.0 is available: 8.0.2 [AndroidGradlePluginVersion]
        gradlePlugins-agp = "8.0.0"
                            ~~~~~~~
        ../gradle/libs.versions.toml:9: Warning: A newer version of com.android.application than 8.1.0-alpha01 is available: 8.1.0-rc01 [AndroidGradlePluginVersion]
        gradlePlugins-agp-alpha = "8.1.0-alpha01"
                                  ~~~~~~~~~~~~~~~
        ../gradle/libs.versions.toml:2: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 21.0 [GradleDependency]
        guavaVersion = "11.0.2"
                       ~~~~~~~~
        ../gradle/libs.versions.toml:3: Warning: A newer version of com.android.support:appcompat-v7 than 13.0.0 is available: 25.3.1 [GradleDependency]
        appCompatVersion="13.0.0"
                         ~~~~~~~~
        ../gradle/libs.versions.toml:4: Warning: A newer version of com.google.android.support:wearable than 1.2.0 is available: 1.3.0 [GradleDependency]
        wearableVersion=" 1.2.0 "
                        ~~~~~~~~~
        ../gradle/libs.versions.toml:11: Warning: A newer version of com.google.firebase.crashlytics than 2.9.2 is available: 2.9.7 [GradleDependency]
        gradlePlugins-crashlytics = "2.9.2"
                                    ~~~~~~~
        ../gradle/libs.versions.toml:12: Warning: A newer version of com.autonomousapps.dependency-analysis than 1.0.0 is available: 1.20.0 [NewerVersionAvailable]
        gradlePlugins-dependency-analysis = "1.0.0"
                                            ~~~~~~~
        0 errors, 7 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for gradle/libs.versions.toml line 8: Change to 8.0.2:
        @@ -8 +8
        - gradlePlugins-agp = "8.0.0"
        + gradlePlugins-agp = "8.0.2"
        Fix for gradle/libs.versions.toml line 9: Change to 8.1.0-rc01:
        @@ -9 +9
        - gradlePlugins-agp-alpha = "8.1.0-alpha01"
        + gradlePlugins-agp-alpha = "8.1.0-rc01"
        Fix for gradle/libs.versions.toml line 2: Change to 21.0:
        @@ -2 +2
        - guavaVersion = "11.0.2"
        + guavaVersion = "21.0"
        Fix for gradle/libs.versions.toml line 3: Change to 25.3.1:
        @@ -3 +3
        - appCompatVersion="13.0.0"
        + appCompatVersion="25.3.1"
        Fix for gradle/libs.versions.toml line 4: Change to 1.3.0:
        @@ -4 +4
        - wearableVersion=" 1.2.0 "
        + wearableVersion=" 1.3.0 "
        Fix for gradle/libs.versions.toml line 11: Change to 2.9.7:
        @@ -11 +11
        - gradlePlugins-crashlytics = "2.9.2"
        + gradlePlugins-crashlytics = "2.9.7"
        Fix for gradle/libs.versions.toml line 12: Change to 1.20.0:
        @@ -12 +12
        - gradlePlugins-dependency-analysis = "1.0.0"
        + gradlePlugins-dependency-analysis = "1.20.0"
        """
      )
  }

  fun testRemoteVersionsWithTomlVersionCatalogs() {
    // Tests that when using version catalogs, remote dependencies (found via lookup to
    // search.maven.org,
    // a different code path than the maven.google.com lookup) also reports warnings.
    lint()
      .files(
        gradleToml(
            """
                [versions]
                jodaVersion = "2.1"
                dagger="1.2.0"

                [libraries]
                joda_library = { module = "joda-time:joda-time", version.ref = "jodaVersion"}
                dagger-lib = { group = "com.squareup.dagger", name ="dagger", version.ref = "dagger" }
                """
          )
          .indented()
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22joda-time%22+AND+a:%22joda-time%22&core=gav&wt=json",
        "" +
          "{\"responseHeader\":" +
          "{\"status\":0,\"QTime\":0,\"params\":" +
          "{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"joda-time\\\" AND a:\\\"joda-time\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"version\":\"2.2\"}}," +
          "\"response\":" +
          "{\"numFound\":34,\"start\":0,\"docs\":[" +
          "{\"id\":\"joda-time:joda-time:2.9.9\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.9\",\"p\":\"jar\",\"timestamp\":1490275993000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb.jar\",\"-sources.jar\",\"-no-tzdb-javadoc.jar\",\"-javadoc.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.8\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.8\",\"p\":\"jar\",\"timestamp\":1490220931000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb.jar\",\"-sources.jar\",\"-no-tzdb-javadoc.jar\",\"-javadoc.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.7\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.7\",\"p\":\"jar\",\"timestamp\":1482188123000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-no-tzdb-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.6\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.6\",\"p\":\"jar\",\"timestamp\":1478812169000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb-javadoc.jar\",\"-no-tzdb.jar\",\"-sources.jar\",\"-javadoc.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.5\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.5\",\"p\":\"jar\",\"timestamp\":1478191007000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb-javadoc.jar\",\"-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.4\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.4\",\"p\":\"jar\",\"timestamp\":1464341135000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb.jar\",\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.3\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.3\",\"p\":\"jar\",\"timestamp\":1459107331000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.2\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.2\",\"p\":\"jar\",\"timestamp\":1453988648000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.1\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.1\",\"p\":\"jar\",\"timestamp\":1447329806000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9\",\"p\":\"jar\",\"timestamp\":1445680109000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-sources.jar\",\"-no-tzdb.jar\",\"-javadoc.jar\",\".jar\",\".pom\"]}]}}"
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.squareup.dagger%22+AND+a:%22dagger%22&core=gav&wt=json",
        "" +
          "{\"responseHeader\":" +
          "{\"status\":0,\"QTime\":0,\"params\":" +
          "{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"com.squareup.dagger\\\" AND a:\\\"dagger\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"version\":\"2.2\"}}," +
          "\"response\":" +
          "{\"numFound\":9,\"start\":0,\"docs\":[" +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.5\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.5\",\"p\":\"jar\",\"timestamp\":1462852968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.4\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.4\",\"p\":\"jar\",\"timestamp\":1462291775000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-tests.jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.3\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.3\",\"p\":\"jar\",\"timestamp\":1462238813000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\"-tests.jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.2\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.2\",\"p\":\"jar\",\"timestamp\":1405987370000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.1\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.1\",\"p\":\"jar\",\"timestamp\":1392614597000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.0\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.0\",\"p\":\"jar\",\"timestamp\":1386979272000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-tests.jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.1.0\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.1.0\",\"p\":\"jar\",\"timestamp\":1375745812000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\"],\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\"-tests.jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.0.1\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.0.1\",\"p\":\"jar\",\"timestamp\":1370304793000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.0.0\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.0.0\",\"p\":\"jar\",\"timestamp\":1367941344000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\".pom\"]}]}}"
      )
      .issues(REMOTE_VERSION)
      .run()
      .expect(
        """
                ../gradle/libs.versions.toml:2: Warning: A newer version of joda-time:joda-time than 2.1 is available: 2.9.9 [NewerVersionAvailable]
                jodaVersion = "2.1"
                              ~~~~~
                ../gradle/libs.versions.toml:3: Warning: A newer version of com.squareup.dagger:dagger than 1.2.0 is available: 1.2.5 [NewerVersionAvailable]
                dagger="1.2.0"
                       ~~~~~~~
                0 errors, 2 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for gradle/libs.versions.toml line 2: Change to 2.9.9:
                @@ -2 +2
                - jodaVersion = "2.1"
                + jodaVersion = "2.9.9"
                Fix for gradle/libs.versions.toml line 3: Change to 1.2.5:
                @@ -3 +3
                - dagger="1.2.0"
                + dagger="1.2.5"
                """
      )
  }

  fun testSwitchToExistingTomlLibrary() {
    // Tests that if a build.gradle file a contains a group:artifact:version dependency
    // already available in the version catalog, we flag this and offer to replace it with
    // the dependency from the catalog.
    lint()
      .files(
        gradleToml(
            """
                [versions]
                appCompat = "1.5.1"
                androidxTest = "1.5.0"

                [libraries]
                androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
                """
          )
          .indented(),
        gradle(
            """
                dependencies {
                    implementation(libs.androidx.appCompat) // OK
                    implementation 'androidx.appcompat:appcompat:1.5.1'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
                build.gradle:3: Warning: Use the existing version catalog reference (libs.androidx.appCompat) instead [UseTomlInstead]
                    implementation 'androidx.appcompat:appcompat:1.5.1'
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 3: Replace with existing version catalog reference `androidx-appCompat`:
                @@ -3 +3
                      implementation(libs.androidx.appCompat) // OK
                -     implementation 'androidx.appcompat:appcompat:1.5.1'
                +     implementation libs.androidx.appCompat
                  }
                """
      )
  }

  fun test290794202() {
    // Regression test for b/290794202
    lint()
      .files(
        gradleToml(
            """
                [versions]
                appCompat = "1.5.1"
                [libraries]
                androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                """
          )
          .indented(),
        gradle(
            """
                dependencies {
                    api project(':myproject')
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expectClean()
  }

  fun testSwitchToExistingTomlLibraryKts() {
    // Tests that if a build.gradle file a contains a group:artifact:version dependency
    // already available in the version catalog, we flag this and offer to replace it with
    // the dependency from the catalog.
    lint()
      .files(
        gradleToml(
            """
                [versions]
                appCompat = "1.5.1"
                androidxTest = "1.5.0"

                [libraries]
                androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
                """
          )
          .indented(),
        kts(
            """
                dependencies {
                    implementation(libs.androidx.appCompat) // OK
                    implementation("androidx.appcompat:appcompat:1.5.1")
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
                build.gradle.kts:3: Warning: Use the existing version catalog reference (libs.androidx.appCompat) instead [UseTomlInstead]
                    implementation("androidx.appcompat:appcompat:1.5.1")
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle.kts line 3: Replace with existing version catalog reference `androidx-appCompat`:
                @@ -3 +3
                      implementation(libs.androidx.appCompat) // OK
                -     implementation("androidx.appcompat:appcompat:1.5.1")
                +     implementation(libs.androidx.appCompat)
                  }
                """
      )
  }

  fun testAddNewTomlDependency() {
    // Checks that in a project using version catalogs, if a gradle file directly
    // declares a group:artifact:version dependency, we flag this, and the quickfix
    // correctly registers it into the TOML file.
    lint()
      .files(
        gradleToml(
            """
                [versions]
                appCompat = "1.5.1"
                androidxTest = "1.5.0"

                [libraries]
                androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
                """
          )
          .indented(),
        gradle(
            """
                dependencies {
                    implementation 'androidx.fragment:fragment:1.5.1'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
                build.gradle:2: Warning: Use version catalog instead [UseTomlInstead]
                    implementation 'androidx.fragment:fragment:1.5.1'
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 2: Replace with new library catalog declaration for androidx-fragment:
                @@ -2 +2
                  dependencies {
                -     implementation 'androidx.fragment:fragment:1.5.1'
                +     implementation libs.androidx.fragment
                  }
                gradle/libs.versions.toml:
                @@ -4 +4
                  androidxTest = "1.5.0"
                + fragment = "1.5.1"

                @@ -7 +8
                  androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                + androidx-fragment = { module = "androidx.fragment:fragment", version.ref = "fragment" }
                  androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
                """
      )
  }

  fun testAddNewTomlDependencyFromVariable() {
    // Checks that (1) we pick a reasonable default library name, (2) when we are already
    // using version variables in build.gradle.kts we suggest the same variable name, and
    // (3) repeatedly adding dependencies reuses the same new variable
    lint()
      .files(
        gradleToml(
            """
            [versions]
            kotlin = "1.7.20"

            [libraries]
            kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
            kotlinReflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }
            kotlinTest = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }

            [plugins]
            kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
            """
          )
          .indented(),
        kts(
            """
            val myVer = "31.1.0-alpha04"
            dependencies {
              implementation("com.android.tools.lint:lint-api:＄myVer")
              implementation("com.android.tools.lint:lint-checks:＄myVer")
              testImplementation("com.google.truth:truth:1.1.3")
              testImplementation(libs.kotlinTest)
            }
            """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
        build.gradle.kts:3: Warning: Use version catalog instead [UseTomlInstead]
          implementation("com.android.tools.lint:lint-api:＄myVer")
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle.kts:4: Warning: Use version catalog instead [UseTomlInstead]
          implementation("com.android.tools.lint:lint-checks:＄myVer")
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle.kts:5: Warning: Use version catalog instead [UseTomlInstead]
          testImplementation("com.google.truth:truth:1.1.3")
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 3 warnings
        """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
        Autofix for build.gradle.kts line 3: Replace with new library catalog declaration for lintApi:
        @@ -3 +3
          dependencies {
        -   implementation("com.android.tools.lint:lint-api:＄myVer")
        +   implementation(libs.lintApi)
            implementation("com.android.tools.lint:lint-checks:＄myVer")
        gradle/libs.versions.toml:
        @@ -3 +3
          kotlin = "1.7.20"
        + myVer = "31.1.0-alpha04"

        @@ -8 +9
          kotlinTest = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
        + lintApi = { module = "com.android.tools.lint:lint-api", version.ref = "myVer" }
        Autofix for build.gradle.kts line 4: Replace with new library catalog declaration for lintChecks:
        @@ -4 +4
            implementation("com.android.tools.lint:lint-api:＄myVer")
        -   implementation("com.android.tools.lint:lint-checks:＄myVer")
        +   implementation(libs.lintChecks)
            testImplementation("com.google.truth:truth:1.1.3")
        gradle/libs.versions.toml:
        @@ -3 +3
          kotlin = "1.7.20"
        + myVer = "31.1.0-alpha04"

        @@ -8 +9
          kotlinTest = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
        + lintChecks = { module = "com.android.tools.lint:lint-checks", version.ref = "myVer" }
        Autofix for build.gradle.kts line 5: Replace with new library catalog declaration for truth:
        @@ -5 +5
            implementation("com.android.tools.lint:lint-checks:＄myVer")
        -   testImplementation("com.google.truth:truth:1.1.3")
        +   testImplementation(libs.truth)
            testImplementation(libs.kotlinTest)
        gradle/libs.versions.toml:
        @@ -3 +3
          kotlin = "1.7.20"
        + truth = "1.1.3"

        @@ -8 +9
          kotlinTest = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
        + truth = { module = "com.google.truth:truth", version.ref = "truth" }
        """
      )
  }

  fun testAddNewTomlDependencyFromVariableAlreadyExistsSameVersion() {
    // If we have a Gradle dependency with a version variable, and that version
    // variable already exists in the TOML file, and the version is an exact match,
    // then we just offer to use it, with no other alternatives
    lint()
      .files(
        gradleToml(
            """
            [versions]
            kotlin = "1.7.20"
            lintVersion = "31.1.0-alpha04"

            [libraries]
            kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
            """
          )
          .indented(),
        kts(
            """
            val lintVersion = "31.1.0-alpha04"
            dependencies {
              implementation("com.android.tools.lint:lint-checks:＄{lintVersion}")
            }
            """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
            build.gradle.kts:3: Warning: Use version catalog instead [UseTomlInstead]
              implementation("com.android.tools.lint:lint-checks:＄{lintVersion}")
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
        Autofix for build.gradle.kts line 3: Replace with new library catalog declaration for lintChecks:
        @@ -3 +3
          dependencies {
        -   implementation("com.android.tools.lint:lint-checks:＄{lintVersion}")
        +   implementation(libs.lintChecks)
          }
        gradle/libs.versions.toml:
        @@ -7 +7
          kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
        + lintChecks = { module = "com.android.tools.lint:lint-checks", version.ref = "lintVersion" }
        """
      )
  }

  @Suppress("GroovyUnusedAssignment")
  fun testAddNewTomlDependencyFromVariableAlreadyExistsSameVersionGroovy() {
    // Like testAddNewTomlDependencyFromVariableAlreadyExistsSameVersion, but with Groovy instead of
    // KTS
    lint()
      .files(
        gradleToml(
            """
            [versions]
            kotlin = "1.7.20"
            lintVersion = "31.1.0-alpha04"

            [libraries]
            kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
            """
          )
          .indented(),
        gradle(
            """
            def lintVersion = "31.1.0-alpha04"
            dependencies {
              implementation("com.android.tools.lint:lint-checks:＄lintVersion")
              implementation("com.android.tools.lint:lint-api:＄{lintVersion}")
            }
            """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
        build.gradle:3: Warning: Use version catalog instead [UseTomlInstead]
          implementation("com.android.tools.lint:lint-checks:＄lintVersion")
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle:4: Warning: Use version catalog instead [UseTomlInstead]
          implementation("com.android.tools.lint:lint-api:＄{lintVersion}")
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
        Autofix for build.gradle line 3: Replace with new library catalog declaration for lintChecks:
        @@ -3 +3
          dependencies {
        -   implementation("com.android.tools.lint:lint-checks:＄lintVersion")
        +   implementation(libs.lintChecks)
            implementation("com.android.tools.lint:lint-api:＄{lintVersion}")
        gradle/libs.versions.toml:
        @@ -7 +7
          kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
        + lintChecks = { module = "com.android.tools.lint:lint-checks", version.ref = "lintVersion" }
        Autofix for build.gradle line 4: Replace with new library catalog declaration for lintApi:
        @@ -4 +4
            implementation("com.android.tools.lint:lint-checks:＄lintVersion")
        -   implementation("com.android.tools.lint:lint-api:＄{lintVersion}")
        +   implementation(libs.lintApi)
          }
        gradle/libs.versions.toml:
        @@ -7 +7
          kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
        + lintApi = { module = "com.android.tools.lint:lint-api", version.ref = "lintVersion" }
        """
      )
  }

  fun testAddNewTomlDependencyFromVariableAlreadyExistsWrongVersion() {
    // Similar to testAddNewTomlDependencyFromVariable, we have a dependency in the build.gradle
    // file which already includes a version variable name; try to reuse this, but here, the
    // variable already exists in the TOML file too (common in the middle of migrating many
    // dependencies); check that we get two fixes suggested; one to reuse, one to create new.
    lint()
      .files(
        gradleToml(
            """
            [versions]
            kotlin = "1.7.20"
            lintVersion = "30.0.0"

            [libraries]
            kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
            """
          )
          .indented(),
        kts(
            """
            val lintVersion = "31.1.0-alpha04"
            dependencies {
              implementation("com.android.tools.lint:lint-checks:＄lintVersion")
            }
            """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
        build.gradle.kts:3: Warning: Use version catalog instead [UseTomlInstead]
          implementation("com.android.tools.lint:lint-checks:＄lintVersion")
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
        Fix for build.gradle.kts line 3: Replace with new library catalog declaration, reusing version variable lintVersion (version=30.0.0):
        @@ -3 +3
          dependencies {
        -   implementation("com.android.tools.lint:lint-checks:＄lintVersion")
        +   implementation(libs.lintChecks)
          }
        gradle/libs.versions.toml:
        @@ -7 +7
          kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
        + lintChecks = { module = "com.android.tools.lint:lint-checks", version.ref = "lintVersion" }
        Fix for build.gradle.kts line 3: Change lintVersion to 31.1.0-alpha04:
        gradle/libs.versions.toml:
        @@ -3 +3
          kotlin = "1.7.20"
        - lintVersion = "30.0.0"
        + lintVersion = "31.1.0-alpha04"
        Autofix for build.gradle.kts line 3: Replace with new library catalog declaration for androidLintChecks:
        @@ -3 +3
          dependencies {
        -   implementation("com.android.tools.lint:lint-checks:＄lintVersion")
        +   implementation(libs.androidLintChecks)
          }
        gradle/libs.versions.toml:
        @@ -3 +3
          kotlin = "1.7.20"
        + lintChecks = "31.1.0-alpha04"
          lintVersion = "30.0.0"
        @@ -6 +7
          [libraries]
        + androidLintChecks = { module = "com.android.tools.lint:lint-checks", version.ref = "lintChecks" }
          kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
        """
      )
  }

  fun testAddNewTomlDependencyFromVariableOfferExistingVariable() {
    // Look through the existing variables, and if one of them is a match for this specific
    // version, offer to use it.
    lint()
      .files(
        gradleToml(
            """
            [versions]
            kotlin = "1.7.20"
            lintVersion = "31.1.0-alpha04"

            [libraries]
            kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
            lint-api = { module = "com.android.tools.lint:lint-api", version.ref = "lintVersion" }
            """
          )
          .indented(),
        kts(
            """
            dependencies {
              implementation("com.android.tools.lint:lint-checks:31.1.0-alpha04")
            }
            """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
        build.gradle.kts:2: Warning: Use version catalog instead [UseTomlInstead]
          implementation("com.android.tools.lint:lint-checks:31.1.0-alpha04")
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
        Autofix for build.gradle.kts line 2: Replace with new library catalog declaration, reusing version variable lintVersion:
        @@ -2 +2
          dependencies {
        -   implementation("com.android.tools.lint:lint-checks:31.1.0-alpha04")
        +   implementation(libs.lint.checks)
          }
        gradle/libs.versions.toml:
        @@ -8 +8
          lint-api = { module = "com.android.tools.lint:lint-api", version.ref = "lintVersion" }
        + lint-checks = { module = "com.android.tools.lint:lint-checks", version.ref = "lintVersion" }
        Autofix for build.gradle.kts line 2: Replace with new library catalog declaration for android-lint-checks:
        @@ -2 +2
          dependencies {
        -   implementation("com.android.tools.lint:lint-checks:31.1.0-alpha04")
        +   implementation(libs.android.lint.checks)
          }
        gradle/libs.versions.toml:
        @@ -3 +3
          kotlin = "1.7.20"
        + lintChecks = "31.1.0-alpha04"
          lintVersion = "31.1.0-alpha04"
        @@ -6 +7
          [libraries]
        + android-lint-checks = { module = "com.android.tools.lint:lint-checks", version.ref = "lintChecks" }
          kotlinStdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib-jdk8", version.ref = "kotlin" }
        """
      )
  }

  fun testAddNewTomlDependencyKts() {
    // Like testAddNewTomlDependency, but with three changes:
    // (11) Add something after the libraries such that the last library isn't at the end of the
    // file.
    // the quickfix logic is slightly different for these offsets.
    // (2) We also do not put spaces around the ='s in the keys to test that we follow that
    // convention, and
    // we name the keys with suffix "Version"; make sure we follow that convention too.
    // (3) We also test using a KTS file instead of a Groovy build file.

    lint()
      .files(
        gradleToml(
            """
                [versions]
                appCompatVersion="1.5.1"
                androidxTestVersion="1.5.0"

                [libraries]
                androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompatVersion" }
                androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestVersion" }
                [bundles]
                """
          )
          .indented(),
        kts(
            """
                dependencies {
                    implementation("androidx.fragment:fragment:1.5.1")
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
                build.gradle.kts:2: Warning: Use version catalog instead [UseTomlInstead]
                    implementation("androidx.fragment:fragment:1.5.1")
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle.kts line 2: Replace with new library catalog declaration for androidx-fragment:
                @@ -2 +2
                  dependencies {
                -     implementation("androidx.fragment:fragment:1.5.1")
                +     implementation(libs.androidx.fragment)
                  }
                gradle/libs.versions.toml:
                @@ -4 +4
                  androidxTestVersion="1.5.0"
                + fragmentVersion="1.5.1"

                @@ -7 +8
                  androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompatVersion" }
                + androidx-fragment = { module = "androidx.fragment:fragment", version.ref = "fragmentVersion" }
                  androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestVersion" }
                  """
      )
  }

  fun testAddNewTomlDependencyAlphabeticalOrder() {
    // Checks that when inserting a new dependency in a toml file that is alphabetical, we insert
    // the new dependency in the right alphabetical places
    lint()
      .files(
        gradleToml(
            """
                [versions]
                androidxTest = "1.5.0"
                appCompat = "1.5.1"
                electric_eel="4" # out of order alphabetically, but we tolerate small discrepancies
                dolphin="3"
                flamingo="5"
                giraffe="6"

                [libraries]
                androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
                """
          )
          .indented(),
        gradle(
            """
                dependencies {
                    implementation 'androidx.fragment:fragment:1.5.1'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
                build.gradle:2: Warning: Use version catalog instead [UseTomlInstead]
                    implementation 'androidx.fragment:fragment:1.5.1'
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 2: Replace with new library catalog declaration for androidx-fragment:
                @@ -2 +2
                  dependencies {
                -     implementation 'androidx.fragment:fragment:1.5.1'
                +     implementation libs.androidx.fragment
                  }
                gradle/libs.versions.toml:
                @@ -7 +7
                  flamingo="5"
                + fragment = "1.5.1"
                  giraffe="6"
                @@ -11 +12
                  androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                + androidx-fragment = { module = "androidx.fragment:fragment", version.ref = "fragment" }
                  androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
                """
      )
  }

  fun testTomlAddVersionVariable() {
    // Checks adding a dependency from build.gradle which is referencing a variable
    // as the version.
    lint()
      .files(
        gradleToml(
            """
                [versions]
                androidxTest = "1.5.0"
                appCompat = "1.5.1"
                dolphin="3"
                electric_eel="4"
                flamingo="5"
                giraffe="6"

                [libraries]
                androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
                """
          )
          .indented(),
        gradle(
            """
                ext {
                    fragmentVersion = "1.5.1"
                    firebaseVersion = "11.0.0"
                }
                dependencies {
                    implementation "androidx.fragment:fragment:＄{fragmentVersion}"
                }
                """
          )
          .indented()
      )
      .sdkHome(mockSupportLibraryInstallation)
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
                build.gradle:6: Warning: Use version catalog instead [UseTomlInstead]
                    implementation "androidx.fragment:fragment:＄{fragmentVersion}"
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 6: Replace with new library catalog declaration for androidx-fragment:
                @@ -6 +6
                  dependencies {
                -     implementation "androidx.fragment:fragment:＄{fragmentVersion}"
                +     implementation libs.androidx.fragment
                  }
                gradle/libs.versions.toml:
                @@ -7 +7
                  flamingo="5"
                + fragmentVersion = "1.5.1"
                  giraffe="6"
                @@ -11 +12
                  androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                + androidx-fragment = { module = "androidx.fragment:fragment", version.ref = "fragmentVersion" }
                  androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
                """
      )
  }

  fun testTomlSwitchToTomlFromNamedDependency() {
    // Checks using the group:, name:, version: syntax and that the quickfixes
    // replace the correct parts.
    lint()
      .files(
        gradleToml(
            """
                [versions]
                [libraries]
                [bundles]
                """
          )
          .indented(),
        gradle(
            """
                apply plugin: 'com.android.application'
                dependencies {
                    implementation group: 'com.android.support', name: 'support-v4', version: '19.0'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
                build.gradle:3: Warning: Use version catalog instead [UseTomlInstead]
                    implementation group: 'com.android.support', name: 'support-v4', version: '19.0'
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 3: Replace with new library catalog declaration for support-v4:
                @@ -3 +3
                  dependencies {
                -     implementation group: 'com.android.support', name: 'support-v4', version: '19.0'
                +     implementation libs.support.v4
                  }
                gradle/libs.versions.toml:
                @@ -2 +2
                  [versions]
                + supportV4 = "19.0"
                  [libraries]
                + support-v4 = { module = "com.android.support:support-v4", version.ref = "supportV4" }
                  [bundles]
                """
      )
  }

  fun testAddNewTomlDependencyToEmptyCatalogs() {
    // Tests that we can add a new dependency into a TOML file which doesn't contain
    // any existing libraries (where we usually anchor dependencies on the last previous
    // version and library).
    lint()
      .files(
        gradleToml(
            """
                [versions]

                [libraries]

                [bundles]
                """
          )
          .indented(),
        gradle(
            """
                dependencies {
                    implementation 'androidx.fragment:fragment:1.5.1'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expect(
        """
                build.gradle:2: Warning: Use version catalog instead [UseTomlInstead]
                    implementation 'androidx.fragment:fragment:1.5.1'
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 2: Replace with new library catalog declaration for androidx-fragment:
                @@ -2 +2
                  dependencies {
                -     implementation 'androidx.fragment:fragment:1.5.1'
                +     implementation libs.androidx.fragment
                  }
                gradle/libs.versions.toml:
                @@ -2 +2
                  [versions]
                + fragment = "1.5.1"

                  [libraries]
                + androidx-fragment = { module = "androidx.fragment:fragment", version.ref = "fragment" }
                """
      )
  }

  fun testSwitchToTomlHigherVersion() {
    // Tests that in a project using version catalogs, if you have a Gradle dependency of a
    // group:artifact:version
    // where the artifact is already available in the version catalog, but with a different version,
    // we offer
    // several fix alternatives -- one to just use the existing catalog version, second to just
    // update the version
    // to this dependency version (assuming it's higher), and finally to add a new catalog entry for
    // this library.
    lint()
      .files(
        gradleToml(
            """
                [versions]
                appCompat = "1.5.1"
                appCompat149 = "1.4.9"
                androidxTest = "1.5.0"

                [libraries]
                androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                androidx-appCompat149 = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                androidx-appCompat153 = { module = "androidx.appcompat:appcompat", version.ref = "appCompat153" }
                androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }

                [plugins]
                """
          )
          .indented(),
        gradle(
            """
                dependencies {
                    // higher version: fix should replace with version catalog *and* update version variable
                    implementation 'androidx.appcompat:appcompat:1.5.2'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        """
                build.gradle:3: Warning: Use version catalog instead (androidx.appcompat:appcompat is already available as androidx-appCompat, but using version 1.5.1 instead) [UseTomlInstead]
                    implementation 'androidx.appcompat:appcompat:1.5.2'
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 3: Replace with new library catalog declaration for androidx-appcompat-v152:
                @@ -3 +3
                      // higher version: fix should replace with version catalog *and* update version variable
                -     implementation 'androidx.appcompat:appcompat:1.5.2'
                +     implementation libs.androidx.appcompat.v152
                  }
                gradle/libs.versions.toml:
                @@ -5 +5
                  androidxTest = "1.5.0"
                + appcompatVersion = "1.5.2"

                @@ -8 +9
                  androidx-appCompat = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                + androidx-appcompat-v152 = { module = "androidx.appcompat:appcompat", version.ref = "appcompatVersion" }
                  androidx-appCompat149 = { module = "androidx.appcompat:appcompat", version.ref = "appCompat" }
                Fix for build.gradle line 3: Replace with existing version catalog reference `androidx-appCompat` (version 1.5.1):
                @@ -3 +3
                      // higher version: fix should replace with version catalog *and* update version variable
                -     implementation 'androidx.appcompat:appcompat:1.5.2'
                +     implementation libs.androidx.appCompat
                  }
                """
      )
  }

  fun testSwitchToTomlMissingVersions() {
    lint()
      .files(
        gradleToml(
            """
                # Only libraries here
                [libraries]
                """
          )
          .indented(),
        gradle(
            """
                dependencies {
                    implementation 'androidx.appcompat:appcompat:1.5.1'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        """
                build.gradle:2: Warning: Use version catalog instead [UseTomlInstead]
                    implementation 'androidx.appcompat:appcompat:1.5.1'
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 2: Replace with new library catalog declaration for androidx-appcompat:
                @@ -2 +2
                  dependencies {
                -     implementation 'androidx.appcompat:appcompat:1.5.1'
                +     implementation libs.androidx.appcompat
                  }
                gradle/libs.versions.toml:
                @@ -3 +3
                  [libraries]
                + androidx-appcompat = { module = "androidx.appcompat:appcompat", version = "1.5.1" }
                """
      )
  }

  fun testSwitchToTomlNoVersion() {
    lint()
      .files(
        gradleToml(
            """
                # Only libraries here
                [libraries]
                compose-bom = { module = 'androidx.compose:compose-bom', version = '2023.10.01' }
                """
          )
          .indented(),
        gradle(
            """
                dependencies {
                    implementation libs.compose.bom
                    implementation 'androidx.compose.material3:material3'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        """
                build.gradle:3: Warning: Use version catalog instead [UseTomlInstead]
                    implementation 'androidx.compose.material3:material3'
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 3: Replace with new library catalog declaration for material3:
                @@ -3 +3
                      implementation libs.compose.bom
                -     implementation 'androidx.compose.material3:material3'
                +     implementation libs.material3
                  }
                gradle/libs.versions.toml:
                @@ -4 +4
                  compose-bom = { module = 'androidx.compose:compose-bom', version = '2023.10.01' }
                + material3 = { module = "androidx.compose.material3:material3" }
                """
      )
  }

  fun testSwitchToTomlWrongVersion() {
    lint()
      .files(
        gradleToml(
            """
                # Only libraries here
                [libraries]
                compose-bom = { module = 'androidx.compose:compose-bom', version = '2023.10.01' }
                """
          )
          .indented(),
        gradle(
            """
                dependencies {
                    implementation libs.compose.bom
                    implementation 'androidx.compose.material3:material3:'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expectClean()
  }

  fun testSwitchToTomlEmptyVersions() {
    lint()
      .files(
        gradleToml("""
                [versions]

                [libraries]
                """)
          .indented(),
        gradle(
            """
                dependencies {
                    implementation 'androidx.appcompat:appcompat:1.5.1'
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        """
                build.gradle:2: Warning: Use version catalog instead [UseTomlInstead]
                    implementation 'androidx.appcompat:appcompat:1.5.1'
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Autofix for build.gradle line 2: Replace with new library catalog declaration for androidx-appcompat:
                @@ -2 +2
                  dependencies {
                -     implementation 'androidx.appcompat:appcompat:1.5.1'
                +     implementation libs.androidx.appcompat
                  }
                gradle/libs.versions.toml:
                @@ -2 +2
                  [versions]
                + appcompat = "1.5.1"

                  [libraries]
                + androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
                """
      )
  }

  fun testVersionCatalogNotSuggestedInSettingsGradle() {
    lint()
      .files(
        gradleToml("""
                [versions]
                [libraries]
                """)
          .indented(),
        kts(
            "settings.gradle.kts",
            """
                buildscript {
                    repositories {
                        google()
                    }
                    dependencies {
                        classpath("com.android.application:com.android.application.gradle.plugin:8.0.1")
                    }
                }
                """
          )
          .indented()
      )
      .issues(SWITCH_TO_TOML)
      .run()
      .expectClean()
  }

  fun testVersionsFromGradleCache() {
    val expected =
      "" +
        "build.gradle:7: Warning: A newer version of com.android.tools.build:gradle than 3.4.0-alpha3 is available: 3.5.0 [AndroidGradlePluginVersion]\n" +
        "        classpath 'com.android.tools.build:gradle:3.4.0-alpha3'\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:11: Warning: A newer version of org.apache.httpcomponents:httpcomponents-core than 4.2 is available: 4.4 [GradleDependency]\n" +
        "    compile 'org.apache.httpcomponents:httpcomponents-core:4.2'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:12: Warning: A newer version of com.android.support:recyclerview-v7 than 25.0.0 is available: 26.0.0 [GradleDependency]\n" +
        "    compile 'com.android.support:recyclerview-v7:25.0.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:13: Warning: A newer version of com.google.firebase:firebase-messaging than 10.2.1 is available: 11.0.0 [GradleDependency]\n" +
        "    compile 'com.google.firebase:firebase-messaging:10.2.1'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 4 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "    repositories {\n" +
            "        google()\n" +
            "        jcenter()\n" +
            "    }\n" +
            "    dependencies {\n" +
            "        classpath 'com.android.tools.build:gradle:3.4.0-alpha3'\n" +
            "    }\n" +
            "}\n" +
            "dependencies {\n" +
            "    compile 'org.apache.httpcomponents:httpcomponents-core:4.2'\n" +
            "    compile 'com.android.support:recyclerview-v7:25.0.0'\n" +
            "    compile 'com.google.firebase:firebase-messaging:10.2.1'\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY, AGP_DEPENDENCY)
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 7: Change to 3.5.0:\n" +
          "@@ -7 +7\n" +
          "-         classpath 'com.android.tools.build:gradle:3.4.0-alpha3'\n" +
          "+         classpath 'com.android.tools.build:gradle:3.5.0'\n" +
          "Fix for build.gradle line 11: Change to 4.4:\n" +
          "@@ -11 +11\n" +
          "-     compile 'org.apache.httpcomponents:httpcomponents-core:4.2'\n" +
          "+     compile 'org.apache.httpcomponents:httpcomponents-core:4.4'\n" +
          "Fix for build.gradle line 12: Change to 26.0.0:\n" +
          "@@ -12 +12\n" +
          "-     compile 'com.android.support:recyclerview-v7:25.0.0'\n" +
          "+     compile 'com.android.support:recyclerview-v7:26.0.0'\n" +
          "Fix for build.gradle line 13: Change to 11.0.0:\n" +
          "@@ -13 +13\n" +
          "-     compile 'com.google.firebase:firebase-messaging:10.2.1'\n" +
          "+     compile 'com.google.firebase:firebase-messaging:11.0.0'\n"
      )
  }

  fun testMoreRecentStableVersion() {
    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "    repositories {\n" +
            "        google()\n" +
            "        jcenter()\n" +
            "    }\n" +
            "    dependencies {\n" +
            "        classpath 'com.android.tools.build:gradle:3.3.0'\n" +
            "        classpath 'com.android.tools.build:gradle:3.3.+'\n" +
            "        classpath 'com.android.tools.build:gradle:3.+'\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(AGP_DEPENDENCY)
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        "" +
          "build.gradle:7: Warning: A newer version of com.android.tools.build:gradle than 3.3.0 is available: 3.5.0. (There is also a newer version of 3.3.\uD835\uDC65 available, if upgrading to 3.5.0 is difficult: 3.3.2) [AndroidGradlePluginVersion]\n" +
          "        classpath 'com.android.tools.build:gradle:3.3.0'\n" +
          "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "0 errors, 1 warnings"
      )
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 7: Change to 3.5.0:\n" +
          "@@ -7 +7\n" +
          "-         classpath 'com.android.tools.build:gradle:3.3.0'\n" +
          "+         classpath 'com.android.tools.build:gradle:3.5.0'\n" +
          "Autofix for build.gradle line 7: Change to 3.3.2:\n" +
          "@@ -7 +7\n" +
          "-         classpath 'com.android.tools.build:gradle:3.3.0'\n" +
          "+         classpath 'com.android.tools.build:gradle:3.3.2'"
      )
  }

  fun testDependenciesWithCallSyntax() {
    // Regression test for 134692580
    val expected =
      "" +
        "build.gradle:7: Warning: A newer version of com.google.firebase:firebase-messaging than 10.2.1 is available: 11.0.0 [GradleDependency]\n" +
        "    implementation(\"com.google.firebase:firebase-messaging:10.2.1\")\n" +
        "                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 1 warnings"

    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "    repositories {\n" +
            "        jcenter()\n" +
            "    }\n" +
            "}\n" +
            "dependencies {\n" +
            "    implementation(\"com.google.firebase:firebase-messaging:10.2.1\")\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 7: Change to 11.0.0:\n" +
          "@@ -7 +7\n" +
          "-     implementation(\"com.google.firebase:firebase-messaging:10.2.1\")\n" +
          "+     implementation(\"com.google.firebase:firebase-messaging:11.0.0\")"
      )
  }

  fun testGradleStrictVersionSyntax() {
    // Regression test for 257726238
    lint()
      .files(
        gradle(
            """
                buildscript {
                    repositories {
                        jcenter()
                    }
                }
                dependencies {
                    implementation("com.google.firebase:firebase-messaging:10.2.1!!") // expect 11.0.0
                    implementation("com.google.firebase:firebase-messaging:11.0.0!!") // OK
                }
                """
          )
          .indented()
      )
      .issues(DEPENDENCY)
      .run()
      .expect(
        """
                build.gradle:7: Warning: A newer version of com.google.firebase:firebase-messaging than 10.2.1 is available: 11.0.0 [GradleDependency]
                    implementation("com.google.firebase:firebase-messaging:10.2.1!!") // expect 11.0.0
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle line 7: Change to 11.0.0:
                @@ -7 +7
                -     implementation("com.google.firebase:firebase-messaging:10.2.1!!") // expect 11.0.0
                +     implementation("com.google.firebase:firebase-messaging:11.0.0!!") // expect 11.0.0
                """
      )
  }

  fun testDependenciesWithOtherArtifacts() {
    // Regression test for b/124415929

    listOf("implementation", "testImplementation", "androidTestImplementation").forEach {
      configuration ->
      listOf(
          "com.android.support:appcompat-v7" to ("13.0.0" to "25.3.1"),
          "com.google.guava:guava" to ("11.0.2" to "21.0")
        )
        .forEach { libraryInfo ->
          val library = libraryInfo.first
          val version = libraryInfo.second.first
          val expectedVersion = libraryInfo.second.second
          listOf(false, true).forEach {
            val versionString = if (it) "\$version" else version
            val dependencyString = "$configuration(\"$library:$versionString\")"
            val source =
              gradle(
                "" +
                  "ext.version = '$version'\n" +
                  "\n" +
                  "buildscript {\n" +
                  "    repositories {\n" +
                  "        jcenter()\n" +
                  "    }\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "    $dependencyString\n" +
                  "}\n"
              )
            val expected =
              "" +
                "build.gradle:9: Warning: A newer version of $library than $version is available: $expectedVersion [GradleDependency]\n" +
                "    $dependencyString\n" +
                "    ${" ".repeat(configuration.length + 1)}" +
                "${"~".repeat(library.length + versionString.length + 3)}\n" +
                "0 errors, 1 warnings"

            lint().files(source).issues(DEPENDENCY).run().expect(expected)
          }
        }
    }
  }

  fun testVersionFromIDE() {
    // Hardcoded cache lookup for the test in GroovyGradleDetector below. In the IDE
    // it consults SDK lib.
    val expected =
      "" +
        "build.gradle:2: Warning: A newer version of com.android.support.constraint:constraint-layout than 1.0.1 is available: 1.0.2 [GradleDependency]\n" +
        "    compile 'com.android.support.constraint:constraint-layout:1.0.1'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:4: Warning: A newer version of com.android.support.constraint:constraint-layout than 1.0.3-alpha5 is available: 1.0.3-alpha8 [GradleDependency]\n" +
        "    compile 'com.android.support.constraint:constraint-layout:1.0.3-alpha5'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 2 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "dependencies {\n" +
            "    compile 'com.android.support.constraint:constraint-layout:1.0.1'\n" +
            "    compile 'com.android.support.constraint:constraint-layout:1.0.2'\n" +
            "    compile 'com.android.support.constraint:constraint-layout:1.0.3-alpha5'\n" +
            "    compile 'com.android.support.constraint:constraint-layout:1.0.+'\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expect(expected)
  }

  fun testWorkManager() {
    // The AndroidX work manager library plans to have preview versions released at the same
    // time: 2.6 for normal/stable work manager, and 2.7 for Android S preview work. They don't
    // want 2.6 preview users to get suggestions to update 2.7, so this is special cased.
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +

            // Made up versions for the various work manager artifacts
            // to make sure we can test the various upgrade scenarios
            // separately:
            // work-runtime: 2.6.0-alpha06, 2.7.0-alpha06
            // work-runtime-ktx: 2.6.0-alpha05, 2.7.0-alpha05
            // work-rxjava2: 2.6.0-alpha06, 2.7.0
            // work-rxjava3: 2.7.0-alpha06
            // work-gcm: 2.7.0-alpha05
            // work-testing: 2.8.0-alpha01
            // work-multiprocess: 2.7.0-alpha06

            // Test 2.6.0 alpha05 going up to 2.6.0 alpha6, NOT 2.7 preview
            "    implementation \"androidx.work:work-runtime:2.6.0-alpha05\" // expect 2.6.0-alpha06\n" +
            // Test 2.6.0 alpha05 (latest available) NOT going up to 2.7 preview
            "    implementation \"androidx.work:work-runtime-ktx:2.6.0-alpha05\" // No suggestion\n" +
            // Test 2.6.0 alpha05 going up to 2.7.0 (once stable)
            "    implementation \"androidx.work:work-rxjava2:2.6.0-alpha05\" // expect 2.7.0\n" +
            // Test 2.6.0 NOT going up to 2.7.0 preview
            "    implementation \"androidx.work:work-gcm:2.6.0\" // No suggestion\n" +
            // Test 2.6.0 alpha05 going up to 2.8 preview
            "    androidTestImplementation \"androidx.work:work-testing:2.6.0-alpha05\" // expect 2.7.0\n" +
            // Test 2.7.0 alpha05 going up to 2.7.0 alpha06
            "    implementation \"androidx.work:work-multiprocess:2.7.0-alpha05\" // expect 2.7.0-alpha06\n" +
            // Test normal upgrades in 2.7: 2.7.0 alpha05 going up to 2.7.0 alpha6
            "    implementation \"androidx.work:work-rxjava3:2.7.0-alpha05\" // expect 2.7.0-alpha06\n" +
            // Make sure dynamic versions also work: don't upgrade from < 2.7 to 2.7 previews
            "    implementation \"androidx.work:work-rxjava3:2.5.+\" // expect 2.6.0\n" +
            // Don't update from a preview of a previous series
            "    implementation \"androidx.work:work-runtime:2.5.0-alpha05\" // No suggestion\n" +
            // Don't update from a stable version
            "    implementation \"androidx.work:work-runtime:2.5.0\" // No suggestion\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .networkData(
        "https://maven.google.com/master-index.xml",
        """
                <?xml version='1.0' encoding='UTF-8'?>
                <metadata>
                  <androidx.core/>
                  <androidx.work/>
                </metadata>
                """
          .trimIndent()
      )
      .networkData(
        "https://maven.google.com/androidx/work/group-index.xml",
        """
                <?xml version='1.0' encoding='UTF-8'?>
                <androidx.work>
                  <work-runtime versions="2.7.0-alpha06,2.6.0-alpha06"/>
                  <work-runtime-ktx versions="2.5.0,2.7.0-alpha05,2.6.0-alpha05"/>
                  <work-rxjava2 versions="2.7.0,2.6.0-alpha06"/>
                  <work-rxjava3 versions="2.7.0-alpha06"/>
                  <work-gcm versions="2.7.0-alpha05"/>
                  <work-testing versions="2.8.0-alpha01,2.7.0"/>
                  <work-multiprocess versions="2.7.0-alpha06,2.6.0"/>
                </androidx.work>
                """
          .trimIndent()
      )
      .run()
      .expect(
        """
                build.gradle:8: Warning: A newer version of androidx.work:work-runtime than 2.6.0-alpha05 is available: 2.6.0-alpha06 [GradleDependency]
                    implementation "androidx.work:work-runtime:2.6.0-alpha05" // expect 2.6.0-alpha06
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:10: Warning: A newer version of androidx.work:work-rxjava2 than 2.6.0-alpha05 is available: 2.7.0 [GradleDependency]
                    implementation "androidx.work:work-rxjava2:2.6.0-alpha05" // expect 2.7.0
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:12: Warning: A newer version of androidx.work:work-testing than 2.6.0-alpha05 is available: 2.7.0 [GradleDependency]
                    androidTestImplementation "androidx.work:work-testing:2.6.0-alpha05" // expect 2.7.0
                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:13: Warning: A newer version of androidx.work:work-multiprocess than 2.7.0-alpha05 is available: 2.7.0-alpha06 [GradleDependency]
                    implementation "androidx.work:work-multiprocess:2.7.0-alpha05" // expect 2.7.0-alpha06
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:14: Warning: A newer version of androidx.work:work-rxjava3 than 2.7.0-alpha05 is available: 2.7.0-alpha06 [GradleDependency]
                    implementation "androidx.work:work-rxjava3:2.7.0-alpha05" // expect 2.7.0-alpha06
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 5 warnings
                """
      )
  }

  fun testSnapshots() {
    // Regression test for b/183137869:
    // Gradle special-cases the SNAPSHOT version; make sure we don't offer updates to/from it except
    // for a few minor scenarios.
    // https://docs.gradle.org/current/userguide/single_versions.html#version_ordering
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +

            // work-runtime has 2.6.0-beta05,2.7.0-alpha06,3.0.0-SNAPSHOT -- we don't want to offer
            // updates to 3.0.0-SNAPSHOT even though it's "higher"
            "    implementation \"androidx.test:work-runtime:2.7.0-alpha06\" // no suggestion\n" +
            // But we *can* update to a higher non-SNAPSHOT of the same series
            "    implementation \"androidx.test:work-runtime:2.6.0-alpha06\" // update to 2.6.0-beta05\n" +
            // For work-runtime-ktx has 2.5.0,2.6.0-alpha05; we don't want to update to SNAPSHOT
            // versions
            "    implementation \"androidx.test:work-runtime-ktx:2.6.0-SNAPSHOT\" // No suggestion\n" +
            // but from old snapshot versions we can jump to a higher version
            "    implementation \"androidx.test:work-runtime-ktx:2.3.0-SNAPSHOT\" // Update to 2.5.0\n" +
            // From a snapshot version we can jump to a stable version if it's higher
            "    implementation \"androidx.test:work-gcm:2.6.0-SNAPSHOT\" // No suggestion\n" +
            // Repeat tests for android.work, which has its own special version filtering code
            "    implementation \"androidx.work:work-runtime:2.7.0-alpha06\" // no suggestion\n" +
            "    implementation \"androidx.work:work-runtime:2.6.0-alpha06\" // update to 2.6.0-beta05\n" +
            "    implementation \"androidx.work:work-runtime-ktx:2.6.0-SNAPSHOT\" // No suggestion\n" +
            "    implementation \"androidx.work:work-runtime-ktx:2.3.0-SNAPSHOT\" // Update to 2.5.0\n" +
            "    implementation \"androidx.work:work-gcm:2.6.0-SNAPSHOT\" // No suggestion\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .networkData(
        "https://maven.google.com/master-index.xml",
        """
                <?xml version='1.0' encoding='UTF-8'?>
                <metadata>
                  <androidx.work/>
                  <androidx.test/>
                </metadata>
                """
          .trimIndent()
      )
      .networkData(
        "https://maven.google.com/androidx/work/group-index.xml",
        """
                <?xml version='1.0' encoding='UTF-8'?>
                <androidx.work>
                  <work-runtime versions="2.6.0-beta05,2.7.0-alpha06,3.0.0-SNAPSHOT"/>
                  <work-runtime-ktx versions="2.5.0,2.6.0-alpha05"/>
                  <work-gcm versions="2.6.0-SNAPSHOT"/>
                  <work-rxjava2 versions="3.0.0-SNAPSHOT,3.0.0"/>
                  <work-rxjava3 versions="3.0.0-SNAPSHOT,3.1.0-alpha01"/>
                </androidx.work>
                """
          .trimIndent()
      )
      .networkData(
        "https://maven.google.com/androidx/test/group-index.xml",
        """
                <?xml version='1.0' encoding='UTF-8'?>
                <androidx.work>
                  <work-runtime versions="2.6.0-beta05,2.7.0-alpha06,3.0.0-SNAPSHOT"/>
                  <work-runtime-ktx versions="2.5.0,2.6.0-alpha05"/>
                  <work-gcm versions="2.6.0-SNAPSHOT"/>
                  <work-rxjava2 versions="3.0.0-SNAPSHOT,3.0.0"/>
                  <work-rxjava3 versions="3.0.0-SNAPSHOT,3.1.0-alpha01"/>
                </androidx.work>
                """
          .trimIndent()
      )
      .run()
      .expect(
        """
                build.gradle:9: Warning: A newer version of androidx.test:work-runtime than 2.6.0-alpha06 is available: 2.6.0-beta05 [GradleDependency]
                    implementation "androidx.test:work-runtime:2.6.0-alpha06" // update to 2.6.0-beta05
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:11: Warning: A newer version of androidx.test:work-runtime-ktx than 2.3.0-SNAPSHOT is available: 2.5.0 [GradleDependency]
                    implementation "androidx.test:work-runtime-ktx:2.3.0-SNAPSHOT" // Update to 2.5.0
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:14: Warning: A newer version of androidx.work:work-runtime than 2.6.0-alpha06 is available: 2.6.0-beta05 [GradleDependency]
                    implementation "androidx.work:work-runtime:2.6.0-alpha06" // update to 2.6.0-beta05
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:16: Warning: A newer version of androidx.work:work-runtime-ktx than 2.3.0-SNAPSHOT is available: 2.5.0 [GradleDependency]
                    implementation "androidx.work:work-runtime-ktx:2.3.0-SNAPSHOT" // Update to 2.5.0
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 4 warnings
                """
      )
  }

  fun testGuavaVersionsAndroidVsJre() {
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 29\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            // Applicable updates: 30.1-android, 30.0-android, 29.0-android, 28.2-android,
            // 28.1-android
            "    compile 'com.google.guava:guava:24.1-android'\n" +
            // Applicable updates: 30.1-jre, 30.0-jre, 29.0-jre, 28.2-jre, 28.1-jre
            "    compile 'com.google.guava:guava:24.1-jre'\n" +
            // Applicable updates: 30.1-android, 30.0-android, 29.0-android, 28.2-android,
            // 28.1-android
            "    compile 'com.google.guava:guava:16.0-rc1'\n" +
            // Applicable updates: 30.1-android, 30.0-android, 29.0-android, 28.2-android,
            // 28.1-android
            "    compile 'com.google.guava:guava:16.0'\n" +
            "}\n"
        )
      )
      .issues(REMOTE_VERSION)
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.google.guava%22+AND+a:%22guava%22&core=gav&rows=1&wt=json",
        """{"responseHeader":{"status":0,"QTime":0,"params":{"q":"g:\"com.google.guava\" AND a:\"guava\"","core":"gav","indent":"off","fl":"id,g,a,v,p,ec,timestamp,tags","start":"","sort":"score desc,timestamp desc,g asc,a asc,v desc","rows":"1","wt":"json","version":"2.2"}},"response":{"numFound":100,"start":0,"docs":[{"id":"com.google.guava:guava:30.1-jre","g":"com.google.guava","a":"guava","v":"30.1-jre","p":"bundle","timestamp":1607961950000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]}]}}"""
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.google.guava%22+AND+a:%22guava%22&core=gav&wt=json",
        """{"responseHeader":{"status":0,"QTime":5,"params":{"q":"g:\"com.google.guava\" AND a:\"guava\"","core":"gav","indent":"off","fl":"id,g,a,v,p,ec,timestamp,tags","start":"","sort":"score desc,timestamp desc,g asc,a asc,v desc","rows":"","wt":"json","version":"2.2"}},"response":{"numFound":100,"start":0,"docs":[{"id":"com.google.guava:guava:30.1-jre","g":"com.google.guava","a":"guava","v":"30.1-jre","p":"bundle","timestamp":1607961950000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]},{"id":"com.google.guava:guava:30.1-android","g":"com.google.guava","a":"guava","v":"30.1-android","p":"bundle","timestamp":1607961275000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]},{"id":"com.google.guava:guava:30.0-jre","g":"com.google.guava","a":"guava","v":"30.0-jre","p":"bundle","timestamp":1602880862000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]},{"id":"com.google.guava:guava:30.0-android","g":"com.google.guava","a":"guava","v":"30.0-android","p":"bundle","timestamp":1602880118000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]},{"id":"com.google.guava:guava:29.0-jre","g":"com.google.guava","a":"guava","v":"29.0-jre","p":"bundle","timestamp":1586813033000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]},{"id":"com.google.guava:guava:29.0-android","g":"com.google.guava","a":"guava","v":"29.0-android","p":"bundle","timestamp":1586812496000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]},{"id":"com.google.guava:guava:28.2-jre","g":"com.google.guava","a":"guava","v":"28.2-jre","p":"bundle","timestamp":1577416125000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]},{"id":"com.google.guava:guava:28.2-android","g":"com.google.guava","a":"guava","v":"28.2-android","p":"bundle","timestamp":1577413219000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]},{"id":"com.google.guava:guava:28.1-jre","g":"com.google.guava","a":"guava","v":"28.1-jre","p":"bundle","timestamp":1567025587000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]},{"id":"com.google.guava:guava:28.1-android","g":"com.google.guava","a":"guava","v":"28.1-android","p":"bundle","timestamp":1567025039000,"ec":["-javadoc.jar","-sources.jar",".jar",".pom"],"tags":["libraries","classes","google","expanded","much","include","that","more","utility","guava","core","suite","collections"]}]}}"""
      )
      .run()
      .expect(
        """
                build.gradle:8: Warning: A newer version of com.google.guava:guava than 24.1-android is available: 30.1-android [NewerVersionAvailable]
                    compile 'com.google.guava:guava:24.1-android'
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:9: Warning: A newer version of com.google.guava:guava than 24.1-jre is available: 30.1-jre [NewerVersionAvailable]
                    compile 'com.google.guava:guava:24.1-jre'
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:10: Warning: A newer version of com.google.guava:guava than 16.0-rc1 is available: 30.1-android [NewerVersionAvailable]
                    compile 'com.google.guava:guava:16.0-rc1'
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:11: Warning: A newer version of com.google.guava:guava than 16.0 is available: 30.1-android [NewerVersionAvailable]
                    compile 'com.google.guava:guava:16.0'
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 4 warnings
                """
      )
  }

  fun testCoroutines() {
    // 171369798: False positive "Obsolete Gradle Dependency" for native version of
    // `kotlinx-coroutines-core`
    //
    // The native version of the coroutines library is versioned incorrectly (as described in
    // comment #2
    // of the above bug) but they're not changing it (see
    // https://github.com/Kotlin/kotlinx.coroutines/issues/2482)
    // so manually filter this case.

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 29\n" +
            "}\n" +
            "\n" +
            // Available versions:
            // 1.4.2-native-mt
            // 1.4.2
            // 1.4.1-native-mt
            // 1.4.1
            // 1.4.0
            // 1.4.0-M1
            // 1.3.9-native-mt-2
            // 1.3.9-native-mt
            // 1.3.8-native-mt-1.4.0-rc
            // 1.3.9
            //
            // Which means latest stable update is 1.4.2
            // Latest native-mt is 1.4.1-native-mt
            // Latest native-mt-2 is 1.3.9-native-mt-2
            "dependencies {\n" +
            // Applicable: 1.4.2, 1.4.1, 1.4.0, 1.3.9
            "    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9' // Suggest 1.4.2\n" +
            // Applicable: 1.3.9-native-mt-2
            "    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8-native-mt-2' // Suggest 1.3.9-native-mt-2\n" +
            // Applicable: 1.3.9-native-mt-2
            "    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9-native-mt-2' // Suggest nothing\n" +
            // Applicable: 1.4.2, 1.4.1, 1.4.0, 1.3.9
            "    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2' // Suggest nothing\n" +
            // Applicable: 1.4.2-native-mt, 1.4.1-native-mt, 1.3.9-native-mt,
            // 1.3.8-native-mt-1.4.0-rc
            "    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0-native-mt' // Suggest 1.4.2-native-mt\n" +
            // Applicable:
            "    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8-native-mt-1.4.0-rc'\n" +
            "}\n"
        )
      )
      .issues(REMOTE_VERSION)
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22org.jetbrains.kotlinx%22+AND+a:%22kotlinx-coroutines-core%22&core=gav&wt=json",
        """{"responseHeader":{"status":0,"QTime":1,"params":{"q":"g:\"org.jetbrains.kotlinx\" AND a:\"kotlinx-coroutines-core\"","core":"gav","indent":"off","fl":"id,g,a,v,p,ec,timestamp,tags","start":"","sort":"score desc,timestamp desc,g asc,a asc,v desc","rows":"","wt":"json","version":"2.2"}},"response":{"numFound":77,"start":0,"docs":[{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2-native-mt","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.4.2-native-mt","p":"jar","timestamp":1606484996000,"ec":["-javadoc.jar","-sources.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]},{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.4.2","p":"jar","timestamp":1606411162000,"ec":["-sources.jar","-javadoc.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]},{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.1-native-mt","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.4.1-native-mt","p":"jar","timestamp":1605797411000,"ec":["-javadoc.jar","-sources.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]},{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.1","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.4.1","p":"jar","timestamp":1604486053000,"ec":["-sources.jar","-javadoc.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]},{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.4.0","p":"jar","timestamp":1603735442000,"ec":["-sources.jar","-javadoc.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]},{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0-M1","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.4.0-M1","p":"jar","timestamp":1602593229000,"ec":["-javadoc.jar","-sources.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]},{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9-native-mt-2","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.3.9-native-mt-2","p":"jar","timestamp":1600769552000,"ec":["-sources.jar","-javadoc.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]},{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9-native-mt","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.3.9-native-mt","p":"jar","timestamp":1598043333000,"ec":["-sources.jar","-javadoc.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]},{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8-native-mt-1.4.0-rc","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.3.8-native-mt-1.4.0-rc","p":"jar","timestamp":1597826260000,"ec":["-sources.jar","-javadoc.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]},{"id":"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9","g":"org.jetbrains.kotlinx","a":"kotlinx-coroutines-core","v":"1.3.9","p":"jar","timestamp":1597402470000,"ec":["-sources.jar","-javadoc.jar",".jar",".module",".pom"],"tags":["libraries","support","kotlin","coroutines"]}]}}"""
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22org.jetbrains.kotlinx%22+AND+a:%22kotlinx-coroutines-core%22&core=gav&rows=1&wt=json",
        """{"responseHeader":{"status":0,"QTime":0,"params":{"q":"g:\"22org.jetbrains.kotlinx\" AND a:\"kotlinx-coroutines-core\"","core":"gav","indent":"off","fl":"id,g,a,v,p,ec,timestamp,tags","start":"","sort":"score desc,timestamp desc,g asc,a asc,v desc","rows":"","wt":"json","version":"2.2"}},"response":{"numFound":0,"start":0,"docs":[]}}"""
      )
      .run()
      .expect(
        """
                build.gradle:8: Warning: A newer version of org.jetbrains.kotlinx:kotlinx-coroutines-core than 1.3.9 is available: 1.4.2 [NewerVersionAvailable]
                    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9' // Suggest 1.4.2
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:9: Warning: A newer version of org.jetbrains.kotlinx:kotlinx-coroutines-core than 1.3.8-native-mt-2 is available: 1.3.9-native-mt-2 [NewerVersionAvailable]
                    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8-native-mt-2' // Suggest 1.3.9-native-mt-2
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:12: Warning: A newer version of org.jetbrains.kotlinx:kotlinx-coroutines-core than 1.4.0-native-mt is available: 1.4.2-native-mt [NewerVersionAvailable]
                    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0-native-mt' // Suggest 1.4.2-native-mt
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:13: Warning: A newer version of org.jetbrains.kotlinx:kotlinx-coroutines-core than 1.3.8-native-mt-1.4.0-rc is available: 1.4.2-native-mt [NewerVersionAvailable]
                    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8-native-mt-1.4.0-rc'
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 4 warnings
                """
      )
  }

  fun testMinSdkVersion() {
    val expected =
      ("" +
        "build.gradle:8: Warning: The value of minSdkVersion is too low. It can be incremented without noticeably reducing the number of supported devices. [MinSdkTooLow]\n" +
        "        minSdkVersion 7\n" +
        "        ~~~~~~~~~~~~~~~\n" +
        "build.gradle:9: Warning: The value of minSdkVersion is too low. It can be incremented without noticeably reducing the number of supported devices. [MinSdkTooLow]\n" +
        "        minSdk 7\n" +
        "        ~~~~~~~~\n" +
        "0 errors, 2 warnings")

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 19\n" +
            "    buildToolsVersion \"19.0.0\"\n" +
            "\n" +
            "    defaultConfig {\n" +
            "        minSdkVersion 7\n" +
            "        minSdk 7\n" +
            "        targetSdkVersion 19\n" +
            "        versionCode 1\n" +
            "        versionName \"1.0\"\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(MIN_SDK_TOO_LOW)
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
            Fix for build.gradle line 8: Update minSdkVersion to $LOWEST_ACTIVE_API:
            @@ -8 +8
            -         minSdkVersion 7
            +         minSdkVersion $LOWEST_ACTIVE_API
            Fix for build.gradle line 9: Update minSdkVersion to $LOWEST_ACTIVE_API:
            @@ -9 +9
            -         minSdk 7
            +         minSdk $LOWEST_ACTIVE_API
            """
      )
  }

  fun testIncompatiblePlugin() {
    val expected =
      "" +
        "build.gradle:6: Error: You must use a newer version of the Android Gradle plugin. The minimum supported version is " +
        GRADLE_PLUGIN_MINIMUM_VERSION +
        " and the recommended version is " +
        GRADLE_PLUGIN_RECOMMENDED_VERSION +
        " [GradlePluginVersion]\n" +
        "    classpath 'com.android.tools.build:gradle:0.1.0'\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "  repositories {\n" +
            "    mavenCentral()\n" +
            "  }\n" +
            "  dependencies {\n" +
            "    classpath 'com.android.tools.build:gradle:0.1.0'\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "allprojects {\n" +
            "  repositories {\n" +
            "    mavenCentral()\n" +
            "  }\n" +
            "}\n"
        )
      )
      .issues(GRADLE_PLUGIN_COMPATIBILITY)
      .run()
      .expect(expected)
  }

  fun testTooRecentVersion() {
    // Regression test for https://issuetracker.google.com/119210741
    // Don't offer Gradle plugin versions newer than the IDE (when running in the IDE)
    // Same (older) version of Studio and Gradle:
    // Studio 3.3, gradle: 3.3.0-alpha04: Offer latest 3.3.0, not 3.4 etc
    val expected =
      "" +
        "build.gradle:7: Warning: A newer version of com.android.tools.build:gradle than 3.3.0-alpha04 is available: 3.3.2 [AndroidGradlePluginVersion]\n" +
        "    classpath 'com.android.tools.build:gradle:3.3.0-alpha04'\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 1 warnings"

    lint()
      .files(
        gradle(
            """
                buildscript {
                  repositories {
                    google()
                    mavenCentral()
                  }
                  dependencies {
                    classpath 'com.android.tools.build:gradle:3.3.0-alpha04'
                  }
                }

                allprojects {
                  repositories {
                    mavenCentral()
                  }
                }
                """
          )
          .indented()
      )
      .issues(AGP_DEPENDENCY)
      .clientFactory({
        object : com.android.tools.lint.checks.infrastructure.TestLintClient(CLIENT_STUDIO) {
          // Studio 3.3.0
          override fun getClientRevision(): String = "3.3.0.0"
        }
      })
      .run()
      .expect(expected)
  }

  fun testTooRecentVersionInVersionCatalog() {
    val expected =
      """
            ../gradle/libs.versions.toml:2: Warning: A newer version of com.android.tools.build:gradle than 3.3.0-alpha04 is available: 3.3.2 [AndroidGradlePluginVersion]
            gradle = "  com.android.tools.build:gradle:3.3.0-alpha04  "
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """

    lint()
      .files(
        gradleToml(
            """
                [libraries]
                gradle = "  com.android.tools.build:gradle:3.3.0-alpha04  "
                """
          )
          .indented()
      )
      .issues(AGP_DEPENDENCY)
      .clientFactory {
        object : com.android.tools.lint.checks.infrastructure.TestLintClient(CLIENT_STUDIO) {
          // Studio 3.3.0
          override fun getClientRevision(): String = "3.3.0.0"
        }
      }
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
                Autofix for gradle/libs.versions.toml line 2: Change to 3.3.2:
                @@ -2 +2
                - gradle = "  com.android.tools.build:gradle:3.3.0-alpha04  "
                + gradle = "  com.android.tools.build:gradle:3.3.2  "
                """
      )
  }

  fun testTooRecentVersion2() {
    // Regression test for https://issuetracker.google.com/119210741
    // Don't offer Gradle plugin versions newer than the IDE (when running in the IDE)
    // Newer Studio than Gradle:
    // Studio 3.4, Gradle 3.3: Offer 3.4
    lint()
      .files(
        gradle(
            """
                buildscript {
                  repositories {
                    google()
                    mavenCentral()
                  }
                  dependencies {
                    classpath 'com.android.tools.build:gradle:3.3.0-alpha01'
                  }
                }

                allprojects {
                  repositories {
                    mavenCentral()
                  }
                }
                """
          )
          .indented()
      )
      .issues(AGP_DEPENDENCY)
      .clientFactory {
        object : com.android.tools.lint.checks.infrastructure.TestLintClient(CLIENT_STUDIO) {
          // Studio 3.4.0
          override fun getClientRevision(): String = "3.4.0"
        }
      }
      .run()
      .expect(
        "" +
          "build.gradle:7: Warning: A newer version of com.android.tools.build:gradle than 3.3.0-alpha01 is available: 3.4.1 [AndroidGradlePluginVersion]\n" +
          "    classpath 'com.android.tools.build:gradle:3.3.0-alpha01'\n" +
          "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "0 errors, 1 warnings"
      )
  }

  fun testTooRecentVersion3() {
    // Regression test for https://issuetracker.google.com/119210741
    // Older Studio than Gradle:
    // Studio 2.3, gradle: 3.0.0-alpha4: Already using Gradle 3.0: offer latest version of it
    lint()
      .files(
        gradle(
            """
                buildscript {
                  repositories {
                    google()
                    mavenCentral()
                  }
                  dependencies {
                    classpath 'com.android.tools.build:gradle:3.3.0-alpha04'
                  }
                }

                allprojects {
                  repositories {
                    mavenCentral()
                  }
                }
                """
          )
          .indented()
      )
      .issues(AGP_DEPENDENCY)
      .clientFactory {
        object : com.android.tools.lint.checks.infrastructure.TestLintClient(CLIENT_STUDIO) {
          // Studio 3.0.0
          override fun getClientRevision(): String = "2.3.0.0"
        }
      }
      .run()
      .expect(
        """
                build.gradle:7: Warning: A newer version of com.android.tools.build:gradle than 3.3.0-alpha04 is available: 3.3.2 [AndroidGradlePluginVersion]
                    classpath 'com.android.tools.build:gradle:3.3.0-alpha04'
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
  }

  @Suppress("GrMethodMayBeStatic", "GroovyAssignabilityCheck")
  fun testSetter() {
    val expected =
      "" +
        "build.gradle:18: Error: Bad method name: pick a unique method name which does not conflict with the implicit getters for the defaultConfig properties. For example, try using the prefix compute- instead of get-. [GradleGetter]\n" +
        "        versionCode getVersionCode\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:19: Error: Bad method name: pick a unique method name which does not conflict with the implicit getters for the defaultConfig properties. For example, try using the prefix compute- instead of get-. [GradleGetter]\n" +
        "        versionName getVersionName\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "2 errors, 0 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "def getVersionName() {\n" +
            "    \"1.0\"\n" +
            "}\n" +
            "\n" +
            "def getVersionCode() {\n" +
            "    50\n" +
            "}\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 19\n" +
            "    buildToolsVersion \"19.0.0\"\n" +
            "\n" +
            "    defaultConfig {\n" +
            "        minSdkVersion 7\n" +
            "        targetSdkVersion 17\n" +
            "        versionCode getVersionCode\n" +
            "        versionName getVersionName\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(GRADLE_GETTER)
      .ignoreUnknownGradleConstructs()
      .run()
      .expect(expected)
  }

  fun testDependencies() {
    val expected =
      "" +
        "build.gradle:24: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 21.0 [GradleDependency]\n" +
        "    freeCompile 'com.google.guava:guava:11.0.2'\n" +
        "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:25: Warning: A newer version of com.android.support:appcompat-v7 than 13.0.0 is available: 25.3.1 [GradleDependency]\n" +
        "    compile 'com.android.support:appcompat-v7:13.0.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:26: Warning: A newer version of com.google.android.support:wearable than 1.2.0 is available: 1.3.0 [GradleDependency]\n" +
        "    compile 'com.google.android.support:wearable:1.2.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:27: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]\n" +
        "    compile 'com.android.support:multidex:1.0.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:29: Warning: A newer version of com.android.support.test:runner than 0.3 is available: 0.5 [GradleDependency]\n" +
        "    androidTestCompile 'com.android.support.test:runner:0.3'\n" +
        "                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 5 warnings\n"

    lint().files(mDependencies).issues(DEPENDENCY).run().expect(expected)
  }

  fun testVersionCatalogDependencies() {
    lint()
      .files(
        gradleToml(
            """
                [versions]
                multidexVersion = "1.0.0"
                guavaVersion = { prefer = "11.0.2" }
                testRunnerVersion = { strictly = "0.3" }
                [libraries]
                appcompat1 = 'com.android.support:appcompat-v7:+'
                guava = { module = "com.google.guava:guava", version.ref="guavaVersion" }
                appcompat2 = "com.android.support:appcompat-v7:13.0.0"
                wearable = { module = "com.google.android.support:wearable", version="1.2.0" }
                multidex = { group = "com.android.support", name ="multidex", version.ref="multidexVersion" }
                testRunner = { module= "com.android.support.test:runner", version= { prefer = "0.1" } }
                testRunner2 = { module = "com.android.support.test:runner", version = { strictly ="0.3" } }
                testRunner3 = { module = "com.android.support.test:runner", version.ref ="testRunnerVersion" }
                testRunner4 = { module = "com.android.support.test:runner", version = { strictly = "[0.3,0.4)", prefer="0.35" } }
                # TODO: support rich versions expressed in non-canonical form (e.g. [0.3,0.4[ or [,])
                # testRunner5 = { module = "com.android.support.test:runner", version = "[0.3,0.4["}
                """
          )
          .indented()
      )
      .issues(DEPENDENCY)
      .run()
      .expect(
        """
            ../gradle/libs.versions.toml:2: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
            multidexVersion = "1.0.0"
                              ~~~~~~~
            ../gradle/libs.versions.toml:3: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 21.0 [GradleDependency]
            guavaVersion = { prefer = "11.0.2" }
                                      ~~~~~~~~
            ../gradle/libs.versions.toml:4: Warning: A newer version of com.android.support.test:runner than 0.3 is available: 0.5 [GradleDependency]
            testRunnerVersion = { strictly = "0.3" }
                                             ~~~~~
            ../gradle/libs.versions.toml:8: Warning: A newer version of com.android.support:appcompat-v7 than 13.0.0 is available: 25.3.1 [GradleDependency]
            appcompat2 = "com.android.support:appcompat-v7:13.0.0"
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            ../gradle/libs.versions.toml:9: Warning: A newer version of com.google.android.support:wearable than 1.2.0 is available: 1.3.0 [GradleDependency]
            wearable = { module = "com.google.android.support:wearable", version="1.2.0" }
                                                                                 ~~~~~~~
            ../gradle/libs.versions.toml:11: Warning: A newer version of com.android.support.test:runner than 0.1 is available: 0.5 [GradleDependency]
            testRunner = { module= "com.android.support.test:runner", version= { prefer = "0.1" } }
                                                                                          ~~~~~
            ../gradle/libs.versions.toml:12: Warning: A newer version of com.android.support.test:runner than 0.3 is available: 0.5 [GradleDependency]
            testRunner2 = { module = "com.android.support.test:runner", version = { strictly ="0.3" } }
                                                                                              ~~~~~
            0 errors, 7 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for gradle/libs.versions.toml line 2: Change to 1.0.1:
            @@ -2 +2
            - multidexVersion = "1.0.0"
            + multidexVersion = "1.0.1"
            Fix for gradle/libs.versions.toml line 3: Change to 21.0:
            @@ -3 +3
            - guavaVersion = { prefer = "11.0.2" }
            + guavaVersion = { prefer = "21.0" }
            Fix for gradle/libs.versions.toml line 4: Change to 0.5:
            @@ -4 +4
            - testRunnerVersion = { strictly = "0.3" }
            + testRunnerVersion = { strictly = "0.5" }
            Fix for gradle/libs.versions.toml line 8: Change to 25.3.1:
            @@ -8 +8
            - appcompat2 = "com.android.support:appcompat-v7:13.0.0"
            + appcompat2 = "com.android.support:appcompat-v7:25.3.1"
            Fix for gradle/libs.versions.toml line 9: Change to 1.3.0:
            @@ -9 +9
            - wearable = { module = "com.google.android.support:wearable", version="1.2.0" }
            + wearable = { module = "com.google.android.support:wearable", version="1.3.0" }
            Fix for gradle/libs.versions.toml line 11: Change to 0.5:
            @@ -11 +11
            - testRunner = { module= "com.android.support.test:runner", version= { prefer = "0.1" } }
            + testRunner = { module= "com.android.support.test:runner", version= { prefer = "0.5" } }
            Fix for gradle/libs.versions.toml line 12: Change to 0.5:
            @@ -12 +12
            - testRunner2 = { module = "com.android.support.test:runner", version = { strictly ="0.3" } }
            + testRunner2 = { module = "com.android.support.test:runner", version = { strictly ="0.5" } }
            """
      )
  }

  fun testLongHandDependencies() {
    val expected =
      "" +
        "build.gradle:9: Warning: A newer version of com.android.support:support-v4 than 19.0 is available: 25.3.1 [GradleDependency]\n" +
        "    compile group: 'com.android.support', name: 'support-v4', version: '19.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 1 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "    buildToolsVersion \"21.1.2\"\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile group: 'com.android.support', name: 'support-v4', version: '19.0'\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expect(expected)
  }

  fun testChromeOSAbiFilters() {
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "    buildToolsVersion \"21.1.2\"\n" +
            "    defaultConfig {\n" +
            "       minSdkVersion 15\n" +
            "       targetSdkVersion 17\n" +
            "       ndk {\n" +
            "           abiFilters 'arm64-v8a', 'x86_64', 'x86'\n" +
            "       }\n" +
            "   }\n" +
            "}\n"
        )
      )
      .issues(CHROMEOS_ABI_SUPPORT)
      .run()
      .expectClean()
  }

  fun testChromeOSAbiFiltersMissingX8664() {
    val expected =
      "build.gradle:10: Warning: Missing x86_64 ABI support for ChromeOS [ChromeOsAbiSupport]\n" +
        "           abiFilters 'arm64-v8a'\n" +
        "                      ~~~~~~~~~~~\n" +
        "0 errors, 1 warnings"
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 21\n" +
            "    buildToolsVersion \"21.1.2\"\n" +
            "    defaultConfig {\n" +
            "       minSdkVersion 15\n" +
            "       targetSdkVersion 17\n" +
            "       ndk {\n" +
            "           abiFilters 'arm64-v8a'\n" +
            "       }\n" +
            "   }\n" +
            "}\n"
        )
      )
      .issues(CHROMEOS_ABI_SUPPORT)
      .run()
      .expect(expected)
  }

  fun testChromeOSAbiSplits() {
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 21\n" +
            "    buildToolsVersion \"21.1.2\"\n" +
            "    splits {\n" +
            "       abi {\n" +
            "           include 'arm64-v8a', 'x86_64', 'x86'\n" +
            "       }\n" +
            "   }\n" +
            "}\n"
        )
      )
      .issues(CHROMEOS_ABI_SUPPORT)
      .run()
      .expectClean()
  }

  fun testChromeOSAbiSplitsMissingX8664() {
    val expected =
      "build.gradle:8: Warning: Missing x86_64 ABI support for ChromeOS [ChromeOsAbiSupport]\n" +
        "           include 'arm64-v8a'\n" +
        "                   ~~~~~~~~~~~\n" +
        "0 errors, 1 warnings"
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 21\n" +
            "    buildToolsVersion \"21.1.2\"\n" +
            "    splits {\n" +
            "       abi {\n" +
            "           include \'arm64-v8a\'\n" +
            "       }\n" +
            "   }\n" +
            "}\n"
        )
      )
      .issues(CHROMEOS_ABI_SUPPORT)
      .run()
      .expect(expected)
  }

  fun testLargeScreenIncorrectDependencies() {
    val expected =
      "build.gradle:9: Warning: Upgrade androidx.slidingpanelayout for keyboard and mouse support [GradleDependency]\n" +
        "    compile 'androidx.slidingpanelayout:slidingpanelayout:1.1.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:10: Warning: Upgrade androidx.compose.foundation for keyboard and mouse support [GradleDependency]\n" +
        "    compile 'androidx.compose.foundation:foundation:1.1.1'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 2 warnings"
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "    buildToolsVersion \"21.1.2\"\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'androidx.slidingpanelayout:slidingpanelayout:1.1.0'\n" +
            "    compile 'androidx.compose.foundation:foundation:1.1.1'\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expect(expected)
  }

  fun testLargeScreenCorrectDependencies() {
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "    buildToolsVersion \"21.1.2\"\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'androidx.slidingpanelayout:slidingpanelayout:1.2.0'\n" +
            "    compile 'androidx.compose.foundation:foundation:1.2.1'\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expectClean()
  }

  fun testDependenciesMinSdkVersion() {
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "\n" +
            "    defaultConfig {\n" +
            "        minSdkVersion 15\n" +
            "        targetSdkVersion 17\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'com.android.support:appcompat-v7:+'\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expectClean()
  }

  fun testDependenciesMinSdkVersionLollipop() {

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "\n" +
            "    defaultConfig {\n" +
            "        minSdkVersion 15\n" +
            "        targetSdkVersion 17\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'com.android.support:appcompat-v7:+'\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expectClean()
  }

  fun testDependenciesNoMicroVersion() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=77594
    val expected =
      "" +
        "build.gradle:13: Warning: A newer version of com.google.code.gson:gson than 2.2 is available: 2.8.2 [GradleDependency]\n" +
        "    compile 'com.google.code.gson:gson:2.2'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 1 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "\n" +
            "    defaultConfig {\n" +
            "        minSdkVersion 15\n" +
            "        targetSdkVersion 17\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'com.google.code.gson:gson:2.2'\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expect(expected)
  }

  fun testPaths() {
    val expected =
      "" +
        "build.gradle:4: Warning: Do not use Windows file separators in .gradle files; use / instead [GradlePath]\n" +
        "    compile files('my\\\\libs\\\\http.jar')\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:5: Warning: Avoid using absolute paths in .gradle files [GradlePath]\n" +
        "    compile files('/libs/android-support-v4.jar')\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 2 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "dependencies {\n" +
            "    compile files('my\\\\libs\\\\http.jar')\n" +
            "    compile files('/libs/android-support-v4.jar')\n" +
            "}\n"
        )
      )
      .issues(PATH)
      .ignoreUnknownGradleConstructs()
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
            Fix for build.gradle line 4: Replace with my/libs/http.jar:
            @@ -4 +4
            -     compile files('my\\libs\\http.jar')
            +     compile files('my/libs/http.jar')
            """
      )
  }

  fun testPathsKts() {
    val expected =
      "" +
        "build.gradle.kts:6: Warning: Do not use Windows file separators in .gradle files; use / instead [GradlePath]\n" +
        "    compile(files(\"my\\\\libs\\\\http.jar\"))\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle.kts:7: Warning: Avoid using absolute paths in .gradle files [GradlePath]\n" +
        "    compile(files(\"/libs/android-support-v4.jar\"))\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 2 warnings\n"

    lint()
      .files(
        kts(
          "" +
            "plugins {\n" +
            "    id(\"com.android.application\")\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile(files(\"my\\\\libs\\\\http.jar\"))\n" +
            "    compile(files(\"/libs/android-support-v4.jar\"))\n" +
            "}\n"
        )
      )
      .issues(PATH)
      .ignoreUnknownGradleConstructs()
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
            Fix for build.gradle.kts line 6: Replace with my/libs/http.jar:
            @@ -6 +6
            -     compile(files("my\\libs\\http.jar"))
            +     compile(files("my/libs/http.jar"))
            """
      )
  }

  fun testIdSuffix() {
    val expected =
      "" +
        "build.gradle:6: Warning: Application ID suffix should probably start with a \".\" [GradlePath]\n" +
        "            applicationIdSuffix \"debug\"\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 1 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    buildTypes {\n" +
            "        debug {\n" +
            "            applicationIdSuffix \"debug\"\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(PATH)
      .run()
      .expect(expected)
  }

  fun testPackage() {
    val expected =
      "" +
        "build.gradle:5: Warning: Deprecated: Replace 'packageName' with 'applicationId' [GradleDeprecated]\n" +
        "        packageName 'my.pkg'\n" +
        "        ~~~~~~~~~~~\n" +
        "build.gradle:9: Warning: Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix' [GradleDeprecated]\n" +
        "            packageNameSuffix \".debug\"\n" +
        "            ~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 2 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    defaultConfig {\n" +
            "        packageName 'my.pkg'\n" +
            "    }\n" +
            "    buildTypes {\n" +
            "        debug {\n" +
            "            packageNameSuffix \".debug\"\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(DEPRECATED)
      .run()
      .expect(expected)
      .expectFixDiffs(
        "Autofix for build.gradle line 5: Replace 'packageName' with 'applicationId':\n" +
          "@@ -5 +5\n" +
          "-         packageName 'my.pkg'\n" +
          "+         applicationId 'my.pkg'\n" +
          "Autofix for build.gradle line 9: Replace 'packageNameSuffix' with 'applicationIdSuffix':\n" +
          "@@ -9 +9\n" +
          "-             packageNameSuffix \".debug\"\n" +
          "+             applicationIdSuffix \".debug\""
      )
  }

  fun testPlus() {
    val expected =
      "" +
        "build.gradle:9: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:+) [GradleDynamicVersion]\n" +
        "    compile 'com.android.support:appcompat-v7:+'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:10: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:support-v4:21.0.+) [GradleDynamicVersion]\n" +
        "    compile group: 'com.android.support', name: 'support-v4', version: '21.0.+'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:11: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:+@aar) [GradleDynamicVersion]\n" +
        "    compile 'com.android.support:appcompat-v7:+@aar'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 3 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 19\n" +
            "    buildToolsVersion \"19.0.1\"\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'com.android.support:appcompat-v7:+'\n" +
            "    compile group: 'com.android.support', name: 'support-v4', version: '21.0.+'\n" +
            "    compile 'com.android.support:appcompat-v7:+@aar'\n" +
            "}\n"
        )
      )
      .issues(PLUS)
      .run()
      .expect(expected)
  }

  fun testStringInt() {
    val expected =
      "" +
        "build.gradle:4: Error: Use an integer rather than a string here (replace '19' with just 19) [StringShouldBeInt]\n" +
        "    compileSdkVersion '19'\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:7: Error: Use an integer rather than a string here (replace '8' with just 8) [StringShouldBeInt]\n" +
        "        minSdkVersion '8'\n" +
        "        ~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:8: Error: Use an integer rather than a string here (replace \"16\" with just 16) [StringShouldBeInt]\n" +
        "        targetSdkVersion \"16\"\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:10: Error: Use an integer rather than a string here (replace '19' with just 19) [StringShouldBeInt]\n" +
        "    compileSdk '19'\n" +
        "    ~~~~~~~~~~~~~~~\n" +
        "4 errors, 0 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion '19'\n" +
            "    buildToolsVersion \"19.0.1\"\n" +
            "    defaultConfig {\n" +
            "        minSdkVersion '8'\n" +
            "        targetSdkVersion \"16\"\n" +
            "    }\n" +
            "    compileSdk '19'\n" +
            "}\n"
        )
      )
      .issues(STRING_INTEGER)
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 4: Replace with integer:\n" +
          "@@ -4 +4\n" +
          "-     compileSdkVersion '19'\n" +
          "+     compileSdkVersion 19\n" +
          "Fix for build.gradle line 7: Replace with integer:\n" +
          "@@ -7 +7\n" +
          "-         minSdkVersion '8'\n" +
          "+         minSdkVersion 8\n" +
          "Fix for build.gradle line 8: Replace with integer:\n" +
          "@@ -8 +8\n" +
          "-         targetSdkVersion \"16\"\n" +
          "+         targetSdkVersion 16\n" +
          "Fix for build.gradle line 10: Replace with integer:\n" +
          "@@ -10 +10\n" +
          "-     compileSdk '19'\n" +
          "+     compileSdk 19"
      )
  }

  fun testCompileSdkString() {
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 19 // OK\n" +
            "    compileSdkPreview 'android-S' // OK\n" +
            "    compileSdk 19 // OK\n" +
            "    compileSdk 'android-S' // ERROR\n" +
            "}\n"
        )
      )
      .issues(STRING_INTEGER)
      .run()
      .expect(
        """
            build.gradle:7: Error: compileSdk does not support strings; did you mean compileSdkPreview ? [StringShouldBeInt]
                compileSdk 'android-S' // ERROR
                ~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for build.gradle line 7: Replace with compileSdkPreview:
            @@ -7 +7
            -     compileSdk 'android-S' // ERROR
            +     compileSdkPreview 'android-S' // ERROR
            """
      )
  }

  fun testSuppressLine2() {
    lint()
      .files(
        gradle(
          "" +
            "//noinspection GradleDeprecated\n" +
            "apply plugin: 'android'\n" +
            "\n" +
            "android {\n" +
            "}\n"
        )
      )
      .run()
      .expectClean()
  }

  fun testSuppressWithAnnotation() {
    lint()
      .files(
        kts(
          "" +
            "@Suppress(\"GradleDeprecated\")\n" +
            "plugins { id(\"android\") }\n" +
            "\n" +
            "android {\n" +
            "}\n"
        )
      )
      .run()
      .expectClean()
  }

  fun testDeprecatedPluginId() {
    val expected =
      "" +
        "build.gradle:4: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]\n" +
        "apply plugin: 'android'\n" +
        "~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:5: Warning: 'android-library' is deprecated; use 'com.android.library' instead [GradleDeprecated]\n" +
        "apply plugin: 'android-library'\n" +
        "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 2 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "apply plugin: 'com.android.library'\n" +
            "apply plugin: 'java'\n" +
            "apply plugin: 'android'\n" +
            "apply plugin: 'android-library'\n" +
            "\n" +
            "android {\n" +
            "}\n"
        )
      )
      .issues(DEPRECATED)
      .ignoreUnknownGradleConstructs()
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Autofix for build.gradle line 4: Replace with com.android.application:\n" +
          "@@ -4 +4\n" +
          "- apply plugin: 'android'\n" +
          "+ apply plugin: 'com.android.application'\n" +
          "Autofix for build.gradle line 5: Replace with com.android.library:\n" +
          "@@ -5 +5\n" +
          "- apply plugin: 'android-library'\n" +
          "+ apply plugin: 'com.android.library'\n"
      )
  }

  fun testIgnoresGStringsInDependencies() {
    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "  ext.androidGradleVersion = '0.11.0'\n" +
            "  dependencies {\n" +
            "    classpath \"com.android.tools.build:gradle:\$androidGradleVersion\"\n" +
            "  }\n" +
            "}\n"
        )
      )
      .ignoreUnknownGradleConstructs()
      .run()
      .expectClean()
  }

  fun testAccidentalOctal() {
    val expected =
      "" +
        "build.gradle:13: Error: The leading 0 turns this number into octal which is probably not what was intended (interpreted as 8) [AccidentalOctal]\n" +
        "        versionCode 010\n" +
        "                    ~~~\n" +
        "1 errors, 0 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    defaultConfig {\n" +
            "        // Ok: not octal\n" +
            "        versionCode 1\n" +
            "        versionCode 10\n" +
            "        versionCode 100\n" +
            "        // ok: octal == decimal\n" +
            "        versionCode 01\n" +
            "\n" +
            "        // Errors\n" +
            "        versionCode 010\n" +
            "\n" +
            "        // Lint Groovy Bug:\n" +
            "        versionCode 01 // line suffix comments are not handled correctly\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(ACCIDENTAL_OCTAL)
      .run()
      .expect(expected)
  }

  fun testBadPlayServicesVersion() {
    val expected =
      "" +
        "build.gradle:5: Error: Version 5.2.08 should not be used; the app can not be published with this version. Use version 11.1.71 instead. [GradleCompatible]\n" +
        "    compile 'com.google.android.gms:play-services:5.2.08'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'android'\n" +
            "\n" +
            "dependencies {\n" +
            "\n" +
            "    compile 'com.google.android.gms:play-services:5.2.08'\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 5: Change to 11.1.71:\n" +
          "@@ -5 +5\n" +
          "-     compile 'com.google.android.gms:play-services:5.2.08'\n" +
          "+     compile 'com.google.android.gms:play-services:11.1.71'\n"
      )
  }

  fun testRemoteVersions() {
    val expected =
      "" +
        "build.gradle:9: Warning: A newer version of joda-time:joda-time than 2.1 is available: 2.9.9 [NewerVersionAvailable]\n" +
        "    compile 'joda-time:joda-time:2.1'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:10: Warning: A newer version of com.squareup.dagger:dagger than 1.2.0 is available: 1.2.5 [NewerVersionAvailable]\n" +
        "    compile 'com.squareup.dagger:dagger:1.2.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 2 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 19\n" +
            "    buildToolsVersion \"19.0.0\"\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'joda-time:joda-time:2.1'\n" +
            "    compile 'com.squareup.dagger:dagger:1.2.0'\n" +
            "}\n"
        )
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22joda-time%22+AND+a:%22joda-time%22&core=gav&wt=json",
        "" +
          "{\"responseHeader\":" +
          "{\"status\":0,\"QTime\":0,\"params\":" +
          "{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"joda-time\\\" AND a:\\\"joda-time\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"version\":\"2.2\"}}," +
          "\"response\":" +
          "{\"numFound\":34,\"start\":0,\"docs\":[" +
          "{\"id\":\"joda-time:joda-time:2.9.9\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.9\",\"p\":\"jar\",\"timestamp\":1490275993000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb.jar\",\"-sources.jar\",\"-no-tzdb-javadoc.jar\",\"-javadoc.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.8\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.8\",\"p\":\"jar\",\"timestamp\":1490220931000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb.jar\",\"-sources.jar\",\"-no-tzdb-javadoc.jar\",\"-javadoc.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.7\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.7\",\"p\":\"jar\",\"timestamp\":1482188123000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-no-tzdb-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.6\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.6\",\"p\":\"jar\",\"timestamp\":1478812169000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb-javadoc.jar\",\"-no-tzdb.jar\",\"-sources.jar\",\"-javadoc.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.5\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.5\",\"p\":\"jar\",\"timestamp\":1478191007000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb-javadoc.jar\",\"-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\"-no-tzdb-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.4\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.4\",\"p\":\"jar\",\"timestamp\":1464341135000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-no-tzdb.jar\",\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.3\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.3\",\"p\":\"jar\",\"timestamp\":1459107331000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.2\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.2\",\"p\":\"jar\",\"timestamp\":1453988648000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9.1\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9.1\",\"p\":\"jar\",\"timestamp\":1447329806000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-no-tzdb.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"joda-time:joda-time:2.9\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.9\",\"p\":\"jar\",\"timestamp\":1445680109000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-sources.jar\",\"-no-tzdb.jar\",\"-javadoc.jar\",\".jar\",\".pom\"]}]}}"
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.squareup.dagger%22+AND+a:%22dagger%22&core=gav&wt=json",
        "" +
          "{\"responseHeader\":" +
          "{\"status\":0,\"QTime\":0,\"params\":" +
          "{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"com.squareup.dagger\\\" AND a:\\\"dagger\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"version\":\"2.2\"}}," +
          "\"response\":" +
          "{\"numFound\":9,\"start\":0,\"docs\":[" +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.5\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.5\",\"p\":\"jar\",\"timestamp\":1462852968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.4\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.4\",\"p\":\"jar\",\"timestamp\":1462291775000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-tests.jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.3\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.3\",\"p\":\"jar\",\"timestamp\":1462238813000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\"-tests.jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.2\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.2\",\"p\":\"jar\",\"timestamp\":1405987370000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.1\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.1\",\"p\":\"jar\",\"timestamp\":1392614597000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.2.0\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.0\",\"p\":\"jar\",\"timestamp\":1386979272000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-tests.jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.1.0\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.1.0\",\"p\":\"jar\",\"timestamp\":1375745812000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\"],\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\"-tests.jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.0.1\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.0.1\",\"p\":\"jar\",\"timestamp\":1370304793000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.squareup.dagger:dagger:1.0.0\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.0.0\",\"p\":\"jar\",\"timestamp\":1367941344000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\".pom\"]}]}}"
      )
      .issues(REMOTE_VERSION)
      .run()
      .expect(expected)
  }

  fun testRemoteVersionsWithPreviews() {
    // If the most recent version is a rc version, query for all versions

    val expected =
      "" +
        "build.gradle:9: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 23.6-android [NewerVersionAvailable]\n" +
        "    compile 'com.google.guava:guava:11.0.2'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:10: Warning: A newer version of com.google.guava:guava than 16.0-rc1 is available: 23.6-android [NewerVersionAvailable]\n" +
        "    compile 'com.google.guava:guava:16.0-rc1'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 2 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 19\n" +
            "    buildToolsVersion \"19.0.0\"\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'com.google.guava:guava:11.0.2'\n" +
            "    compile 'com.google.guava:guava:16.0-rc1'\n" +
            "}\n"
        )
      )
      .issues(REMOTE_VERSION)
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.google.guava%22+AND+a:%22guava%22&core=gav&rows=1&wt=json",
        "{\"responseHeader\":{\"status\":0,\"QTime\":0,\"params\":{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"com.google.guava\\\" AND a:\\\"guava\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"rows\":\"1\",\"version\":\"2.2\"}},\"response\":{\"numFound\":38,\"start\":0,\"docs\":[{\"id\":\"com.google.guava:guava:18.0-rc1\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"18.0-rc1\",\"p\":\"bundle\",\"timestamp\":1407266204000,\"tags\":[\"spec\",\"libraries\",\"classes\",\"google\",\"code\",\"expanded\",\"much\",\"include\",\"annotation\",\"dependency\",\"that\",\"more\",\"utility\",\"guava\",\"javax\",\"only\",\"core\",\"suite\",\"collections\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\"-site.jar\",\".pom\"]}]}}"
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.google.guava%22+AND+a:%22guava%22&core=gav&wt=json",
        "{\"responseHeader\":{\"status\":0,\"QTime\":0,\"params\":{\"q\":\"g:\\\"com.google.guava\\\" AND a:\\\"guava\\\"\",\"core\":\"gav\",\"indent\":\"off\",\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"wt\":\"json\",\"version\":\"2.2\"}},\"response\":{\"numFound\":68,\"start\":0,\"docs\":[{\"id\":\"com.google.guava:guava:23.6-jre\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.6-jre\",\"p\":\"bundle\",\"timestamp\":1513818220000,\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]},{\"id\":\"com.google.guava:guava:23.6-android\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.6-android\",\"p\":\"bundle\",\"timestamp\":1513817611000,\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]},{\"id\":\"com.google.guava:guava:23.5-jre\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.5-jre\",\"p\":\"bundle\",\"timestamp\":1511382806000,\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]},{\"id\":\"com.google.guava:guava:23.5-android\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.5-android\",\"p\":\"bundle\",\"timestamp\":1511382148000,\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]},{\"id\":\"com.google.guava:guava:23.4-jre\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.4-jre\",\"p\":\"bundle\",\"timestamp\":1510248931000,\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]},{\"id\":\"com.google.guava:guava:23.4-android\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.4-android\",\"p\":\"bundle\",\"timestamp\":1510248248000,\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]},{\"id\":\"com.google.guava:guava:23.3-jre\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.3-jre\",\"p\":\"bundle\",\"timestamp\":1509048371000,\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]},{\"id\":\"com.google.guava:guava:23.3-android\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.3-android\",\"p\":\"bundle\",\"timestamp\":1509047759000,\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]},{\"id\":\"com.google.guava:guava:23.2-jre\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.2-jre\",\"p\":\"bundle\",\"timestamp\":1507762486000,\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]},{\"id\":\"com.google.guava:guava:23.2-android\",\"g\":\"com.google.guava\",\"a\":\"guava\",\"v\":\"23.2-android\",\"p\":\"bundle\",\"timestamp\":1507761822000,\"ec\":[\"-sources.jar\",\"-javadoc.jar\",\".jar\",\".pom\"],\"tags\":[\"libraries\",\"classes\",\"google\",\"expanded\",\"much\",\"include\",\"that\",\"more\",\"utility\",\"guava\",\"core\",\"suite\",\"collections\"]}]}}"
      )
      .run()
      .expect(expected)
  }

  fun testPreviewVersions() {
    val expected =
      "" +
        "build.gradle:7: Error: You must use a newer version of the Android Gradle plugin. The minimum supported version is " +
        GRADLE_PLUGIN_MINIMUM_VERSION +
        " and the recommended version is " +
        GRADLE_PLUGIN_RECOMMENDED_VERSION +
        " [GradlePluginVersion]\n" +
        "        classpath 'com.android.tools.build:gradle:1.0.0-rc8'\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:8: Warning: A newer version of com.android.tools.build:gradle than 3.2.1 is available: 3.5.0 [AndroidGradlePluginVersion]\n" +
        "        classpath 'com.android.tools.build:gradle:3.2.1'\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:9: Warning: A newer version of com.android.tools.build:gradle than 3.3.0-alpha04 is available: 3.5.0 [AndroidGradlePluginVersion]\n" +
        "        classpath 'com.android.tools.build:gradle:3.3.0-alpha04'\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 2 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "    repositories {\n" +
            "        google()\n" +
            "        jcenter()\n" +
            "    }\n" +
            "    dependencies {\n" +
            "        classpath 'com.android.tools.build:gradle:1.0.0-rc8'\n" +
            "        classpath 'com.android.tools.build:gradle:3.2.1'\n" +
            "        classpath 'com.android.tools.build:gradle:3.3.0-alpha04'\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "allprojects {\n" +
            "    repositories {\n" +
            "        jcenter()\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(AGP_DEPENDENCY, GRADLE_PLUGIN_COMPATIBILITY)
      .run()
      .expect(expected)
  }

  fun testPreviewVersionsKts() {
    val expected =
      "" +
        "build.gradle.kts:7: Error: You must use a newer version of the Android Gradle plugin. The minimum supported version is " +
        GRADLE_PLUGIN_MINIMUM_VERSION +
        " and the recommended version is " +
        GRADLE_PLUGIN_RECOMMENDED_VERSION +
        " [GradlePluginVersion]\n" +
        "        classpath(\"com.android.tools.build:gradle:1.0.0-rc8\")\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle.kts:8: Warning: A newer version of com.android.tools.build:gradle than 3.2.1 is available: 3.5.0 [AndroidGradlePluginVersion]\n" +
        "        classpath(\"com.android.tools.build:gradle:3.2.1\")\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle.kts:9: Warning: A newer version of com.android.tools.build:gradle than 3.3.0-alpha04 is available: 3.5.0 [AndroidGradlePluginVersion]\n" +
        "        classpath(\"com.android.tools.build:gradle:3.3.0-alpha04\")\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 2 warnings\n"

    lint()
      .files(
        kts(
          "" +
            "buildscript {\n" +
            "    repositories {\n" +
            "        google()\n" +
            "        jcenter()\n" +
            "    }\n" +
            "    dependencies {\n" +
            "        classpath(\"com.android.tools.build:gradle:1.0.0-rc8\")\n" +
            "        classpath(\"com.android.tools.build:gradle:3.2.1\")\n" +
            "        classpath(\"com.android.tools.build:gradle:3.3.0-alpha04\")\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "allprojects {\n" +
            "    repositories {\n" +
            "        jcenter()\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(AGP_DEPENDENCY, GRADLE_PLUGIN_COMPATIBILITY)
      .run()
      .expect(expected)
  }

  fun testPreviewVersionsNoGoogleMaven() {
    // regression test for b/144442233: if we don't have google() in buildscript repositories,
    // we probably shouldn't unconditionally update AGP version dependencies.
    val expected =
      "" +
        "build.gradle:6: Error: You must use a newer version of the Android Gradle plugin. The minimum supported version is " +
        GRADLE_PLUGIN_MINIMUM_VERSION +
        " and the recommended version is " +
        GRADLE_PLUGIN_RECOMMENDED_VERSION +
        " [GradlePluginVersion]\n" +
        "        classpath 'com.android.tools.build:gradle:1.0.0-rc8'\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "    repositories {\n" +
            "        jcenter()\n" +
            "    }\n" +
            "    dependencies {\n" +
            "        classpath 'com.android.tools.build:gradle:1.0.0-rc8'\n" +
            "        classpath 'com.android.tools.build:gradle:3.3.0'\n" +
            "        classpath 'com.android.tools.build:gradle:3.4.0-alpha04'\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "allprojects {\n" +
            "    repositories {\n" +
            "        jcenter()\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY, GRADLE_PLUGIN_COMPATIBILITY)
      .run()
      .expect(expected)
      .expectFixDiffs("")
  }

  fun testPreviewVersionsNoGoogleMavenKts() {
    // regression test for b/144442233, Kts version: check that we can detect and react to
    // 0-argument calls, even if it's unrealistic that any .build.kts file suffers from this
    // precise problem.
    val expected =
      "" +
        "build.gradle.kts:6: Error: You must use a newer version of the Android Gradle plugin. The minimum supported version is " +
        GRADLE_PLUGIN_MINIMUM_VERSION +
        " and the recommended version is " +
        GRADLE_PLUGIN_RECOMMENDED_VERSION +
        " [GradlePluginVersion]\n" +
        "        classpath(\"com.android.tools.build:gradle:1.0.0-rc8\")\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"

    lint()
      .files(
        kts(
          "" +
            "buildscript {\n" +
            "    repositories {\n" +
            "        jcenter()\n" +
            "    }\n" +
            "    dependencies {\n" +
            "        classpath(\"com.android.tools.build:gradle:1.0.0-rc8\")\n" +
            "        classpath(\"com.android.tools.build:gradle:3.3.0\")\n" +
            "        classpath(\"com.android.tools.build:gradle:3.4.0-alpha04\")\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "allprojects {\n" +
            "    repositories {\n" +
            "        jcenter()\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY, GRADLE_PLUGIN_COMPATIBILITY)
      .run()
      .expect(expected)
      .expectFixDiffs("")
  }

  fun testDependenciesInVariables() {
    val expected =
      "" +
        "build.gradle:10: Warning: A newer version of com.google.android.gms:play-services-wearable than 5.0.77 is available: 6.1.71 [GradleDependency]\n" +
        "    compile \"com.google.android.gms:play-services-wearable:\${GPS_VERSION}\"\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 1 warnings\n"
    lint()
      .files(
        source(
          "build.gradle",
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion $HIGHEST_KNOWN_STABLE_API\n" +
            "}\n" +
            "\n" +
            "final GPS_VERSION = '5.0.77'\n" +
            "\n" +
            "dependencies {\n" +
            "    compile \"com.google.android.gms:play-services-wearable:\${GPS_VERSION}\"\n" +
            "}\n"
        ),
        gradle(
          "internal-only.gradle",
          "" +
            // Not part of the lint check; used only to provide a mock model to
            // the infrastructure
            "dependencies {\n" +
            "    compile 'com.google.android.gms:play-services-wearable:5.0.77'\n" +
            "}"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expect(expected)
  }

  fun testPlayServiceConsistency() {
    val expected =
      "" +
        "build.gradle:4: Error: All gms/firebase libraries must use the exact same version specification (mixing versions can lead to runtime crashes). Found versions 7.5.0, 7.3.0. Examples include com.google.android.gms:play-services-wearable:7.5.0 and com.google.android.gms:play-services-location:7.3.0 [GradleCompatible]\n" +
        "    compile 'com.google.android.gms:play-services-wearable:7.5.0'\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'android'\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'com.google.android.gms:play-services-wearable:7.5.0'\n" +
            "    compile 'com.google.android.gms:play-services-location:7.3.0'\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .incremental()
      .run()
      .expect(expected)
  }

  // TODO(b/158677029): Uncomment and fix when either made to work without the dependency
  //                    hierarchy or when the hiearchy is available.
  fun /*test*/ SupportLibraryConsistencyWithDataBinding() {
    val expected =
      "" +
        "build.gradle:3: Error: All com.android.support libraries must use the exact " +
        "same version specification (mixing versions can lead to runtime crashes). " +
        "Found versions 25.0.0, 21.0.3. Examples include " +
        "com.android.support:recyclerview-v7:25.0.0 and " +
        "com.android.support:support-v4:21.0.3. " +
        "Note that this project is using data binding " +
        "(com.android.databinding:library:1.3.1) which pulls in " +
        "com.android.support:support-v4:21.0.3. " +
        "You can try to work around this by adding an explicit dependency on" +
        " com.android.support:support-v4:25.0.0 [GradleCompatible]\n" +
        "    compile \"com.android.support:recyclerview-v7:25.0.0\"\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"
    lint()
      .projects(
        project(
            gradle(
              "" +
                "apply plugin: 'android'\n" +
                "dependencies {\n" +
                "    compile \"com.android.support:recyclerview-v7:25.0.0\"\n" +
                "    compile \"com.android.databinding:library:1.3.1\"\n" +
                "    compile \"com.android.databinding:baseLibrary:2.3.0-alpha2\"\n" +
                "}\n"
            )
          )
          .withDependencyGraph(
            "" +
              "+--- com.android.support:recyclerview-v7:25.0.0\n" +
              "|    +--- com.android.support:support-annotations:25.0.0\n" +
              "|    +--- com.android.support:support-compat:25.0.0\n" +
              "|    |    \\--- com.android.support:support-annotations:25.0.0\n" +
              "|    \\--- com.android.support:support-core-ui:25.0.0\n" +
              "|         \\--- com.android.support:support-compat:25.0.0 (*)\n" +
              "+--- com.android.databinding:library:1.3.1\n" +
              "|    +--- com.android.support:support-v4:21.0.3\n" +
              "|    |    \\--- com.android.support:support-annotations:21.0.3 -> 25.0.0\n" +
              "|    \\--- com.android.databinding:baseLibrary:2.3.0-dev -> 2.3.0-alpha2\n" +
              "+--- com.android.databinding:baseLibrary:2.3.0-alpha2\n" +
              "\\--- com.android.databinding:adapters:1.3.1\n" +
              "     +--- com.android.databinding:library:1.3 -> 1.3.1 (*)\n" +
              "     \\--- com.android.databinding:baseLibrary:2.3.0-dev -> 2.3.0-alpha2"
          )
      )
      .issues(COMPATIBILITY)
      .incremental()
      .run()
      .expect(expected)
  }

  fun testWearableConsistency1() {
    // Regression test 1 for b/29006320.
    val expected =
      "" +
        "build.gradle:4: Error: Project depends on com.google.android.support:wearable:2.0.0-alpha3, so it must also depend (as a provided dependency) on com.google.android.wearable:wearable:2.0.0-alpha3 [GradleCompatible]\n" +
        "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'android'\n" +
            "\n" +
            "dependencies {\n" +
            "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .incremental("build.gradle")
      .run()
      .expect(expected)
  }

  fun testWearableConsistency2() {
    // Regression test 2 for b/29006320.
    val expected =
      "" +
        "build.gradle:4: Error: The wearable libraries for com.google.android.support and com.google.android.wearable must use exactly the same versions; found 2.0.0-alpha3 and 2.0.0-alpha4 [GradleCompatible]\n" +
        "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'android'\n" +
            "\n" +
            "dependencies {\n" +
            "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n" +
            "    provided \"com.google.android.wearable:wearable:2.0.0-alpha4\"\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .incremental()
      .run()
      .expect(expected)
  }

  fun testWearableConsistency3() {
    // Regression test 3 for b/29006320.
    val expected =
      "" +
        "build.gradle:4: Error: This dependency should be marked as compileOnly, not compile [GradleCompatible]\n" +
        "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'android'\n" +
            "\n" +
            "dependencies {\n" +
            "    compile \"com.google.android.support:wearable:2.0.0-alpha3\"\n" +
            "    compile \"com.google.android.wearable:wearable:2.0.0-alpha3\"\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .incremental()
      .run()
      .expect(expected)
  }

  fun testSupportLibraryNotFatal() {
    // In fatal-only issue mode should not be reporting these
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'android'\n" +
            "\n" +
            "dependencies {\n" +
            "    compile \"com.android.support:appcompat-v7:24.2\"\n" +
            "    compile \"com.android.support:support-v13:24.1\"\n" +
            "    compile \"com.android.support:preference-v7:25.0-SNAPSHOT\"\n" +
            "    compile \"com.android.support:cardview-v7:24.2\"\n" +
            "    compile \"com.android.support:multidex:1.0.1\"\n" +
            "    compile \"com.android.support:support-annotations:25.0.0\"\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .vital(true)
      .run()
      .expectClean()
  }

  fun testPlayServiceInconsistentVersionsVersion14() {
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'android'\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'com.google.android.gms:play-services-wearable:14.0.0'\n" +
            "    compile 'com.google.android.gms:play-services-location:15.0.1'\n" +
            "    compile 'com.google.android.gms:play-services-foo-bar:0.0.1'\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .run()
      .expectClean()
  }

  fun testWrongQuotes() {
    val expected =
      "" +
        "build.gradle:5: Error: It looks like you are trying to substitute a version variable, but using single quotes ('). For Groovy string interpolation you must use double quotes (\"). [NotInterpolated]\n" +
        "    compile 'com.android.support:design:\${supportLibVersion}'\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "ext {\n" +
            "    supportLibVersion = \"23.1.1\"\n" +
            "}\n" +
            "dependencies {\n" +
            "    compile 'com.android.support:design:\${supportLibVersion}'\n" +
            "    compile \"com.android.support:appcompat-v7:\${supportLibVersion}\"\n" +
            "}\n"
        )
      )
      .issues(NOT_INTERPOLATED)
      .ignoreUnknownGradleConstructs()
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 5: Replace single quotes with double quotes:\n" +
          "@@ -5 +5\n" +
          "-     compile 'com.android.support:design:\${supportLibVersion}'\n" +
          "+     compile \"com.android.support:design:\${supportLibVersion}\"\n"
      )
  }

  fun testOldFabric() {
    // This version of Fabric created a unique string for every build which results in
    // Hotswaps getting disabled due to resource changes
    val expected =
      "" +
        "build.gradle:3: Warning: Use Fabric Gradle plugin version 1.21.6 or later to improve Instant Run performance (was 1.21.2) [GradleDependency]\n" +
        "    classpath 'io.fabric.tools:gradle:1.21.2'\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:4: Warning: Use Fabric Gradle plugin version 1.21.6 or later to improve Instant Run performance (was 1.20.0) [GradleDependency]\n" +
        "    classpath 'io.fabric.tools:gradle:1.20.0'\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:5: Warning: A newer version of io.fabric.tools:gradle than 1.22.0 is available: 1.25.1 [GradleDependency]\n" +
        "    classpath 'io.fabric.tools:gradle:1.22.0'\n" +
        "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 3 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "  dependencies {\n" +
            "    classpath 'io.fabric.tools:gradle:1.21.2'\n" + // Not OK
            "    classpath 'io.fabric.tools:gradle:1.20.0'\n" + // Not OK
            "    classpath 'io.fabric.tools:gradle:1.22.0'\n" + // Old
            "    classpath 'io.fabric.tools:gradle:1.+'\n" + // OK
            "  }\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 3: Change to 1.22.1:\n" +
          "@@ -3 +3\n" +
          "-     classpath 'io.fabric.tools:gradle:1.21.2'\n" +
          "+     classpath 'io.fabric.tools:gradle:1.22.1'\n" +
          "Fix for build.gradle line 4: Change to 1.22.1:\n" +
          "@@ -4 +4\n" +
          "-     classpath 'io.fabric.tools:gradle:1.20.0'\n" +
          "+     classpath 'io.fabric.tools:gradle:1.22.1'\n" +
          "Fix for build.gradle line 5: Change to 1.25.1:\n" +
          "@@ -5 +5\n" +
          "-     classpath 'io.fabric.tools:gradle:1.22.0'\n" +
          "+     classpath 'io.fabric.tools:gradle:1.25.1'"
      )
  }

  fun testOldRobolectric() {
    // Old robolectric warning is shown only for windows users
    val expected =
      if (isWindows())
        """
                    build.gradle:2: Warning: Use robolectric version 4.2.1 or later to fix issues with parsing of Windows paths [GradleDependency]
                        testImplementation 'org.robolectric:robolectric:4.1'
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    build.gradle:3: Warning: Use robolectric version 4.2.1 or later to fix issues with parsing of Windows paths [GradleDependency]
                        testImplementation 'org.robolectric:robolectric:3.8'
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    build.gradle:4: Warning: Use robolectric version 4.2.1 or later to fix issues with parsing of Windows paths [GradleDependency]
                        testImplementation 'org.robolectric:robolectric:3.6'
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    build.gradle:5: Warning: Use robolectric version 4.2.1 or later to fix issues with parsing of Windows paths [GradleDependency]
                        testImplementation 'org.robolectric:robolectric:2.0'
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 4 warnings
                """
      else "No warnings."

    lint()
      .files(
        gradle(
            """
                    dependencies {
                        testImplementation 'org.robolectric:robolectric:4.1'
                        testImplementation 'org.robolectric:robolectric:3.8'
                        testImplementation 'org.robolectric:robolectric:3.6'
                        testImplementation 'org.robolectric:robolectric:2.0'
                        testImplementation 'org.robolectric:robolectric:4.2.1'
                    }
                """
          )
          .indented()
      )
      .issues(DEPENDENCY)
      .run()
      .expect(expected)
      .expectFixDiffs(
        if (isWindows())
          """
                        Fix for build.gradle line 2: Change to 4.2.1:
                        @@ -2 +2
                        -     testImplementation 'org.robolectric:robolectric:4.1'
                        +     testImplementation 'org.robolectric:robolectric:4.2.1'
                        Fix for build.gradle line 3: Change to 4.2.1:
                        @@ -3 +3
                        -     testImplementation 'org.robolectric:robolectric:3.8'
                        +     testImplementation 'org.robolectric:robolectric:4.2.1'
                        Fix for build.gradle line 4: Change to 4.2.1:
                        @@ -4 +4
                        -     testImplementation 'org.robolectric:robolectric:3.6'
                        +     testImplementation 'org.robolectric:robolectric:4.2.1'
                        Fix for build.gradle line 5: Change to 4.2.1:
                        @@ -5 +5
                        -     testImplementation 'org.robolectric:robolectric:2.0'
                        @@ -7 +6
                        +     testImplementation 'org.robolectric:robolectric:4.2.1'
                    """
        else ""
      )
  }

  fun testOldBugSnag() {
    // This version of BugSnag triggered instant run full rebuilds
    val expected =
      "" +
        "build.gradle:3: Warning: Use BugSnag Gradle plugin version 2.1.2 or later to improve Instant Run performance (was 2.1.0) [GradleDependency]\n" +
        "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.0'\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:4: Warning: Use BugSnag Gradle plugin version 2.1.2 or later to improve Instant Run performance (was 2.1.1) [GradleDependency]\n" +
        "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.1'\n" +
        "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:5: Warning: A newer version of com.bugsnag:bugsnag-android-gradle-plugin than 2.1.2 is available: 3.2.5 [GradleDependency]\n" +
        "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.2'\n" +
        "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:6: Warning: A newer version of com.bugsnag:bugsnag-android-gradle-plugin than 2.2 is available: 3.2.5 [GradleDependency]\n" +
        "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.2'\n" +
        "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "build.gradle:7: Warning: A newer version of com.bugsnag:bugsnag-android-gradle-plugin than 2.5 is available: 3.2.5 [GradleDependency]\n" +
        "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.5'\n" +
        "              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 5 warnings"

    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "  dependencies {\n" +
            "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.0'\n" + // Bad
            "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.1'\n" + // Bad
            "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.2'\n" + // Old
            "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.2'\n" + // Old
            "    classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.5'\n" + // OK
            "  }\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 3: Change to 2.4.1:\n" +
          "@@ -3 +3\n" +
          "-     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.0'\n" +
          "+     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.4.1'\n" +
          "Fix for build.gradle line 4: Change to 2.4.1:\n" +
          "@@ -4 +4\n" +
          "-     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.1'\n" +
          "+     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.4.1'\n" +
          "Fix for build.gradle line 5: Change to 3.2.5:\n" +
          "@@ -5 +5\n" +
          "-     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.1.2'\n" +
          "+     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:3.2.5'\n" +
          "Fix for build.gradle line 6: Change to 3.2.5:\n" +
          "@@ -6 +6\n" +
          "-     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.2'\n" +
          "+     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:3.2.5'\n" +
          "Fix for build.gradle line 7: Change to 3.2.5:\n" +
          "@@ -7 +7\n" +
          "-     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:2.5'\n" +
          "+     classpath 'com.bugsnag:bugsnag-android-gradle-plugin:3.2.5'"
      )
  }

  fun testDeprecatedAppIndexingDependency() {
    val expected =
      "" +
        "build.gradle:8: Warning: Deprecated: Replace 'com.google.android.gms:play-services-appindexing:9.8.0' with 'com.google.firebase:firebase-appindexing:10.0.0' or above. More info: http://firebase.google.com/docs/app-indexing/android/migrate [GradleDeprecated]\n" +
        "compile 'com.google.android.gms:play-services-appindexing:9.8.0'\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 1 warnings\n"

    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 25\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "compile 'com.google.android.gms:play-services-appindexing:9.8.0'\n" +
            "}\n"
        )
      )
      .issues(DEPRECATED)
      .run()
      .expect(expected)
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 8: Replace with Firebase:\n" +
          "@@ -8 +8\n" +
          "- compile 'com.google.android.gms:play-services-appindexing:9.8.0'\n" +
          "+ compile 'com.google.firebase:firebase-appindexing:10.2.1'\n"
      )
  }

  fun testGetNamedDependency() {
    TestCase.assertEquals(
      "com.android.support:support-v4:21.0.+",
      getNamedDependency("group: 'com.android.support', name: 'support-v4', version: '21.0.+'")
    )
    TestCase.assertEquals(
      "com.android.support:support-v4:21.0.+",
      getNamedDependency("name:'support-v4', group: \"com.android.support\", version: '21.0.+'")
    )
    TestCase.assertEquals(
      "junit:junit:4.+",
      getNamedDependency("group: 'junit', name: 'junit', version: '4.+'")
    )
    TestCase.assertEquals(
      "com.android.support:support-v4:19.0.+",
      getNamedDependency("group: 'com.android.support', name: 'support-v4', version: '19.0.+'")
    )
    TestCase.assertEquals(
      "com.google.guava:guava:11.0.1",
      getNamedDependency(
        "group: 'com.google.guava', name: 'guava', version: '11.0.1', transitive: false"
      )
    )
    TestCase.assertEquals(
      "com.google.api-client:google-api-client:1.6.0-beta",
      getNamedDependency(
        "group: 'com.google.api-client', name: 'google-api-client', version: '1.6.0-beta', transitive: false"
      )
    )
    TestCase.assertEquals(
      "org.robolectric:robolectric:2.3-SNAPSHOT",
      getNamedDependency("group: 'org.robolectric', name: 'robolectric', version: '2.3-SNAPSHOT'")
    )
  }

  fun testSupportAnnotations() {
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 19\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    testCompile 'com.android.support:support-annotations:24.0.0'\n" +
            "    compile 'com.android.support:appcompat-v7:+'\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .run()
      .expectClean()
  }

  fun testBundledGmsDependency() {
    lint()
      .files(
        gradle(
          "" +
            "dependencies {\n" +
            "    compile 'com.google.android.gms:play-services:8.5.6'\n" +
            "}\n"
        )
      )
      .issues(BUNDLED_GMS)
      .run()
      .expect(
        "" +
          "build.gradle:2: Warning: Avoid using bundled version of Google Play services SDK. [UseOfBundledGooglePlayServices]\n" +
          "    compile 'com.google.android.gms:play-services:8.5.6'\n" +
          "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "0 errors, 1 warnings\n"
      )
  }

  fun testUnbundledGmsDependency() {
    lint()
      .files(
        gradle(
          "" +
            "dependencies {\n" +
            "    compile 'com.google.android.gms:play-services-auth:9.2.1'\n" +
            "}\n"
        )
      )
      .issues(BUNDLED_GMS)
      .run()
      .expectClean()
  }

  fun testHighAppVersionCode() {
    val expected =
      "" +
        "build.gradle:5: Error: The 'versionCode' is very high and close to the max allowed value [HighAppVersionCode]\n" +
        "        versionCode 2146435071\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    defaultConfig {\n" +
            "        versionCode 2146435071\n" +
            "    }\n" +
            "}"
        )
      )
      .issues(HIGH_APP_VERSION_CODE)
      .run()
      .expect(expected)
  }

  fun testORequirementsNotApplicable() {
    // targetSdkVersion < O: No check
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion \"android-O\"\n" +
            "    buildToolsVersion \"26.0.0 rc1\"\n" +
            "\n" +
            "    defaultConfig {\n" +
            "        minSdkVersion 15\n" +
            "        targetSdkVersion 25\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'com.google.android.gms:play-services-gcm:10.2.0'\n" +
            "    compile 'com.google.firebase:firebase-messaging:10.2.0'\n" +
            "    compile 'com.google.firebase:firebase-jobdispatcher:0.5.0'\n" +
            "    compile 'com.google.firebase:firebase-jobdispatcher-with-gcm-dep:0.5.0'\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .incremental()
      .run()
      .expectClean()
  }

  fun testORequirementsSatisfied() {
    // Versions > threshold: No problem
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion \"android-O\"\n" +
            "    buildToolsVersion \"26.0.0 rc1\"\n" +
            "\n" +
            "    defaultConfig {\n" +
            "        minSdkVersion 15\n" +
            "        targetSdkVersion \"O\"\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile 'com.google.android.gms:play-services-gcm:10.2.1'\n" +
            "    compile 'com.google.firebase:firebase-messaging:10.2.1'\n" +
            "    compile 'com.google.firebase:firebase-jobdispatcher:0.6.0'\n" +
            "    compile 'com.google.firebase:firebase-jobdispatcher-with-gcm-dep:0.6.0'\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .incremental()
      .run()
      .expectClean()
  }

  fun testDevVariantNotNeeded() {
    val expected =
      "" +
        "build.gradle:9: Warning: You no longer need a dev mode to enable multi-dexing during development, and this can break API version checks [DevModeObsolete]\n" +
        "            minSdkVersion 21\n" +
        "            ~~~~~~~~~~~~~~~~\n" +
        "build.gradle:10: Warning: You no longer need a dev mode to enable multi-dexing during development, and this can break API version checks [DevModeObsolete]\n" +
        "            minSdk 21\n" +
        "            ~~~~~~~~~\n" +
        "0 errors, 2 warnings"
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    productFlavors {\n" +
            "        // When building a variant that uses this flavor, the following configurations\n" +
            "        // override those in the defaultConfig block.\n" +
            "        dev {\n" +
            "            // To avoid using legacy multidex, set minSdkVersion to 21 or higher.\n" +
            "            minSdkVersion 21\n" +
            "            minSdk 21\n" +
            "            versionNameSuffix \"-dev\"\n" +
            "            applicationIdSuffix '.dev'\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        )
      )
      .issues(DEV_MODE_OBSOLETE)
      .incremental()
      .run()
      .expect(expected)
  }

  fun testDuplicateWarnings() {
    lint()
      .projects(
        project(
            gradle(
              "dependencies {\n" +
                "    implementation 'my.indirect.dependency:myname:1.2.3'\n" +
                "    implementation 'xpp3:xpp3:1.1.4c'\n" +
                "    implementation 'commons-logging:commons-logging:1.2'\n" +
                "    implementation 'xerces:xmlParserAPIs:2.6.2'\n" +
                "    implementation 'org.json:json:20170516'\n" +
                "    implementation 'org.khronos:opengl-api:gl1.1-android-2.1_r1'\n" +
                "    implementation 'com.google.android:android:4.1.1.4'\n" +
                // Multi-line scenario:
                "    compile group: 'org.apache.httpcomponents',\n" +
                "        name: 'httpclient',\n" +
                "        version: '4.5.3'\n" +
                "}\n"
            )
          )
          .withDependencyGraph(
            "" +
              "+--- my.indirect.dependency:myname:1.2.3\n" +
              "|    \\--- org.json:json:20170516\n" +
              "+--- commons-logging:commons-logging:1.2\n" +
              "+--- org.apache.httpcomponents:httpclient:4.5.3\n" +
              "|    +--- org.apache.httpcomponents:httpcore:4.4.6\n" +
              "|    +--- commons-logging:commons-logging:1.2\n" +
              "|    \\--- commons-codec:commons-codec:1.9\n" +
              "+--- xpp3:xpp3:1.1.4c\n" +
              "+--- xerces:xmlParserAPIs:2.6.2\n" +
              "+--- org.json:json:20170516\n" +
              "+--- org.khronos:opengl-api:gl1.1-android-2.1_r1\n" +
              "\\--- com.google.android:android:4.1.1.4\n" +
              "     +--- commons-logging:commons-logging:1.1.1 -> 1.2\n" +
              "     +--- org.apache.httpcomponents:httpclient:4.0.1 -> 4.5.3 (*)\n" +
              "     +--- org.khronos:opengl-api:gl1.1-android-2.1_r1\n" +
              "     +--- xerces:xmlParserAPIs:2.6.2\n" +
              "     +--- xpp3:xpp3:1.1.4c\n" +
              "     \\--- org.json:json:20080701 -> 20170516"
          )
      )
      .issues(DUPLICATE_CLASSES)
      .run()
      .expect(
        "build.gradle:3: Error: xpp3 defines classes that conflict with classes now provided by Android. Solutions include finding newer versions or alternative libraries that don't have the same problem (for example, for httpclient use HttpUrlConnection or okhttp instead), or repackaging the library using something like jarjar. [DuplicatePlatformClasses]\n" +
          "    implementation 'xpp3:xpp3:1.1.4c'\n" +
          "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "build.gradle:4: Error: commons-logging defines classes that conflict with classes now provided by Android. Solutions include finding newer versions or alternative libraries that don't have the same problem (for example, for httpclient use HttpUrlConnection or okhttp instead), or repackaging the library using something like jarjar. [DuplicatePlatformClasses]\n" +
          "    implementation 'commons-logging:commons-logging:1.2'\n" +
          "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "build.gradle:5: Error: xmlParserAPIs defines classes that conflict with classes now provided by Android. Solutions include finding newer versions or alternative libraries that don't have the same problem (for example, for httpclient use HttpUrlConnection or okhttp instead), or repackaging the library using something like jarjar. [DuplicatePlatformClasses]\n" +
          "    implementation 'xerces:xmlParserAPIs:2.6.2'\n" +
          "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "build.gradle:6: Error: json defines classes that conflict with classes now provided by Android. Solutions include finding newer versions or alternative libraries that don't have the same problem (for example, for httpclient use HttpUrlConnection or okhttp instead), or repackaging the library using something like jarjar. [DuplicatePlatformClasses]\n" +
          "    implementation 'org.json:json:20170516'\n" +
          "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "build.gradle:7: Error: opengl-api defines classes that conflict with classes now provided by Android. Solutions include finding newer versions or alternative libraries that don't have the same problem (for example, for httpclient use HttpUrlConnection or okhttp instead), or repackaging the library using something like jarjar. [DuplicatePlatformClasses]\n" +
          "    implementation 'org.khronos:opengl-api:gl1.1-android-2.1_r1'\n" +
          "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "build.gradle:8: Error: android defines classes that conflict with classes now provided by Android. Solutions include finding newer versions or alternative libraries that don't have the same problem (for example, for httpclient use HttpUrlConnection or okhttp instead), or repackaging the library using something like jarjar. [DuplicatePlatformClasses]\n" +
          "    implementation 'com.google.android:android:4.1.1.4'\n" +
          "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "build.gradle:9: Error: httpclient defines classes that conflict with classes now provided by Android. Solutions include finding newer versions or alternative libraries that don't have the same problem (for example, for httpclient use HttpUrlConnection or okhttp instead), or repackaging the library using something like jarjar. [DuplicatePlatformClasses]\n" +
          "    compile group: 'org.apache.httpcomponents',\n" +
          "    ^\n" +
          "7 errors, 0 warnings"
      )
      .expectFixDiffs(
        "" +
          "Fix for build.gradle line 3: Delete dependency:\n" +
          "@@ -3 +3\n" +
          "-     implementation 'xpp3:xpp3:1.1.4c'\n" +
          "Fix for build.gradle line 4: Delete dependency:\n" +
          "@@ -4 +4\n" +
          "-     implementation 'commons-logging:commons-logging:1.2'\n" +
          "Fix for build.gradle line 5: Delete dependency:\n" +
          "@@ -5 +5\n" +
          "-     implementation 'xerces:xmlParserAPIs:2.6.2'\n" +
          "Fix for build.gradle line 6: Delete dependency:\n" +
          "@@ -6 +6\n" +
          "-     implementation 'org.json:json:20170516'\n" +
          "Fix for build.gradle line 7: Delete dependency:\n" +
          "@@ -7 +7\n" +
          "-     implementation 'org.khronos:opengl-api:gl1.1-android-2.1_r1'\n" +
          "Fix for build.gradle line 8: Delete dependency:\n" +
          "@@ -8 +8\n" +
          "-     implementation 'com.google.android:android:4.1.1.4'\n" +
          "Fix for build.gradle line 9: Delete dependency:\n" +
          "@@ -9 +9\n" +
          "-     compile group: 'org.apache.httpcomponents',\n" +
          "-         name: 'httpclient',\n" +
          "-         version: '4.5.3'"
      )
  }

  fun testKtsSupport() {
    lint()
      .files(
        // https://github.com/gradle/kotlin-dsl/blob/master/samples/hello-android/build.gradle.kts
        kts(
          "" +
            "plugins {\n" +
            "    id(\"com.android.application\") version \"2.3.3\"\n" +
            // Deprecated version of the above (shouldn't be used in real KTS file,
            // but here to check that visitors also touch method calls)
            "    id(\"android\") version \"2.3.3\"\n" +
            // Another version of the above (shouldn't be used in real KTS file, but
            // here to check that visitors can cope with nested binary expressions)
            "    id(\"android\") version \"2.3.3\" apply true\n" +
            "    kotlin(\"android\") version \"1.1.51\"\n" +
            "}\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion($HIGHEST_KNOWN_STABLE_API)\n" +
            "\n" +
            "    defaultConfig {\n" +
            "        minSdkVersion(7)\n" +
            "        targetSdkVersion(23)\n" +
            "\n" +
            "        applicationId = \"com.example.kotlingradle\"\n" +
            "        versionCode = 1\n" +
            "        versionName = \"1.0\"\n" +
            "    }\n" +
            "\n" +
            "    buildTypes {\n" +
            "        getByName(\"release\") {\n" +
            "            isMinifyEnabled = false\n" +
            "            proguardFiles(\"proguard-rules.pro\")\n" +
            "        }\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "dependencies {\n" +
            "    compile(\"com.android.support:appcompat-v7:25.3.1\")\n" +
            "    compile(\"com.android.support.constraint:constraint-layout:1.0.0-alpha8\")\n" +
            "    compile(kotlin(\"stdlib\", \"1.1.51\"))\n" +
            "}\n" +
            "\n" +
            "repositories {\n" +
            "    jcenter()\n" +
            "}"
        )
      )
      .issues(DEPENDENCY, MIN_SDK_TOO_LOW, DEPRECATED)
      .run()
      .expect(
        """
                build.gradle.kts:3: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]
                    id("android") version "2.3.3"
                        ~~~~~~~
                build.gradle.kts:4: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]
                    id("android") version "2.3.3" apply true
                        ~~~~~~~
                build.gradle.kts:30: Warning: A newer version of com.android.support.constraint:constraint-layout than 1.0.0-alpha8 is available: 1.0.3-alpha8 [GradleDependency]
                    compile("com.android.support.constraint:constraint-layout:1.0.0-alpha8")
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:12: Warning: The value of minSdkVersion is too low. It can be incremented without noticeably reducing the number of supported devices. [MinSdkTooLow]
                        minSdkVersion(7)
                        ~~~~~~~~~~~~~~~~
                0 errors, 4 warnings
                """
      )
      .expectFixDiffs(
        """
                Autofix for build.gradle.kts line 3: Replace with com.android.application:
                @@ -3 +3
                -     id("android") version "2.3.3"
                +     id("com.android.application") version "2.3.3"
                Autofix for build.gradle.kts line 4: Replace with com.android.application:
                @@ -4 +4
                -     id("android") version "2.3.3" apply true
                +     id("com.android.application") version "2.3.3" apply true
                Fix for build.gradle.kts line 30: Change to 1.0.3-alpha8:
                @@ -30 +30
                -     compile("com.android.support.constraint:constraint-layout:1.0.0-alpha8")
                +     compile("com.android.support.constraint:constraint-layout:1.0.3-alpha8")
                Fix for build.gradle.kts line 12: Update minSdkVersion to $LOWEST_ACTIVE_API:
                @@ -12 +12
                -         minSdkVersion(7)
                +         minSdkVersion($LOWEST_ACTIVE_API)
                """
      )
  }

  fun testGroovyPluginsDsl() {
    // tests for the equivalent Kotlin script Plugins Dsl are in testKtsSupport()
    lint()
      .files(
        gradle(
          "" +
            "plugins {\n" +
            "    id 'com.android.application' version '2.3.3'\n" +
            // Deprecated version of the above (shouldn't be used in real plugins Dsl,
            // but here to check that visitors also touch method calls)
            "    id 'android' version '2.3.3'\n" +
            // Another version of the above (shouldn't be used in real KTS file, but
            // here to check that visitors can cope with nested binary expressions)
            "    id 'android' version '2.3.3' apply true\n" +
            "}"
        )
      )
      .issues(DEPENDENCY, DEPRECATED)
      .run()
      .expect(
        """
                build.gradle:3: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]
                    id 'android' version '2.3.3'
                       ~~~~~~~~~
                build.gradle:4: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]
                    id 'android' version '2.3.3' apply true
                       ~~~~~~~~~
                0 errors, 2 warnings
                """
      )
      .expectFixDiffs(
        """
                Autofix for build.gradle line 3: Replace with com.android.application:
                @@ -3 +3
                -     id 'android' version '2.3.3'
                +     id 'com.android.application' version '2.3.3'
                Autofix for build.gradle line 4: Replace with com.android.application:
                @@ -4 +4
                -     id 'android' version '2.3.3' apply true
                +     id 'com.android.application' version '2.3.3' apply true
                """
      )
  }

  fun testTargetEdited() {
    val temporaryFolder = TemporaryFolder()
    temporaryFolder.create()
    try {
      val rootDirectory = temporaryFolder.root.canonicalFile

      fun createLintTask(targetSdkVersion: Int, name: String = "build.gradle"): TestLintTask {
        return lint()
          .rootDirectory(rootDirectory)
          .files(
            gradle(
                name,
                """
                apply plugin: 'com.android.application'

                android {
                    defaultConfig {
                        targetSdkVersion $targetSdkVersion
                    }
                }
                """
              )
              .indented()
          )
          .issues(GradleDetector.EDITED_TARGET_SDK_VERSION)
          .incremental(name)
          .clientFactory {
            com.android.tools.lint.checks.infrastructure
              .TestLintClient(LintClient.CLIENT_STUDIO)
              .apply { addCleanupDir(rootDirectory) }
          }
          .testModes(TestMode.DEFAULT)
      }

      // Initial edit: no problem
      createLintTask(31).run().expectClean()

      // Lower version: no problem
      createLintTask(30).run().expectClean()

      // Higher person: problem
      createLintTask(32)
        .run()
        .expect(
          """
          build.gradle:5: Error: It looks like you just edited the targetSdkVersion from 31 to 32 in the editor. Be sure to consult the documentation on the behaviors that change as result of this. The Android SDK Upgrade Assistant can help with safely migrating. [EditedTargetSdkVersion]
                  targetSdkVersion 32
                  ~~~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings
          """
        )

      // Ok if you change it back
      createLintTask(31).run().expectClean()

      // OK on a different file
      createLintTask(32, "settings.gradle").run().expectClean()

      // ...until we edit it there too
      createLintTask(33, "settings.gradle")
        .run()
        .expect(
          """
          settings.gradle:5: Error: It looks like you just edited the targetSdkVersion from 32 to 33 in the editor. Be sure to consult the documentation on the behaviors that change as result of this. The Android SDK Upgrade Assistant can help with safely migrating. [EditedTargetSdkVersion]
                  targetSdkVersion 33
                  ~~~~~~~~~~~~~~~~~~~
          1 errors, 0 warnings
          """
        )
    } finally {
      temporaryFolder.delete()
    }
  }

  fun testExpiring1() {
    // Not meeting last year's requirement either
    try {
      val calendar = Calendar.getInstance()
      GradleDetector.calendar = calendar
      calendar.set(Calendar.YEAR, MINIMUM_TARGET_SDK_VERSION_YEAR)
      calendar.set(Calendar.MONTH, 6)

      lint()
        .files(
          gradle(
            "" +
              "apply plugin: 'com.android.application'\n" +
              "\n" +
              "android {\n" +
              "    defaultConfig {\n" +
              "        targetSdkVersion 17\n" +
              "        targetSdk 17\n" +
              "    }\n" +
              "}\n"
          )
        )
        .issues(EXPIRED_TARGET_SDK_VERSION, EXPIRING_TARGET_SDK_VERSION)
        .sdkHome(mockSupportLibraryInstallation)
        .run()
        .expect(
          """
                    build.gradle:5: Error: Google Play requires that apps target API level $PREVIOUS_MINIMUM_TARGET_SDK_VERSION or higher. [ExpiredTargetSdkVersion]
                            targetSdkVersion 17
                            ~~~~~~~~~~~~~~~~~~~
                    build.gradle:6: Error: Google Play requires that apps target API level $PREVIOUS_MINIMUM_TARGET_SDK_VERSION or higher. [ExpiredTargetSdkVersion]
                            targetSdk 17
                            ~~~~~~~~~~~~
                    2 errors, 0 warnings
                    """
        )
    } finally {
      GradleDetector.calendar = null
    }
  }

  fun testExpiring2() {
    // Already meeting last year's requirement but not this year's requirement
    try {
      val calendar = Calendar.getInstance()
      GradleDetector.calendar = calendar
      calendar.set(Calendar.YEAR, MINIMUM_TARGET_SDK_VERSION_YEAR)
      calendar.set(Calendar.MONTH, 6)

      lint()
        .files(
          gradle(
            "" +
              "apply plugin: 'com.android.application'\n" +
              "\n" +
              "android {\n" +
              "    defaultConfig {\n" +
              "        // Already meeting last year's requirement but not this year's requirement\n" +
              "        targetSdkVersion $PREVIOUS_MINIMUM_TARGET_SDK_VERSION\n" +
              "        targetSdkVersion $MINIMUM_TARGET_SDK_VERSION_YEAR // OK\n" +
              "        targetSdkVersion ${MINIMUM_TARGET_SDK_VERSION_YEAR + 1} // OK\n" +
              "    }\n" +
              "}\n"
          )
        )
        .issues(EXPIRED_TARGET_SDK_VERSION, EXPIRING_TARGET_SDK_VERSION)
        .sdkHome(mockSupportLibraryInstallation)
        .run()
        .expect(
          """
                    build.gradle:6: Warning: Google Play will soon require that apps target API level $MINIMUM_TARGET_SDK_VERSION or higher. This will be required for new apps and updates starting on August 31, $MINIMUM_TARGET_SDK_VERSION_YEAR. [ExpiringTargetSdkVersion]
                            targetSdkVersion $PREVIOUS_MINIMUM_TARGET_SDK_VERSION
                            ~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                    """
        )
    } finally {
      GradleDetector.calendar = null
    }
  }

  fun testExpired() {
    try {
      val calendar = Calendar.getInstance()
      GradleDetector.calendar = calendar
      calendar.set(Calendar.YEAR, MINIMUM_TARGET_SDK_VERSION_YEAR - 1)
      calendar.set(Calendar.MONTH, 10)

      lint()
        .files(
          gradle(
            "" +
              "apply plugin: 'com.android.application'\n" +
              "\n" +
              "android {\n" +
              "    defaultConfig {\n" +
              "        targetSdkVersion 17\n" +
              "    }\n" +
              "}\n"
          )
        )
        .issues(EXPIRED_TARGET_SDK_VERSION, EXPIRING_TARGET_SDK_VERSION)
        .sdkHome(mockSupportLibraryInstallation)
        .run()
        .expect(
          """
                    build.gradle:5: Error: Google Play requires that apps target API level $PREVIOUS_MINIMUM_TARGET_SDK_VERSION or higher. [ExpiredTargetSdkVersion]
                            targetSdkVersion 17
                            ~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                    """
        )
    } finally {
      GradleDetector.calendar = null
    }
  }

  fun testExpired2() {
    try {
      val calendar = Calendar.getInstance()
      GradleDetector.calendar = calendar
      calendar.set(Calendar.YEAR, MINIMUM_TARGET_SDK_VERSION_YEAR - 1)
      calendar.set(Calendar.MONTH, 10)

      lint()
        .files(
          gradle(
            "" +
              "apply plugin: 'com.android.application'\n" +
              "\n" +
              "android {\n" +
              "    defaultConfig {\n" +
              "        targetSdkVersion 'O'\n" +
              "    }\n" +
              "}\n"
          )
        )
        .issues(EXPIRED_TARGET_SDK_VERSION, EXPIRING_TARGET_SDK_VERSION)
        .sdkHome(mockSupportLibraryInstallation)
        .run()
        .expect(
          """
                    build.gradle:5: Error: Google Play requires that apps target API level $PREVIOUS_MINIMUM_TARGET_SDK_VERSION or higher. [ExpiredTargetSdkVersion]
                            targetSdkVersion 'O'
                            ~~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                    """
        )
    } finally {
      GradleDetector.calendar = null
    }
  }

  fun testWearNotExpired() {
    try {
      val calendar = Calendar.getInstance()
      GradleDetector.calendar = calendar
      // Make sure test doesn't fail on computers without a correct date set
      calendar.set(Calendar.YEAR, MINIMUM_TARGET_SDK_VERSION_YEAR)
      calendar.set(Calendar.MONTH, 2)

      lint()
        .files(
          manifest(
              """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                            <uses-feature android:name="android.hardware.type.watch" />
                            <application
                                android:icon="@mipmap/ic_launcher"
                                android:label="@string/app_name">
                            </application>
                        </manifest>
                        """
            )
            .indented(),
          gradle(
              """
                    apply plugin: 'com.android.application'

                    android {
                        defaultConfig {
                            targetSdkVersion $MINIMUM_WEAR_TARGET_SDK_VERSION
                        }
                    }
                    """
            )
            .indented()
        )
        .issues(EXPIRED_TARGET_SDK_VERSION, EXPIRING_TARGET_SDK_VERSION)
        .sdkHome(mockSupportLibraryInstallation)
        .run()
        .expectClean()
    } finally {
      GradleDetector.calendar = null
    }
  }

  fun testWearExpired() {
    try {
      val calendar = Calendar.getInstance()
      GradleDetector.calendar = calendar
      // Make sure test doesn't fail on computers without a correct date set
      calendar.set(Calendar.YEAR, MINIMUM_TARGET_SDK_VERSION_YEAR)
      calendar.set(Calendar.MONTH, 2)

      lint()
        .files(
          manifest(
              """
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                            <uses-feature android:name="android.hardware.type.watch" />
                            <application
                                android:icon="@mipmap/ic_launcher"
                                android:label="@string/app_name">
                            </application>
                        </manifest>
                        """
            )
            .indented(),
          gradle(
              """
                    apply plugin: 'com.android.application'

                    android {
                        defaultConfig {
                            targetSdkVersion 19
                        }
                    }
                    """
            )
            .indented()
        )
        .issues(EXPIRED_TARGET_SDK_VERSION, EXPIRING_TARGET_SDK_VERSION)
        .sdkHome(mockSupportLibraryInstallation)
        .run()
        .expect(
          """
                    build.gradle:5: Error: Google Play requires that apps target API level $MINIMUM_WEAR_TARGET_SDK_VERSION or higher. [ExpiredTargetSdkVersion]
                            targetSdkVersion 19
                            ~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                    """
        )
    } finally {
      GradleDetector.calendar = null
    }
  }

  fun testSdkIndexLibrary() {
    val expectedFixes =
      """
        Show URL for build.gradle line 7: View details in Google Play SDK Index:
        http://index.example.url/
        Show URL for build.gradle line 15: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 18: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 22: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 22: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 6: View details in Google Play SDK Index:
        http://index.example.url/
        Show URL for build.gradle line 13: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 14: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 16: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 17: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 19: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 20: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 21: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 21: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 21: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 23: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 23: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 8: View details in Google Play SDK Index:
        http://index.example.url/
        Show URL for build.gradle line 5: View details in Google Play SDK Index:
        http://index.example.url/
        Show URL for build.gradle line 13: View details in Google Play SDK Index:
        http://another.example.url/
      """
    lint()
      .files(
        gradle(
            """
                dependencies {
                    compile 'log4j:log4j:1.2.18' // OK, latest
                    compile 'log4j:log4j:1.2.17' // OK
                    compile 'log4j:log4j:1.2.16' // Critical NON_BLOCKING
                    compile 'log4j:log4j:1.2.15' // Outdated NON_BLOCKING
                    compile 'log4j:log4j:1.2.14' // Non compliant
                    compile 'log4j:log4j:1.2.13' // Critical BLOCKING
                    compile 'log4j:log4j:1.2.12' // OUTDATED BLOCKING
                    compile 'log4j:log4j:1.2.11' // Ok (not in Index)
                    compile 'com.example.ads.third.party:example:8.0.0' // OK
                    compile 'com.example.ads.third.party:example:7.2.2' // OK
                    compile 'com.example.ads.third.party:example:7.2.1' // OK
                    compile 'com.example.ads.third.party:example:7.2.0' // Outdated + Critical + Policy (multiple issues), no severity
                    compile 'com.example.ads.third.party:example:7.1.0' // Policy (Ads), non-blocking
                    compile 'com.example.ads.third.party:example:7.1.1' // Policy (Device and Network Abuse), blocking
                    compile 'com.example.ads.third.party:example:7.1.2' // Policy (Deceptive Behavior), no severity
                    compile 'com.example.ads.third.party:example:7.1.3' // Policy (User Data), non-blocking
                    compile 'com.example.ads.third.party:example:7.1.4' // Policy (Permissions), blocking
                    compile 'com.example.ads.third.party:example:7.1.5' // Policy (Mobile Unwanted Software), no-severity
                    compile 'com.example.ads.third.party:example:7.1.6' // Policy (Malware), non-blocking
                    compile 'com.example.ads.third.party:example:7.1.7' // Policy (multiple types), non-blocking
                    compile 'com.example.ads.third.party:example:7.1.8' // Policy (multiple types), blocking
                    compile 'com.example.ads.third.party:example:7.1.9' // Policy (multiple types), no severity

                    compile 'log4j:log4j:latest.release' // OK
                    compile 'log4j:log4j' // OK
                    compile 'log4j:log4j:_' // OK

                    compile 'com.another.example:example' // Ok (not in Index)
                }
                """
          )
          .indented()
      )
      .issues(
        RISKY_LIBRARY,
        DEPRECATED_LIBRARY,
        DEPENDENCY,
        PLAY_SDK_INDEX_NON_COMPLIANT,
        PLAY_SDK_INDEX_GENERIC_ISSUES
      )
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        """
          build.gradle:7: Error: log4j:log4j version 1.2.13 has been reported as problematic by its author and will block publishing of your app to Play Console [RiskyLibrary]
              compile 'log4j:log4j:1.2.13' // Critical BLOCKING
                      ~~~~~~~~~~~~~~~~~~~~
          build.gradle:15: Error: com.example.ads.third.party:example version 7.1.1 has Device and Network Abuse policy issues that will block publishing of your app to Play Console [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.1' // Policy (Device and Network Abuse), blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:18: Error: com.example.ads.third.party:example version 7.1.4 has Permissions policy issues that will block publishing of your app to Play Console [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.4' // Policy (Permissions), blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:22: Error: com.example.ads.third.party:example version 7.1.8 has Malware policy issues that will block publishing of your app to Play Console [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.8' // Policy (multiple types), blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:22: Error: com.example.ads.third.party:example version 7.1.8 has User Data policy issues that will block publishing of your app to Play Console [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.8' // Policy (multiple types), blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:6: Warning: log4j:log4j version 1.2.14 has policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'log4j:log4j:1.2.14' // Non compliant
                      ~~~~~~~~~~~~~~~~~~~~
          build.gradle:13: Warning: com.example.ads.third.party:example version 7.2.0 has User Data policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.2.0' // Outdated + Critical + Policy (multiple issues), no severity
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:14: Warning: com.example.ads.third.party:example version 7.1.0 has Ads policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.0' // Policy (Ads), non-blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:16: Warning: com.example.ads.third.party:example version 7.1.2 has Deceptive Behavior policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.2' // Policy (Deceptive Behavior), no severity
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:17: Warning: com.example.ads.third.party:example version 7.1.3 has User Data policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.3' // Policy (User Data), non-blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:19: Warning: com.example.ads.third.party:example version 7.1.5 has Mobile Unwanted Software policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.5' // Policy (Mobile Unwanted Software), no-severity
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:20: Warning: com.example.ads.third.party:example version 7.1.6 has Malware policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.6' // Policy (Malware), non-blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:21: Warning: com.example.ads.third.party:example version 7.1.7 has Malware policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.7' // Policy (multiple types), non-blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:21: Warning: com.example.ads.third.party:example version 7.1.7 has Permissions policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.7' // Policy (multiple types), non-blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:21: Warning: com.example.ads.third.party:example version 7.1.7 has User Data policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.7' // Policy (multiple types), non-blocking
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:23: Warning: com.example.ads.third.party:example version 7.1.9 has Malware policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.9' // Policy (multiple types), no severity
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:23: Warning: com.example.ads.third.party:example version 7.1.9 has Permissions policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.9' // Policy (multiple types), no severity
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:8: Error: log4j:log4j version 1.2.12 has been marked as outdated by its author and will block publishing of your app to Play Console [OutdatedLibrary]
              compile 'log4j:log4j:1.2.12' // OUTDATED BLOCKING
                      ~~~~~~~~~~~~~~~~~~~~
          build.gradle:5: Warning: log4j:log4j version 1.2.15 has been marked as outdated by its author [OutdatedLibrary]
              compile 'log4j:log4j:1.2.15' // Outdated NON_BLOCKING
                      ~~~~~~~~~~~~~~~~~~~~
          build.gradle:13: Warning: com.example.ads.third.party:example version 7.2.0 has been marked as outdated by its author [OutdatedLibrary]
              compile 'com.example.ads.third.party:example:7.2.0' // Outdated + Critical + Policy (multiple issues), no severity
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          6 errors, 14 warnings
        """
      )
      .expectFixDiffs(expectedFixes)
  }

  fun testSdkIndexLibraryWithToml() {
    lint()
      .files(
        gradleToml(
          """
                [versions]
                log4Critical = "1.2.16"         # Critical NON_BLOCKING
                log4CriticalBlock = "1.2.13"    # Critical BLOCKING
                log4Outdated = "1.2.12"         # Outdated NON_BLOCKING
                #noinspection RiskyLibrary
                log4Suppressed = "1.2.13"       # Suppressed
                log4Ok = "1.2.17"               # OK
                exMultipleIssues = "7.2.0"      # Critical + Outdated + Policy, no severity
                exPolicyAds = "7.1.0"           # Policy (Ads), non-blocking
                exPolicyDevNetBlock = "7.1.1"   # Policy (Device and Network Abuse), blocking
                exPolicyBehavior = "7.1.2"      # Policy (Deceptive Behavior), no severity
                exPolicyUseData = "7.1.3"       # Policy (User Data), non-blocking
                exPolicyPermBlock = "7.1.4"     # Policy (Permissions), blocking
                exPolicyMobile = "7.1.5"        # Policy (Mobile Unwanted Software), no-severity
                exPolicyMalware = "7.1.6"       # Policy (Malware), non-blocking
                exPolicyMultiNon = "7.1.7"      # Policy (multiple types), non-blocking
                exPolicyMultiBlock = "7.1.8"    # Policy (multiple types), blocking
                exPolicyMulti = "7.1.9"         # Policy (multiple types), no severity

                [libraries]
                critical_log4j = { module = "log4j:log4j", version.ref = "log4Critical"}
                criticalBlocking_log4j = { module = "log4j:log4j", version.ref = "log4CriticalBlock"}
                suppressed_log4j = { module = "log4j:log4j", version.ref = "log4Suppressed"}
                outdated_log4j = { module = "log4j:log4j", version.ref = "log4Outdated"}
                ok_log4j = { module = "log4j:log4j", version.ref = "log4Ok"}
                multipleIssues_ex = { module = "com.example.ads.third.party:example", version.ref = "exMultipleIssues"}
                policyAds_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyAds"}
                policyDevNetBlock_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyDevNetBlock"}
                policyBehavior_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyBehavior"}
                policyUseData_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyUseData"}
                policyPermBlock_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyPermBlock"}
                policyMobile_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyMobile"}
                policyMalware_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyMalware"}
                policyMultiNon_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyMultiNon"}
                policyMultiBlock_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyMultiBlock"}
                policyMulti_ex = { module = "com.example.ads.third.party:example", version.ref = "exPolicyMulti"}
                """
        )
      )
      .issues(
        RISKY_LIBRARY,
        DEPRECATED_LIBRARY,
        DEPENDENCY,
        PLAY_SDK_INDEX_NON_COMPLIANT,
        PLAY_SDK_INDEX_GENERIC_ISSUES
      )
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        """
                ../gradle/libs.versions.toml:4: Error: log4j:log4j version 1.2.13 has been reported as problematic by its author and will block publishing of your app to Play Console [RiskyLibrary]
                                log4CriticalBlock = "1.2.13"    # Critical BLOCKING
                                                    ~~~~~~~~
                ../gradle/libs.versions.toml:11: Error: com.example.ads.third.party:example version 7.1.1 has Device and Network Abuse policy issues that will block publishing of your app to Play Console [PlaySdkIndexNonCompliant]
                                exPolicyDevNetBlock = "7.1.1"   # Policy (Device and Network Abuse), blocking
                                                      ~~~~~~~
                ../gradle/libs.versions.toml:14: Error: com.example.ads.third.party:example version 7.1.4 has Permissions policy issues that will block publishing of your app to Play Console [PlaySdkIndexNonCompliant]
                                exPolicyPermBlock = "7.1.4"     # Policy (Permissions), blocking
                                                    ~~~~~~~
                ../gradle/libs.versions.toml:18: Error: com.example.ads.third.party:example version 7.1.8 has Malware policy issues that will block publishing of your app to Play Console [PlaySdkIndexNonCompliant]
                                exPolicyMultiBlock = "7.1.8"    # Policy (multiple types), blocking
                                                     ~~~~~~~
                ../gradle/libs.versions.toml:18: Error: com.example.ads.third.party:example version 7.1.8 has User Data policy issues that will block publishing of your app to Play Console [PlaySdkIndexNonCompliant]
                                exPolicyMultiBlock = "7.1.8"    # Policy (multiple types), blocking
                                                     ~~~~~~~
                ../gradle/libs.versions.toml:9: Warning: com.example.ads.third.party:example version 7.2.0 has User Data policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exMultipleIssues = "7.2.0"      # Critical + Outdated + Policy, no severity
                                                   ~~~~~~~
                ../gradle/libs.versions.toml:10: Warning: com.example.ads.third.party:example version 7.1.0 has Ads policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyAds = "7.1.0"           # Policy (Ads), non-blocking
                                              ~~~~~~~
                ../gradle/libs.versions.toml:12: Warning: com.example.ads.third.party:example version 7.1.2 has Deceptive Behavior policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyBehavior = "7.1.2"      # Policy (Deceptive Behavior), no severity
                                                   ~~~~~~~
                ../gradle/libs.versions.toml:13: Warning: com.example.ads.third.party:example version 7.1.3 has User Data policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyUseData = "7.1.3"       # Policy (User Data), non-blocking
                                                  ~~~~~~~
                ../gradle/libs.versions.toml:15: Warning: com.example.ads.third.party:example version 7.1.5 has Mobile Unwanted Software policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyMobile = "7.1.5"        # Policy (Mobile Unwanted Software), no-severity
                                                 ~~~~~~~
                ../gradle/libs.versions.toml:16: Warning: com.example.ads.third.party:example version 7.1.6 has Malware policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyMalware = "7.1.6"       # Policy (Malware), non-blocking
                                                  ~~~~~~~
                ../gradle/libs.versions.toml:17: Warning: com.example.ads.third.party:example version 7.1.7 has Malware policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyMultiNon = "7.1.7"      # Policy (multiple types), non-blocking
                                                   ~~~~~~~
                ../gradle/libs.versions.toml:17: Warning: com.example.ads.third.party:example version 7.1.7 has Permissions policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyMultiNon = "7.1.7"      # Policy (multiple types), non-blocking
                                                   ~~~~~~~
                ../gradle/libs.versions.toml:17: Warning: com.example.ads.third.party:example version 7.1.7 has User Data policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyMultiNon = "7.1.7"      # Policy (multiple types), non-blocking
                                                   ~~~~~~~
                ../gradle/libs.versions.toml:19: Warning: com.example.ads.third.party:example version 7.1.9 has Malware policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyMulti = "7.1.9"         # Policy (multiple types), no severity
                                                ~~~~~~~
                ../gradle/libs.versions.toml:19: Warning: com.example.ads.third.party:example version 7.1.9 has Permissions policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
                                exPolicyMulti = "7.1.9"         # Policy (multiple types), no severity
                                                ~~~~~~~
                ../gradle/libs.versions.toml:5: Error: log4j:log4j version 1.2.12 has been marked as outdated by its author and will block publishing of your app to Play Console [OutdatedLibrary]
                                log4Outdated = "1.2.12"         # Outdated NON_BLOCKING
                                               ~~~~~~~~
                ../gradle/libs.versions.toml:9: Warning: com.example.ads.third.party:example version 7.2.0 has been marked as outdated by its author [OutdatedLibrary]
                                exMultipleIssues = "7.2.0"      # Critical + Outdated + Policy, no severity
                                                   ~~~~~~~
                6 errors, 12 warnings
                """
      )
  }

  /**
   * Test that version upgrade quickfix is shown if the suggested version does not have errors or
   * warnings from SDK Index
   */
  fun testSdkIndexLibraryUpgradeToVersionWithoutWarningOrError() {
    val expectedFixes =
      """
        Fix for build.gradle line 3: Change to 8.0.0:
        @@ -3 +3
        -     compile 'com.example.ads.third.party:example:7.2.1' // suggest 8.0.0 since it does not have issues
        +     compile 'com.example.ads.third.party:example:8.0.0' // suggest 8.0.0 since it does not have issues
        Fix for build.gradle line 4: Change to 1.2.11:
        @@ -4 +4
        -     compile 'log4j:log4j:1.2.10' // Suggest 1.2.11 even if 1.2.12 is available (but it has SDK issues)
        +     compile 'log4j:log4j:1.2.11' // Suggest 1.2.11 even if 1.2.12 is available (but it has SDK issues)
        Show URL for build.gradle line 2: View details in Google Play SDK Index:
        http://another.example.url/
        Show URL for build.gradle line 2: View details in Google Play SDK Index:
        http://another.example.url/
      """
    lint()
      .files(
        gradle(
            """
                dependencies {
                    compile 'com.example.ads.third.party:example:7.2.0' // Only SDK Index issue should be shown (not NewerVersionAvailable)
                    compile 'com.example.ads.third.party:example:7.2.1' // suggest 8.0.0 since it does not have issues
                    compile 'log4j:log4j:1.2.10' // Suggest 1.2.11 even if 1.2.12 is available (but it has SDK issues)
                }
                """
          )
          .indented()
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.example.ads.third.party%22+AND+a:%22example%22&core=gav&wt=json",
        "" +
          "{\"responseHeader\":" +
          "{\"status\":0,\"QTime\":0,\"params\":" +
          "{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"com.example.ads.third.party\\\" AND a:\\\"example\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"version\":\"2.2\"}}," +
          "\"response\":" +
          "{\"numFound\":4,\"start\":0,\"docs\":[" +
          "{\"id\":\"com.example.ads.third.party:example:8.0.0\",\"g\":\"com.example.ads.third.party\",\"a\":\"example\",\"v\":\"8.0.0\",\"p\":\"jar\",\"timestamp\":1462852968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.example.ads.third.party:example:7.2.2\",\"g\":\"com.example.ads.third.party\",\"a\":\"example\",\"v\":\"7.2.2\",\"p\":\"jar\",\"timestamp\":1462851968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.example.ads.third.party:example:7.2.1\",\"g\":\"com.example.ads.third.party\",\"a\":\"example\",\"v\":\"7.2.1\",\"p\":\"jar\",\"timestamp\":1462850968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.example.ads.third.party:example:7.2.0\",\"g\":\"com.example.ads.third.party\",\"a\":\"example\",\"v\":\"7.2.0\",\"p\":\"jar\",\"timestamp\":1462849968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}]}}"
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22log4j%22+AND+a:%22log4j%22&core=gav&wt=json",
        "" +
          "{\"responseHeader\":" +
          "{\"status\":0,\"QTime\":0,\"params\":" +
          "{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"log4j\\\" AND a:\\\"log4j\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"version\":\"2.2\"}}," +
          "\"response\":" +
          "{\"numFound\":4,\"start\":0,\"docs\":[" +
          "{\"id\":\"log4j:log4j:1.2.12\",\"g\":\"log4j\",\"a\":\"log4j\",\"v\":\"1.2.12\",\"p\":\"jar\",\"timestamp\":1462852968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"log4j:log4j:1.2.11\",\"g\":\"log4j\",\"a\":\"log4j\",\"v\":\"1.2.11\",\"p\":\"jar\",\"timestamp\":1462851968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"log4j:log4j:1.2.10\",\"g\":\"log4j\",\"a\":\"log4j\",\"v\":\"1.2.10\",\"p\":\"jar\",\"timestamp\":1462850968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"log4j:log4j:1.2.9\",\"g\":\"log4j\",\"a\":\"log4j\",\"v\":\"1.2.9\",\"p\":\"jar\",\"timestamp\":1462849968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}]}}"
      )
      .issues(
        REMOTE_VERSION,
        RISKY_LIBRARY,
        DEPRECATED_LIBRARY,
        DEPENDENCY,
        PLAY_SDK_INDEX_NON_COMPLIANT,
        PLAY_SDK_INDEX_GENERIC_ISSUES
      )
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        """
          build.gradle:3: Warning: A newer version of com.example.ads.third.party:example than 7.2.1 is available: 8.0.0 [NewerVersionAvailable]
              compile 'com.example.ads.third.party:example:7.2.1' // suggest 8.0.0 since it does not have issues
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:4: Warning: A newer version of log4j:log4j than 1.2.10 is available: 1.2.11 [NewerVersionAvailable]
              compile 'log4j:log4j:1.2.10' // Suggest 1.2.11 even if 1.2.12 is available (but it has SDK issues)
                      ~~~~~~~~~~~~~~~~~~~~
          build.gradle:2: Warning: com.example.ads.third.party:example version 7.2.0 has User Data policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.2.0' // Only SDK Index issue should be shown (not NewerVersionAvailable)
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:2: Warning: com.example.ads.third.party:example version 7.2.0 has been marked as outdated by its author [OutdatedLibrary]
              compile 'com.example.ads.third.party:example:7.2.0' // Only SDK Index issue should be shown (not NewerVersionAvailable)
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 4 warnings
        """
      )
      .expectFixDiffs(expectedFixes)
  }

  /**
   * Test that version upgrade quickfix is not shown if the suggested version has errors or warnings
   * from SDK Index
   */
  fun testSdkIndexLibraryNoUpgradeToVersionWithWarningOrError() {
    val expectedFixes =
      """
        Show URL for build.gradle line 3: View details in Google Play SDK Index:
        http://another.example.url/
      """
    lint()
      .files(
        gradle(
            """
                dependencies {
                    compile 'log4j:log4j:1.2.11' // No Issue but no suggestion since 1.2.15 has outdated non blocking issues (warning)
                    compile 'com.example.ads.third.party:example:7.1.0' //Issue but no suggestion since 7.1.4 has issues (error)
                }
                """
          )
          .indented()
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22log4j%22+AND+a:%22log4j%22&core=gav&wt=json",
        "" +
          "{\"responseHeader\":" +
          "{\"status\":0,\"QTime\":0,\"params\":" +
          "{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"log4j\\\" AND a:\\\"log4j\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"version\":\"2.2\"}}," +
          "\"response\":" +
          "{\"numFound\":4,\"start\":0,\"docs\":[" +
          "{\"id\":\"log4j:log4j:1.2.15\",\"g\":\"log4j\",\"a\":\"log4j\",\"v\":\"1.2.15\",\"p\":\"jar\",\"timestamp\":1462852968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"log4j:log4j:1.2.11\",\"g\":\"log4j\",\"a\":\"log4j\",\"v\":\"1.2.11\",\"p\":\"jar\",\"timestamp\":1462851968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"log4j:log4j:1.2.10\",\"g\":\"log4j\",\"a\":\"log4j\",\"v\":\"1.2.10\",\"p\":\"jar\",\"timestamp\":1462850968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"log4j:log4j:1.2.9\",\"g\":\"log4j\",\"a\":\"log4j\",\"v\":\"1.2.9\",\"p\":\"jar\",\"timestamp\":1462849968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}]}}"
      )
      .networkData(
        "https://search.maven.org/solrsearch/select?q=g:%22com.example.ads.third.party%22+AND+a:%22example%22&core=gav&wt=json",
        "" +
          "{\"responseHeader\":" +
          "{\"status\":0,\"QTime\":0,\"params\":" +
          "{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"com.example.ads.third.party\\\" AND a:\\\"example\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"version\":\"2.2\"}}," +
          "\"response\":" +
          "{\"numFound\":4,\"start\":0,\"docs\":[" +
          "{\"id\":\"com.example.ads.third.party:example:7.1.4\",\"g\":\"com.example.ads.third.party\",\"a\":\"example\",\"v\":\"7.1.4\",\"p\":\"jar\",\"timestamp\":1462852968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.example.ads.third.party:example:7.1.2\",\"g\":\"com.example.ads.third.party\",\"a\":\"example\",\"v\":\"7.1.2\",\"p\":\"jar\",\"timestamp\":1462851968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.example.ads.third.party:example:7.1.1\",\"g\":\"com.example.ads.third.party\",\"a\":\"example\",\"v\":\"7.1.1\",\"p\":\"jar\",\"timestamp\":1462850968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}," +
          "{\"id\":\"com.example.ads.third.party:example:7.1.0\",\"g\":\"com.example.ads.third.party\",\"a\":\"example\",\"v\":\"7.1.0\",\"p\":\"jar\",\"timestamp\":1462849968000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}]}}"
      )
      .issues(
        REMOTE_VERSION,
        RISKY_LIBRARY,
        DEPRECATED_LIBRARY,
        DEPENDENCY,
        PLAY_SDK_INDEX_NON_COMPLIANT,
        PLAY_SDK_INDEX_GENERIC_ISSUES
      )
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        """
          build.gradle:3: Warning: com.example.ads.third.party:example version 7.1.0 has Ads policy issues that will block publishing of your app to Play Console in the future [PlaySdkIndexNonCompliant]
              compile 'com.example.ads.third.party:example:7.1.0' //Issue but no suggestion since 7.1.4 has issues (error)
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 1 warnings
        """
      )
      .expectFixDiffs(expectedFixes)
  }

  /**
   * Test that version upgrade quickfix is shown if there is a custom message (even if the current
   * version has errors or warnings)
   */
  fun testSdkIndexLibraryUpgradeToVersionWithCustomMessage() {
    val expectedFixes =
      """
        Fix for build.gradle line 2: Change to 1.2.0:
        @@ -2 +2
        -     compile 'androidx.slidingpanelayout:slidingpanelayout:1.1.0' // Current has issues but there is a custom message
        +     compile 'androidx.slidingpanelayout:slidingpanelayout:1.2.0' // Current has issues but there is a custom message
        Fix for build.gradle line 3: Change to 18.3.0:
        @@ -3 +3
        -     compile 'com.google.android.gms:play-services-maps:18.1.0' // Current has issues but there is a custom message
        +     compile 'com.google.android.gms:play-services-maps:18.3.0' // Current has issues but there is a custom message
        Fix for build.gradle line 4: Change to 18.3.0:
        @@ -4 +4
        -     compile 'com.google.android.gms:play-services-maps:18.2.0' // There is a custom message but no issues
        +     compile 'com.google.android.gms:play-services-maps:18.3.0' // There is a custom message but no issues
        Show URL for build.gradle line 2: View details in Google Play SDK Index:
        http://sdk.google.com/
        Show URL for build.gradle line 3: View details in Google Play SDK Index:
        http://sdk.google.com/
      """
    lint()
      .files(
        gradle(
            """
                dependencies {
                    compile 'androidx.slidingpanelayout:slidingpanelayout:1.1.0' // Current has issues but there is a custom message
                    compile 'com.google.android.gms:play-services-maps:18.1.0' // Current has issues but there is a custom message
                    compile 'com.google.android.gms:play-services-maps:18.2.0' // There is a custom message but no issues
                }
                """
          )
          .indented()
      )
      .issues(
        REMOTE_VERSION,
        RISKY_LIBRARY,
        DEPRECATED_LIBRARY,
        DEPENDENCY,
        PLAY_SDK_INDEX_NON_COMPLIANT,
        PLAY_SDK_INDEX_GENERIC_ISSUES
      )
      .sdkHome(mockSupportLibraryInstallation)
      .run()
      .expect(
        """
          build.gradle:2: Warning: Upgrade androidx.slidingpanelayout for keyboard and mouse support [GradleDependency]
              compile 'androidx.slidingpanelayout:slidingpanelayout:1.1.0' // Current has issues but there is a custom message
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:3: Warning: A newer version of com.google.android.gms:play-services-maps than 18.1.0 is available: 18.3.0. Upgrading to at least 18.2.0 is highly recommended to take advantage of the new renderer, which supports customization options like map styling, 3D tiles, and is more reliable, with better support going forward. [GradleDependency]
              compile 'com.google.android.gms:play-services-maps:18.1.0' // Current has issues but there is a custom message
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:4: Warning: A newer version of com.google.android.gms:play-services-maps than 18.2.0 is available: 18.3.0 [GradleDependency]
              compile 'com.google.android.gms:play-services-maps:18.2.0' // There is a custom message but no issues
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:2: Error: androidx.slidingpanelayout:slidingpanelayout version 1.1.0 has Permissions policy issues that will block publishing of your app to Play Console [PlaySdkIndexNonCompliant]
              compile 'androidx.slidingpanelayout:slidingpanelayout:1.1.0' // Current has issues but there is a custom message
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:3: Warning: com.google.android.gms:play-services-maps version 18.1.0 has been marked as outdated by its author [OutdatedLibrary]
              compile 'com.google.android.gms:play-services-maps:18.1.0' // Current has issues but there is a custom message
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          1 errors, 4 warnings
        """
      )
      .expectFixDiffs(expectedFixes)
  }

  /**
   * Tests that the navigation libraries are not considered as part of androidx even when their name
   * does start with "androidx."
   */
  fun testAndroidxMixedDependenciesWithNavigation() {
    lint()
      .files(
        gradle(
          "" +
            "buildscript {\n" +
            "    repositories {\n" +
            "        jcenter()\n" +
            "    }\n" +
            "    dependencies {\n" +
            "        classpath 'com.android.tools.build:gradle:3.5.0-alpha10'\n" +
            "    }\n" +
            "}\n" +
            "dependencies {\n" +
            "    compile 'com.android.support:recyclerview-v7:28.0.0'\n" +
            "    compile 'androidx.navigation:navigation-fragment:1.0.0'\n" +
            "}\n"
        )
      )
      .issues(COMPATIBILITY)
      .run()
      .expect("No warnings.")
  }

  fun testDataBindingWithKaptUsingApplyPluginSyntax() {
    // android.dataBinding.enabled format
    lint()
      .files(
        gradle(
            """
                apply plugin: 'com.android.application'
                apply plugin: 'kotlin-android'
                apply plugin: 'kotlin-kapt'

                android {
                  dataBinding {
                    enabled true
                  }
                }
                """
          )
          .indented()
      )
      .issues(DATA_BINDING_WITHOUT_KAPT)
      .run()
      .expectClean()

    // android.buildFeatures.dataBinding format
    lint()
      .files(
        gradle(
            """
                apply plugin: 'com.android.application'
                apply plugin: 'kotlin-android'
                apply plugin: 'kotlin-kapt'

                android {
                  buildFeatures {
                    dataBinding true
                  }
                }
                """
          )
          .indented()
      )
      .issues(DATA_BINDING_WITHOUT_KAPT)
      .run()
      .expectClean()
  }

  fun testDataBindingWithoutKaptUsingApplyPluginSyntax() {
    // android.dataBinding.enabled format
    lint()
      .files(
        gradle(
            """
                apply plugin: 'com.android.application'
                apply plugin: 'kotlin-android'

                android {
                  dataBinding {
                    enabled true
                  }
                }
                """
          )
          .indented()
      )
      .issues(DATA_BINDING_WITHOUT_KAPT)
      .run()
      .expect(
        "build.gradle:6: Warning: If you plan to use data binding in a Kotlin project, you should apply the kotlin-kapt plugin. [DataBindingWithoutKapt]\n" +
          "    enabled true\n" +
          "    ~~~~~~~~~~~~\n" +
          "0 errors, 1 warnings"
      )

    // android.buildFeatures.dataBinding format
    lint()
      .files(
        gradle(
            """
                apply plugin: 'com.android.application'
                apply plugin: 'kotlin-android'

                android {
                  buildFeatures {
                    dataBinding true
                  }
                }
                """
          )
          .indented()
      )
      .issues(DATA_BINDING_WITHOUT_KAPT)
      .run()
      .expect(
        "build.gradle:6: Warning: If you plan to use data binding in a Kotlin project, you should apply the kotlin-kapt plugin. [DataBindingWithoutKapt]\n" +
          "    dataBinding true\n" +
          "    ~~~~~~~~~~~~~~~~\n" +
          "0 errors, 1 warnings"
      )
  }

  fun testDataBindingWithKaptUsingPluginBlockSyntax() {
    // Test groovy
    lint()
      .files(
        gradle(
            """
                plugins {
                  id 'com.android.application'
                  id 'kotlin-android'
                  id 'kotlin-kapt'
                }

                android {
                  dataBinding {
                    enabled true
                  }
                }
                """
          )
          .indented()
      )
      .issues(DATA_BINDING_WITHOUT_KAPT)
      .run()
      .expectClean()

    // Test kotlin
    lint()
      .files(
        kts(
            """
                plugins {
                  id("com.android.application")
                  id("kotlin-android")
                  id("kotlin-kapt")
                }

                android {
                  dataBinding {
                    isEnabled = true
                  }
                }
                """
          )
          .indented()
      )
      .issues(DATA_BINDING_WITHOUT_KAPT)
      .run()
      .expectClean()
  }

  fun testDataBindingWithoutKaptUsingPluginBlockSyntax() {
    // Test groovy
    lint()
      .files(
        gradle(
            """
                plugins {
                  id 'com.android.application'
                  id 'kotlin-android'
                }

                android {
                  dataBinding {
                    enabled true
                  }
                }
                """
          )
          .indented()
      )
      .issues(DATA_BINDING_WITHOUT_KAPT)
      .run()
      .expect(
        "build.gradle:8: Warning: If you plan to use data binding in a Kotlin project, you should apply the kotlin-kapt plugin. [DataBindingWithoutKapt]\n" +
          "    enabled true\n" +
          "    ~~~~~~~~~~~~\n" +
          "0 errors, 1 warnings"
      )

    // Test kotlin
    lint()
      .files(
        kts(
            """
                plugins {
                  id("com.android.application")
                  id("kotlin-android")
                }

                android {
                  dataBinding {
                    isEnabled = true
                  }
                }
                """
          )
          .indented()
      )
      .issues(DATA_BINDING_WITHOUT_KAPT)
      .run()
      .expect(
        "build.gradle.kts:8: Warning: If you plan to use data binding in a Kotlin project, you should apply the kotlin-kapt plugin. [DataBindingWithoutKapt]\n" +
          "    isEnabled = true\n" +
          "    ~~~~~~~~~~~~~~~~\n" +
          "0 errors, 1 warnings"
      )
  }

  fun testDataBindingWithKaptUsingMixedPluginSyntax() {
    lint()
      .files(
        gradle(
            """
                plugins {
                  id 'com.android.application'
                  id 'kotlin-android'
                }

                apply plugin: 'kotlin-kapt'

                android {
                  dataBinding {
                    enabled true
                  }
                }
                """
          )
          .indented()
      )
      .issues(DATA_BINDING_WITHOUT_KAPT)
      .run()
      .expectClean()
  }

  fun testJava8WithLifecycleAnnotationProcessor() {
    lint()
      .files(
        gradle(
          "dependencies {\n" +
            "  implementation \"android.arch.lifecycle:runtime:1.1.1\"\n" +
            "  annotationProcessor \"android.arch.lifecycle:compiler:1.1.1\"\n" +
            "}" +
            "android {\n" +
            "    compileOptions {\n" +
            "        sourceCompatibility JavaVersion.VERSION_1_8\n" +
            "        targetCompatibility JavaVersion.VERSION_1_8\n" +
            "    }\n" +
            "}"
        )
      )
      .issues(LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8)
      .run()
      .expect(
        "" +
          "build.gradle:3: Warning: Use the Lifecycle Java 8 API provided by the lifecycle-common library instead of Lifecycle annotations for faster incremental build. [LifecycleAnnotationProcessorWithJava8]\n" +
          "  annotationProcessor \"android.arch.lifecycle:compiler:1.1.1\"\n" +
          "                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "0 errors, 1 warnings"
      )
  }

  fun testJava8WithoutLifecycleAnnotationProcessor() {
    lint()
      .files(
        gradle(
          "dependencies {\n" +
            "  implementation \"android.arch.lifecycle:runtime:1.1.1\"\n" +
            "  implementation \"android.arch.lifecycle:common-java8:1.1.1\"\n" +
            "}" +
            "android {\n" +
            "    compileOptions {\n" +
            "        sourceCompatibility JavaVersion.VERSION_1_8\n" +
            "        targetCompatibility JavaVersion.VERSION_1_8\n" +
            "    }\n" +
            "}"
        )
      )
      .issues(LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8)
      .run()
      .expectClean()
  }

  fun testJava7WithLifecycleAnnotationProcessor() {
    lint()
      .files(
        gradle(
          "dependencies {\n" +
            "  implementation \"android.arch.lifecycle:runtime:1.1.1\"\n" +
            "  annotationProcessor \"android.arch.lifecycle:compiler:1.1.1\"\n" +
            "}" +
            "android {\n" +
            "    compileOptions {\n" +
            "        sourceCompatibility JavaVersion.VERSION_1_7\n" +
            "        targetCompatibility JavaVersion.VERSION_1_7\n" +
            "    }\n" +
            "}"
        )
      )
      .issues(LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8)
      .run()
      .expectClean()
  }

  fun testCompileDeprecationInConsumableModule() {
    val expected =
      """
            build.gradle:9: Warning: compile is deprecated; replace with either api to maintain current behavior, or implementation to improve build performance by not sharing this dependency transitively. [GradleDeprecatedConfiguration]
                compile 'androidx.appcompat:appcompat:1.0.0'
                ~~~~~~~
            build.gradle:10: Warning: debugCompile is deprecated; replace with either debugApi to maintain current behavior, or debugImplementation to improve build performance by not sharing this dependency transitively. [GradleDeprecatedConfiguration]
                debugCompile 'androidx.appcompat:appcompat:1.0.0'
                ~~~~~~~~~~~~
            0 errors, 2 warnings"""

    val expectedFix =
      """
            Autofix for build.gradle line 9: Replace 'compile' with 'api':
            @@ -9 +9
            -     compile 'androidx.appcompat:appcompat:1.0.0'
            +     api 'androidx.appcompat:appcompat:1.0.0'
            Autofix for build.gradle line 9: Replace 'compile' with 'implementation':
            @@ -9 +9
            -     compile 'androidx.appcompat:appcompat:1.0.0'
            +     implementation 'androidx.appcompat:appcompat:1.0.0'
            Autofix for build.gradle line 10: Replace 'debugCompile' with 'debugApi':
            @@ -10 +10
            -     debugCompile 'androidx.appcompat:appcompat:1.0.0'
            +     debugApi 'androidx.appcompat:appcompat:1.0.0'
            Autofix for build.gradle line 10: Replace 'debugCompile' with 'debugImplementation':
            @@ -10 +10
            -     debugCompile 'androidx.appcompat:appcompat:1.0.0'
            +     debugImplementation 'androidx.appcompat:appcompat:1.0.0'
            """

    lint()
      .files(
        gradle(
            """
                        buildscript {
                            dependencies {
                                classpath 'com.android.tools.build:gradle:3.0.0'
                            }
                        }
                        apply plugin: 'com.android.library'

                        dependencies {
                            compile 'androidx.appcompat:appcompat:1.0.0'
                            debugCompile 'androidx.appcompat:appcompat:1.0.0'
                        }
                    """
          )
          .indented()
      )
      .issues(DEPRECATED_CONFIGURATION)
      .run()
      .expect(expected)
      .expectFixDiffs(expectedFix)
  }

  fun testCompileDeprecationInLeafModule() {
    val expected =
      """
            build.gradle:9: Warning: compile is deprecated; replace with implementation [GradleDeprecatedConfiguration]
                compile 'androidx.appcompat:appcompat:1.0.0'
                ~~~~~~~
            0 errors, 1 warnings"""

    val expectedFix =
      """
            Autofix for build.gradle line 9: Replace 'compile' with 'implementation':
            @@ -9 +9
            -     compile 'androidx.appcompat:appcompat:1.0.0'
            +     implementation 'androidx.appcompat:appcompat:1.0.0'
            """

    lint()
      .files(
        gradle(
            """
                        buildscript {
                            dependencies {
                                classpath 'com.android.tools.build:gradle:3.0.0'
                            }
                        }
                        apply plugin: 'com.android.application'

                        dependencies {
                            compile 'androidx.appcompat:appcompat:1.0.0'
                        }
                    """
          )
          .indented()
      )
      .issues(DEPRECATED_CONFIGURATION)
      .run()
      .expect(expected)
      .expectFixDiffs(expectedFix)
  }

  fun testTestCompileDeprecation() {
    val expected =
      """
            build.gradle:7: Warning: testCompile is deprecated; replace with testImplementation [GradleDeprecatedConfiguration]
                testCompile 'androidx.appcompat:appcompat:1.0.0'
                ~~~~~~~~~~~
            build.gradle:8: Warning: testDebugCompile is deprecated; replace with testDebugImplementation [GradleDeprecatedConfiguration]
                testDebugCompile 'androidx.appcompat:appcompat:1.0.0'
                ~~~~~~~~~~~~~~~~
            build.gradle:9: Warning: androidTestDebugCompile is deprecated; replace with androidTestDebugImplementation [GradleDeprecatedConfiguration]
                androidTestDebugCompile 'androidx.appcompat:appcompat:1.0.0'
                ~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """

    val fixDiff =
      "Autofix for build.gradle line 7: Replace 'testCompile' with 'testImplementation':\n" +
        "@@ -7 +7\n" +
        "-     testCompile 'androidx.appcompat:appcompat:1.0.0'\n" +
        "+     testImplementation 'androidx.appcompat:appcompat:1.0.0'\n" +
        "Autofix for build.gradle line 8: Replace 'testDebugCompile' with 'testDebugImplementation':\n" +
        "@@ -8 +8\n" +
        "-     testDebugCompile 'androidx.appcompat:appcompat:1.0.0'\n" +
        "+     testDebugImplementation 'androidx.appcompat:appcompat:1.0.0'\n" +
        "Autofix for build.gradle line 9: Replace 'androidTestDebugCompile' with 'androidTestDebugImplementation':\n" +
        "@@ -9 +9\n" +
        "-     androidTestDebugCompile 'androidx.appcompat:appcompat:1.0.0'\n" +
        "+     androidTestDebugImplementation 'androidx.appcompat:appcompat:1.0.0'"

    lint()
      .files(
        gradle(
            """
                    buildscript {
                        dependencies {
                            classpath 'com.android.tools.build:gradle:3.0.0'
                        }
                    }
                    dependencies {
                        testCompile 'androidx.appcompat:appcompat:1.0.0'
                        testDebugCompile 'androidx.appcompat:appcompat:1.0.0'
                        androidTestDebugCompile 'androidx.appcompat:appcompat:1.0.0'
                    }
                """
          )
          .indented()
      )
      .issues(DEPRECATED_CONFIGURATION)
      .run()
      .expect(expected)
      .expectFixDiffs(fixDiff)
  }

  fun testAnnotationProcessorOnCompilePath() {
    val expected =
      """
            build.gradle:2: Warning: Add annotation processor to processor path using annotationProcessor instead of api [AnnotationProcessorOnCompilePath]
                api 'com.jakewharton:butterknife-compiler:10.1.0'
                ~~~
            build.gradle:3: Warning: Add annotation processor to processor path using annotationProcessor instead of implementation [AnnotationProcessorOnCompilePath]
                implementation 'com.github.bumptech.glide:compiler:4.9.0'
                ~~~~~~~~~~~~~~
            build.gradle:4: Warning: Add annotation processor to processor path using annotationProcessor instead of compile [AnnotationProcessorOnCompilePath]
                compile "androidx.lifecycle:lifecycle-compiler:2.2.0-alpha01"
                ~~~~~~~
            build.gradle:5: Warning: Add annotation processor to processor path using testAnnotationProcessor instead of testImplementation [AnnotationProcessorOnCompilePath]
                testImplementation "com.google.auto.value:auto-value:1.6.2"
                ~~~~~~~~~~~~~~~~~~
            build.gradle:6: Warning: Add annotation processor to processor path using androidTestAnnotationProcessor instead of androidTestCompile [AnnotationProcessorOnCompilePath]
                androidTestCompile "org.projectlombok:lombok:1.18.8"
                ~~~~~~~~~~~~~~~~~~
            build.gradle:8: Warning: Add annotation processor to processor path using debugAnnotationProcessor instead of debugCompile [AnnotationProcessorOnCompilePath]
                debugCompile "android.arch.persistence.room:compiler:1.1.1"
                ~~~~~~~~~~~~
            0 errors, 6 warnings
        """
    val fixDiff =
      """
            Autofix for build.gradle line 2: Replace api with annotationProcessor:
            @@ -2 +2
            -     api 'com.jakewharton:butterknife-compiler:10.1.0'
            +     annotationProcessor 'com.jakewharton:butterknife-compiler:10.1.0'
            Autofix for build.gradle line 3: Replace implementation with annotationProcessor:
            @@ -3 +3
            -     implementation 'com.github.bumptech.glide:compiler:4.9.0'
            +     annotationProcessor 'com.github.bumptech.glide:compiler:4.9.0'
            Autofix for build.gradle line 4: Replace compile with annotationProcessor:
            @@ -4 +4
            -     compile "androidx.lifecycle:lifecycle-compiler:2.2.0-alpha01"
            +     annotationProcessor "androidx.lifecycle:lifecycle-compiler:2.2.0-alpha01"
            Autofix for build.gradle line 5: Replace testImplementation with testAnnotationProcessor:
            @@ -5 +5
            -     testImplementation "com.google.auto.value:auto-value:1.6.2"
            +     testAnnotationProcessor "com.google.auto.value:auto-value:1.6.2"
            Autofix for build.gradle line 6: Replace androidTestCompile with androidTestAnnotationProcessor:
            @@ -6 +6
            -     androidTestCompile "org.projectlombok:lombok:1.18.8"
            +     androidTestAnnotationProcessor "org.projectlombok:lombok:1.18.8"
            Autofix for build.gradle line 8: Replace debugCompile with debugAnnotationProcessor:
            @@ -8 +8
            -     debugCompile "android.arch.persistence.room:compiler:1.1.1"
            +     debugAnnotationProcessor "android.arch.persistence.room:compiler:1.1.1"
        """
    lint()
      .files(
        gradle(
            """
                    dependencies {
                        api 'com.jakewharton:butterknife-compiler:10.1.0'
                        implementation 'com.github.bumptech.glide:compiler:4.9.0'
                        compile "androidx.lifecycle:lifecycle-compiler:2.2.0-alpha01"
                        testImplementation "com.google.auto.value:auto-value:1.6.2"
                        androidTestCompile "org.projectlombok:lombok:1.18.8"
                        annotationProcessor 'com.jakewharton:butterknife-compiler:10.1.0'
                        debugCompile "android.arch.persistence.room:compiler:1.1.1"
                        implementation "com.jakewharton:butterknife:10.1.0"
                    }
                """
          )
          .indented()
      )
      .issues(ANNOTATION_PROCESSOR_ON_COMPILE_PATH)
      .run()
      .expect(expected)
      .expectFixDiffs(fixDiff)
  }

  fun testKtxExtensions() {
    lint()
      .files(
        gradle(
            """
                    plugins {
                        id 'com.android.application'
                        id 'kotlin-android'
                    }
                    dependencies {
                        implementation "org.jetbrains.kotlin:kotlin-stdlib:1.0.0"
                        implementation "androidx.core:core:1.2.0"
                        implementation "androidx.core:core:999.2.0" // No KTX extensions for this version.
                        implementation "androidx.core:fake-artifact:1.2.0" // No KTX extensions for this artifact.
                    }
                """
          )
          .indented()
      )
      .issues(KTX_EXTENSION_AVAILABLE)
      .run()
      .expect(
        """
                build.gradle:7: Information: Add suffix -ktx to enable the Kotlin extensions for this library [KtxExtensionAvailable]
                    implementation "androidx.core:core:1.2.0"
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle line 7: Replace with KTX dependency:
                @@ -7 +7
                -     implementation "androidx.core:core:1.2.0"
                +     implementation "androidx.core:core-ktx:1.2.0"
                """
      )
  }

  fun testKtxExtensionsClean() {
    // Expect clean when the project does not depend on Kotlin.
    lint()
      .files(
        gradle(
            """
                    plugins {
                        id 'com.android.application'
                    }
                    dependencies {
                        implementation "androidx.core:core:1.2.0"
                    }
                """
          )
          .indented()
      )
      .issues(KTX_EXTENSION_AVAILABLE)
      .run()
      .expectClean()

    // Expect clean when the project only depends on Kotlin for tests.
    lint()
      .files(
        gradle(
            """
                    plugins {
                        id 'com.android.application'
                        id 'kotlin-android'
                    }
                    dependencies {
                        testImplementation "org.jetbrains.kotlin:kotlin-stdlib:1.0.0"
                        implementation "androidx.core:core:1.2.0"
                    }
                """
          )
          .indented()
      )
      .issues(KTX_EXTENSION_AVAILABLE)
      .run()
      .expectClean()
  }

  fun testKaptToKspMigration() {
    lint()
      .files(
        gradle(
            """
                    plugins {
                        id 'com.android.application'
                        id 'kotlin-android'
                        id 'kotlin-kapt'
                        id 'com.google.devtools.ksp'
                    }
                    dependencies {
                      def room_version = "2.5.0"
                      kapt 'androidx.room:room-compiler:2.5.0'
                      kapt "androidx.room:room-compiler:＄room_version"

                      def glide_version = "4.14.2"
                      kapt 'com.github.bumptech.glide:compiler:4.14.2'
                      kapt("com.github.bumptech.glide:compiler:glide_version")
                    }
                """
          )
          .indented()
      )
      .issues(KAPT_USAGE_INSTEAD_OF_KSP)
      .run()
      .expect(
        """
                build.gradle:9: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt 'androidx.room:room-compiler:2.5.0'
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:10: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt "androidx.room:room-compiler:＄room_version"
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:13: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt 'com.github.bumptech.glide:compiler:4.14.2'
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:14: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt("com.github.bumptech.glide:compiler:glide_version")
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 4 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle line 9: Replace usage of kapt with KSP:
                @@ -9 +9
                -   kapt 'androidx.room:room-compiler:2.5.0'
                +   ksp 'androidx.room:room-compiler:2.5.0'
                Fix for build.gradle line 10: Replace usage of kapt with KSP:
                @@ -10 +10
                -   kapt "androidx.room:room-compiler:＄room_version"
                +   ksp "androidx.room:room-compiler:＄room_version"
                Fix for build.gradle line 13: Replace usage of kapt with KSP:
                @@ -13 +13
                -   kapt 'com.github.bumptech.glide:compiler:4.14.2'
                +   ksp 'com.github.bumptech.glide:ksp:4.14.2'
                Fix for build.gradle line 14: Replace usage of kapt with KSP:
                @@ -14 +14
                -   kapt("com.github.bumptech.glide:compiler:glide_version")
                +   ksp("com.github.bumptech.glide:ksp:glide_version")
                """
      )
  }

  fun testKaptToKspMigrationWithVersionCatalog() {
    lint()
      .files(
        gradleToml(
            """
                [versions]
                room = "2.5.0"
                [libraries]
                room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
                glide-simple = "com.github.bumptech.glide:compiler:4.14.2"
                glide-module = { module = "com.github.bumptech.glide:compiler", version = "4.14.2" }
                glide-groupname = { group = "com.github.bumptech.glide", name = "compiler", version = "4.14.2" }
                glide-but_fun-and_exciting = { group = "com.github.bumptech.glide", name = "compiler", version = "4.14.2" }
                """
          )
          .indented(),
        gradle(
            """
                    plugins {
                        id 'com.android.application'
                        id 'kotlin-android'
                        id 'kotlin-kapt'
                        id 'com.google.devtools.ksp'
                    }
                    dependencies {
                      kapt(libs.room.compiler)
                      kapt(libs.glide.simple)
                      kapt(libs.glide.module)
                      kapt(libs.glide.groupname)
                      kapt(libs.glide.but.fun.and.exciting)
                    }
                """
          )
          .indented()
      )
      .issues(KAPT_USAGE_INSTEAD_OF_KSP)
      .run()
      .expect(
        """
                build.gradle:8: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt(libs.room.compiler)
                  ~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:9: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt(libs.glide.simple)
                  ~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:10: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt(libs.glide.module)
                  ~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:11: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt(libs.glide.groupname)
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:12: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt(libs.glide.but.fun.and.exciting)
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 5 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle line 8: Replace usage of kapt with KSP:
                @@ -8 +8
                -   kapt(libs.room.compiler)
                +   ksp(libs.room.compiler)
                Fix for build.gradle line 9: Replace usage of kapt with KSP:
                @@ -9 +9
                -   kapt(libs.glide.simple)
                +   ksp(libs.glide.simple)
                gradle/libs.versions.toml:
                @@ -5 +5
                - glide-simple = "com.github.bumptech.glide:compiler:4.14.2"
                + glide-simple = "com.github.bumptech.glide:ksp:4.14.2"
                Fix for build.gradle line 10: Replace usage of kapt with KSP:
                @@ -10 +10
                -   kapt(libs.glide.module)
                +   ksp(libs.glide.module)
                gradle/libs.versions.toml:
                @@ -6 +6
                - glide-module = { module = "com.github.bumptech.glide:compiler", version = "4.14.2" }
                + glide-module = { module = "com.github.bumptech.glide:ksp", version = "4.14.2" }
                Fix for build.gradle line 11: Replace usage of kapt with KSP:
                @@ -11 +11
                -   kapt(libs.glide.groupname)
                +   ksp(libs.glide.groupname)
                gradle/libs.versions.toml:
                @@ -7 +7
                - glide-groupname = { group = "com.github.bumptech.glide", name = "compiler", version = "4.14.2" }
                + glide-groupname = { group = "com.github.bumptech.glide", name = "ksp", version = "4.14.2" }
                Fix for build.gradle line 12: Replace usage of kapt with KSP:
                @@ -12 +12
                -   kapt(libs.glide.but.fun.and.exciting)
                +   ksp(libs.glide.but.fun.and.exciting)
                gradle/libs.versions.toml:
                @@ -8 +8
                - glide-but_fun-and_exciting = { group = "com.github.bumptech.glide", name = "compiler", version = "4.14.2" }
                + glide-but_fun-and_exciting = { group = "com.github.bumptech.glide", name = "ksp", version = "4.14.2" }
                """
      )
  }

  /*
   * Tests that plugins like KSP are detected correctly, even when they're included
   * using a version catalog plugin entry and an alias in a plugin block
   */
  fun testKaptToKspMigrationWithEverythingInVersionCatalog() {
    lint()
      .files(
        gradleToml(
            """
                [versions]
                glide = "4.14.2"
                kotlin = "1.7.20"
                [libraries]
                glide = { module = "com.github.bumptech.glide:compiler", version.ref = "glide" }
                [plugins]
                android-application = { id = "com.android.application", version = "7.3.1" }
                kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
                kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
                ksp = { id = "com.google.devtools.ksp", version = "1.7.20-1.0.8" }
                """
          )
          .indented(),
        gradle(
            """
                plugins {
                    alias(libs.plugins.android.application)
                    alias(libs.plugins.kotlin.android)
                    alias(libs.plugins.kotlin.kapt)
                    alias(libs.plugins.ksp)
                }
                dependencies {
                    kapt(libs.glide)
                }
                """
          )
          .indented()
      )
      .issues(KAPT_USAGE_INSTEAD_OF_KSP)
      .run()
      .expect(
        """
                build.gradle:8: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                    kapt(libs.glide)
                    ~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle line 8: Replace usage of kapt with KSP:
                @@ -8 +8
                -     kapt(libs.glide)
                +     ksp(libs.glide)
                gradle/libs.versions.toml:
                @@ -5 +5
                - glide = { module = "com.github.bumptech.glide:compiler", version.ref = "glide" }
                + glide = { module = "com.github.bumptech.glide:ksp", version.ref = "glide" }
                """
      )
  }

  fun testKaptToKspMigrationWithKspNotEnabledYet() {
    lint()
      .files(
        gradleToml(
            """
                [versions]
                room = "2.5.0"
                [libraries]
                room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
                glide = "com.github.bumptech.glide:compiler:4.14.2"
                """
          )
          .indented(),
        gradle(
            """
                    plugins {
                        id 'com.android.application'
                        id 'kotlin-android'
                        id 'kotlin-kapt'
                    }
                    dependencies {
                      kapt 'androidx.room:room-compiler:2.5.0'
                      kapt 'com.github.bumptech.glide:compiler:4.14.2'
                      kapt(libs.room.compiler)
                      kapt(libs.glide)
                    }
                """
          )
          .indented()
      )
      .issues(KAPT_USAGE_INSTEAD_OF_KSP)
      .run()
      .expect(
        """
                build.gradle:7: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt 'androidx.room:room-compiler:2.5.0'
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:8: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt 'com.github.bumptech.glide:compiler:4.14.2'
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:9: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt(libs.room.compiler)
                  ~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle:10: Warning: This library supports using KSP instead of kapt, which greatly improves performance. Learn more: https://developer.android.com/studio/build/migrate-to-ksp [KaptUsageInsteadOfKsp]
                  kapt(libs.glide)
                  ~~~~~~~~~~~~~~~~
                0 errors, 4 warnings
                """
      )
      .expectFixDiffs(
        """
                Show URL for build.gradle line 7: Learn about how to enable KSP and use the KSP processor for this dependency instead:
                https://developer.android.com/studio/build/migrate-to-ksp
                Show URL for build.gradle line 8: Learn about how to enable KSP and use the KSP processor for this dependency instead:
                https://developer.android.com/studio/build/migrate-to-ksp
                Show URL for build.gradle line 9: Learn about how to enable KSP and use the KSP processor for this dependency instead:
                https://developer.android.com/studio/build/migrate-to-ksp
                Show URL for build.gradle line 10: Learn about how to enable KSP and use the KSP processor for this dependency instead:
                https://developer.android.com/studio/build/migrate-to-ksp
                """
      )
  }

  fun testPlatformVersionDependencySuggestions() {
    // b/313937481
    lint()
      .files(
        gradle(
            """
            dependencies {
                implementation(platform(libs.compose.bom)) // OK 1g: newer version reported in version catalog instead
                implementation platform("androidx.compose:compose-bom:2022.12.00") // ERROR 1g
                implementation testFixtures("androidx.compose:compose-bom:2022.12.00") // ERROR 2g
                implementation(enforcedPlatform("androidx.compose:compose-bom:2022.12.00")) // ERROR 3g
            }
            """
          )
          .indented(),
        kts(
          """
          dependencies {
            implementation(platform(libs.compose.bom)) // OK 1k: newer version reported in version catalog instead
            implementation(platform("androidx.compose:compose-bom:2022.12.00")) // ERROR 1k
            implementation(testFixtures("androidx.compose:compose-bom:2022.12.00")) // ERROR 2k
            implementation(enforcedPlatform("androidx.compose:compose-bom:2022.12.00")) // ERROR 3k
          }
          """
        ),
        gradleToml(
            """
            [versions]
            composeBom = "2022.12.00" # ERROR 1t
            [libraries]
            compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
            """
          )
          .indented()
      )
      .issues(DEPENDENCY)
      .run()
      .expect(
        """
        build.gradle:3: Warning: A newer version of androidx.compose:compose-bom than 2022.12.00 is available: 2023.01.00 [GradleDependency]
            implementation platform("androidx.compose:compose-bom:2022.12.00") // ERROR 1g
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle:4: Warning: A newer version of androidx.compose:compose-bom than 2022.12.00 is available: 2023.01.00 [GradleDependency]
            implementation testFixtures("androidx.compose:compose-bom:2022.12.00") // ERROR 2g
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle:5: Warning: A newer version of androidx.compose:compose-bom than 2022.12.00 is available: 2023.01.00 [GradleDependency]
            implementation(enforcedPlatform("androidx.compose:compose-bom:2022.12.00")) // ERROR 3g
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle.kts:4: Warning: A newer version of androidx.compose:compose-bom than 2022.12.00 is available: 2023.01.00 [GradleDependency]
                    implementation(platform("androidx.compose:compose-bom:2022.12.00")) // ERROR 1k
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle.kts:5: Warning: A newer version of androidx.compose:compose-bom than 2022.12.00 is available: 2023.01.00 [GradleDependency]
                    implementation(testFixtures("androidx.compose:compose-bom:2022.12.00")) // ERROR 2k
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle.kts:6: Warning: A newer version of androidx.compose:compose-bom than 2022.12.00 is available: 2023.01.00 [GradleDependency]
                    implementation(enforcedPlatform("androidx.compose:compose-bom:2022.12.00")) // ERROR 3k
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        ../gradle/libs.versions.toml:2: Warning: A newer version of androidx.compose:compose-bom than 2022.12.00 is available: 2023.01.00 [GradleDependency]
        composeBom = "2022.12.00" # ERROR 1t
                     ~~~~~~~~~~~~
        0 errors, 7 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for build.gradle line 3: Change to 2023.01.00:
        @@ -3 +3
        -     implementation platform("androidx.compose:compose-bom:2022.12.00") // ERROR 1g
        +     implementation platform("androidx.compose:compose-bom:2023.01.00") // ERROR 1g
        Fix for build.gradle line 4: Change to 2023.01.00:
        @@ -4 +4
        -     implementation testFixtures("androidx.compose:compose-bom:2022.12.00") // ERROR 2g
        +     implementation testFixtures("androidx.compose:compose-bom:2023.01.00") // ERROR 2g
        Fix for build.gradle line 5: Change to 2023.01.00:
        @@ -5 +5
        -     implementation(enforcedPlatform("androidx.compose:compose-bom:2022.12.00")) // ERROR 3g
        +     implementation(enforcedPlatform("androidx.compose:compose-bom:2023.01.00")) // ERROR 3g
        Fix for build.gradle.kts line 4: Change to 2023.01.00:
        @@ -4 +4
        -             implementation(platform("androidx.compose:compose-bom:2022.12.00")) // ERROR 1k
        +             implementation(platform("androidx.compose:compose-bom:2023.01.00")) // ERROR 1k
        Fix for build.gradle.kts line 5: Change to 2023.01.00:
        @@ -5 +5
        -             implementation(testFixtures("androidx.compose:compose-bom:2022.12.00")) // ERROR 2k
        +             implementation(testFixtures("androidx.compose:compose-bom:2023.01.00")) // ERROR 2k
        Fix for build.gradle.kts line 6: Change to 2023.01.00:
        @@ -6 +6
        -             implementation(enforcedPlatform("androidx.compose:compose-bom:2022.12.00")) // ERROR 3k
        +             implementation(enforcedPlatform("androidx.compose:compose-bom:2023.01.00")) // ERROR 3k
        Fix for gradle/libs.versions.toml line 2: Change to 2023.01.00:
        @@ -2 +2
        - composeBom = "2022.12.00" # ERROR 1t
        + composeBom = "2023.01.00" # ERROR 1t
        """
      )
  }

  fun testBomWithoutPlatform() {
    lint()
      .files(
        gradleToml(
            """
            [versions]
            composeBom = "2023.01.00"
            [libraries]
            compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
            """
          )
          .indented(),
        gradle(
            """
            plugins {
                id 'com.android.application'
                id 'kotlin-android'
            }
            dependencies {
                implementation(libs.compose.bom)
                testImplementation(libs.compose.bom)
                testImplementation "androidx.compose:compose-bom:2023.01.00"
                api("androidx.compose:compose-bom:2023.01.00")
                // Make sure we don't complain about existing platforms
                implementation platform("androidx.compose:compose-bom:2023.01.00")
            }
            """
          )
          .indented()
      )
      .issues(BOM_WITHOUT_PLATFORM)
      .run()
      .expect(
        """
        build.gradle:6: Warning: BOM should be added with a call to platform() [BomWithoutPlatform]
            implementation(libs.compose.bom)
                           ~~~~~~~~~~~~~~~~
        build.gradle:7: Warning: BOM should be added with a call to platform() [BomWithoutPlatform]
            testImplementation(libs.compose.bom)
                               ~~~~~~~~~~~~~~~~
        build.gradle:8: Warning: BOM should be added with a call to platform() [BomWithoutPlatform]
            testImplementation "androidx.compose:compose-bom:2023.01.00"
                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle:9: Warning: BOM should be added with a call to platform() [BomWithoutPlatform]
            api("androidx.compose:compose-bom:2023.01.00")
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 4 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for build.gradle line 6: Add platform() to BOM declaration:
        @@ -6 +6
        -     implementation(libs.compose.bom)
        +     implementation(platform(libs.compose.bom))
        Fix for build.gradle line 7: Add platform() to BOM declaration:
        @@ -7 +7
        -     testImplementation(libs.compose.bom)
        +     testImplementation(platform(libs.compose.bom))
        Fix for build.gradle line 8: Add platform() to BOM declaration:
        @@ -8 +8
        -     testImplementation "androidx.compose:compose-bom:2023.01.00"
        +     testImplementation platform("androidx.compose:compose-bom:2023.01.00")
        Fix for build.gradle line 9: Add platform() to BOM declaration:
        @@ -9 +9
        -     api("androidx.compose:compose-bom:2023.01.00")
        +     api(platform("androidx.compose:compose-bom:2023.01.00"))
        """
      )
  }

  fun testBomWithoutPlatformClean() {
    lint()
      .files(
        gradleToml(
            """
            [versions]
            composeBom = "2023.01.00"
            [libraries]
            compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
            """
          )
          .indented(),
        gradle(
            """
            plugins {
                id 'com.android.application'
                id 'kotlin-android'
            }
            dependencies {
                def composeBom = platform(libs.compose.bom)
                implementation(composeBom)
                implementation(platform(libs.compose.bom))
                api(platform("androidx.compose:compose-bom:2023.01.00"))
                testImplementation(platform("androidx.compose:compose-bom:2023.01.00"))
            }
            """
          )
          .indented()
      )
      .issues(BOM_WITHOUT_PLATFORM)
      .run()
      .expectClean()
  }

  fun testJavaLanguageLevelClean() {
    val sourceCompatibility =
      listOf(
        "java.sourceCompatibility JavaVersion.VERSION_1_8",
        "java.sourceCompatibility = JavaVersion.VERSION_1_8",
        "java { sourceCompatibility JavaVersion.VERSION_1_8 }",
        "java { sourceCompatibility = JavaVersion.VERSION_1_8 }",
        // special-case syntax (assignment only)
        "sourceCompatibility = JavaVersion.VERSION_1_8"
      )
    val targetCompatibility =
      listOf(
        "java.targetCompatibility JavaVersion.VERSION_1_8",
        "java.targetCompatibility = JavaVersion.VERSION_1_8",
        "java { targetCompatibility JavaVersion.VERSION_1_8 }",
        "java { targetCompatibility = JavaVersion.VERSION_1_8 }",
        "targetCompatibility = JavaVersion.VERSION_1_8"
      )
    sourceCompatibility.forEach { sc ->
      targetCompatibility.forEach { tc ->
        lint()
          .files(
            gradle(
                """
                           plugins {
                               id 'java'
                           }
                        """
                  .trimIndent() + "\n${sc}\n$tc"
              )
              .indented()
          )
          .issues(JAVA_PLUGIN_LANGUAGE_LEVEL)
          .run()
          .expectClean()
      }
    }
  }

  fun testJavaLanguageLevelNoSourceCompatibility() {
    val targetCompatibility =
      listOf(
        "java.targetCompatibility JavaVersion.VERSION_1_8",
        "java.targetCompatibility = JavaVersion.VERSION_1_8",
        "java { targetCompatibility JavaVersion.VERSION_1_8 }",
        "java { targetCompatibility = JavaVersion.VERSION_1_8 }",
        "targetCompatibility = JavaVersion.VERSION_1_8"
      )
    targetCompatibility.forEach { tc ->
      lint()
        .files(
          gradle(
              """
                        plugins {
                           id 'java'
                        }
                    """
                .trimIndent() + "\n$tc"
            )
            .indented()
        )
        .issues(JAVA_PLUGIN_LANGUAGE_LEVEL)
        .run()
        .expect(
          """
                        build.gradle:2: Warning: no Java sourceCompatibility directive [JavaPluginLanguageLevel]
                           id 'java'
                           ~~~~~~~~~
                        0 errors, 1 warnings
                    """
        )
        .expectFixDiffs(
          """
                        Fix for build.gradle line 2: Insert sourceCompatibility directive for JDK8:
                        @@ -5 +5
                        + java.sourceCompatibility = JavaVersion.VERSION_1_8
                    """
        )
    }
  }

  fun testJavaLanguageLevelNoTargetCompatibility() {
    val sourceCompatibility =
      listOf(
        "java.sourceCompatibility JavaVersion.VERSION_1_8",
        "java.sourceCompatibility = JavaVersion.VERSION_1_8",
        "java { sourceCompatibility JavaVersion.VERSION_1_8 }",
        "java { sourceCompatibility = JavaVersion.VERSION_1_8 }",
        "sourceCompatibility = JavaVersion.VERSION_1_8"
      )
    sourceCompatibility.forEach { sc ->
      lint()
        .files(
          gradle(
              """
                        plugins {
                           id 'java'
                        }
                    """
                .trimIndent() + "\n$sc"
            )
            .indented()
        )
        .issues(JAVA_PLUGIN_LANGUAGE_LEVEL)
        .run()
        .expect(
          """
                        build.gradle:2: Warning: no Java targetCompatibility directive [JavaPluginLanguageLevel]
                           id 'java'
                           ~~~~~~~~~
                        0 errors, 1 warnings
                    """
        )
        .expectFixDiffs(
          """
                        Fix for build.gradle line 2: Insert targetCompatibility directive for JDK8:
                        @@ -5 +5
                        + java.targetCompatibility = JavaVersion.VERSION_1_8
                    """
        )
    }
  }

  fun testJavaLanguageLevelNoDirectives() {
    val plugins =
      listOf(
        "java",
        "java-library",
        "application",
        "org.gradle.java",
        "org.gradle.java-library",
        "org.gradle.application"
      )
    plugins.forEach { plugin ->
      lint()
        .files(
          gradle(
              """
                    plugins {
                       id '$plugin'
                    }
                    """
            )
            .indented()
        )
        .issues(JAVA_PLUGIN_LANGUAGE_LEVEL)
        .run()
        .expect(
          """
                    build.gradle:2: Warning: no Java language level directives [JavaPluginLanguageLevel]
                       id '$plugin'
                       ${"~".repeat(plugin.length + 5)}
                    0 errors, 1 warnings
                """
        )
        .expectFixDiffs(
          """
                    Fix for build.gradle line 2: Insert JDK8 language level directives:
                    @@ -4 +4
                    + java {
                    +     sourceCompatibility = JavaVersion.VERSION_1_8
                    +     targetCompatibility = JavaVersion.VERSION_1_8
                    + }
                    """
        )
    }
  }

  fun testJavaLanguageLevelWithToolChains() {
    lint()
      .files(
        gradle(
            """
            plugins {
                id 'java'
                id 'org.jetbrains.kotlin.jvm'
                id 'com.android.lint'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }

            kotlin {
                jvmToolchain(17)
            }
            """
          )
          .indented()
      )
      .issues(JAVA_PLUGIN_LANGUAGE_LEVEL)
      .run()
      .expectClean()
  }

  fun testJavaLanguageLevelCleanKts() {
    val sourceCompatibility =
      listOf(
        "java.sourceCompatibility = JavaVersion.VERSION_1_8",
        "java { sourceCompatibility = JavaVersion.VERSION_1_8 }"
      )
    val targetCompatibility =
      listOf(
        "java.targetCompatibility = JavaVersion.VERSION_1_8",
        "java { targetCompatibility = JavaVersion.VERSION_1_8 }"
      )
    sourceCompatibility.forEach { sc ->
      targetCompatibility.forEach { tc ->
        lint()
          .files(
            kts(
                """
                           plugins {
                               id("java")
                           }
                        """
                  .trimIndent() + "\n${sc}\n$tc"
              )
              .indented()
          )
          .issues(JAVA_PLUGIN_LANGUAGE_LEVEL)
          .run()
          .expectClean()
      }
    }
  }

  fun testJavaLanguageLevelNoSourceCompatibilityKts() {
    val targetCompatibility =
      listOf(
        "java.targetCompatibility = JavaVersion.VERSION_1_8",
        "java { targetCompatibility = JavaVersion.VERSION_1_8 }"
      )
    targetCompatibility.forEach { tc ->
      lint()
        .files(
          kts(
              """
                        plugins {
                           id("java")
                        }
                    """
                .trimIndent() + "\n$tc"
            )
            .indented()
        )
        .issues(JAVA_PLUGIN_LANGUAGE_LEVEL)
        .run()
        .expect(
          """
                        build.gradle.kts:2: Warning: no Java sourceCompatibility directive [JavaPluginLanguageLevel]
                           id("java")
                           ~~~~~~~~~~
                        0 errors, 1 warnings
                    """
        )
        .expectFixDiffs(
          """
                        Fix for build.gradle.kts line 2: Insert sourceCompatibility directive for JDK8:
                        @@ -5 +5
                        + java.sourceCompatibility = JavaVersion.VERSION_1_8
                    """
        )
    }
  }

  fun testJavaLanguageLevelNoTargetCompatibilityKts() {
    val sourceCompatibility =
      listOf(
        "java.sourceCompatibility = JavaVersion.VERSION_1_8",
        "java { sourceCompatibility = JavaVersion.VERSION_1_8 }"
      )
    sourceCompatibility.forEach { sc ->
      lint()
        .files(
          kts(
              """
                        plugins {
                           id("java")
                        }
                    """
                .trimIndent() + "\n$sc"
            )
            .indented()
        )
        .issues(JAVA_PLUGIN_LANGUAGE_LEVEL)
        .run()
        .expect(
          """
                        build.gradle.kts:2: Warning: no Java targetCompatibility directive [JavaPluginLanguageLevel]
                           id("java")
                           ~~~~~~~~~~
                        0 errors, 1 warnings
                    """
        )
        .expectFixDiffs(
          """
                        Fix for build.gradle.kts line 2: Insert targetCompatibility directive for JDK8:
                        @@ -5 +5
                        + java.targetCompatibility = JavaVersion.VERSION_1_8
                    """
        )
    }
  }

  fun testJavaLanguageLevelNoDirectivesKts() {
    val plugins =
      listOf(
        "java",
        "java-library",
        "application",
        "org.gradle.java",
        "org.gradle.java-library",
        "org.gradle.application"
      )
    plugins.forEach { plugin ->
      lint()
        .files(
          kts(
              """
                    plugins {
                       id("$plugin")
                    }
                    """
            )
            .indented()
        )
        .issues(JAVA_PLUGIN_LANGUAGE_LEVEL)
        .run()
        .expect(
          """
                    build.gradle.kts:2: Warning: no Java language level directives [JavaPluginLanguageLevel]
                       id("$plugin")
                       ${"~".repeat(plugin.length + 6)}
                    0 errors, 1 warnings
                """
        )
        .expectFixDiffs(
          """
                    Fix for build.gradle.kts line 2: Insert JDK8 language level directives:
                    @@ -4 +4
                    + java {
                    +     sourceCompatibility = JavaVersion.VERSION_1_8
                    +     targetCompatibility = JavaVersion.VERSION_1_8
                    + }
                    """
        )
    }
  }

  fun testJCenterObsolete() {
    lint()
      .files(
        gradle(
            """
                    buildscript {
                        ext {
                            versions = [kotlin: '1.4.20']
                        }
                        repositories {
                            google()
                            jcenter()
                        }
                    }

                    allprojects {
                        repositories {
                            google()
                            jcenter()
                        }
                    }
                """
          )
          .indented()
      )
      .issues(JCENTER_REPOSITORY_OBSOLETE)
      .run()
      .expect(
        """
                build.gradle:7: Warning: JCenter Maven repository is no longer receiving updates: newer library versions may be available elsewhere [JcenterRepositoryObsolete]
                        jcenter()
                        ~~~~~~~~~
                build.gradle:14: Warning: JCenter Maven repository is no longer receiving updates: newer library versions may be available elsewhere [JcenterRepositoryObsolete]
                        jcenter()
                        ~~~~~~~~~
                0 errors, 2 warnings
            """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle line 7: Replace with mavenCentral:
                @@ -7 +7
                -         jcenter()
                +         mavenCentral()
                Fix for build.gradle line 7: Delete this repository declaration:
                @@ -7 +7
                -         jcenter()
                Fix for build.gradle line 14: Replace with mavenCentral:
                @@ -14 +14
                -         jcenter()
                +         mavenCentral()
                Fix for build.gradle line 14: Delete this repository declaration:
                @@ -14 +14
                -         jcenter()
            """
      )
  }

  fun testJCenterObsoleteKts() {
    lint()
      .files(
        kts(
            """
                    buildscript {
                        val versions by extra("1.4.20")
                        repositories {
                            google()
                            jcenter()
                        }
                    }

                    allprojects {
                        repositories {
                            google()
                            jcenter()
                        }
                    }
                """
          )
          .indented()
      )
      .issues(JCENTER_REPOSITORY_OBSOLETE)
      .run()
      .expect(
        """
                build.gradle.kts:5: Warning: JCenter Maven repository is no longer receiving updates: newer library versions may be available elsewhere [JcenterRepositoryObsolete]
                        jcenter()
                        ~~~~~~~~~
                build.gradle.kts:12: Warning: JCenter Maven repository is no longer receiving updates: newer library versions may be available elsewhere [JcenterRepositoryObsolete]
                        jcenter()
                        ~~~~~~~~~
                0 errors, 2 warnings
            """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle.kts line 5: Replace with mavenCentral:
                @@ -5 +5
                -         jcenter()
                +         mavenCentral()
                Fix for build.gradle.kts line 5: Delete this repository declaration:
                @@ -5 +5
                -         jcenter()
                Fix for build.gradle.kts line 12: Replace with mavenCentral:
                @@ -12 +12
                -         jcenter()
                +         mavenCentral()
                Fix for build.gradle.kts line 12: Delete this repository declaration:
                @@ -12 +12
                -         jcenter()
            """
      )
  }

  fun testJCenterObsoleteContent() {
    lint()
      .files(
        gradle(
            """
                    repositories {
                        jcenter {
                            content {
                                // exclude artifacts starting with "my.company"
                                excludeGroupByRegex "^my\\.company.*"
                            }
                        }
                    }
                """
          )
          .indented()
      )
      .issues(JCENTER_REPOSITORY_OBSOLETE)
      .run()
      .expect(
        """
                build.gradle:2: Warning: JCenter Maven repository is no longer receiving updates: newer library versions may be available elsewhere [JcenterRepositoryObsolete]
                    jcenter {
                    ^
                0 errors, 1 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle line 2: Replace with mavenCentral:
                @@ -2 +2
                -     jcenter {
                +     mavenCentral {
                Fix for build.gradle line 2: Delete this repository declaration:
                @@ -2 +2
                -     jcenter {
                -         content {
                -             // exclude artifacts starting with "my.company"
                -             excludeGroupByRegex "^my\\.company.*"
                -         }
                -     }
                """
      )
  }

  fun testJCenterObsoleteContentKts() {
    lint()
      .files(
        kts(
            """
                    repositories {
                        jcenter {
                            content {
                                // exclude artifacts starting with "my.company"
                                excludeGroupByRegex("^my\\.company.*")
                            }
                        }
                    }
                """
          )
          .indented()
      )
      .issues(JCENTER_REPOSITORY_OBSOLETE)
      .run()
      .expect(
        """
                build.gradle.kts:2: Warning: JCenter Maven repository is no longer receiving updates: newer library versions may be available elsewhere [JcenterRepositoryObsolete]
                    jcenter {
                    ^
                0 errors, 1 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle.kts line 2: Replace with mavenCentral:
                @@ -2 +2
                -     jcenter {
                +     mavenCentral {
                Fix for build.gradle.kts line 2: Delete this repository declaration:
                @@ -2 +2
                -     jcenter {
                -         content {
                -             // exclude artifacts starting with "my.company"
                -             excludeGroupByRegex("^my\\.company.*")
                -         }
                -     }
                """
      )
  }

  fun testEmptyKtWithGradleKts() {
    TestLintTask.lint()
      .files(
        kts(
          """
                plugins {
                  id("com.android.application")
                }
                """
        ),
        kotlin("src/some/pkg/Empty.kt", "")
      )
      .allowMissingSdk()
      .issues(UastImplementationDetector.ISSUE) // Any detector that triggers FILE-level UAST visit
      .run()
      .expectClean()
  }

  fun testNonEmptyRootPackageKtWithGradleKts() {
    TestLintTask.lint()
      .files(
        kts(
          """
                plugins {
                  id("com.android.application")
                }
                """
        ),
        kotlin("src/NonEmpty.kt", "val p = 42")
      )
      .allowMissingSdk()
      .issues(UastImplementationDetector.ISSUE) // Any detector that triggers FILE-level UAST visit
      .run()
      .expectClean()
  }

  fun testCoordinateQualifiers() {
    // Regression test for 264799024
    lint()
      .files(
        kts(
            """
                dependencies {
                   val androidx = "com.android.support"
                   val artifact = "multidex"
                   val multiDexVersion = "1.0.0"
                   val unknownVersion = getUnknownVersion() // Can't infer from build file, but we'll find it in model

                   implementation("com.android.support:multidex:1.0.0")
                   implementation("com.android.support:multidex:1.0.0@aar")
                   implementation("com.android.support:multidex:＄multiDexVersion")
                   implementation("com.android.support:multidex:" + multiDexVersion)
                   implementation("com.android.support:multidex:" + multiDexVersion + "@aar")
                   implementation("com.android.support:multidex:＄multiDexVersion@aar")
                   implementation("com.android.support:multidex:＄{multiDexVersion}@aar")
                   implementation(""${'"'}com.android.support:multidex:＄multiDexVersion""${'"'})
                   implementation(""${'"'}com.android.support:multidex:＄multiDexVersion@aar""${'"'})
                   implementation(androidx + ":" + artifact + ":" + multiDexVersion)
                   implementation((androidx + ":" + artifact) + ":" + multiDexVersion)

                   implementation("com.android.support:multidex:＄unknownVersion@aar")
                   implementation("com.android.support:multidex:＄{unknownVersion}@aar")
                   implementation("com.android.support:multidex:" + unknownVersion + "@aar")
                   implementation((androidx + ":" + artifact) + ":" + unknownVersion)
                }
                """
          )
          .indented()
      )
      .issues(DEPENDENCY, PLUS)
      .run()
      .expect(
        """
                build.gradle.kts:7: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation("com.android.support:multidex:1.0.0")
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:8: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation("com.android.support:multidex:1.0.0@aar")
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:9: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation("com.android.support:multidex:＄multiDexVersion")
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:10: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation("com.android.support:multidex:" + multiDexVersion)
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:11: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation("com.android.support:multidex:" + multiDexVersion + "@aar")
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:12: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation("com.android.support:multidex:＄multiDexVersion@aar")
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:13: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation("com.android.support:multidex:＄{multiDexVersion}@aar")
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:14: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation(""${'"'}com.android.support:multidex:＄multiDexVersion""${'"'})
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:15: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation(""${'"'}com.android.support:multidex:＄multiDexVersion@aar""${'"'})
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:16: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation(androidx + ":" + artifact + ":" + multiDexVersion)
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                build.gradle.kts:17: Warning: A newer version of com.android.support:multidex than 1.0.0 is available: 1.0.1 [GradleDependency]
                   implementation((androidx + ":" + artifact) + ":" + multiDexVersion)
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 11 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for build.gradle.kts line 7: Change to 1.0.1:
                @@ -7 +7
                -    implementation("com.android.support:multidex:1.0.0")
                +    implementation("com.android.support:multidex:1.0.1")
                Fix for build.gradle.kts line 8: Change to 1.0.1:
                @@ -8 +8
                -    implementation("com.android.support:multidex:1.0.0@aar")
                +    implementation("com.android.support:multidex:1.0.1@aar")
                """
      )
  }

  fun testCachedFilter() {
    // Regression test for b/282127516
    lint()
      .files(
        gradle(
            """
          dependencies {
            implementation 'com.example.cached:library:1.0-alpha01'
            implementation 'com.example.cached:library:1.0-beta01'
            implementation 'com.example.cached:library:1.0'
            implementation 'com.example.cached:library:1.1-alpha01'
            implementation 'com.example.cached:library:1.1-beta01'
          }
        """
          )
          .indented()
      )
      .issues(DEPENDENCY)
      .run()
      .expect(
        """
        build.gradle:2: Warning: A newer version of com.example.cached:library than 1.0-alpha01 is available: 1.0 [GradleDependency]
          implementation 'com.example.cached:library:1.0-alpha01'
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle:3: Warning: A newer version of com.example.cached:library than 1.0-beta01 is available: 1.0 [GradleDependency]
          implementation 'com.example.cached:library:1.0-beta01'
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        build.gradle:5: Warning: A newer version of com.example.cached:library than 1.1-alpha01 is available: 1.1-beta01 [GradleDependency]
          implementation 'com.example.cached:library:1.1-alpha01'
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 3 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for build.gradle line 2: Change to 1.0:
        @@ -2 +2
        -   implementation 'com.example.cached:library:1.0-alpha01'
        +   implementation 'com.example.cached:library:1.0'
        Fix for build.gradle line 3: Change to 1.0:
        @@ -3 +3
        -   implementation 'com.example.cached:library:1.0-beta01'
        @@ -5 +4
        +   implementation 'com.example.cached:library:1.0'
        Fix for build.gradle line 5: Change to 1.1-beta01:
        @@ -5 +5
        -   implementation 'com.example.cached:library:1.1-alpha01'
        @@ -7 +6
        +   implementation 'com.example.cached:library:1.1-beta01'
        """
      )
  }

  fun testCachedFilterCommonsIo() {
    // regression test for b/218605730
    lint()
      .files(
        gradle(
          """
          dependencies {
            implementation 'commons-io:commons-io:2.12.0'
          }
        """
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expectClean()
  }

  fun testCachedFilterGuava() {
    // regression test for b/315310898
    lint()
      .files(
        gradle(
            """
            dependencies {
              implementation 'com.google.guava:spurious:16.0'
              implementation 'com.google.guava:spurious:16.0-rc01'
              implementation 'com.google.guava:spurious:16.0-jre'
              implementation 'com.google.guava:spurious:16.0-android'
            }
          """
          )
          .indented()
      )
      .issues(DEPENDENCY)
      .run()
      .expect(
        """
          build.gradle:2: Warning: A newer version of com.google.guava:spurious than 16.0 is available: 18.0-android [GradleDependency]
            implementation 'com.google.guava:spurious:16.0'
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:3: Warning: A newer version of com.google.guava:spurious than 16.0-rc01 is available: 18.0-android [GradleDependency]
            implementation 'com.google.guava:spurious:16.0-rc01'
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:4: Warning: A newer version of com.google.guava:spurious than 16.0-jre is available: 18.0-jre [GradleDependency]
            implementation 'com.google.guava:spurious:16.0-jre'
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          build.gradle:5: Warning: A newer version of com.google.guava:spurious than 16.0-android is available: 18.0-android [GradleDependency]
            implementation 'com.google.guava:spurious:16.0-android'
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 4 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for build.gradle line 2: Change to 18.0-android:
          @@ -2 +2
          -   implementation 'com.google.guava:spurious:16.0'
          +   implementation 'com.google.guava:spurious:18.0-android'
          Fix for build.gradle line 3: Change to 18.0-android:
          @@ -3 +3
          -   implementation 'com.google.guava:spurious:16.0-rc01'
          +   implementation 'com.google.guava:spurious:18.0-android'
          Fix for build.gradle line 4: Change to 18.0-jre:
          @@ -4 +4
          -   implementation 'com.google.guava:spurious:16.0-jre'
          +   implementation 'com.google.guava:spurious:18.0-jre'
          Fix for build.gradle line 5: Change to 18.0-android:
          @@ -5 +5
          -   implementation 'com.google.guava:spurious:16.0-android'
          +   implementation 'com.google.guava:spurious:18.0-android'
        """
      )
  }

  fun testExpiredTargetSdkInManifest() {
    val calendar = Calendar.getInstance()
    ManifestDetector.calendar = calendar
    calendar.set(Calendar.YEAR, MINIMUM_TARGET_SDK_VERSION_YEAR + 1)

    val expected =
      """
      AndroidManifest.xml:6: Error: Google Play requires that apps target API level 33 or higher. [ExpiredTargetSdkVersion]
          <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="17" />
                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      1 errors, 0 warnings
       """
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.bytecode"
                android:versionCode="1"
                android:versionName="1.0" >

                <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="17" />

                <application
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name" >
                    <activity
                        android:name=".BytecodeTestsActivity"
                        android:label="@string/app_name" >
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />

                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """
          )
          .indented()
      )
      .issues(EXPIRED_TARGET_SDK_VERSION)
      .run()
      .expect(expected)
  }

  fun testNotTheNewestTargetSdk() {
    val expectedTarget = createClient().highestKnownApiLevel.toString()
    if (createClient().highestKnownApiLevel <= MINIMUM_TARGET_SDK_VERSION) {
      // test makes sense only if highestKnownApiLevel > MINIMUM_TARGET_SDK_VERSION, otherwise we
      // will always have [EXPIRED_TARGET_SDK_VERSION] first
      return
    }
    val expected =
      """
      AndroidManifest.xml:6: Warning: Not targeting the latest versions of Android; compatibility modes apply. Consider testing and updating this version. Consult the android.os.Build.VERSION_CODES javadoc for details. [OldTargetApi]
          <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="$MINIMUM_TARGET_SDK_VERSION" />
                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      0 errors, 1 warnings
      """
    lint()
      .sdkHome(TestUtils.getSdk().toFile())
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.bytecode"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="$MINIMUM_TARGET_SDK_VERSION" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:name=".BytecodeTestsActivity"
                            android:label="@string/app_name" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .testModes(TestMode.DEFAULT)
      .issues(TARGET_NEWER)
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
        Fix for AndroidManifest.xml line 6: Update targetSdkVersion to $expectedTarget:
        @@ -9 +9
        -         android:targetSdkVersion="$MINIMUM_TARGET_SDK_VERSION" />
        +         android:targetSdkVersion="$expectedTarget" />
        """
      )
  }

  fun testNotTheNewestTargetSdkForWear() {
    if (createClient().highestKnownApiLevel <= MINIMUM_TARGET_SDK_VERSION) {
      // test makes sense only if highestKnownApiLevel > MINIMUM_TARGET_SDK_VERSION, otherwise we
      // will always have [EXPIRED_TARGET_SDK_VERSION] first
      return
    }
    lint()
      .sdkHome(TestUtils.getSdk().toFile())
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.bytecode"
                android:versionCode="1"
                android:versionName="1.0" >
                <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="$MINIMUM_TARGET_SDK_VERSION" />
                <uses-feature android:name="android.hardware.type.watch" />
            </manifest>
            """
          )
          .indented()
      )
      .issues(TARGET_NEWER)
      .run()
      .expectClean()
  }

  fun testNewerCompileSdkVersionAvailable() {
    lint()
      .files(
        gradle(
          "" +
            "apply plugin: 'com.android.application'\n" +
            "\n" +
            "android {\n" +
            "    compileSdkVersion 28\n" +
            "    compileSdkVersion 1000 // OK\n" +
            "    compileSdkPreview = \"android-S\" // OK\n" +
            "}\n"
        )
      )
      .issues(DEPENDENCY)
      .run()
      .expect(
        """
        build.gradle:4: Warning: A newer version of compileSdkVersion than 28 is available: $HIGHEST_KNOWN_STABLE_API [GradleDependency]
            compileSdkVersion 28
            ~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for build.gradle line 4: Set compileSdkVersion to $HIGHEST_KNOWN_STABLE_API:
        @@ -4 +4
        -     compileSdkVersion 28
        +     compileSdkVersion $HIGHEST_KNOWN_STABLE_API
        """
      )
  }

  // -------------------------------------------------------------------------------------------
  // Test infrastructure below here
  // -------------------------------------------------------------------------------------------

  override fun getDetector(): Detector {
    return GroovyGradleDetector()
  }

  class GroovyGradleDetector : GradleDetector() {
    override val gradleUserHome: File
      get() = GradleDetectorTest.gradleUserHome ?: super.gradleUserHome
  }

  companion object {
    private var sdkRootDir: File? = null
    private var fullSdkDir: File? = null
    private var leanSdkDir: File? = null
    private var gradleUserHome: File? = null

    /** Creates a mock SDK installation structure, containing a fixed set of dependencies. */
    private val mockSupportLibraryInstallation: File?
      get() {
        initializeMockSdkDirs()
        return fullSdkDir
      }

    /** Like [.getMockSupportLibraryInstallation] but without local support library. */
    private val sdkDirWithoutSupportLib: File?
      get() {
        initializeMockSdkDirs()
        return leanSdkDir
      }

    @JvmStatic
    fun createRelativePaths(sdkDir: File, paths: Array<String>) {
      for (path in paths) {
        val file = File(sdkDir, path.platformPath())
        val parent = file.parentFile
        if (!parent.exists()) {
          val ok = parent.mkdirs()
          TestCase.assertTrue(parent.path, ok)
        }
        try {
          val created = file.createNewFile()
          TestCase.assertTrue(file.path, created)
        } catch (e: IOException) {
          TestCase.fail(e.toString())
        }
      }
    }

    private fun initializeMockSdkDirs() {
      if (sdkRootDir == null) {
        // Make fake SDK "installation" such that we can predict the set
        // of Maven repositories discovered by this test
        sdkRootDir = TestUtils.createTempDirDeletedOnExit().toFile()

        fullSdkDir = File(sdkRootDir, "full")
        createRelativePaths(
          fullSdkDir!!,
          arrayOf(
            // build tools
            "build-tools/23.0.0/aapt",
            "build-tools/23.0.3/aapt"
          )
        )

        leanSdkDir = File(sdkRootDir, "lean")
        createRelativePaths(
          leanSdkDir!!,
          arrayOf(
            // build tools
            "build-tools/23.0.0/aapt",
            "build-tools/23.0.3/aapt"
          )
        )

        // Test-isolated version of ~/.gradle/
        gradleUserHome = File(sdkRootDir, "gradle-user-home")
        createRelativePaths(
          gradleUserHome!!,
          arrayOf(
            "caches/modules-2/files-2.1/com.android.tools.build/gradle/2.2.0/sample",
            "caches/modules-2/files-2.1/com.android.tools.build/gradle/2.2.3/sample",
            "caches/modules-2/files-2.1/com.android.tools.build/gradle/2.3.0/sample",
            "caches/modules-2/files-2.1/com.android.tools.build/gradle/2.3.1/sample",
            "caches/modules-2/files-2.1/com.android.tools.build/gradle/2.4.0-alpha3/sample",
            "caches/modules-2/files-2.1/com.android.tools.build/gradle/2.4.0-alpha5/sample",
            "caches/modules-2/files-2.1/com.android.tools.build/gradle/2.4.0-alpha6/sample",
            "caches/modules-2/files-2.1/com.example.cached/library/1.0-alpha02/sample",
            "caches/modules-2/files-2.1/com.example.cached/library/1.0-beta01/sample",
            "caches/modules-2/files-2.1/com.example.cached/library/1.0/sample",
            "caches/modules-2/files-2.1/com.example.cached/library/1.1-alpha02/sample",
            "caches/modules-2/files-2.1/com.example.cached/library/1.1-beta01/sample",
            "caches/modules-2/files-2.1/com.example.cached/library/1.1-SNAPSHOT/sample",
            "caches/modules-2/files-2.1/com.google.guava/guava/17.0/sample",
            "caches/modules-2/files-2.1/com.google.guava/spurious/18.0-android/sample",
            "caches/modules-2/files-2.1/com.google.guava/spurious/18.0-jre/sample",
            "caches/modules-2/files-2.1/commons-io/commons-io/20030203.000550/sample",
            "caches/modules-2/files-2.1/org.apache.httpcomponents/httpcomponents-core/4.1/sample",
            "caches/modules-2/files-2.1/org.apache.httpcomponents/httpcomponents-core/4.2.1/sample",
            "caches/modules-2/files-2.1/org.apache.httpcomponents/httpcomponents-core/4.2.5/sample",
            "caches/modules-2/files-2.1/org.apache.httpcomponents/httpcomponents-core/4.4/sample",

            // SDK distributed via Maven
            "caches/modules-2/files-2.1/com.android.support/recyclerview-v7/26.0.0/sample",
            "caches/modules-2/files-2.1/com.google.firebase/firebase-messaging/11.0.0/sample",
            "caches/modules-2/files-2.1/androidx.slidingpanelayout/slidingpanelayout/1.2.0/sample",
            "caches/modules-2/files-2.1/androidx.compose.foundation/foundation/1.2.1/sample"
          )
        )
      }
    }

    fun initializeNetworkMocksAndCaches(task: TestLintTask): TestLintTask {
      // Set up exactly the expected maven.google.com network output to ensure stable
      // version suggestions in the tests
      task.networkData(
        "https://maven.google.com/master-index.xml",
        // language=xml
        """
        <?xml version='1.0' encoding='UTF-8'?>
        <metadata>
          <com.google.firebase.crashlytics/>
          <com.android.application/>
          <com.android.support/>
          <com.android.support.test/>
          <com.android.tools/>
          <com.android.tools.build/>
          <com.google.android.gms/>
          <com.google.android.support/>
          <androidx.core/>
          <androidx.slidingpanelayout/>
          <androidx.compose/>
        </metadata>
        """
          .trimIndent()
      )
      task.networkData(
        "https://maven.google.com/com/android/application/group-index.xml",
        // language=xml
        "" +
          "<?xml version='1.0' encoding='UTF-8'?>\n" +
          "<com.android.application>\n" +
          "  <com.android.application.gradle.plugin versions=\"7.4.0-alpha05,7.4.0-alpha06,7.4.0-alpha07,7.4.0-alpha08," +
          "7.4.0-alpha09,7.4.0-alpha10,7.4.0-beta01,7.4.0-beta02,7.4.0-beta03,7.4.0-beta04,7.4.0-beta05,7.4.0-rc01,7.4.0-rc02," +
          "7.4.0-rc03,7.4.0,7.4.1,7.4.2,8.0.0-alpha01,8.0.0-alpha02,8.0.0-alpha03,8.0.0-alpha04,8.0.0-alpha05,8.0.0-alpha06," +
          "8.0.0-alpha07,8.0.0-alpha08,8.0.0-alpha09,8.0.0-alpha10,8.0.0-alpha11,8.0.0-beta01,8.0.0-beta02,8.0.0-beta03,8.0.0-beta04," +
          "8.0.0-beta05,8.0.0-rc01,8.0.0,8.0.1,8.0.2,8.1.0-alpha01,8.1.0-alpha02,8.1.0-alpha03,8.1.0-alpha04,8.1.0-alpha05," +
          "8.1.0-alpha06,8.1.0-alpha07,8.1.0-alpha08,8.1.0-alpha09,8.1.0-alpha10,8.1.0-alpha11,8.1.0-beta01,8.1.0-beta02," +
          "8.1.0-beta03,8.1.0-beta04,8.1.0-beta05,8.1.0-rc01,8.2.0-alpha01,8.2.0-alpha02,8.2.0-alpha03,8.2.0-alpha04,8.2.0-alpha05," +
          "8.2.0-alpha06,8.2.0-alpha07,8.2.0-alpha08,8.2.0-alpha09,8.2.0-alpha10,8.2.0-alpha11,8.2.0-alpha12" +
          "\"/>\n" +
          "</com.android.application>"
      )
      task.networkData(
        "https://maven.google.com/com/google/firebase/crashlytics/group-index.xml",
        // language=xml
        "" +
          "<?xml version='1.0' encoding='UTF-8'?>\n" +
          "<com.google.firebase.crashlytics>\n" +
          "  <com.google.firebase.crashlytics.gradle.plugin versions=\"2.8.1,2.9.0,2.9.1,2.9.2,2.9.3,2.9.4,2.9.5,2.9.6,2.9.7\"/>\n" +
          "</com.google.firebase.crashlytics>"
      )
      task.networkData(
        "https://maven.google.com/com/android/tools/build/group-index.xml",
        // language=xml
        "" +
          "<?xml version='1.0' encoding='UTF-8'?>\n" +
          "<com.android.tools.build>\n" +
          "  <gradle versions=\"3.0.0-alpha1,3.0.0-alpha2,3.0.0-alpha3,3.0.0-alpha4,3.0.0-alpha5,3.0.0-alpha6,3.0.0-alpha7,3.0.0-alpha8,3.0.0-alpha9,3.0.0-beta1,3.0.0-beta2,3.0.0-beta3,3.0.0-beta4,3.0.0-beta5,3.0.0-beta6,3.0.0-beta7,3.0.0-rc1,3.0.0-rc2,3.0.0,3.0.1," +
          "3.1.0-alpha01,3.1.0-alpha02,3.1.0-alpha03,3.1.0-alpha04,3.1.0-alpha05,3.1.0-alpha06,3.1.0-alpha07,3.1.0-alpha08,3.1.0-alpha09,3.1.0-beta1,3.1.0-beta2,3.1.0-beta3,3.1.0-beta4,3.1.0-rc1,3.1.0," +
          "3.2.0-alpha01,3.2.0-alpha02,3.2.0-alpha03,3.2.0-alpha04,3.2.0-alpha05,3.2.0-alpha06,3.2.0-alpha07,3.2.0-alpha08,3.2.0-alpha09,3.2.0-alpha10,3.2.0-alpha11,3.2.0-alpha12,3.2.0-alpha13,3.2.0-alpha14,3.2.0-alpha15,3.2.0-alpha16,3.2.0-alpha17,3.2.0-alpha18,3.2.0-beta01,3.2.0-beta02,3.2.0-beta03,3.2.0-beta04,3.2.0-beta05,3.2.0-rc01,3.2.0-rc02,3.2.0-rc03,3.2.0,3.2.1," +
          "3.3.0-alpha01,3.3.0-alpha02,3.3.0-alpha03,3.3.0-alpha04,3.3.0-alpha05,3.3.0-alpha06,3.3.0-alpha07,3.3.0-alpha08,3.3.0-alpha09,3.3.0-alpha10,3.3.0-alpha11,3.3.0-alpha12,3.3.0-alpha13,3.3.0-beta01,3.3.0-beta02,3.3.0-beta03,3.3.0-beta04,3.3.0-rc01,3.3.0-rc02,3.3.0-rc03,3.3.0,3.3.1,3.3.2," +
          "3.4.0-alpha01,3.4.0-alpha02,3.4.0-alpha03,3.4.0-alpha04,3.4.0-alpha05,3.4.0-alpha06,3.4.0-alpha07,3.4.0-alpha08,3.4.0-alpha09,3.4.0-alpha10,3.4.0-beta01,3.4.0-beta02,3.4.0-beta03,3.4.0-beta04,3.4.0-beta05,3.4.0-rc01,3.4.0-rc02,3.4.0-rc03,3.4.0,3.4.1," +
          "3.5.0-alpha01,3.5.0-alpha02,3.5.0-alpha03,3.5.0-alpha04,3.5.0-alpha05,3.5.0-alpha06,3.5.0-alpha07,3.5.0-alpha08,3.5.0-alpha09,3.5.0-alpha10,3.5.0," +
          "" +
          "3.6.0-alpha01\"/>\n" +
          "</com.android.tools.build>"
      )
      task.networkData(
        "https://maven.google.com/com/android/support/group-index.xml",
        // language=xml
        """
        <?xml version='1.0' encoding='UTF-8'?>
        <com.android.support>
          <support-compat versions="19.1.0,25.3.1,26.0.0-beta1"/>
          <appcompat-v7 versions="19.1.0,19.1.0,25.3.1,26.0.0-beta1"/>
          <multidex versions="1.0.1,1.0.1"/>
          <support-v4 versions="19.1.0,21.0.2,25.3.1,26.0.0-beta1"/>
        </com.android.support>
        """
          .trimIndent()
      )
      task.networkData(
        "https://maven.google.com/com/google/android/support/group-index.xml",
        // language=xml
        """
        <?xml version='1.0' encoding='UTF-8'?>
        <com.google.android.support>
          <wearable versions="1.3.0,26.0.0-alpha1"/>
        </com.google.android.support>
        """
          .trimIndent()
      )
      task.networkData(
        "https://maven.google.com/com/google/android/gms/group-index.xml",
        // language=xml
        """
        <?xml version='1.0' encoding='UTF-8'?>
        <com.google.android.gms>
          <play-services-wearable versions="6.1.71"/>
          <play-services versions="11.1.71"/>
          <play-services-maps versions="18.1.0,18.2.0,18.3.0"/>
        </com.google.android.gms>
        """
          .trimIndent()
      )
      task.networkData(
        "https://maven.google.com/com/android/support/constraint/group-index.xml",
        // language=xml
        """
        <?xml version='1.0' encoding='UTF-8'?>
        <com.android.support.constraint>
          <constraint-layout versions="1.0.0,1.0.2"/>
        </com.android.support.constraint>
        """
          .trimIndent()
      )
      task.networkData(
        "https://maven.google.com/com/android/support/test/group-index.xml",
        // language=xml
        """
        <?xml version='1.0' encoding='UTF-8'?>
        <com.android.support.test>
          <runner versions="0.3,0.5"/>
        </com.android.support.test>
        """
          .trimIndent()
      )
      task.networkData(
        "https://maven.google.com/androidx/core/group-index.xml",
        // language=xml
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <androidx.core>
          <core-ktx versions="1.2.0"/>
          <core versions="1.2.0"/>
        </androidx.core>
        """
          .trimIndent()
      )
      task.networkData(
        "https://maven.google.com/androidx/slidingpanelayout/group-index.xml",
        // language=xml
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <androidx.slidingpanelayout>
          <slidingpanelayout versions="1.1.0,1.2.0"/>
        </androidx.slidingpanelayout>
        """
          .trimIndent()
      )
      task.networkData(
        "https://maven.google.com/androidx/compose/group-index.xml",
        // language=xml
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <androidx.compose>
          <compose-bom versions="2022.10.00,2022.11.00,2022.12.00,2023.01.00"/>
        </androidx.compose>
        """
          .trimIndent()
      )
      task.networkData(
        "https://maven.google.com/androidx/compose/foundation/group-index.xml",
        // language=xml
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <androidx.compose.foundation>
          <foundation versions="1.1.0,1.2.0"/>
        </androidx.compose.foundation>
        """
          .trimIndent()
      )

      // Similarly set up the expected SDK Index network output from dl.google.com to
      // ensure stable SDK library suggestions in the tests
      val index =
        Index.newBuilder()
          .addSdks(
            Sdk.newBuilder()
              .setIndexUrl("http://index.example.url/")
              .addLibraries(
                Library.newBuilder()
                  .setLibraryId(
                    LibraryIdentifier.newBuilder()
                      .setMavenId(
                        LibraryIdentifier.MavenIdentifier.newBuilder()
                          .setGroupId("log4j")
                          .setArtifactId("log4j")
                          .build()
                      )
                  )
                  // Ok, latest, no issues
                  .addVersions(
                    LibraryVersion.newBuilder().setVersionString("1.2.18").setIsLatestVersion(true)
                  )
                  // Ok, latest, no issues
                  .addVersions(
                    LibraryVersion.newBuilder().setVersionString("1.2.17").setIsLatestVersion(false)
                  )
                  // Critical NON_BLOCKING
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("1.2.16")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder())
                          .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                      )
                  )
                  // Outdated NON_BLOCKING
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("1.2.15")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
                          .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                      )
                  )
                  // Policy (deprecated)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("1.2.14")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(LibraryVersionLabels.PolicyIssuesInfo.newBuilder())
                      )
                  )
                  // Critical BLOCKING
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("1.2.13")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder())
                          .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                      )
                  )
                  // Outdated BLOCKING
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("1.2.12")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
                          .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                      )
                  )
              )
          )
          .addSdks(
            Sdk.newBuilder()
              .setIndexUrl("http://another.example.url/")
              .addLibraries(
                Library.newBuilder()
                  .setLibraryId(
                    LibraryIdentifier.newBuilder()
                      .setMavenId(
                        LibraryIdentifier.MavenIdentifier.newBuilder()
                          .setGroupId("com.example.ads.third.party")
                          .setArtifactId("example")
                          .build()
                      )
                  )
                  // Ok, latest
                  .addVersions(
                    LibraryVersion.newBuilder().setVersionString("8.0.0").setIsLatestVersion(true)
                  )
                  // Ok
                  .addVersions(
                    LibraryVersion.newBuilder().setVersionString("7.2.2").setIsLatestVersion(false)
                  )
                  // Ok
                  .addVersions(
                    LibraryVersion.newBuilder().setVersionString("7.2.1").setIsLatestVersion(false)
                  )
                  // Outdated & non compliant (user data) & Critical
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.2.0")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setCriticalIssueInfo(LibraryVersionLabels.CriticalIssueInfo.newBuilder())
                          .setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                              )
                          )
                      )
                  )
                  // Non-compliant (Ads, non-blocking)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.0")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_ADS
                              )
                          )
                          .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                      )
                  )
                  // Non-compliant (Device and Network Abuse, blocking)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.1")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                  .SDK_POLICY_DEVICE_AND_NETWORK_ABUSE
                              )
                          )
                          .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                      )
                  )
                  // Non-compliant (Deceptive Behavior, no severity)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.2")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                  .SDK_POLICY_DECEPTIVE_BEHAVIOR
                              )
                          )
                      )
                  )
                  // Non-compliant (User Data, non-blocking)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.3")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                              )
                          )
                          .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                      )
                  )
                  // Non-compliant (Permissions, blocking)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.4")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                  .SDK_POLICY_PERMISSIONS
                              )
                          )
                          .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                      )
                  )
                  // Non-compliant (Mobile Unwanted Software, no severity)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.5")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                  .SDK_POLICY_MOBILE_UNWANTED_SOFTWARE
                              )
                          )
                      )
                  )
                  // Non-compliant (Malware, non-blocking)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.6")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                              )
                          )
                          .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                      )
                  )
                  // Non-compliant (Multiple violations, non-blocking)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.7")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                              )
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                              )
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                  .SDK_POLICY_PERMISSIONS
                              )
                          )
                          .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                      )
                  )
                  // Non-compliant (Multiple violations, blocking)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.8")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
                              )
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                              )
                          )
                          .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                      )
                  )
                  // Non-compliant (Multiple violations, no severity)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("7.1.9")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                  .SDK_POLICY_PERMISSIONS
                              )
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
                              )
                          )
                      )
                  )
              )
          )
          .addSdks(
            Sdk.newBuilder()
              .setIndexUrl("http://sdk.google.com/")
              .addLibraries(
                Library.newBuilder()
                  .setLibraryId(
                    LibraryIdentifier.newBuilder()
                      .setMavenId(
                        LibraryIdentifier.MavenIdentifier.newBuilder()
                          .setGroupId("com.google.android.gms")
                          .setArtifactId("play-services-maps")
                          .build()
                      )
                  )
                  // Ok, latest, no issues
                  .addVersions(
                    LibraryVersion.newBuilder().setVersionString("18.3.0").setIsLatestVersion(true)
                  )
                  // Ok, latest, no issues
                  .addVersions(
                    LibraryVersion.newBuilder().setVersionString("18.2.0").setIsLatestVersion(false)
                  )
                  // Outdated version (Warning)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("18.1.0")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setOutdatedIssueInfo(LibraryVersionLabels.OutdatedIssueInfo.newBuilder())
                          .setSeverity(LibraryVersionLabels.Severity.NON_BLOCKING_SEVERITY)
                      )
                  )
              )
              .addLibraries(
                Library.newBuilder()
                  .setLibraryId(
                    LibraryIdentifier.newBuilder()
                      .setMavenId(
                        LibraryIdentifier.MavenIdentifier.newBuilder()
                          .setGroupId("androidx.slidingpanelayout")
                          .setArtifactId("slidingpanelayout")
                          .build()
                      )
                  )
                  // Ok, latest, no issues
                  .addVersions(
                    LibraryVersion.newBuilder().setVersionString("1.2.0").setIsLatestVersion(true)
                  )
                  // Policy issue (error)
                  .addVersions(
                    LibraryVersion.newBuilder()
                      .setVersionString("1.1.0")
                      .setIsLatestVersion(false)
                      .setVersionLabels(
                        LibraryVersionLabels.newBuilder()
                          .setPolicyIssuesInfo(
                            LibraryVersionLabels.PolicyIssuesInfo.newBuilder()
                              .addViolatedSdkPolicies(
                                LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy
                                  .SDK_POLICY_PERMISSIONS
                              )
                          )
                          .setSeverity(LibraryVersionLabels.Severity.BLOCKING_SEVERITY)
                      )
                  )
              )
          )
          .build()
      val bos = ByteArrayOutputStream()
      val gzip = GZIPOutputStream(bos)
      gzip.write(index.toByteArray())
      gzip.close()
      task.networkData(
        "${GooglePlaySdkIndex.GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_URL}${GooglePlaySdkIndex.GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_FILE}",
        bos.toByteArray()
      )
      bos.close()

      // Also ensure we don't have a stale cache on disk.
      val cacheDir =
        com.android.tools.lint.checks.infrastructure
          .TestLintClient()
          .getCacheDir(MAVEN_GOOGLE_CACHE_DIR_KEY, true)
      if (cacheDir != null && cacheDir.isDirectory) {
        try {
          FileUtils.deleteDirectoryContents(cacheDir)
        } catch (e: IOException) {
          fail(e.message)
        }
      }

      task.clientFactory({
        object : com.android.tools.lint.checks.infrastructure.TestLintClient() {
          override fun getSdkHome(): File? {
            if (task.sdkHome != null) {
              return task.sdkHome
            }
            return mockSupportLibraryInstallation
          }

          override fun getHighestKnownVersion(
            dependency: Dependency,
            filter: Predicate<Version>?
          ): Version? {
            // Hardcoded for unit test to ensure stable data
            return if (
              "com.android.support.constraint" == dependency.group &&
                "constraint-layout" == dependency.name
            ) {
              if (dependency.version?.lowerBound?.isPreview != false) {
                Version.parse("1.0.3-alpha8")
              } else {
                Version.parse("1.0.2")
              }
            } else null
          }
        }
      })

      val cacheDir2 =
        com.android.tools.lint.checks.infrastructure
          .TestLintClient()
          .getCacheDir(GooglePlaySdkIndex.GOOGLE_PLAY_SDK_INDEX_KEY, true)
      if (cacheDir2 != null && cacheDir2.isDirectory) {
        try {
          FileUtils.deleteDirectoryContents(cacheDir2)
        } catch (e: IOException) {
          fail(e.message)
        }
      }

      return task
    }

    private val IMPLEMENTATION =
      Implementation(GroovyGradleDetector::class.java, Scope.GRADLE_SCOPE)

    init {
      LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
      for (issue in TestIssueRegistry().issues) {
        if (issue.implementation.detectorClass == GradleDetector::class.java) {
          if (issue.implementation.scope.size > 1) {
            issue.implementation =
              Implementation(
                GroovyGradleDetector::class.java,
                issue.implementation.scope,
                *issue.implementation.analysisScopes
              )
          } else {
            issue.implementation = IMPLEMENTATION
          }
        }
      }
    }
  }
}
