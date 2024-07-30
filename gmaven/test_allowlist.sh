#!/bin/bash

cp $2 "${TEST_UNDECLARED_OUTPUTS_DIR}/maven-artifacts.txt"

output="$(diff $1 $2)"
if [[ "$?" -ne 0 ]]; then
  >&2 echo "Diff detected in maven artifacts!"
  >&2 echo "${output}"
  >&2 echo "-"
  >&2 echo "IMPORTANT: New artifacts must be added to"
  >&2 echo "http://google3/configs/production/pod/android-devtools-infra/admrs/service-config/admrs_config.textproto"
  >&2 echo " See go/gmaven#registering-new-artifacts for more details."
  >&2 echo "-"
  >&2 echo "Run the following command to update the allowlist:"
  >&2 echo " bazel run //tools/base/gmaven:update-allowlist"
  exit 1
fi