load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_memory",
    srcs = [
        "commands/heap_dump.cc",
        "heap_dump_manager.cc",
        "internal_memory_service.cc",
        "memory_cache.cc",
        "memory_collector.cc",
        "memory_service.cc",
        "memory_usage_reader_impl.cc",
    ],
    hdrs = [
        "commands/heap_dump.h",
        "heap_dump_manager.h",
        "internal_memory_service.h",
        "memory_cache.h",
        "memory_collector.h",
        "memory_profiler_component.h",
        "memory_service.h",
        "memory_usage_reader.h",
        "memory_usage_reader_impl.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/profiler/native/perfd/common:atrace",
        "//tools/base/profiler/native/perfd/common:perfetto",
        "//tools/base/profiler/native/perfd/common:simpleperf",
        "//tools/base/profiler/native/perfd/common:trace_manager",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_test(
    name = "perfd_memory_tests",
    srcs = [
        "commands/heap_dump_test.cc",
        "heap_dump_manager_test.cc",
        "memory_cache_test.cc",
        "memory_usage_reader_impl_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/memory",
    ],
    linkopts = [
        "-ldl",  # Needed for dladdr referenced in TerminationService
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":profiler_memory",
        "//tools/base/profiler/native/perfd",
        "//tools/base/profiler/native/perfd/common:perfetto",
        "//tools/base/profiler/native/test:testutils",
        "//tools/base/transport/native/tests/test:testutils",
        "//tools/base/transport/proto:cc_proto",
        "@googletest//:gtest_main",
    ],
)
