#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

# Invocation ID must be lower case in Upsalite URL
readonly invocation_id=$(uuidgen | tr A-F a-f)

# TODO(b/267533769) Add --config=rcache once RBE creds are available
readonly config_options="--config=release --config=ants"
readonly target_filters="qa_smoke,ui_test,-qa_unreliable,-no_mac,-no_test_mac,-requires_emulator"

# Use test strategy to run 1 test at a time after all build dependencies are built
# (b/301490683) Use strategy as local for UI tests to run in mac arm machines
"${script_dir}/../bazel" \
        --max_idle_secs=60 \
	--output_base="${TMPDIR}" \
        test \
        --keep_going \
        ${config_options} \
        --test_strategy=exclusive \
	--strategy=TestRunner=local \
        --invocation_id=${invocation_id} \
        --define=meta_android_build_number=${build_number} \
        --build_metadata=ab_build_id="${build_number}" \
        --build_metadata=ab_target="qa-mac_arm_smoke" \
        --build_tag_filters=${target_filters} \
        --test_tag_filters=${target_filters} \
        --tool_tag=${script_name} \
        -- \
        //tools/adt/idea/android-uitests/...

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${dist_dir}"/upsalite_test_results.html

  readonly testlogs_dir="$("${script_dir}/../bazel" --output_base="${TMPDIR}" info --config=release bazel-testlogs)"
  mkdir "${dist_dir}"/testlogs
  (mv "${testlogs_dir}"/* "${dist_dir}"/testlogs/)

  echo "Remove any empty file in testlogs"
  find  "${dist_dir}"/testlogs/ -size  0 -print0 |xargs -0 rm --
fi

# TODO(b/267533769) Once we can upload test results, remove early exit
# and conditionally exit based on test status
exit $bazel_status


# See http://docs.bazel.build/versions/master/guide.html#what-exit-code-will-i-get
# Exit with status 0 if all of the above tests' exit codes is 0, 3, or 4.
for test_exit_code in "${bazel_status_no_emu}" "${bazel_status_emu}"; do
  case $test_exit_code in
    [034])
      # Exit code 0: successful test run
      # Exit code 3: tests failed or timed out. We ignore test failures for
      # manual review
      # Exit code 4: No tests found. This can happen if all tests are moved out
      # of the reliable group.
      ;;
    *)
      exit $test_exit_code
  esac
done

exit 0
