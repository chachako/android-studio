""" Rule to extract Lint checks from .aar file, to be used outside of Android projects """

def _aar_lint_impl(ctx):
    aar = None
    for f in ctx.files.dep:
        if f.short_path.endswith("aar"):
            aar = f
            break

    if aar == None:
        fail("Could not locate .arr in %s" % ctx.attr.dep.label)

    ctx.actions.run_shell(
        mnemonic = "zipper",
        tools = [ctx.executable._zipper],
        inputs = [aar],
        outputs = [ctx.outputs.jar],
        progress_message = "Extracting lint.jar ...",
        command = "mkdir {name}_aar && {zipper} x  {aar} -d {name}_aar lint.jar && cp {name}_aar/lint.jar {lint_jar_output}".format(
            aar = aar.path,
            name = ctx.label.name,
            lint_jar_output = ctx.outputs.jar.path,
            zipper = ctx.executable._zipper.path,
        ),
    )

    return [
        JavaInfo(
            output_jar = ctx.outputs.jar,
            compile_jar = ctx.outputs.jar,
        ),
        DefaultInfo(files = depset([ctx.outputs.jar]), data_runfiles = ctx.runfiles(files = [ctx.outputs.jar])),
    ]

aar_lint = rule(
    attrs = {
        "dep": attr.label(mandatory = True),
        "_zipper": attr.label(
            cfg = "exec",
            default = Label("@bazel_tools//tools/zip:zipper"),
            executable = True,
        ),
    },
    outputs = {
        "jar": "%{name}.jar",
    },
    implementation = _aar_lint_impl,
    provides = [JavaInfo],
)
