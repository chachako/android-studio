java_binary(
    name = "jarjar",
    main_class = "com.tonicsystems.jarjar.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [":jarjar_lib"],
)

java_library(
    name = "jarjar_lib",
    srcs = glob(
        include = [
            "src/android/**/*.java",
            "src/main/**/*.java",
        ],
        exclude = [
            # exclude Ant and Maven stuff
            "src/main/com/tonicsystems/jarjar/JarJarMojo.java",
            "src/main/com/tonicsystems/jarjar/JarJarTask.java",
            "src/main/com/tonicsystems/jarjar/util/AntJarProcessor.java",
        ],
    ),
    resource_strip_prefix = "res/",
    resources = ["res/com/tonicsystems/jarjar/help.txt"],
    visibility = ["//visibility:private"],
    deps = ["@//prebuilts/tools/common/m2:jarjar_asm"],
)
