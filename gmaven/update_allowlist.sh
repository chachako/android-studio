#!/bin/bash
DST="${BUILD_WORKSPACE_DIRECTORY}/tools/base/gmaven/artifacts_allowlist.txt"
cp "${BUILD_WORKSPACE_DIRECTORY}/$1" "${DST}"
# bazel output is executable by default, remove executable bit.
chmod -x "${DST}"