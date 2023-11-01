"""Aspect to validate exec_properties."""

LARGE_MACHINE_ALLOWLIST = [
    # Issue b/228456598
    # These targets requires a large (min 16 GB) amount of memory to run.
    "@//tools/adt/idea/sync-memory-tests:intellij.android.sync-memory-tests_tests__Benchmark2000Memory",
    "@//tools/adt/idea/sync-memory-tests:intellij.android.sync-memory-tests_tests__Benchmark2000Cpu",
    "@//tools/adt/idea/sync-memory-tests:intellij.android.sync-memory-tests_tests__BenchmarkMultiApp190Memory",
    "@//tools/adt/idea/sync-memory-tests:intellij.android.sync-memory-tests_tests__BenchmarkMultiApp190Cpu",
]

LARGE_MACHINE_FAILURE_MESSAGE = """'{}' is trying to use large machines.
Only approved targets can depend on large machine types.
If this is intentional, contact android-devtools-infra@ to approve the target."""

def _limit_exec_properties_impl(target, ctx):
    _ = target  # unused  # buildifier: disable=unused-variable
    if not hasattr(ctx.rule.attr, "exec_properties"):
        return []
    if not ctx.rule.attr.exec_properties:
        return []
    if "manual" in ctx.rule.attr.tags:
        return []
    _check_machine_size(str(ctx.label), ctx.rule.attr.exec_properties)
    return []

def _check_machine_size(label, exec_properties):
    machine_size = exec_properties.get("label:machine-size")
    if machine_size == "large" and label not in LARGE_MACHINE_ALLOWLIST:
        fail(LARGE_MACHINE_FAILURE_MESSAGE.format(label))

limit_exec_properties = aspect(
    implementation = _limit_exec_properties_impl,
    attr_aspects = [],
)
