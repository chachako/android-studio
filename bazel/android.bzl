def _jni_library_impl(ctx):
    inputs = []
    for cpu, deps in ctx.split_attr.deps.items():
        if not cpu:
            # --fat_apk_cpu wasn't used, so the dependencies were compiled using the
            # cpu value from --cpu, so use that as the directory name.
            cpu = ctx.fragments.cpp.cpu
        for dep in deps:
            for f in dep.files.to_list():
                inputs.append((cpu, f))

    # If two targets in deps share the same files (e.g. in the data attribute)
    # they would be included mulitple times in the same path in the zip, so
    # dedupe the files.
    deduped_inputs = depset(inputs)
    zipper_args = ["c", ctx.outputs.zip.path]
    for cpu, file in deduped_inputs.to_list():
        # "lib/" is the JNI directory looked for in android
        target = "lib/%s/%s" % (cpu, file.basename)

        # Using bazel stripping convention
        # https://docs.bazel.build/versions/master/be/c-cpp.html#cc_binary
        if target.endswith(".stripped"):
            target = target[:-9]
        name = target + "=" + file.path
        zipper_args.append(name)

    ctx.actions.run(
        inputs = [f for cpu, f in deduped_inputs.to_list()],
        outputs = [ctx.outputs.zip],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating zip...",
        mnemonic = "zipper",
    )

def _android_cc_binary_impl(ctx):
    for cpu, binary in ctx.split_attr.binary.items():
        name = ctx.attr.filename
        file = binary.files.to_list()[0]
        for out in ctx.outputs.outs:
            if out.path.endswith(cpu + "/" + name):
                ctx.actions.run_shell(
                    mnemonic = "SplitCp",
                    inputs = [file],
                    outputs = [out],
                    command = "cp " + file.path + " " + out.path,
                )

_android_cc_binary = rule(
    attrs = {
        "filename": attr.string(),
        "binary": attr.label(
            allow_files = True,
            cfg = android_common.multi_cpu_configuration,
        ),
        "outs": attr.output_list(),
    },
    implementation = _android_cc_binary_impl,
)

def android_cc_binary(name, binary, filename, **kwargs):
    outs = []

    # This is tightly coupled to the value given to --android_platforms because it uses
    # transitions based the given platforms.
    # LINT.IfChange(android_platforms)
    for abi in ["x86", "x86_64", "armeabi-v7a", "arm64-v8a"]:
        # LINT.ThenChange(/bazel/common.bazelrc:android_platforms)
        outs.append(name + "/" + abi + "/" + filename)
    _android_cc_binary(
        name = name,
        filename = filename,
        binary = binary,
        outs = outs,
        **kwargs
    )

jni_library = rule(
    attrs = {
        "deps": attr.label_list(
            cfg = android_common.multi_cpu_configuration,
            allow_files = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "exec",
            executable = True,
        ),
    },
    fragments = ["cpp"],
    outputs = {"zip": "%{name}.jar"},
    implementation = _jni_library_impl,
)

def select_android(android, default = []):
    return select({
        "@platforms//os:android": android,
        "//conditions:default": default,
    })

def _aidl_to_java(filename, output_dir):
    """Converts the name of an .aidl file to the name of a generated .java file by replacing
       the file extension and the directory prefix up to the "aidl" segment with the value of
       the output_dir parameter."""

    if filename.endswith(".aidl"):
        filename = filename[:-len(".aidl")] + ".java"
    segments = filename.split("/")
    return output_dir + "/" + "/".join(segments[segments.index("aidl") + 1:])

def _name(file):
    """Returns the name of the file."""

    return file.split("/")[-1]

def aidl_library(name, srcs = [], **kwargs):
    """Builds a Java library out of .aidl files."""
    gen_dir = name + "_gen_aidl"
    for src in srcs:
        gen_name = name + "_gen_" + _name(src).replace(".", "_")
        java_file = _aidl_to_java(src, gen_dir)
        cmd = "$(location @androidsdk//:aidl_binary) $< -o$(RULEDIR)/" + gen_dir
        native.genrule(
            name = gen_name,
            srcs = [src],
            outs = [java_file],
            cmd = cmd,
            tags = kwargs.get("tags", []),
            target_compatible_with = kwargs.get("target_compatible_with", []),
            tools = ["@androidsdk//:aidl_binary"],
        )

    intermediates = [_aidl_to_java(src, gen_dir) for src in srcs]
    native.java_library(
        name = name,
        srcs = intermediates,
        **kwargs,
    )

def dex_library(name, jars = [], output = None, visibility = None, tags = [], flags = []):
    native.genrule(
        name = name,
        srcs = jars,
        outs = [output] if output != None else [name + ".jar"],
        visibility = visibility,
        tags = tags,
        cmd = "$(location //prebuilts/r8:d8) --output ./$@ " + " ".join(flags) + " ./$(SRCS)",
        tools = ["//prebuilts/r8:d8"],
    )

ANDROID_COPTS = select_android(
    [
        "-fPIC",
        "-std=c++17",
        "-flto",
    ],
)

ANDROID_LINKOPTS = select_android(
    [
        "-llog",
        "-latomic",
        "-lm",
        "-ldl",
        "-pie",
        "-Wl,--gc-sections",
        "-Wl,--as-needed",
        "-Wl,-z,max-page-size=16384",
        "-flto",
    ],
)
