py_binary(
    name = "rename_jar_services",
    srcs = ["rename_jar_services.py"],
    visibility = ["//visibility:public"],
)

py_test(
    name = "rename_jar_services_test",
    srcs = ["rename_jar_services_test.py"],
    tags = [
        "noci:studio-win",
    ],
    deps = [":rename_jar_services"],
)