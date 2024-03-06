load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("//tools/base/bazel:emulator.bzl", "setup_external_sdk")
load("//tools/base/bazel:repositories.bzl", "setup_external_repositories", "vendor_repository")

setup_external_repositories()

register_toolchains(
    "@native_toolchain//:cc-toolchain-x64_linux",
    "@native_toolchain//:cc-toolchain-darwin",
    "@native_toolchain//:cc-toolchain-x64_windows-clang-cl",
    "//tools/base/bazel/toolchains/darwin:python_toolchain",
    "//tools/base/bazel/toolchains/darwin:python_toolchain_10.13",
    "//prebuilts/studio/jdk/jdk11:runtime_toolchain_definition",
    "//prebuilts/studio/jdk/jdk17:java_runtime_toolchain",
    "//prebuilts/studio/jdk/jdk17:java8_compile_toolchain_definition",
    "//prebuilts/studio/jdk/jdk17:java11_compile_toolchain_definition",
    "//prebuilts/studio/jdk/jdk17:java17_compile_toolchain_definition",
    "//prebuilts/studio/jdk/jbr-next:jetbrains_java_runtime_toolchain",
)

new_local_repository(
    name = "studio_jdk",
    build_file = "prebuilts/studio/jdk/jdk8/BUILD.studio_jdk",
    path = "prebuilts/studio/jdk/jdk8",
)

local_repository(
    name = "blaze",
    path = "tools/vendor/google3/blaze",
    repo_mapping = {
        "@local_jdk": "@studio_jdk",
    },
)

vendor_repository(
    name = "vendor",
    bzl = "@//tools/base/bazel:vendor.bzl",
    function = "setup_vendor_repositories",
)

load("@vendor//:vendor.bzl", "setup_vendor_repositories")

setup_vendor_repositories()

local_repository(
    name = "io_bazel_rules_kotlin",
    path = "tools/external/bazelbuild-rules-kotlin",
)

local_repository(
    name = "windows_toolchains",
    path = "tools/base/bazel/toolchains/windows",
)

# Bazel cannot auto-detect python on Windows yet
# See: https://github.com/bazelbuild/bazel/issues/7844
register_toolchains("@windows_toolchains//:python_toolchain")

local_repository(
    name = "bazel_skylib",
    path = "prebuilts/tools/common/external-src-archives/bazel-skylib/bazel-skylib-1.0.2",
)

local_repository(
    name = "bazel_toolchains",
    path = "prebuilts/tools/common/external-src-archives/bazel-toolchains/bazel-toolchains-5.1.2",
)

load(
    "@bazel_toolchains//repositories:repositories.bzl",
    bazel_toolchains_repositories = "repositories",
)

bazel_toolchains_repositories()

setup_external_sdk(
    name = "externsdk",
)

## Coverage related workspaces
# Coverage reports construction
local_repository(
    name = "cov",
    path = "tools/base/bazel/coverage",
)

# Coverage results processing
load("@cov//:results.bzl", "setup_testlogs_loop_repo")

setup_testlogs_loop_repo()

# Coverage baseline construction
load("@cov//:baseline.bzl", "setup_bin_loop_repo")

setup_bin_loop_repo()

load(
    "@bazel_toolchains//rules/exec_properties:exec_properties.bzl",
    "create_rbe_exec_properties_dict",
    "custom_exec_properties",
)

custom_exec_properties(
    name = "exec_properties",
    constants = {
        "LARGE_MACHINE": create_rbe_exec_properties_dict(
            labels = {"machine-size": "large"},
        ),
    },
)

# Download system images when needed by avd.
http_archive(
    name = "system_image_android-30_default_x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "8d591034a4244a920d7a3ec274bb1734dd6474a3d8c11d0fce902010db3a13aa",
    strip_prefix = "x86_64",
    url = "https://dl.google.com/android/repository/sys-img/android/x86_64-30_r11.zip",
)

http_archive(
    name = "system_image_android-31_default_x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "7e7081f5784e98dd391ddae52573a75bc1db17a2fd286cb20be46d3eec251f94",
    strip_prefix = "x86_64",
    url = "https://dl.google.com/android/repository/sys-img/google_apis/x86_64-31_r14.zip",
)

http_archive(
    name = "system_image_android-32_aosp_atd_x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "192ff0f288b182200cb63046897a531d88def2d14bfc84e5bcb5ff3dc7a8f780",
    strip_prefix = "x86_64",
    url = "https://dl.google.com/android/repository/sys-img/aosp_atd/x86_64-32_r01.zip",
)

http_archive(
    name = "system_image_android-33_default_x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "71cd5ab0990ae34a98f48d1b282414219ba22160e253f7bf8d91d84a08d4da57",
    strip_prefix = "x86_64",
    url = "https://dl.google.com/android/repository/sys-img/google_apis/x86_64-33_r10.zip",
)

http_archive(
    name = "system_image_android-33_default_arm64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "55ba9a20d473dd5351c77342016956342c40375f3e073b65a43dc9db13ccc5c6",
    strip_prefix = "arm64-v8a",
    url = "https://dl.google.com/android/repository/sys-img/android/arm64-v8a-33_r02.zip",
)

http_archive(
    name = "system_image_android-33_aosp_atd_x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "d17967001f453c4b82d09dd3d6931b938c16c9f2bf0e054b521293ca24e3b95e",
    strip_prefix = "x86_64",
    url = "https://dl.google.com/android/repository/sys-img/aosp_atd/x86_64-33_r02.zip",
)

http_archive(
    name = "system_image_android-33PlayStore_default_x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "74b0a57c2cfee755dcf7645e5da9d5468a2982af0bf012dfb46f661bc8b9f84a",
    strip_prefix = "x86_64",
    url = "https://dl.google.com/android/repository/sys-img/google_apis_playstore/x86_64-33_r07.zip",
)

# Sdk components when needed by Gradle Managed Devices
http_file(
    name = "emulator_zip",
    downloaded_file_path = "emulator-linux_x64-11078245.zip",
    sha256 = "7ebfd686b4f6e0d3f8bb02bbf1e61e587a9fcd5b776b310d8d3feae8569a078f",
    urls = ["https://dl.google.com/android/repository/emulator-linux_x64-11078245.zip"],
)

# An empty local repository which must be overridden according to the instructions at
# go/agp-profiled-benchmarks if running the "_profiled" AGP build benchmarks.
new_local_repository(
    name = "yourkit_controller",
    build_file = "tools/base/yourkit-controller/yourkit.BUILD",
    path = "tools/base/yourkit-controller",
)

new_local_repository(
    name = "maven",
    build_file = "tools/base/bazel/maven/BUILD.maven",
    path = "prebuilts/tools/common/m2",
)

new_local_repository(
    name = "jar_jar",
    build_file = "tools/base/bazel/jarjar/jarjar.BUILD",
    path = "external/jarjar",
)

local_repository(
    name = "absl-py",
    path = "external/python/absl-py",
)

# In Android Studio, we cannot bundle the standard Compose compiler because it might not be
# compatible with the particular Kotlin compiler bundled inside the Kotlin IDE plugin
# (often it is a dev/snapshot build). So instead we download the Compose sources here,
# add a few patches on top, and compile directly against the Kotlin IDE plugin.
# The following 'http_archive' is consumed in //tools/adt/idea/compose-ide-plugin/compose-compiler.
# See b/265493659 for more background and discussion.
http_archive(
    name = "compose-compiler-sources",
    build_file = "@//tools/adt/idea/compose-ide-plugin/compose-compiler:compose-compiler-sources.BUILD",
    # To create a new patch file: download the linked sources zip; unpack into
    # an empty git project; commit the baseline; make your changes; then run
    # `git diff --no-prefix --output=/path/to/file.patch`.
    patches = [
        "@//tools/adt/idea/compose-ide-plugin/compose-compiler:suppress-kotlinc-version-check.patch",
        "@//tools/adt/idea/compose-ide-plugin/compose-compiler:support-k2-registrar.patch",
        "@//tools/adt/idea/compose-ide-plugin/compose-compiler:intellij-233.patch",
    ],
    sha256 = "278c85ac3e3b5908ca527c9a6a2cfe9db2184b95c755b790e7e39b71f286177a",
    # The following URL comes from https://androidx.dev/storage/compose-compiler/repository.
    url = "https://androidx.dev/storage/compose-compiler/repository/androidx/compose/compiler/compiler-hosted/1.5.8-dev-k1.9.22-42b6ec2b037/compiler-hosted-1.5.8-dev-k1.9.22-42b6ec2b037-sources.jar",
)

http_archive(
    name = "robolectric",
    sha256 = "5bcde5db598f6938c9887a140a0a1249f95d3c16274d40869503d0c322a20d5d",
    strip_prefix = "robolectric-bazel-4.8.2",
    urls = ["https://github.com/robolectric/robolectric-bazel/archive/4.8.2.tar.gz"],
)

load("@robolectric//bazel:robolectric.bzl", "robolectric_repositories")

robolectric_repositories()
