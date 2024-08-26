load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file", "http_jar")
load("//tools/base/bazel:emulator.bzl", "setup_external_sdk")
load("//tools/base/bazel:repositories.bzl", "setup_external_repositories", "vendor_repository")

setup_external_repositories()

# BEGIN Cc toolchain dependencies
new_local_repository(
    name = "clang_linux_x64",
    build_file = "//build/bazel/toolchains/cc/linux_clang:clang.BUILD",
    path = "prebuilts/clang/host/linux-x86/clang-r536225",
)

new_local_repository(
    name = "clang_mac_all",
    build_file = "//build/bazel/toolchains/cc/mac_clang:clang.BUILD",
    path = "prebuilts/clang/host/darwin-x86/clang-r536225",
)

new_local_repository(
    name = "clang_win_x64",
    build_file = "//build/bazel/toolchains/cc/windows_clang:clang.BUILD",
    path = "prebuilts/clang/host/windows-x86/clang-r536225",
)

new_local_repository(
    name = "gcc_lib",
    build_file = "//build/bazel/toolchains/cc/linux_clang:gcc_lib.BUILD",
    path = "prebuilts/gcc/linux-x86/host/x86_64-linux-glibc2.17-4.8",
)

load(
    "//build/bazel/toolchains/cc:repository_rules.bzl",
    "macos_sdk_repository",
    "msvc_tools_repository",
    "windows_sdk_repository",
)

macos_sdk_repository(
    name = "macos_sdk",
    build_file = "//build/bazel/toolchains/cc/mac_clang:sdk.BUILD",
)

msvc_tools_repository(
    name = "vctools",
    build_file = "//build/bazel/toolchains/cc/windows_clang:vctools.BUILD",
)

windows_sdk_repository(
    name = "windows_sdk",
    build_file_template = "//build/bazel/toolchains/cc/windows_clang:sdk.BUILD.tpl",
    sdk_path = "C:\\Program Files (x86)\\Windows Kits\\10",
)

# END Cc toolchain dependencies

new_local_repository(
    name = "studio_jdk",
    build_file = "prebuilts/studio/jdk/jdk8/BUILD.studio_jdk",
    path = "prebuilts/studio/jdk/jdk8",
)

# rules_android_ndk must come before loading vendor.bzl, because it is
# configured as a vendor dependency and can only be configured with a valid
# ANDROID_NDK path.
local_repository(
    name = "rules_android_ndk",
    path = "prebuilts/tools/common/external-src-archives/bazelbuild-rules_android_ndk/1ed5be3",
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
    path = "prebuilts/tools/common/external-src-archives/bazel-skylib/bazel-skylib-1.6.1",
)

local_repository(
    name = "bazel_toolchains",
    path = "prebuilts/tools/common/external-src-archives/bazel-toolchains/bazel-toolchains-5.1.2",
)

local_repository(
    name = "rules_android",
    path = "prebuilts/tools/common/external-src-archives/bazelbuild-rules_android",
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
    name = "system_image_android-34_aosp_atd_x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "00f8c28481184e9cd06da4ca92df870fc68532c6ca30c372e1720e4cbc632fbd",
    strip_prefix = "x86_64",
    url = "https://dl.google.com/android/repository/sys-img/aosp_atd/x86_64-34_r02.zip",
)

http_archive(
    name = "system_image_android-33PlayStore_default_x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "74b0a57c2cfee755dcf7645e5da9d5468a2982af0bf012dfb46f661bc8b9f84a",
    strip_prefix = "x86_64",
    url = "https://dl.google.com/android/repository/sys-img/google_apis_playstore/x86_64-33_r07.zip",
)

http_archive(
    name = "system_image_android-35_default_x86_64",
    build_file = "//tools/base/bazel/avd:system_images.BUILD",
    sha256 = "6c3d0fe28dc1d8277c62f7b80503b8a29ec050c0458ae1ca497543328a9b40b8",
    strip_prefix = "x86_64",
    url = "https://dl.google.com/android/repository/sys-img/google_apis/x86_64-35_r06.zip",
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
    build_file = "//tools/base:yourkit-controller/yourkit.BUILD",
    path = "tools/base/yourkit-controller",
)

new_local_repository(
    name = "maven",
    build_file = "//tools/base/bazel:maven/BUILD.maven",
    path = "prebuilts/tools/common/m2",
)

new_local_repository(
    name = "jar_jar",
    build_file = "//tools/base/bazel/jarjar:jarjar.BUILD",
    path = "external/jarjar",
)

local_repository(
    name = "absl-py",
    path = "external/python/absl-py",
)

http_archive(
    name = "robolectric",
    sha256 = "5bcde5db598f6938c9887a140a0a1249f95d3c16274d40869503d0c322a20d5d",
    strip_prefix = "robolectric-bazel-4.8.2",
    urls = ["https://github.com/robolectric/robolectric-bazel/archive/4.8.2.tar.gz"],
)

load("@robolectric//bazel:robolectric.bzl", "robolectric_repositories")

robolectric_repositories()

load("//tools/base/intellij-bazel:platforms.bzl", "setup_intellij_platforms")

setup_intellij_platforms()

http_archive(
    name = "rules_pkg",
    sha256 = "d250924a2ecc5176808fc4c25d5cf5e9e79e6346d79d5ab1c493e289e722d1d0",
    urls = [
        "https://github.com/bazelbuild/rules_pkg/releases/download/0.10.1/rules_pkg-0.10.1.tar.gz",
    ],
)

load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")

rules_pkg_dependencies()

vendor_repository(
    name = "aswb_test_deps",
    bzl = "@//tools/adt/idea/aswb/testing/test_deps:deps.bzl",
    function = "aswb_test_deps_dependencies",
)

load("@aswb_test_deps//:vendor.bzl", "aswb_test_deps_dependencies")

aswb_test_deps_dependencies()

http_jar(
    name = "bazel_diff",
    sha256 = "ed5410288bd7ec5b49b556103f561fb15e4c82f13f12cb41be128d447ecc2d46",
    urls = [
        "https://github.com/Tinder/bazel-diff/releases/download/7.1.1/bazel-diff_deploy.jar",
    ],
)

http_archive(
    name = "with_cfg.bzl",
    sha256 = "06a2b1b56a58c471ab40d8af166c4d51f0982e1c6bc46375b805915b3fc0658e",
    strip_prefix = "with_cfg.bzl-0.2.4",
    url = "https://github.com/fmeum/with_cfg.bzl/releases/download/v0.2.4/with_cfg.bzl-v0.2.4.tar.gz",
)
