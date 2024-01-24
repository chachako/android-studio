/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.baselineprofiles

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class D8StartupProfileDexOptimizationTest {

    private val app = HelloWorldApp.forPluginWithNamespace(
        "com.android.application",
        "com.example.app"
    ).also {
        it.addFile(
        "src/main/java/com/example/app/Foo.java",
        """
            package com.example.app;
            public class Foo {
                public void foo() {
                    System.out.println("foo !");
                }
            }
            """.trimIndent()
        )
        it.addFile(
            "src/main/java/com/example/app/Bar.java",
            """
            package com.example.app;
            public class Bar {
                public void bar() {
                    System.out.println("bar !");
                }
            }
            """.trimIndent()
        )

        val rewrittenFile = it.getFile("src/main/java/com/example/app/HelloWorld.java")
            .rewriteContent(
            """
            package com.example.app;

            import android.app.Activity;
            import android.os.Bundle;

            public class HelloWorld extends Activity {
                /** Called when the activity is first created. */
                @Override
                public void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    setContentView(R.layout.main);
                    // onCreate
                    new Foo().foo();
                    new Bar().bar();
                }
            }
            """.trimIndent())
        it.removeFile("src/main/java/com/example/app/HelloWorld.java")
        it.addFile(rewrittenFile)
        it.appendToBuild(
            """
                android.defaultConfig.minSdkVersion = 26
                androidComponents {
                    onVariants(selector().withName("release"), { variant ->
                        variant.experimentalProperties.put(
                            "${ModulePropertyKey.BooleanWithDefault.R8_DEX_STARTUP_OPTIMIZATION.key}", false
                        )
                        variant.experimentalProperties.put(
                            "${ModulePropertyKey.BooleanWithDefault.D8_DEX_STARTUP_OPTIMIZATION.key}", true
                        )
                    })
                }
            """.trimIndent())
    }

    @JvmField
    @Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .build()
            ).create()

    @Test
    fun testStartupProfile() {
        // If feature turned on but no startup baseline profile is provided, log should be present
        val buildResult =
            project.executor().withLoggingLevel(LoggingLevel.INFO).run("assembleRelease")
        ScannerSubject.assertThat(buildResult.stdout).contains(
            "Dex optimization based on startup profile is enabled, but there are no input " +
            "baseline profiles found in the baselineProfiles sources."
        )

        FileUtils.createFile(
            project.getSubproject("app")
                .file("src/main/baselineProfiles/startup-prof.txt"),
            """
                Lcom/example/app/Foo;->foo()V
            """.trimIndent()
        )
        project.execute("clean", "assembleRelease")
        var apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE)
        Truth.assertThat(apk.allDexes).hasSize(2)
        Truth.assertThat(apk.mainDexFile.get().classes).hasSize(1)

        TestFileUtils.searchAndReplace(project.getSubproject("app").buildFile,
            "\"${ModulePropertyKey.BooleanWithDefault.D8_DEX_STARTUP_OPTIMIZATION.key}\", true",
            "\"${ModulePropertyKey.BooleanWithDefault.D8_DEX_STARTUP_OPTIMIZATION.key}\", false"
        )

        project.execute("clean", "assembleRelease")
        apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.RELEASE)
        Truth.assertThat(apk.allDexes).hasSize(1)
        Truth.assertThat(apk.mainDexFile.get().classes).hasSize(7)
    }
}
