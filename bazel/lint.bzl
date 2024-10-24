"""This module implements the lint_test rule."""

script_template = """\
:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

{binary} {xml} {baseline} {extraArgs}
exit $?
:CMDSCRIPT

{win_binary} {win_xml} {win_baseline} {extraArgs}
EXIT /B %ERRORLEVEL%
"""

def _lint_test_impl(ctx):
    transitive = []
    for dep in ctx.attr.deps:
        if JavaInfo in dep:
            transitive.append(dep[JavaInfo].transitive_runtime_jars)
            transitive.append(dep[JavaInfo].transitive_compile_time_jars)
    classpath = depset(transitive = transitive)

    # Create project XML:
    project_xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
    project_xml += "<project>\n"

    for jar in ctx.files.custom_rules:
        project_xml += "<lint-checks jar=\"{0}\" />\n".format(jar.short_path)

    for zip in ctx.files.external_annotations:
        project_xml += "<annotations file=\"{0}\" />\n".format(zip.short_path)

    project_xml += "<module name=\"{0}\" android=\"false\">\n".format(ctx.label.name)

    for file in ctx.files.srcs:
        project_xml += "  <src file=\"{0}\" ".format(file.path)
        if ctx.attr.is_test_sources:
            project_xml += "test=\"true\" "
        project_xml += "/>\n"

    for file in classpath.to_list():
        project_xml += "  <classpath jar=\"{0}\" />\n".format(file.short_path)

    project_xml += "</module>\n"
    project_xml += "</project>\n"

    ctx.actions.write(output = ctx.outputs.project_xml, content = project_xml)

    # Create the launcher script:
    ctx.actions.write(
        output = ctx.outputs.launcher_script,
        content = script_template.format(
            binary = ctx.executable._binary.short_path,
            win_binary = ctx.executable._binary.short_path.replace("/", "\\"),
            xml = ctx.outputs.project_xml.short_path,
            win_xml = ctx.outputs.project_xml.short_path.replace("/", "\\"),
            baseline = "--lint-baseline " + ctx.file.baseline.path if ctx.file.baseline else "",
            win_baseline = "--lint-baseline " + ctx.file.baseline.path.replace("/", "\\") if ctx.file.baseline else "",
            extraArgs = " ".join(ctx.attr.extra_args),
        ),
        is_executable = True,
    )

    # Compute runfiles:
    runfiles = ctx.runfiles(
        files = (
            [ctx.outputs.project_xml] +
            ([ctx.file.baseline] if ctx.file.baseline else []) +
            ctx.files.srcs +
            ctx.files.custom_rules +
            ctx.files.external_annotations
        ),
        transitive_files = depset(
            transitive = [
                ctx.attr._binary[DefaultInfo].default_runfiles.files,
                classpath,
            ],
        ),
    )

    return [DefaultInfo(executable = ctx.outputs.launcher_script, runfiles = runfiles)]

lint_test = rule(
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "custom_rules": attr.label_list(allow_files = True),
        "external_annotations": attr.label_list(allow_files = True),
        "deps": attr.label_list(allow_files = True),
        "baseline": attr.label(allow_single_file = True),
        "is_test_sources": attr.bool(),
        "extra_args": attr.string_list(),
        "_binary": attr.label(
            executable = True,
            cfg = "target",
            default = Label("//tools/base/bazel:BazelLintWrapper"),
        ),
    },
    outputs = {
        "launcher_script": "%{name}.cmd",
        "project_xml": "%{name}_project.xml",
    },
    implementation = _lint_test_impl,
    test = True,
)
