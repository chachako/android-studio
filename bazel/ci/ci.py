"""Module for running CI targets."""

import argparse
import os
import platform
import subprocess
import sys
from typing import Callable, List
import uuid

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import errors
from tools.base.bazel.ci import query_checks
from tools.base.bazel.ci import studio_linux
from tools.base.bazel.ci import studio_win


class CI:
  """Continuous Integration wrapper.

  This is used to run multiple functions that may fail, but
  should be aggregated and displayed before exiting.
  """

  exceptions: List[BaseException] = []
  build_env: bazel.BuildEnv

  def __init__(self, build_env: bazel.BuildEnv):
    self.build_env = build_env
    self.exit_code = 0

  def run(self, func: Callable[[bazel.BuildEnv], None]):
    """Runs callables that might raise exceptions."""
    try:
      func(self.build_env)
    except errors.CIError as e:
      self.exceptions.append(e)
      self.exit_code = e.exit_code
    except subprocess.CalledProcessError as e:
      stderr = (e.stderr or b'').decode('utf-8')
      msg = f'#### internal subprocess error\n{e}\n{stderr}'
      self.exceptions.append(RuntimeError(msg))
      self.exit_code = 1

  def has_errors(self) -> bool:
    """Returns true if there were exceptions."""
    return bool(self.exceptions)

  def print_errors(self):
    """Write CI exceptions to stderr."""
    for err in self.exceptions:
      print(err, file=sys.stderr)


def find_workspace() -> str:
  """Returns the path of the WORKSPACE directory."""
  bazel_workspace = os.environ.get('BUILD_WORKSPACE_DIRECTORY')
  if bazel_workspace:
    return bazel_workspace
  raise RuntimeError('Missing environment variable: BUILD_WORKSPACE_DIRECTORY')


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


def main():
  """Runs the CI target command.

  If any commands raise a CIError, this exits with the exit code of the last
  exception raised.
  """
  parser = argparse.ArgumentParser()
  parser.add_argument('target', help='The name of the CI target')
  args = parser.parse_args()

  bazel_name = 'bazel.cmd' if platform.system() == 'Windows' else 'bazel'
  bazel_path = os.path.join(find_workspace(), f'tools/base/bazel/{bazel_name}')
  build_env = bazel.BuildEnv(bazel_path=bazel_path)
  ci = CI(build_env=build_env)

  match args.target:
    case 'studio-build-checks':
      studio_build_checks(ci)
    case 'studio-linux':
      ci.run(studio_linux.studio_linux)
    case 'studio-linux_very_flaky':
      ci.run(studio_linux.studio_linux_very_flaky)
    case 'studio-linux-k2':
      ci.run(studio_linux.studio_linux_k2)
    case 'studio-win':
      ci.run(studio_win.studio_win)
    case _:
      raise NotImplementedError(f'target: "{args.target}" does not exist')

  if ci.has_errors():
    ci.print_errors()
    sys.exit(ci.exit_code)


if __name__ == '__main__':
  main()
