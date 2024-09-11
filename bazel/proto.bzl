load(":android.bzl", "select_android")
load(":functions.bzl", "label_workspace_path", "workspace_path")
load(":maven.bzl", "maven_library")
load(":utils.bzl", "java_jarjar")

# Enum-like values to determine the language the gen_proto rule will compile
# the .proto files to.
proto_languages = struct(
    CPP = 0,
    JAVA = 1,
)

# This version of protoc is the one currently used in the studio-sdk.jar
# It will be used to pin the version of protoc used to generate protofiles for analytics and utp
# Please do not remove or change it unless the version in the platform has changed
INTELLIJ_PLATFORM_PROTO_VERSION = "3.19.6"
PROTOC_VERSION = "3.22.3"
PROTOC_GRPC_VERSION = "1.57.0"

ProtoPackageInfo = provider(fields = ["proto_src", "proto_paths"])

def _gen_proto_impl(ctx):
    inputs = []
    inputs += ctx.files.srcs + ctx.files.include

    args = []
    needs_label_path = False
    proto_paths = []
    if ctx.attr.extra_proto_path:
        proto_paths.append(ctx.attr.extra_proto_path)

    for src_target in ctx.attr.srcs:
        if ProtoPackageInfo in src_target:
            for path in src_target[ProtoPackageInfo].proto_paths:
                proto_paths.append(path)
        else:
            # if src_target doesn't have ProtoPackageInfo provider that should be used to look up proto files
            # then we're going to path where BUILD is placed.
            # Example: //rule/proto/BUILD -> --proto_path="rule/proto"
            needs_label_path = True

    label_dir = label_workspace_path(ctx.label)
    if needs_label_path:
        proto_paths.append(label_dir)

    for dep in ctx.attr.deps:
        if dep[ProtoPackageInfo].proto_paths:
            for path in dep[ProtoPackageInfo].proto_paths:
                proto_paths.append(path)
        else:
            proto_paths.append(label_workspace_path(dep.label))

        inputs += dep[ProtoPackageInfo].proto_src

    args += ["--proto_path=" + p for p in proto_paths]
    args += [s.path for s in ctx.files.srcs]

    # Try to generate cc protos first.
    if ctx.attr.target_language == proto_languages.CPP:
        out_path = ctx.var["GENDIR"] + "/" + label_dir
        args.append(
            "--cpp_out=" + out_path,
        )
        if ctx.executable.grpc_plugin != None and ctx.executable.grpc_plugin.path != None:
            args += [
                "--grpc_out=" + out_path,
                "--plugin=protoc-gen-grpc=" + ctx.executable.grpc_plugin.path,
            ]
        outs = ctx.outputs.outs

        # Try to generate java protos only if we won't generate cc protos
    elif ctx.attr.target_language == proto_languages.JAVA:
        srcjar = ctx.outputs.outs[0]  # outputs.out size should be 1
        outs = [ctx.actions.declare_file(srcjar.basename + ".jar")]

        out_path = outs[0].path
        args.append(
            "--java_out=" + out_path,
        )
        if ctx.executable.grpc_plugin != None:
            args += [
                "--java_rpc_out=" + out_path,
                "--plugin=protoc-gen-java_rpc=" + ctx.executable.grpc_plugin.path,
            ]

    tools = []
    if ctx.executable.grpc_plugin != None:
        tools.append(ctx.executable.grpc_plugin)

    ctx.actions.run(
        mnemonic = "GenProto",
        inputs = inputs,
        outputs = outs,
        tools = tools,
        arguments = args,
        executable = ctx.executable.protoc,
    )

    if ctx.attr.target_language == proto_languages.JAVA:
        # This is required because protoc only understands .jar extensions, but Bazel
        # requires source JAR files end in .srcjar.
        ctx.actions.run_shell(
            mnemonic = "FixProtoSrcJar",
            inputs = outs,
            outputs = [srcjar],
            command = "cp " + srcjar.path + ".jar" + " " + srcjar.path,
        )

    return ProtoPackageInfo(
        proto_src = inputs,
        proto_paths = proto_paths,
    )

_gen_proto_rule = rule(
    attrs = {
        "srcs": attr.label_list(
            allow_files = [".proto"],
            providers = [[ProtoPackageInfo], ["files"]],
        ),
        "deps": attr.label_list(
            allow_files = False,
            providers = [ProtoPackageInfo],
        ),
        "include": attr.label(
            allow_files = [".proto"],
        ),
        "proto_include_version": attr.string(),
        "protoc": attr.label(
            cfg = "exec",
            executable = True,
            mandatory = True,
            allow_single_file = True,
        ),
        "grpc_plugin": attr.label(
            cfg = "exec",
            executable = True,
            allow_single_file = True,
        ),
        "target_language": attr.int(),
        "extra_proto_path": attr.string(default = ""),
        "outs": attr.output_list(),
    },
    output_to_genfiles = True,
    implementation = _gen_proto_impl,
)

def java_proto_library(
        name,
        srcs = None,
        proto_deps = [],
        java_deps = [],
        visibility = None,
        grpc_support = False,
        protoc_version = PROTOC_VERSION,
        protoc_grpc_version = PROTOC_GRPC_VERSION,
        proto_java_runtime_library = ["@maven//:com.google.protobuf.protobuf-java"],
        strip_prefix = "",
        skip_default_includes = False,
        **kwargs):
    """Compiles protobuf into a .jar file and optionally creates a maven artifact.

    NOTE: Be cautious to use this rule. You may need to use android_java_proto_library instead.
    See the comments in android_java_proto_library rule before using it.

    Args:
      name: Name of the rule.
      srcs:  A list of file names of the protobuf definition to compile.
      proto_deps: A list of dependent proto_library to compile the library.
      java_deps: An additional java libraries to be packaged into the library.
      visibility: Visibility of the rule.
      grpc_support: True if the proto library requires grpc protoc plugin.
      protoc_version: The protoc version to use.
      protoc_grpc_version: A version of the grpc protoc plugin to use.
      proto_java_runtime_library: A label of java_library to be loaded at runtime.
      strip_prefix: A directory prefix to remove from source files when compiling protos,
                    so they can be properly found when included from other protos.
                    E.g. if the proto you want to include is build at my/target/path/foo.proto,
                    but it's included as just "path/foo.proto", you can specify
                    strip_prefix="my/target". (In terms of the protoc command run, this means that
                    it will get "--proto_path=my/target" as an extra argument).
      skip_default_includes: don't add //tools/base/bazel:common-java_proto as a dependency.
                             This should probably only be used by
                             //tools/base/bazel:common-java_proto itself.
      **kwargs: other arguments accepted by bazel rule `java_library` are passed untouched.
    """

    # Targets that require grpc support should specify the version of protoc-gen-grpc-java plugin.
    if grpc_support and not protoc_grpc_version:
        fail("grpc support was requested, but the version of grpc java protoc plugin was not specified")

    srcs_name = name + "_srcs"
    outs = [srcs_name + ".srcjar"]
    _gen_proto_rule(
        name = srcs_name,
        srcs = srcs,
        deps = proto_deps + ([] if skip_default_includes else ["@//tools/base/bazel:common-java_proto_srcs"]),
        outs = outs,
        proto_include_version = protoc_version,
        protoc = "@//prebuilts/tools/common/m2:com.google.protobuf.protoc." + protoc_version + "_exe",
        grpc_plugin =
            "@//prebuilts/tools/common/m2:io.grpc.protoc-gen-grpc-java." + protoc_grpc_version + "_exe" if grpc_support else None,
        target_language = proto_languages.JAVA,
        visibility = visibility,
        extra_proto_path = strip_prefix,
    )

    grpc_extra_deps = ["@//prebuilts/tools/common/m2:javax.annotation.javax.annotation-api.1.3.2"]
    java_deps = list(java_deps) + (grpc_extra_deps if grpc_support else [])
    java_deps += proto_java_runtime_library

    native.java_library(
        name = name,
        srcs = outs,
        deps = java_deps,
        javacopts = kwargs.pop("javacopts", []) + ["--release", "8"],
        visibility = visibility,
        **kwargs
    )

def android_java_proto_library(
        name,
        srcs = None,
        grpc_support = False,
        protoc_grpc_version = PROTOC_GRPC_VERSION,
        java_deps = [],
        proto_deps = [],
        visibility = None,
        **kwargs):
    """Compiles protobuf into a .jar file in Android Studio compatible runtime version.

    Unlike java_proto_library rule defined above, android_java_proto_library
    repackage com.google.protobuf.* dependencies to com.android.tools.idea.protobuf
    by applying JarJar tool after the protobuf compilation. This repackaging is necessary
    in order to avoid version incompatibility to IntelliJ platform runtime dependencies.

    tl;dr; Use this rule if your proto library is linked to Android Studio, otherwise
    use java_proto_library.

    NOTE: The repackaged runtime is at //tools/base/bazel:studio-proto.

    Args:
      name: Name of the rule.
      srcs:  A list of file names of the protobuf definition to compile.
      grpc_support: True if the proto library requires grpc protoc plugin.
      protoc_grpc_version: A version of the grpc protoc plugin to use.
      java_deps: Any additional java libraries to be packaged into the library.
      proto_deps: Any protos depended upon by the protos in this library (via `import`)
      visibility: Visibility of the rule.
      **kwargs: other arguments accepted by bazel rule `java_proto_library` are passed untouched.
    """
    internal_name = "_" + name + "_internal"
    java_proto_library(
        name = internal_name,
        srcs = srcs,
        grpc_support = grpc_support,
        protoc_grpc_version = protoc_grpc_version,
        java_deps = java_deps,
        proto_deps = proto_deps,
        **kwargs
    )
    java_jarjar(
        name = name,
        srcs = [":" + internal_name],
        rules = "//tools/base/bazel:jarjar_rules.txt",
        visibility = visibility,
    )

def cc_grpc_proto_library(
        name,
        srcs = [],
        deps = [],
        includes = [],
        visibility = None,
        grpc_support = False,
        protoc_version = PROTOC_VERSION,
        tags = None,
        target_compatible_with = [],
        include_prefix = None):
    outs = []
    hdrs = []
    proto_deps = []  # Assumes c++ deps point to *_grpc_proto_library packages
    for src in srcs:
        # .proto suffix should not be present in the output files
        p_name = src[:-len(".proto")]
        outs.append(p_name + ".pb.cc")
        hdrs.append(p_name + ".pb.h")
        if grpc_support:
            outs.append(p_name + ".grpc.pb.cc")
            hdrs.append(p_name + ".grpc.pb.h")
    for dep in deps:
        proto_deps.append(dep + "_srcs")
    proto_deps.append("@//tools/base/bazel:common-java_proto_srcs")

    _gen_proto_rule(
        name = name + "_srcs",
        srcs = srcs,
        deps = proto_deps,
        outs = outs + hdrs,
        proto_include_version = protoc_version,
        protoc = "@com_google_protobuf//:protoc",
        grpc_plugin = "@grpc_repo//:grpc_cpp_plugin" if grpc_support else None,
        target_language = proto_languages.CPP,
        tags = tags,
        target_compatible_with = target_compatible_with,
    )
    native.cc_library(
        name = name,
        srcs = outs + hdrs,
        deps = deps + ["@grpc_repo//:grpc++_unsecure", "@com_google_protobuf//:protobuf"],
        includes = includes,
        visibility = visibility,
        tags = tags,
        hdrs = hdrs,
        copts = select_android(["-std=c++11"], []),
        strip_include_prefix = ".",
        include_prefix = include_prefix,
        target_compatible_with = target_compatible_with,
    )

def maven_proto_library(
        name,
        srcs = None,
        proto_deps = [],
        java_deps = [],
        java_exports = [],
        coordinates = "",
        visibility = None,
        grpc_support = False,
        protoc_version = PROTOC_VERSION,
        protoc_grpc_version = PROTOC_GRPC_VERSION,
        proto_java_runtime_library = ["@maven//:com.google.protobuf.protobuf-java"],
        **kwargs):
    # Targets that require grpc support should specify the version of protoc-gen-grpc-java plugin.
    if grpc_support and not protoc_grpc_version:
        fail("grpc support was requested, but the version of grpc java protoc plugin was not specified")

    srcs_name = name + "_srcs"
    outs = [srcs_name + ".srcjar"]
    _gen_proto_rule(
        name = srcs_name,
        srcs = srcs,
        deps = proto_deps + ["//tools/base/bazel:common-java_proto_srcs"],
        outs = outs,
        proto_include_version = protoc_version,
        protoc = "@//prebuilts/tools/common/m2:com.google.protobuf.protoc." + protoc_version + "_exe",
        grpc_plugin =
            "@//prebuilts/tools/common/m2:io.grpc.protoc-gen-grpc-java." + protoc_grpc_version + "_exe" if grpc_support else None,
        target_language = proto_languages.JAVA,
        visibility = visibility,
    )

    grpc_extra_deps = ["@maven//:javax.annotation.javax.annotation-api"]  # CompileOnly should not make it into the pom
    java_deps = list(java_deps) + (grpc_extra_deps if grpc_support else [])
    java_deps += proto_java_runtime_library

    if coordinates:
        maven_library(
            name = name,
            srcs = outs,
            deps = java_deps,
            exports = java_exports,
            coordinates = coordinates,
            visibility = visibility,
            **kwargs
        )
    else:
        native.java_library(
            name = name,
            srcs = outs,
            deps = java_deps,
            exports = java_exports,
            javacopts = kwargs.pop("javacopts", []) + ["--release", "8"],
            visibility = visibility,
            **kwargs
        )
