#!/bin/bash

# Creates the directory if it does not exist and returns its absolute path
function make_target_dir() {
  mkdir -p "$1" && cd "$1" && pwd
}

declare -r top=$(pwd)
declare -r out_dir=$(make_target_dir "$1")
declare -r dist_dir=$(make_target_dir "$2")
declare -r build_number="$3"

# Build fsNotifier
(
    mkdir $out_dir/fsNotifier && cd $out_dir/fsNotifier
    cp -r $top/tools/idea/native/fsNotifier/linux/. ./
    ./make.sh
    cp fsnotifier $dist_dir/.
)

echo "Done Building IntelliJ Linux Tools!"