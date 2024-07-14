"""
This module implements lint_test rule to run Lint lib tests
for both K1 and K2 environments.
"""

load("//tools/base/bazel:kotlin.bzl", "kotlin_test")

def lint_test(name, useK2):
    kotlin_test(
        name = name,
        timeout = "long",
        # buildifier: disable=bad-recursive-glob
        srcs = native.glob([
            "src/test/java/**/*.kt",
            "src/test/java/**/*.java",
        ]),
        data = [
            "//prebuilts/studio/sdk:platform-tools",
            "//prebuilts/studio/sdk:platforms/latest",
            "//tools/adt/idea/android/annotations",
        ],
        jvm_flags = [
            "-Dtest.suite.jar=" + name + ".jar",
            "-Djdk.attach.allowAttachSelf=true",
            # TODO: Inject the cache directory into tests.
            "-Duser.home=/tmp",
        ] + (["-Dlint.use.fir.uast=true"] if useK2 else []),
        lint_baseline = "lint_baseline.xml",
        resources = native.glob(["src/test/resources/**"]),
        shard_count = 6,
        tags = [
            "slow",
        ],
        test_class = "com.android.testutils.JarTestSuite",
        deps = [
            ":lint-tests",
            "//prebuilts/tools/common/lint-psi/intellij-core",
            "//prebuilts/tools/common/lint-psi/kotlin-compiler",
            "//prebuilts/tools/common/lint-psi/uast",
            "//tools/analytics-library/protos/src/main/proto",
            "//tools/analytics-library/shared:tools.analytics-shared",
            "//tools/analytics-library/testing:tools.analytics-testing",
            "//tools/analytics-library/tracker:tools.analytics-tracker",
            "//tools/base/annotations",
            "//tools/base/apkparser:tools.binary-resources",
            "//tools/base/apkparser/analyzer:tools.analyzer",
            "//tools/base/bazel:langtools",
            "//tools/base/build-system:tools.manifest-merger",
            "//tools/base/build-system/builder-model",
            "//tools/base/common:tools.common",
            "//tools/base/common:tools.fixtures",
            "//tools/base/layoutlib-api:tools.layoutlib-api",
            "//tools/base/lint:tools.lint-api",
            "//tools/base/lint:tools.lint-checks",
            "//tools/base/lint:tools.lint-model",
            "//tools/base/lint/cli",
            "//tools/base/repository:tools.repository",
            "//tools/base/repository:tools.testlib",
            "//tools/base/sdk-common:tools.sdk-common",
            "//tools/base/sdklib:tools.sdklib",
            "//tools/base/testutils:tools.testutils",
            "@maven//:com.google.code.gson.gson",
            "@maven//:com.google.truth.truth",
            "@maven//:junit.junit",
            "@maven//:net.sf.kxml.kxml2",
            "@maven//:org.codehaus.groovy.groovy",
            "@maven//:org.jetbrains.annotations",
            "@maven//:org.jetbrains.intellij.deps.trove4j",
            "@maven//:org.jetbrains.kotlin.kotlin-reflect",
            "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
            "@maven//:org.mockito.mockito-core",
            "@maven//:org.ow2.asm.asm-tree",
        ],
    )
