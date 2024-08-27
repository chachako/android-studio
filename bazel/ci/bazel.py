"""A module providing a BazelCmd object."""

import getpass
import logging
import os
import subprocess
from typing import List


EXITCODE_SUCCESS = 0
EXITCODE_TEST_FAILURES = 3
EXITCODE_NO_TESTS_FOUND = 4


class BuildEnv:
  """Represents the build environment."""

  build_number: str
  build_target_name: str
  workspace_dir: str
  dist_dir: str
  tmp_dir: str
  bazel_path: str
  bazel_version: str
  user: str

  # Startup options for Bazel commands.
  _startup_options: List[str]

  def __init__(self, bazel_path: str, user: str = getpass.getuser(),
               bazel_version: str = ""):
    self.build_number = os.environ.get("BUILD_NUMBER", "SNAPSHOT")
    self.build_target_name = os.environ.get("BUILD_TARGET_NAME", "")
    self.workspace_dir = os.environ.get("BUILD_WORKSPACE_DIRECTORY", "")
    self.dist_dir = os.environ.get("DIST_DIR", "")
    self.tmp_dir = os.environ.get("TMPDIR", "")
    self.bazel_path = os.path.normpath(bazel_path)
    if bazel_version:
      self.bazel_version = bazel_version
    else:
      with open(os.path.join(self.workspace_dir, ".bazelversion")) as f:
        self.bazel_version = f.read()
    self.user = user

    self._startup_options = ["--max_idle_secs=60"]
    if self.is_ab_environment():
      install_base = os.path.join(
          self.tmp_dir, "bazel_install", self.bazel_version)
      self._startup_options.extend([
          f"--output_base={os.path.join(self.tmp_dir, 'bazel_out')}",
          f"--install_base={install_base}",
      ])

  def is_ab_environment(self) -> bool:
    """Returns true if on an Android Build machine."""
    return self.build_target_name and self.user == "android-build"

  def bazel_build(self, *build_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel build' command."""
    return self._bazel(True, False, "build", *build_args)

  def bazel_test(self, *test_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel test' command."""
    return self._bazel(False, False, "test", *test_args)

  def bazel_run(self, *run_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel run' command.

    Raises:
      CalledProcessError: If the command fails.
    """
    return self._bazel(False, True, "run", *run_args)

  def bazel_query(self, *query_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel query' command.

    Raises:
      CalledProcessError: If the query fails.
    """
    return self._bazel(True, True, "query", *query_args)

  def bazel_cquery(self, *query_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel cquery' command.

    Raises:
      CalledProcessError: If the query fails.
    """
    return self._bazel(True, True, "cquery", *query_args)

  def bazel_info(self, *info_args) -> subprocess.CompletedProcess:
    """Runs a 'bazel info' command.

    Raises:
      CalledProcessError: If the command fails.
    """
    return self._bazel(True, True, "info", *info_args)

  def bazel_shutdown(self) -> subprocess.CompletedProcess:
    """Runs a 'bazel shutdown' command."""
    return self._bazel(False, False, "shutdown")

  def _bazel(
      self, capture_output: bool, check: bool, *args: List[str]
  ) -> subprocess.CompletedProcess:
    """Runs a Bazel command with the given args."""
    cmd = [self.bazel_path]
    cmd.extend(self._startup_options)
    cmd.extend(args)
    logging.info("Running command: %s", cmd)
    return subprocess.run(
        cmd,
        capture_output=capture_output,
        check=check,
        cwd=self.workspace_dir,
    )
