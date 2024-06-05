#!/bin/bash -x
#
# Build and test targets using the Kotlin K2 plugin.

export BUILD_KOTLIN_K2=true
exec "$(dirname "$0")"/studio_linux.sh "$@"
