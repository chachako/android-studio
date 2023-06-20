#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

if [[ $build_number =~ ^[0-9]+$ ]];
then
  IS_POST_SUBMIT=true
fi

declare -a conditional_flags
if [[ $IS_POST_SUBMIT ]]; then
  conditional_flags+=(--nocache_test_results)
fi

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

# Invocation ID must be lower case in Sponge URL
readonly invocation_id=$(uuidgen | tr A-F a-f)

# TODO(b/267533769) Add --config=rcache once RBE creds are available
readonly config_options="--config=release --config=ants"

"${script_dir}/bazel" \
        --max_idle_secs=60 \
        --output_base="${TMPDIR}" \
        test \
        ${config_options} \
        --invocation_id=${invocation_id} \
        --build_metadata=ab_target=studio-mac \
        --build_metadata=ab_build_id=${build_number} \
        --build_tag_filters=-no_mac \
        --test_tag_filters=-no_mac,-no_test_mac,-ui_test,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate \
        --tool_tag=${script_name} \
        --worker_quit_after_build \
        "${conditional_flags[@]}" \
        -- \
        //tools/base/bazel/... \
        //tools/base/dynamic-layout-inspector/skia:skiaparser \
        //tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/...

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  # info breaks if we pass --config=local or --config=rcache because they don't
  # affect info, so we need to pass only --config=release here in order to fetch the proper
  # binaries
  readonly bin_dir="$("${script_dir}"/bazel info --config=release bazel-bin)"
  cp -a ${bin_dir}/tools/base/dynamic-layout-inspector/skia/skiaparser.zip ${dist_dir}
  cp -a ${bin_dir}/tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon ${dist_dir}
  cp -a ${bin_dir}/tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/libjni_object_tagger.dylib ${dist_dir}
  #echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${dist_dir}"/sponge_test_results.html
fi

# TODO(b/267533769) Once we can upload test results, remove early exit
# and conditionally exit based on test status
exit $bazel_status

BAZEL_EXITCODE_TEST_FAILURES=3

# For post-submit builds, if the tests fail we still want to report success
# otherwise ATP will think the build failed and there are no tests. b/152755167
if [[ $IS_POST_SUBMIT && $bazel_status == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $bazel_status
fi
