#!/bin/bash

# Deletes orphaned files in //prebuilts/tools/common/m2

set -eu
set -o pipefail

top=$(cd "$(dirname "$0")/../../../.." && pwd -P)
m2=$top/prebuilts/tools/common/m2
BAZEL=$top/tools/base/bazel/bazel
workdir=`mktemp -d`

$BAZEL query 'labels(srcs,  //prebuilts/tools/common/m2/...  except //prebuilts/tools/common/m2:all)' \
    | grep '//prebuilts/tools/common/m2'  \
    | sed "s|//prebuilts/tools/common/m2:repository|$top/prebuilts/tools/common/m2/repository|g" \
    >> $workdir/maven_file_refs.txt

$BAZEL query 'labels(jars,  //prebuilts/tools/common/m2/... except //prebuilts/tools/common/m2:all)' \
    | grep '//prebuilts/tools/common/m2'  \
    | sed "s|//prebuilts/tools/common/m2:repository|$top/prebuilts/tools/common/m2/repository|g" \
    >> $workdir/maven_file_refs.txt  || true

# Collect artifacts from //tools/base/bazel/maven/BUILD.maven
$BAZEL query 'labels(srcs,  deps(@maven//...))' \
    | grep '@maven//:repository'  \
    | sed "s|@maven//:repository|$top/prebuilts/tools/common/m2/repository|g" \
    >> $workdir/maven_file_refs.txt  || true

$BAZEL query 'labels(jars,  deps(@maven//...))' \
    | grep '@maven//:repository'  \
    | sed "s|@maven//:repository|$top/prebuilts/tools/common/m2/repository|g" \
    >> $workdir/maven_file_refs.txt  || true

$BAZEL query 'labels(files,  deps(@maven//...))' \
    | grep '@maven//:repository'  \
    | sed "s|@maven//:repository|$top/prebuilts/tools/common/m2/repository|g" \
    >> $workdir/maven_file_refs.txt  || true

# artifact file -> dir
cat $workdir/maven_file_refs.txt \
    | xargs dirname | sort | uniq \
    | grep -xv "$top/prebuilts/tools/common/m2/repository" \
    | grep -xv "//prebuilts/tools/common" \
    | grep -v "^[[:space:]]*$" \
    > $workdir/maven_dir_refs.txt

echo "Computing orphaned maven artifacts ..."
find $m2/repository -type f \
    | grep -v 'gitignore' \
    | grep -v '\-bom' \
    | grep -vF -f $workdir/maven_dir_refs.txt \
    > $workdir/unused_files.txt

echo "found $(wc -l < $workdir/unused_files.txt) unused files"
echo "Size before: "
du -hs $m2

while read -r file ; do
    rm -r "$file"
done < $workdir/unused_files.txt
find $m2 -empty -type d -delete

echo "Size after: "
du -hs $m2

rm -rf $workdir
echo "Done."
