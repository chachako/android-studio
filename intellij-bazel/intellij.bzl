# A set of rules to develop stand alone IJ plguins.
# This initially is implemented as a bridge to Android Studio rules
load("//tools/adt/idea/studio:studio.bzl", "LINUX", "PluginInfo", "studio_plugin")
load("//tools/base/bazel:functions.bzl", "create_option_file")

def _intellij_plugin_impl(ctx):
    info = ctx.attr.plugin[PluginInfo]
    files = LINUX.get(info.plugin_files)
    map = []
    inputs = []
    for path, file in files.items():
        if not path.startswith("plugins/"):
            error = "File %s, expected to be in the plugins directory." % path
            fail(error)
        path = path[len("plugins/"):]
        map.append((path, file))
        inputs.append(file)

    zipper_files = [r + "=" + (f.path if f else "") + "\n" for r, f in map]
    zipper_list = create_option_file(ctx, ctx.outputs.zip.basename + ".res.lst", "".join(zipper_files))
    ctx.actions.run(
        inputs = inputs + [zipper_list],
        outputs = [ctx.outputs.zip],
        executable = ctx.executable._zipper,
        arguments = ["c", ctx.outputs.zip.path, "@" + zipper_list.path],
        progress_message = "Creating %s zip..." % ctx.attr.name,
        mnemonic = "zipper",
    )

_intellij_plugin = rule(
    attrs = {
        "plugin": attr.label(providers = [PluginInfo]),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {
        "zip": "%{name}.zip",
    },
    implementation = _intellij_plugin_impl,
)

def intellij_plugin(name, plugin_id, **kwargs):
    studio_plugin(
        name = plugin_id,
        **kwargs
    )
    _intellij_plugin(
        name = name,
        plugin = ":%s" % plugin_id,
    )
