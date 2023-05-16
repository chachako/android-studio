load("//tools/base/bazel:merge_archives.bzl", "run_singlejar")

def _merge_deps_impl(ctx):
    jars = []
    for src in ctx.attr.srcs:
        jars.extend(src[JavaInfo].outputs.jars)
    run_singlejar(
        ctx = ctx,
        jars = [java_out.class_jar for java_out in jars],
        out = ctx.outputs.out,
        allow_duplicates = True,
    )

_merge_deps = rule(
    attrs = {
        "srcs": attr.label_list(
            providers = [JavaInfo],
        ),
        "out": attr.output(mandatory = True),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
    },
    implementation = _merge_deps_impl,
)

def merge_deps(name, srcs):
    _merge_deps(
        name = name,
        srcs = srcs,
        out = "%s.jar" % name,
    )
