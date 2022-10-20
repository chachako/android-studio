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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

/** Checks that Data Binding works in KMP projects. */
class DataBindingKmpTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        subProject(":app") {
            plugins.addAll(listOf(PluginType.ANDROID_LIB, PluginType.KOTLIN_MPP, PluginType.KAPT))
            android {
                namespace = "com.example.app"
                defaultCompileSdk()
                buildFeatures {
                    dataBinding = true
                }
            }
            appendToBuildFile {
                """
                kotlin {
                    android()
                }
                """.trimIndent()
            }
            dependencies {
                implementation(project(":lib"))
            }
            addFile(
                "src/main/java/com/example/app/MainActivity.java",
                """
                package com.example.app;

                import android.os.Bundle;
                import android.app.Activity;
                import androidx.databinding.DataBindingUtil;
                import com.example.lib.LibData;
                import com.example.app.databinding.ActivityMainBinding;

                public class MainActivity extends Activity {

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);

                        ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
                        binding.setLibData(new LibData());
                    }
                }
                """.trimIndent()
            )
            addFile(
                "src/main/res/layout/activity_main.xml",
                """
                <?xml version="1.0" encoding="utf-8"?>
                <layout xmlns:android="http://schemas.android.com/apk/res/android">
                    <data>
                        <variable name="libData" type="com.example.lib.LibData" />
                    </data>
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:dataFromLib="@{libData}" />
                </layout>
                """.trimIndent()
            )
        }
        subProject(":lib") {
            plugins.addAll(listOf(PluginType.ANDROID_LIB, PluginType.KOTLIN_MPP, PluginType.KAPT))
            android {
                namespace = "com.example.lib"
                defaultCompileSdk()
                buildFeatures {
                    dataBinding = true
                }
            }
            appendToBuildFile {
                """
                kotlin {
                    android()
                }
                """.trimIndent()
            }
            addFile(
                "src/main/java/com/example/lib/LibData.java",
                """
                package com.example.lib;

                import android.widget.TextView;
                import androidx.databinding.BindingAdapter;

                public class LibData {
                    public final String greetings = "Hello from lib!";

                    @BindingAdapter("android:dataFromLib")
                    public static void bindLibData(TextView textView, LibData libData) {
                        textView.setText(libData.greetings);
                    }
                }
                """.trimIndent()
            )
        }
    }
        .withKotlinGradlePlugin(true)
        .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
        .create()

    // Regression test for bug 238964168
    @Test
    fun testCompilation() {
        project.executor().run("clean", "compileDebugJavaWithJavac")
    }
}
