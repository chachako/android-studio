"""This module implements kotlin rules."""

load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_toolchain")
load(":coverage.bzl", "coverage_baseline", "coverage_java_test")
load(":functions.bzl", "create_option_file")
load(":lint.bzl", "lint_test")
load(":merge_archives.bzl", "run_singlejar")

def kotlin_compile(ctx, name, srcs, deps, friend_jars, out, out_ijar, java_runtime, kotlinc_opts, transitive_classpath):
    """Runs kotlinc on the given source files.

    Args:
        ctx: the analysis context
        name: the name of the module being compiled
        srcs: a list of Java and Kotlin source files
        deps: a list of JavaInfo providers from direct dependencies
        friend_jars: a list of friend jars (allowing access to 'internal' members)
        out: the output jar file
        out_ijar: the output ijar file or None to disable ijar creation
        java_runtime: a JavaRuntimeInfo provider corresponding to the target JVM
        kotlinc_opts: list of additional flags to pass to the Kotlin compiler
        transitive_classpath: whether to include transitive deps in the compile classpath

    Returns:
        JavaInfo for the resulting jar.

    Expects that ctx.executable._kotlinc and ctx.file._jvm_abi_gen are defined.

    Note: kotlinc only compiles Kotlin, not Java. So if there are Java
    sources, then you will also need to run javac after this action.
    """

    # TODO: Either disable transitive_classpath in all cases, or otherwise
    # implement strict-deps enforcement for Kotlin to ensure that targets
    # declare dependencies on everything they directly use.
    merged_deps = java_common.merge(deps)
    if transitive_classpath:
        merged_deps = java_common.make_non_strict(merged_deps)
    classpath = merged_deps.compile_jars

    args = ctx.actions.args()

    args.add("-module-name", name)
    args.add("-nowarn")  # Mirrors the default javac opts.
    args.add("-api-version", "1.8")
    args.add("-language-version", "1.8")
    args.add("-Xjvm-default=all-compatibility")
    args.add("-no-stdlib")
    args.add("-Xsam-conversions=class")  # Needed for Gradle configuration caching (see b/202512551).

    tools = []
    tools.append(ctx.file._jvm_abi_gen)
    if out_ijar:
        args.add(ctx.file._jvm_abi_gen, format = "-Xplugin=%s")
        args.add("-P", out_ijar, format = "plugin:org.jetbrains.kotlin.jvm.abi:outputDir=%s")

    # Dependency jars may be compiled with a new kotlinc IR backend.
    args.add("-Xallow-unstable-dependencies")

    # Use the Compiler Compose plugin
    tools.append(ctx.file._compose_plugin)
    if ctx.attr.kotlin_use_compose:
        args.add(ctx.file._compose_plugin, format = "-Xplugin=%s")
        args.add("-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true")

    # Use custom JRE instead of the default one picked by kotlinc.
    args.add("-jdk-home", java_runtime.java_home)

    # Note: there are some open questions regarding the transitivity of friends.
    # See https://github.com/bazelbuild/rules_kotlin/issues/211.
    args.add_joined(friend_jars, join_with = ",", format_joined = "-Xfriend-paths=%s")

    args.add_joined("-cp", classpath, join_with = ctx.configuration.host_path_separator)
    args.add("-d", out)
    args.add_all(srcs)

    # Add custom kotlinc options last so that they override the global options.
    args.add_all(kotlinc_opts)

    # To enable persistent Bazel workers, all arguments must come in an argfile.
    args.use_param_file("@%s", use_always = True)
    args.set_param_file_format("multiline")
    ctx.actions.run(
        inputs = depset(direct = srcs, transitive = [classpath, java_runtime.files]),
        outputs = [out, out_ijar] if out_ijar else [out],
        tools = tools,
        mnemonic = "kotlinc",
        arguments = [args],
        executable = ctx.executable._kotlinc,
        execution_requirements = {"supports-multiplex-workers": "1"},
    )

    return JavaInfo(output_jar = out, compile_jar = out_ijar or out)

def kotlin_test(
        name,
        srcs,
        deps = [],
        runtime_deps = [],
        friends = [],
        kotlinc_opts = [],
        visibility = None,
        lint_baseline = None,
        lint_classpath = [],
        lint_enabled = True,
        javacopts = [],
        **kwargs):
    kotlin_library(
        name = name + ".testlib",
        srcs = srcs,
        deps = deps,
        testonly = True,
        runtime_deps = runtime_deps,
        jar = name + ".jar",
        lint_enabled = lint_enabled,
        lint_baseline = lint_baseline,
        lint_classpath = lint_classpath,
        lint_is_test_sources = True,
        visibility = visibility,
        friends = friends,
        kotlinc_opts = kotlinc_opts,
        javacopts = javacopts,
    )

    coverage_java_test(
        name = name + ".test",
        runtime_deps = [
            ":" + name + ".testlib",
        ] + runtime_deps,
        visibility = visibility,
        **kwargs
    )

    native.test_suite(
        name = name,
        tests = [name + ".test"],
    )

# Creates actions to generate the sources jar
def _sources(ctx, srcs, source_jars, jar, java_toolchain):
    java_common.pack_sources(
        ctx.actions,
        output_source_jar = jar,
        sources = srcs,
        source_jars = source_jars,
        java_toolchain = java_toolchain,
    )

# Creates actions to generate a resources_jar from the given resources.
def _resources(ctx, resources, notice, resources_jar):
    prefix = ctx.attr.resource_strip_prefix
    rel_paths = []
    if notice:
        rel_paths.append((notice.basename, notice))
    for res in resources:
        short = res.short_path
        if short.startswith(prefix):
            short = short[len(prefix):]
            if short.startswith("/"):
                short = short[1:]
        rel_paths.append((short, res))
    zipper_args = ["cC" if ctx.attr.compress_resources else "c", resources_jar.path]
    zipper_files = "".join([k + "=" + v.path + "\n" for k, v in rel_paths])
    zipper_list = create_option_file(ctx, resources_jar.basename + ".res.lst", zipper_files)
    zipper_args.append("@" + zipper_list.path)
    ctx.actions.run(
        inputs = resources + ([notice] if notice else []) + [zipper_list],
        outputs = [resources_jar],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating resources %s (%d files)" % (resources_jar.short_path, len(resources)),
        mnemonic = "zipper",
    )

def kotlin_library(
        name,
        srcs,
        deps = None,
        javacopts = [],
        kotlinc_opts = [],
        lint_enabled = True,
        lint_baseline = None,
        lint_classpath = [],
        lint_is_test_sources = False,
        lint_timeout = None,
        compress_resources = False,
        testonly = False,
        stdlib = "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        kotlin_use_compose = False,
        coverage_baseline_enabled = True,
        **kwargs):
    """Compiles a library jar from Java and Kotlin sources

    Args:
        name: The sources of the library.
        srcs: The sources of the library.
        deps: The dependencies of this library.
        javacopts: Additional javac options.
        kotlinc_opts: Additional kotlinc options.
        lint_enabled: enable or disable Lint checks
        lint_baseline: See impl.
        lint_classpath: See impl.
        lint_is_test_sources: See impl.
        lint_timeout: See impl.
        compress_resources: Whether to compress resources.
        testonly: See impl.
        kotlin_use_compose:  See impl.
        stdlib: See impl.
        coverage_baseline_enabled: whether to generate the coverage baseline
        **kwargs: arguments to pass through to _kotlin_library
    """

    # Target 11 explicitly to avoid JDK updates impacting generated bytecode (b/166472930).
    javacopts = ["--release", "11"] + javacopts
    kotlinc_opts = ["-jvm-target", "11"] + kotlinc_opts

    # Include non-test kotlin libraries in coverage
    if coverage_baseline_enabled and not testonly:
        coverage_baseline(
            name = name,
            srcs = srcs,
        )

    jar = kwargs.pop("jar", "lib" + name + ".jar")
    _kotlin_library(
        name = name,
        srcs = srcs,
        jar = jar,
        deps = deps,
        compress_resources = compress_resources,
        kotlin_use_compose = kotlin_use_compose,
        javacopts = javacopts,
        kotlinc_opts = kotlinc_opts,
        testonly = testonly,
        stdlib = stdlib,
        **kwargs
    )

    # TODO move lint tests out of here
    if lint_baseline and not srcs:
        fail("lint_baseline set for rule that has no sources")

    if lint_enabled and srcs:
        lint_test(
            name = name + "_lint_test",
            srcs = srcs,
            baseline = lint_baseline,
            deps = (deps or []) + (lint_classpath or []),
            custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"],
            tags = ["no_windows"],
            is_test_sources = lint_is_test_sources,
            timeout = lint_timeout if lint_timeout else None,
        )

def _kotlin_library_impl(ctx):
    kotlin_srcs = []
    java_srcs = []
    source_jars = []
    for src in ctx.files.srcs:
        if src.path.endswith(".kt"):
            kotlin_srcs.append(src)
        elif src.path.endswith(".java"):
            java_srcs.append(src)
        elif src.path.endswith(".srcjar"):
            source_jars.append(src)
        else:
            fail("Unexpected file type passed to kotlin_library target: " + src.path)

    name = ctx.label.name

    java_jar = ctx.actions.declare_file(name + ".java.jar") if java_srcs or source_jars else None
    kotlin_jar = ctx.actions.declare_file(name + ".kotlin.jar") if kotlin_srcs else None
    kotlin_ijar = ctx.actions.declare_file(name + ".kotlin-ijar.jar") if kotlin_srcs else None
    full_ijar = ctx.actions.declare_file(name + ".merged-ijar-kotlin-lib.jar")

    deps = [dep[JavaInfo] for dep in ctx.attr.deps + ctx.attr.exports]
    java_info_deps = [dep[JavaInfo] for dep in ctx.attr.deps]

    # Kotlin
    jars = []
    ijars = []
    kotlin_providers = []

    if kotlin_srcs:
        if ctx.attr.stdlib:
            deps.append(ctx.attr.stdlib[JavaInfo])
            java_info_deps.append(ctx.attr.stdlib[JavaInfo])
        friend_jars = []
        for friend in ctx.attr.friends:
            friend_jars += friend[JavaInfo].compile_jars.to_list()

        kotlin_providers.append(kotlin_compile(
            ctx = ctx,
            name = ctx.attr.module_name,
            srcs = java_srcs + kotlin_srcs,
            deps = deps,
            friend_jars = friend_jars,
            out = kotlin_jar,
            out_ijar = kotlin_ijar,
            java_runtime = ctx.attr._kt_java_runtime[java_common.JavaRuntimeInfo],
            kotlinc_opts = ctx.attr.kotlinc_opts,
            transitive_classpath = True,  # Matches Java rules (sans strict-deps enforcement)
        ))
        jars.append(kotlin_jar)
        ijars.append(kotlin_ijar)

    # Resources.
    # We only add Resources to the output jar, but exclude them from the compile_jar.
    if ctx.files.resources or ctx.files.notice:
        resources_jar = ctx.actions.declare_file(name + ".res.jar")
        _resources(ctx, ctx.files.resources, ctx.file.notice, resources_jar)
        jars.append(resources_jar)

    java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain)

    # Java
    if java_srcs or source_jars:
        java_jar = ctx.actions.declare_file(name + ".java.jar")
        java_provider = java_common.compile(
            ctx,
            source_files = java_srcs,
            source_jars = source_jars,
            output = java_jar,
            deps = deps + kotlin_providers,
            javac_opts = java_common.default_javac_opts(java_toolchain = java_toolchain) + ctx.attr.javacopts,
            java_toolchain = java_toolchain,
            plugins = [plugin[JavaPluginInfo] for plugin in ctx.attr.plugins],
        )

        jars.append(java_jar)
        ijars += java_provider.compile_jars.to_list()

    run_singlejar(
        ctx = ctx,
        jars = jars,
        out = ctx.outputs.jar,
        # allow_duplicates = True,  # TODO: Ideally we could be more strict here.
    )

    run_singlejar(
        ctx = ctx,
        jars = ijars,
        out = full_ijar,
        # Even if there are no duplicates in the jars, there might be duplicates in the ijars.
        # For example, protoc adds a stripped version of its runtime dependency under
        # META-INF/TRANSITIVE, which results in duplicates for multiple proto dependencies.
        allow_duplicates = True,
    )

    _sources(ctx, java_srcs + kotlin_srcs, source_jars, ctx.outputs.source_jar, java_toolchain)

    java_info = JavaInfo(
        output_jar = ctx.outputs.jar,
        compile_jar = full_ijar or ctx.outputs.jar,
        source_jar = ctx.outputs.source_jar,
        deps = java_info_deps,
        exports = [dep[JavaInfo] for dep in ctx.attr.exports],
        runtime_deps = java_info_deps,
    )

    transitive_runfiles = depset(transitive = [
        dep[DefaultInfo].default_runfiles.files
        for dep in ctx.attr.deps + ctx.attr.exports
        if dep[DefaultInfo].default_runfiles
    ])
    runfiles = ctx.runfiles(files = ctx.files.data, transitive_files = transitive_runfiles)
    return [
        java_info,
        DefaultInfo(files = depset([ctx.outputs.jar]), runfiles = runfiles),
    ]

_kotlin_library = rule(
    attrs = {
        "srcs": attr.label_list(allow_files = [".kt", ".java", ".srcjar"]),
        "resources": attr.label_list(allow_files = True),
        "notice": attr.label(allow_single_file = True),
        "data": attr.label_list(allow_files = True),
        "friends": attr.label_list(),
        "jar": attr.output(mandatory = True),
        "deps": attr.label_list(providers = [JavaInfo]),
        "exports": attr.label_list(providers = [JavaInfo]),
        "runtime_deps": attr.label_list(
            providers = [JavaInfo],
        ),
        "module_name": attr.string(
            default = "unnamed",
        ),
        "resource_strip_prefix": attr.string(),
        "javacopts": attr.string_list(),
        "kotlinc_opts": attr.string_list(),
        "kotlin_use_compose": attr.bool(),
        "compress_resources": attr.bool(),
        "plugins": attr.label_list(
            providers = [JavaPluginInfo],
        ),
        "stdlib": attr.label(),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_toolchain")),
        "_kt_java_runtime": attr.label(
            # We need this to be able to target JRE 11 in Kotlin, because
            # Kotlinc does not support the --release 11 Javac option.
            default = Label("//prebuilts/studio/jdk/jdk11:jdk11_runtime"),
            providers = [java_common.JavaRuntimeInfo],
            cfg = "exec",
        ),
        "_kotlinc": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//tools/base/bazel:kotlinc"),
        ),
        "_jvm_abi_gen": attr.label(
            default = Label("//prebuilts/tools/common/m2:jvm-abi-gen-plugin"),
            cfg = "exec",
            allow_single_file = [".jar"],
        ),
        "_compose_plugin": attr.label(
            default = Label("//prebuilts/tools/common/m2:compose-compiler-hosted"),
            cfg = "exec",
            allow_single_file = [".jar"],
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "exec",
            executable = True,
        ),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "exec",
            executable = True,
        ),
    },
    fragments = ["java"],
    outputs = {
        "source_jar": "%{name}-src.jar",
    },
    implementation = _kotlin_library_impl,
)
