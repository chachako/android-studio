"""Module for running bazel-diff."""

import pathlib
from typing import List

from tools.base.bazel.ci import bazel


def generate_hash_file(build_env: bazel.BuildEnv, output_path: pathlib.Path):
  """Generates the hash file for the current build."""
  build_env.bazel_run(
      '//tools/base/bazel:bazel-diff',
      '--',
      '--verbose',
      'generate-hashes',
      '--bazelPath',
      build_env.bazel_path,
      '--workspacePath',
      build_env.workspace_dir,
      str(output_path),
  )


def get_impacted_targets(
    build_env: bazel.BuildEnv,
    starting_hashes_path: str,
    final_hashes_path: str,
    output_path: pathlib.Path,
) -> None:
  """Generates the list of impacted targets given base and current hash file paths."""
  build_env.bazel_run(
      '//tools/base/bazel:bazel-diff',
      '--',
      '--verbose',
      'get-impacted-targets',
      '--startingHashes',
      starting_hashes_path,
      '--finalHashes',
      final_hashes_path,
      '--output',
      str(output_path),
  )