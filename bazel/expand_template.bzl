"""Rule for expansion of template files.

This is a simple rule for making the expand_template action available
as a higher level build rule. See
https://bazel.build/rules/lib/actions#expand_template
"""

def _expand_template_impl(ctx):
    ctx.actions.expand_template(
        template = ctx.file.template,
        output = ctx.outputs.out,
        substitutions = ctx.attr.substitutions,
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
            doc = "A dictionary mapping strings to their substitutions",
            mandatory = True,
        ),
        "out": attr.output(
            doc = "The destination of the expanded file",
            mandatory = True,
        ),
    },
)
