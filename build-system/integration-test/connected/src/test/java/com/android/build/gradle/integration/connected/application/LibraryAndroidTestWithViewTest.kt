
package com.android.build.gradle.integration.connected.application

import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.DEFAULT_MIN_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_CONSTRAINT_LAYOUT_VERSION
import com.android.build.gradle.integration.common.fixture.app.LayoutFileBuilder
import com.android.build.gradle.integration.common.fixture.app.ManifestFileBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.connected.utils.getEmulator
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Connected tests for Library Android Test with views.  */
class LibraryAndroidTestWithViewTest {
    companion object {
        @JvmField
        @ClassRule
        val emulator = getEmulator()

        private const val testNamespace = "com.android.tests.atviews"
        private val srcPackage = testNamespace.replace('.','/')
        private val testManifest = with(ManifestFileBuilder()) {
            addApplicationTag("TestActivity", testNamespace, false)
            build()
        }
        private val testLayout = with(LayoutFileBuilder()) {
            addView("com.android.tests.atviews.TestView", "view_instance_1")
            build()
        }

        private val testViewSrc = """
        package $testNamespace

        import android.content.Context
        import android.util.AttributeSet
        import androidx.appcompat.widget.AppCompatEditText
        import androidx.appcompat.R as AppCompatR

        class TestView @JvmOverloads constructor(
                context: Context,
                attrs: AttributeSet? = null,
                defStyleAttr: Int = AppCompatR.attr.editTextStyle,
        ) : AppCompatEditText(context, attrs, defStyleAttr)
    """.trimIndent()

        private val testActivitySrc = """
        package $testNamespace

        import androidx.appcompat.app.AppCompatActivity
        import com.android.tests.atviews.test.R
        class TestActivity : AppCompatActivity(R.layout.test_view_layout)
    """.trimIndent()

        private val testTestViewSrc = """
        package $testNamespace

        import android.Manifest
        import android.os.Build

        import androidx.test.ext.junit.rules.ActivityScenarioRule
        import androidx.test.ext.junit.runners.AndroidJUnit4
        import androidx.test.rule.GrantPermissionRule
        import com.android.tests.atviews.TestView
        import com.android.tests.atviews.test.R
        import org.junit.Assert.*
        import org.junit.Rule
        import org.junit.Test
        import org.junit.runner.RunWith

        @RunWith(AndroidJUnit4::class)
        class TestTestView {
            @get:Rule var rule = ActivityScenarioRule(TestActivity::class.java)

            @get:Rule
            val mRuntimePermissionRule: GrantPermissionRule =
                    if (Build.VERSION.SDK_INT >= 33) {
                        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        GrantPermissionRule.grant()
                    }

            @Test
            fun testLaunchActivity() {
                rule.scenario.onActivity { activity ->
                    assertNotNull(activity)
                    assertNotNull(activity.findViewById<TestView>(R.id.view_instance_1))
                }
            }
        }
    """.trimIndent()
    }

    @Rule
    @JvmField
    var project = createGradleProject {
        withKotlinPlugin = true
        rootProject {
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            dependencies {
                implementation(externalLibrary("androidx.core:core-ktx:1.1.0"))
                implementation(externalLibrary("androidx.appcompat:appcompat:1.3.+"))
                testImplementation(externalLibrary("junit:junit:4.12"))
                androidTestImplementation(externalLibrary("androidx.core:core-ktx:1.1.0"))
                androidTestImplementation(externalLibrary("androidx.test.ext:junit:1.1.3-alpha02"))
                androidTestImplementation(externalLibrary("androidx.test:runner:1.4.0-alpha06"))
                androidTestImplementation(externalLibrary("androidx.test:rules:1.4.0-alpha06"))
                androidTestImplementation(externalLibrary("com.google.guava:guava:19.0"))
                androidTestImplementation(externalLibrary("com.android.support.constraint:constraint-layout:$SUPPORT_LIB_CONSTRAINT_LAYOUT_VERSION"))
            }
            android {
                namespace = testNamespace
                compileSdk = DEFAULT_COMPILE_SDK_VERSION
                minSdk = DEFAULT_MIN_SDK_VERSION
                hasInstrumentationTests = true
                buildFeatures {
                    viewBinding = true
                }
                kotlinOptions {
                    jvmTarget = "1.8"
                }
            }
            addFile("src/main/java/${srcPackage}/TestView.kt", testViewSrc)
            addFile("src/androidTest/AndroidManifest.xml", testManifest)
            addFile("src/androidTest/res/layout/test_view_layout.xml", testLayout)
            addFile("src/androidTest/java/${srcPackage}/TestActivity.kt", testActivitySrc)
            addFile("src/androidTest/java/${srcPackage}/TestTestView.kt", testTestViewSrc)
        }
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {
        project.addUseAndroidXProperty()

        // fail fast if no response
        project.addAdbTimeout()
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll")
    }

    @Test
    fun connectedCheck() {
        project.executor().run("connectedAndroidTest")
    }
}
