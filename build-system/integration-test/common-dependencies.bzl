"""
This module contains maven deps which are common between integration tests
and can be combined together
"""

# KGP version used in AGP tests
KGP_VERSION_FOR_TESTS = "2.0.0-Beta1"

# KGP dependencies used in AGP tests
KGP_FOR_TESTS = [
    "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.jvm.org.jetbrains.kotlin.jvm.gradle.plugin_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kapt.org.jetbrains.kotlin.kapt.gradle.plugin_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-annotation-processing-gradle_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-build-tools-impl_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-compiler_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-reflect_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_" + KGP_VERSION_FOR_TESTS,
]
