py_binary(
    name = "rename_jar_resources",
    srcs = ["rename_jar_resources.py"],
    visibility = ["//visibility:public"],
)

py_test(
    name = "rename_jar_resources_test",
    srcs = ["rename_jar_resources_test.py"],
    tags = [
        "noci:studio-win",
    ],
    deps = [
        ":rename_jar_resources",
        "@absl-py//absl/testing:absltest",
    ],
)
