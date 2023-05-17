#!/bin/bash -x
#
# Build a nightly release of Android Studio.

readonly ARGV=("$@")

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
readonly BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"
# AS_BUILD_NUMBER is the same as BUILD_NUMBER but omits the P for presubmit,
# to satisfy Integer.parseInt in BuildNumber.parseBuildNumber
# The "#P" matches "P" only at the beginning of BUILD_NUMBER
readonly AS_BUILD_NUMBER="${BUILD_NUMBER/#P/0}"

readonly SCRIPT_DIR="$(dirname "$0")"
readonly BAZEL="${SCRIPT_DIR}/../bazel"
readonly SCRIPT_NAME="$(basename "$0")"

readonly CONFIG_OPTIONS="--config=ci"

####################################
# Copies bazel artifacts to an output directory named 'artifacts'.
# Globals:
#   DIST_DIR
#   BAZEL
#   CONFIG_OPTIONS
# Arguments:
#   None
####################################
function copy_bazel_artifacts() {(
  set -e
  local -r artifacts_dir="${DIST_DIR}/artifacts"
  mkdir -p ${artifacts_dir}
  local -r bin_dir="$("${BAZEL}" info ${CONFIG_OPTIONS} bazel-bin)"

  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.linux.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.win.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.mac.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.mac_arm.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio_build_manifest.textproto ${artifacts_dir}/android-studio_build_manifest.textproto
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio_update_message.html ${artifacts_dir}/android-studio_update_message.html
  cp -a ${bin_dir}/tools/adt/idea/studio/updater_deploy.jar ${artifacts_dir}/android-studio-updater.jar
  cp -a ${bin_dir}/tools/adt/idea/native/installer/android-studio-bundle-data.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/vendor/google/adrt/android-studio-cros-skeleton.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/vendor/google/adrt/android-studio-nsis-prebuilt.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/vendor/intel/android-studio-intel-haxm.zip ${artifacts_dir}
)}


function run_bazel() {
  local target_name="studio-nightly"
  local version="Nightly $(date +%Y-%m-%d)"

  # Generate a UUID for use as the bazel test invocation id
  local -r invocation_id="$(uuidgen)"

  if [[ -d "${DIST_DIR}" ]]; then
    # Generate a simple html page that redirects to the test results page.
    echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${DIST_DIR}"/upsalite_test_results.html
  fi

  # Run Bazel
  "${BAZEL}" \
    --max_idle_secs=60 \
    build \
    ${CONFIG_OPTIONS} \
    --invocation_id=${invocation_id} \
    --build_metadata=ab_build_id="${BUILD_NUMBER}" \
    --build_metadata=ab_target="${target_name}" \
    --tool_tag=${SCRIPT_NAME} \
    --embed_label="${AS_BUILD_NUMBER}" \
    --//tools/adt/idea/studio:create-nightly-build \
    --//tools/adt/idea/studio:version-suffix="${version}" \
    -- \
    //tools/adt/idea/studio:android-studio \
    //tools/adt/idea/native/installer:android-studio-bundle-data \
    //tools/adt/idea/studio:updater_deploy.jar
}

run_bazel
readonly BAZEL_STATUS=$?

readonly BAZEL_EXITCODE_SUCCESS=0

# Artifacts should only be copied when the build succeeds.
if [[ $BAZEL_STATUS -ne $BAZEL_EXITCODE_SUCCESS ]];
then
  readonly SKIP_BAZEL_ARTIFACTS=1
fi

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then

  if [[ ! $SKIP_BAZEL_ARTIFACTS ]]; then
    copy_bazel_artifacts
    if [[ $? -ne 0 ]]; then
      echo "Failed to copy artifacts!"
      exit 1
    fi
  fi
fi


exit $BAZEL_STATUS
