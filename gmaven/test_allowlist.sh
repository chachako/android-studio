#!/bin/bash

cp $2 "${TEST_UNDECLARED_OUTPUTS_DIR}/maven-artifacts.txt"

output="$(diff $1 $2)"
if [[ "$?" -ne 0 ]]; then
  >&2 echo "Diff detected in maven artifacts!"
  >&2 echo "${output}"
  >&2 echo "-"
  >&2 echo "Run the following command to update the allowlist:"
  >&2 echo " bazel run //tools/base/gmaven:update-allowlist"
  exit 1
fi