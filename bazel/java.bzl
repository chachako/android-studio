""" Java rules, that allow to specify target JDK version

Temporary solution until java rules suppor pinning java versions nativly (presumably by transitions)

Minimalistic implementation, to keep support complexity low.
Please comment on b/260924999 if your use case in not supported.
"""

load(":merge_archives.bzl", "run_singlejar")
load(":functions.bzl", "create_option_file")
load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_toolchain")

# Client list. When adding new dependence, please create a tracking bug describing:
# - why target need a custom java version target
# - condition when it could be switched to default
# - test target that verify bytecode version/sdk compatibility
clients = [
    "//tools/data-binding:",  # b/260925941
]

def java_pinned_library(name, java_language_version, srcs, resources = [], resource_strip_prefix = None, deps = []):
    """Compiles sources into a .jar file with specified bytecode version

    Arguments apart from `java_language_version` have same meaning as in java_library rule

    Args:
      java_language_version: Bytecode version to compile to. Possible values: "8", "11", "17"
      name: A unique name for this target.
      srcs: The list of source files that are processed to create the target.
      resources: A list of data files to include in a Java jar.
      resource_strip_prefix: The path prefix to strip from Java resources.
      deps: The list of libraries to link into this library
   """
    _java_library(
        name = name,
        java_language_version = java_language_version,
        srcs = srcs,
        resources = resources,
        resource_strip_prefix = resource_strip_prefix,
        deps = deps,
    )

def _resources(ctx):
    """sets up an action to build a resource jar for the target being compiled.

    Returns: The file resource jar file.
    """
    resources = ctx.files.resources
    resources_jar = ctx.actions.declare_file(ctx.label.name + "-resources.jar")
    prefix = ctx.attr.resource_strip_prefix
    rel_paths = []
    for res in resources:
        short = res.short_path
        if not short.startswith(prefix):
            fail("Resource file %s is not under the specified prefix to strip" % short)
        short = short[len(prefix):]
        if short.startswith("/"):
            short = short[1:]
        rel_paths.append((short, res))

    zipper_files = "".join([k + "=" + v.path + "\n" for k, v in rel_paths])
    zipper_list = create_option_file(ctx, "%s_resources_zipper_args" % ctx.label.name, zipper_files)
    ctx.actions.run(
        inputs = resources + [zipper_list],
        outputs = [resources_jar],
        executable = ctx.executable._zipper,
        arguments = ["c", resources_jar.path, "@" + zipper_list.path],
        progress_message = "Creating resources %s (%d files)" % (resources_jar.short_path, len(resources)),
        mnemonic = "zipper",
    )
    return resources_jar

def _java_library_impl(ctx):
    # TODO replace with load visibility check after Bazel upgraded to 6.0
    target_allowed = False
    for client in clients:
        if str(ctx.label).startswith(client):
            target_allowed = True

    if not target_allowed:
        fail("%s is not allowed. See //tools/base/bazel/java.bzl for details." % ctx.label)

    output_jar = ctx.outputs.jar
    srcs = ctx.files.srcs
    resources = ctx.files.resources
    jars = []
    deps = [dep[JavaInfo] for dep in ctx.attr.deps]

    # use zipper to strip resource prefix
    if resources and ctx.attr.resource_strip_prefix:
        jars.append(_resources(ctx))
        resources = []

    java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain)
    java_jar = ctx.actions.declare_file(ctx.label.name + ".java.jar")
    java_common.compile(
        ctx,
        source_files = srcs,
        resources = resources,
        output = java_jar,
        deps = deps,
        javac_opts = java_common.default_javac_opts(java_toolchain = java_toolchain),
        java_toolchain = java_toolchain,
    )
    jars.append(java_jar)

    run_singlejar(
        ctx = ctx,
        jars = jars,
        out = ctx.outputs.jar,
    )

    return [JavaInfo(
        output_jar = output_jar,
        compile_jar = output_jar,
        deps = deps,
    )]

def _java_version_transition_impl(settings, attr):
    """ Get java_language_version rule attribute value, and set --java_language_version option"""
    return {"//command_line_option:java_language_version": attr.java_language_version}

_java_version_transition = transition(
    implementation = _java_version_transition_impl,
    inputs = [],  # Transition don't depends on anything from current configuration
    outputs = ["//command_line_option:java_language_version"],  # overrides one command-line option
)

_java_library = rule(
    implementation = _java_library_impl,
    attrs = {
        "java_language_version": attr.string(
            mandatory = True,
            doc = """Java bytecode version to compile to.

            Target indirectly transitions to the differen (than parent target) java toolchain.
            bazel --java_language_version option is updated from rule attribute value,
            after on toolchain resolution phase, a java toolchain would be selected as if target is building
            with `bazel build --java_language_version=X  //...` command
            """,
        ),
        #Bazel allowlist to be able to use transitions
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
        "deps": attr.label_list(providers = [[JavaInfo]]),
        "srcs": attr.label_list(allow_files = [".java"]),
        "resources": attr.label_list(allow_files = True),
        "resource_strip_prefix": attr.string(),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_toolchain")),
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
    cfg = _java_version_transition,
    fragments = ["java"],
    outputs = {
        "jar": "%{name}.jar",
    },
)
