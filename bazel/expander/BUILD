load("//tools/base/bazel:expand_template.bzl", "expand_template")

py_binary(
    name = "expander",
    srcs = ["expander.py"],
    visibility = ["//visibility:public"],
)

py_test(
    name = "expander_test",
    srcs = ["expander_test.py"],
    data = [
        "test_data/expected.txt",
        ":actual.txt",
    ],
    visibility = ["//visibility:public"],
    deps = [":expander"],
)

expand_template(
    name = "test_data",
    out = "actual.txt",
    data = [
        "test_data/file.txt",
    ],
    substitutions = {
        "VAR1": "VALUE1",
        "VAR2": "VALUE2",
        "FILE1": "$(inline $(location test_data/file.txt))",
        "FILE2": "$(inline $(location test_data/file.txt))",
    },
    template = "test_data/template.txt",
)
