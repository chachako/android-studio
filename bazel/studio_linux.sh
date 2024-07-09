#!/bin/bash -x
#
# Build and test a set of targets via bazel using RBE.

TARGET_NAME=studio-linux
if [[ " $@ " =~ " --very_flaky " ]]; then
  TARGET_NAME=studio-linux_very_flaky
fi

"$(dirname $0)"/ci/ci $TARGET_NAME
