"""A module providing a BazelCmd object."""
from typing import List
import subprocess
import os


class BuildEnv:
  """Represents the build environment."""
  build_number: str
  build_target_name: str
  dist_dir: str
  tmp_dir: str
  bazel_path: str

  def __init__(self, bazel_path: str):
    self.build_number = os.environ.get("BUILD_NUMBER", "")
    self.build_target_name = os.environ.get("BUILD_TARGET_NAME", "")
    self.dist_dir = os.environ.get("DIST_DIR", "")
    self.tmp_dir = os.environ.get("TMPDIR", "")
    self.bazel_path = bazel_path

  def is_ci(self) -> bool:
    """Returns true if in a continuous integration environment."""
    return self.build_target_name != ""


class BazelCmd:
  """Represents a Bazel command and arguments."""

  build_env: BuildEnv
  startup_options: List[str] = []

  def __init__(self, build_env: BuildEnv):
    self.build_env = build_env

  def query(self, *query_args) -> subprocess.CompletedProcess:
    """Run a 'bazel query' command.

    Raises:
      CalledProcessError: If the query fails.
    """
    args = [self.build_env.bazel_path]
    args.extend(self.startup_options)
    args.append("query")
    args.extend(query_args)

    return subprocess.run(args, capture_output=True, check=True, cwd=os.environ.get('BUILD_WORKSPACE_DIRECTORY'))
