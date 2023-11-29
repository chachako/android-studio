#!/bin/bash
# Copies files while in the workspace directory.
# This script must be run using a sh_binary() rule, and
# using `bazel run :binary`

cd "${BUILD_WORKSPACE_DIRECTORY}"
echo "Running: cp ${@}"
cp "${@}"
