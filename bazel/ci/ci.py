"""Module for running CI targets."""

import argparse
import os
import platform
import uuid
import subprocess
import sys
from typing import Callable, List

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import query_checks
from tools.base.bazel.ci import studio


class CI:
  """Continuous Integration wrapper.

  This is used to run multiple functions that may fail, but
  should be aggregated and displayed before exiting.
  """

  exceptions: List[BaseException] = []
  build_env: bazel.BuildEnv

  def __init__(self, build_env: bazel.BuildEnv):
    self.build_env = build_env

  def run(self, func: Callable[[bazel.BuildEnv], None]):
    """Runs callables that might raise exceptions."""
    try:
      func(self.build_env)
    except query_checks.BuildGraphException as e:
      self.exceptions.append(e)
    except subprocess.CalledProcessError as e:
      msg = f'#### internal subprocess error\n{e}\n{e.stderr.decode("utf8")}'
      self.exceptions.append(RuntimeError(msg))

  def has_errors(self) -> int:
    """Returns true if there were exceptions."""
    return 1 if self.exceptions else 0

  def print_errors(self):
    """Write CI exceptions to stderr."""
    for err in self.exceptions:
      print(err, file=sys.stderr)


def find_workspace() -> str:
  """Returns the path of the WORKSPACE directory."""
  bazel_workspace = os.environ.get("BUILD_WORKSPACE_DIRECTORY")
  if bazel_workspace:
    return bazel_workspace
  raise RuntimeError("Missing environment variable: BUILD_WORKSPACE_DIRECTORY")


def studio_build_checks(ci: CI):
  """Runs checks against the build graph."""
  ci.run(query_checks.no_local_genrules)
  ci.run(query_checks.require_cpu_tags)
  ci.run(query_checks.gradle_requires_cpu4_or_more)

  def validate_coverage_graph(env: bazel.BuildEnv):
    inv_id = uuid.uuid4()
    result = bazel.BazelCmd(env).build(
      '--config=ci', '--nobuild', f'--invocation_id={inv_id}',
      '--', '@cov//:all.suite')
    if result.returncode:
      raise RuntimeError((
        'Coverage build configuration is broken; you may need to update'
        ' tools/base/bazel/coverage/BUILD\n'
        f'\n See https://fusion2.corp.google.com/invocations/{inv_id}'
      ))
  ci.run(validate_coverage_graph)


def studio_linux(ci: CI):
  ci.run(studio.studio_linux)


def studio_win(ci: CI):
  ci.run(studio.studio_win)


def main():
  """Runs the CI target command."""
  parser = argparse.ArgumentParser()
  parser.add_argument("target", help="The name of the CI target")
  args = parser.parse_args()

  bazel_name = "bazel.cmd" if platform.system() == "Windows" else "bazel"
  bazel_path = os.path.join(find_workspace(), f"tools/base/bazel/{bazel_name}")
  build_env = bazel.BuildEnv(bazel_path=bazel_path)
  ci = CI(build_env=build_env)

  match args.target:
    case "studio-build-checks":
      studio_build_checks(ci)
    case "studio-linux":
      studio_linux(ci)
    case "studio-win":
      studio_win(ci)
    case _:
      raise NotImplementedError(f'target: "{args.target}" does not exist')

  if ci.has_errors():
    ci.print_errors()
    sys.exit(1)


if __name__ == "__main__":
  main()
