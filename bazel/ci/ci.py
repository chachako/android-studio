"""Module for running CI targets."""
from typing import Callable, List
import argparse
import os
import sys
import subprocess

import bazel
import query_checks

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

  def exit(self):
    """Displays any errors and exits."""
    for err in self.exceptions:
      print(err, file=sys.stderr)
    sys.exit(1 if self.exceptions else 0)


def find_workspace() -> str:
  """Returns the path of the WORKSPACE directory."""
  bazel_workspace = os.environ.get('BUILD_WORKSPACE_DIRECTORY')
  if bazel_workspace:
    return bazel_workspace
  raise RuntimeError("Missing environment variable: BUILD_WORKSPACE_DIRECTORY")

def studio_build_checks(ci: CI):
  """Runs checks against the build graph."""
  ci.run(query_checks.no_local_genrules)
  ci.run(query_checks.require_cpu_tags)
  ci.run(query_checks.gradle_requires_cpu4_or_more)


def main():
  """Runs the CI target command."""
  parser = argparse.ArgumentParser()
  parser.add_argument("target", help="The name of the CI target")
  args = parser.parse_args()

  bazel_path = os.path.join(find_workspace(), 'tools/base/bazel/bazel')
  build_env = bazel.BuildEnv(bazel_path=bazel_path)
  ci = CI(build_env=build_env)

  if args.target == "studio-build-checks":
    studio_build_checks(ci)
  else:
    raise NotImplementedError(f'target: "{args.target}" does not exist')
  ci.exit()

if __name__ == "__main__":
  main()
