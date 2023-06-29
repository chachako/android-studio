"""Rule for expansion of template files.

This is a simple rule for making the expand_template action available
as a higher level build rule. See
https://bazel.build/rules/lib/actions#expand_template
"""

def expand_template_ex(
        ctx,
        template,
        out,
        substitutions = {},
        data = [],
        files = []):
    file_substitutions = {}
    args = ctx.actions.args()
    args.add("--template", template)
    args.add("--out", out)
    for k, v in substitutions.items():
        args.add("--replace")
        args.add(k)
        args.add(ctx.expand_location(v, data))
    ctx.actions.run(
        inputs = [template] + files,
        outputs = [out],
        executable = ctx.executable._expander,
        arguments = [args],
        progress_message = "Expanding %s " % out,
        mnemonic = "expander",
    )

def _expand_template_impl(ctx):
    expand_template_ex(
        ctx,
        ctx.file.template,
        ctx.outputs.out,
        ctx.attr.substitutions,
        ctx.attr.data,
        ctx.files.data,
    )

    return DefaultInfo(files = depset([ctx.outputs.out]))

expand_template = rule(
    implementation = _expand_template_impl,
    attrs = {
        "template": attr.label(
            doc = "The template file to expand",
            mandatory = True,
            allow_single_file = True,
        ),
        "substitutions": attr.string_dict(
            doc = """A dictionary mapping strings to their substitutions.
            It supports the notation $(location <target>) for paths, and
            a custom $(inline <path>) to inline files into the result.""",
        ),
        "data": attr.label_list(
            allow_files = True,
            doc = "The files used in $location values.",
        ),
        "out": attr.output(
            doc = "The destination of the expanded file",
            mandatory = True,
        ),
        "_expander": attr.label(
            default = Label("//tools/base/bazel/expander"),
            cfg = "host",
            executable = True,
        ),
    },
)
