load("//tools/base/bazel:merge_archives.bzl", "merge_jars")

# Usage:
# jarjar(
#   name = <the name of the rule. The output of the rule will be ${name}.jar.
#   srcs = <a list of all the jars to jarjar and include into the output jar>
#   rules = <the rule file to apply>
#   rename_services = <whether to also apply jarjar rules to META-INF/services>
# )

# This Implementation is combination of
# 1. bazel-common/jarjar - that could merge multiple jars together
#    https://github.com/google/bazel-common/tree/master/tools/jarjar
#
# 2. android jarjar fork - that support java 17
#    https://android.googlesource.com/platform/external/jarjar/+/refs/heads/master
#
# 3. previous Android Studio's jarjar rules - that could rename services META-INF/services
#
#
# TODO: - combine all 3 executable together into single tool
#         (either by adding missing features to jarjar, or by adding combining wrapper)
#       - publish this bazel rule to android.googlesource.com/platform/external/jarjar
#

def jarjar(name, src_jars, manifest_lines = [], **kwargs):
    # 1. Combine multiple jars into one if nessesary
    if len(src_jars) == 1 and not manifest_lines:
        src_jar = src_jars[0]
    else:
        src_jar = "jarjar_" + name + "_combined.jar"
        merge_jars(
            name = name + "_merge_jars",
            jars = src_jars,
            manifest_lines = manifest_lines,
            out = src_jar,
            allow_duplicates = True,
        )

    _jarjar(
        name = name,
        src_jar = src_jar,
        **kwargs
    )

def _jarjar_impl(ctx):
    name = ctx.label.name
    rule_file = ctx.file.rule_file
    src_jar = ctx.file.src_jar
    if rule_file != None and ctx.attr.rule_text != []:
        fail("rule_file and rule_text are mutually exclusive")
    if rule_file == None and ctx.attr.rule_text != []:
        rule_file = ctx.actions.declare_file("jarjar_" + name + "_rules.txt")
        ctx.actions.write(
            output = rule_file,
            content = "\n".join(ctx.attr.rule_text),
        )

    # 2. Run jarjar rules on combined jar
    shaded_jar = ctx.actions.declare_file("jarjar_" + name + "_shaded.jar")
    args = ctx.actions.args()
    args.add("process")
    args.add(rule_file)
    args.add(src_jar)
    args.add(shaded_jar)
    ctx.actions.run(
        inputs = [rule_file, src_jar],
        outputs = [shaded_jar],
        executable = ctx.executable._jarjar,
        progress_message = "jarjar %s" % ctx.label,
        arguments = [args],
    )

    output_jar = ctx.actions.declare_file(name + ".jar")

    # 3. Remane services in META-INF if requested
    if not ctx.attr.rename_services:
        ctx.actions.symlink(output = output_jar, target_file = shaded_jar)
    else:
        renamed_services_jar = ctx.actions.declare_file("jarjar_" + name + "_renamed_services.jar")
        args = ctx.actions.args()
        args.add("--rules").add(rule_file)
        args.add("--in").add(shaded_jar)
        args.add("--out").add(renamed_services_jar)
        ctx.actions.run(
            inputs = [rule_file, shaded_jar],
            outputs = [renamed_services_jar],
            executable = ctx.executable._rename_jar_services,
            progress_message = "jarjar rename services %s" % ctx.label,
            arguments = [args],
        )
        ctx.actions.symlink(output = output_jar, target_file = renamed_services_jar)

    return [
        JavaInfo(
            output_jar = output_jar,
            compile_jar = output_jar,
        ),
        DefaultInfo(files = depset([output_jar]), data_runfiles = ctx.runfiles(files = [output_jar])),
    ]

_jarjar = rule(
    attrs = {
        "src_jar": attr.label(mandatory = True, allow_single_file = True),
        "rule_file": attr.label(allow_single_file = True),
        "rule_text": attr.string_list(),
        "rename_services": attr.bool(),
        "manifest_lines": attr.string_list(),
        "_jarjar": attr.label(executable = True, cfg = "exec", default = "@jar_jar//:jarjar"),
        "_rename_jar_services": attr.label(executable = True, cfg = "exec", default = "//tools/base/bazel/jarjar:rename_jar_services"),
    },
    implementation = _jarjar_impl,
)
