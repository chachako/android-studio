load("//tools/base/build-system/integration-test:integration-test.bzl", "single_gradle_integration_test", "single_gradle_integration_test_per_source")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# A gradle connected test
#
# Usage:
# gradle_connected_test(
#     name = the name of the file containing the test(s), excluding the file extension, for example
#            "BasicConnectedTest"
#     srcs = the relative path of the file containing the test(s), excluding the file name, for
#            example "src/test/java/com/android/build/gradle/integration/connected/application/"
#     deps = test classes output
#     data = test data: SDK parts, test projects, and avd (see //tools/base/bazel/avd:avd.bzl)
#     maven_repos = Absolute targets for maven repos containing the plugin(s) under test
# )
def gradle_connected_test(
        name,
        srcs,
        avd,
        deps,
        data,
        maven_repos,
        emulator_binary_path = "prebuilts/studio/sdk/linux/emulator/emulator",
        maven_artifacts = [],
        runtime_deps = [],
        tags = [],
        timeout = "long",
        jvm_flags = [],
        **kwargs):
    if avd:
        script_path = "$(rootpath %s)" % avd
        jvm_flags = jvm_flags + [
            "-DEMULATOR_SCRIPT_PATH=%s" % script_path,
            "-DEMULATOR_BINARY_PATH=%s" % emulator_binary_path,
        ]
    if maven_artifacts:
        repo_name = name + ".mavenRepo"
        maven_repository(
            name = repo_name,
            artifacts = maven_artifacts,
        )
        absolute_path = "//%s:%s" % (native.package_name(), repo_name)
        maven_repos += [absolute_path]
    single_gradle_integration_test(
        name = name,
        srcs = srcs,
        deps = deps,
        data = data + ([avd] if avd else []),
        maven_repos = maven_repos,
        runtime_deps = runtime_deps,
        tags = tags + [
            "noci:studio-win",
        ],
        timeout = timeout,
        jvm_flags = jvm_flags,
        **kwargs
    )

# Given a glob, this will create connected gradle test target for each of the sources in the glob.
#
# Usage:
# single_gradle_connected_test_per_source(
#     name = 'name',
#     deps = test classes output
#     data = test data: SDK parts, test projects, and avd (see //tools/base/bazel/avd:avd.bzl)
#     avd = avd target name, for example ":avd_34" for API 34 devices
#     maven_repos = Absolute targets for maven repos containing the plugin(s) under test
#     package_name = 'tools/base/build-system/integration-test/connected'
#     srcs = glob of source files containing the test(s) for which a new gradle integration test
#            target will be created
#     non_target_srcs = source files without tests added to each gradle integration test target
# )
# Given a glob, this will create connected gradle test target for each of the sources in the glob.
def single_gradle_connected_test_per_source(
        name,
        deps,
        data,
        avd,
        maven_repos,
        package_name,
        srcs,
        non_target_srcs = [],
        emulator_binary_path = "prebuilts/studio/sdk/linux/emulator/emulator",
        maven_artifacts = [],
        runtime_deps = [],
        tags = [],
        jvm_flags = [],
        **kwargs):
    if avd:
        script_path = "$(rootpath %s)" % avd
        jvm_flags = jvm_flags + [
            "-DEMULATOR_SCRIPT_PATH=%s" % script_path,
            "-DEMULATOR_BINARY_PATH=%s" % emulator_binary_path,
        ]
    if maven_artifacts:
        repo_name = name + ".mavenRepo"
        maven_repository(
            name = repo_name,
            artifacts = maven_artifacts,
        )
        absolute_path = "//%s:%s" % (native.package_name(), repo_name)
        maven_repos += [absolute_path]
    single_gradle_integration_test_per_source(
        name = name,
        deps = deps,
        data = data + ([avd] if avd else []),
        maven_repos = maven_repos,
        package_name = package_name,
        srcs = srcs,
        non_target_srcs = non_target_srcs,
        runtime_deps = runtime_deps,
        tags = tags + [
            "noci:studio-win",
        ],
        jvm_flags = jvm_flags,
        **kwargs
    )
