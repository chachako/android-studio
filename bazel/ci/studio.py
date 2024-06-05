"""Implements studio_* presubmit and postsubmit checks."""

import dataclasses
import enum
import os
import pathlib
import shutil
import tempfile
from typing import Sequence
import uuid

from tools.base.bazel.ci import bazel


_BAZEL_EXITCODE_SUCCESS = 0
_BAZEL_EXITCODE_TEST_FAILURES = 3
_BAZEL_EXITCODE_NO_TESTS_FOUND = 4


class BuildType(enum.Enum):
  """Represents the type of build being run."""
  LOCAL      = 1
  PRESUBMIT  = 2
  POSTSUBMIT = 3

  @classmethod
  def from_build_number(cls, build_number: str) -> 'BuildType':
    """Returns the build type corresponding to the given build number."""
    if build_number == 'SNAPSHOT':
      return BuildType.LOCAL
    if build_number.startswith('P'):
      return BuildType.PRESUBMIT
    build_type = BuildType.POSTSUBMIT


@dataclasses.dataclass(frozen=True)
class BazelTestResult:
  """Represents the output of a bazel test."""
  exit_code: int
  bes_path: pathlib.Path


def studio_linux(build_env: bazel.BuildEnv):
  """Runs Linux pre/postsubmit tests."""
  # If DIST_DIR does not exist, create one.
  if not build_env.dist_dir:
    build_env.dist_dir = tempfile.mkdtemp('dist-dir')
  dist_path = pathlib.Path(build_env.dist_dir)

  as_build_number = build_env.build_number.replace('P', '0')
  profile_path = dist_path / f'profile-{build_env.build_number}.json.gz'

  flags = [
      '--build_manual_tests',

      f'--define=meta_android_build_number={build_env.build_number}',

      f'--profile={profile_path}',

      '--build_tag_filters=-no_linux',
      '--test_tag_filters=-no_linux,-no_test_linux,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate,-very_flaky',

      '--tool_tag=studio_linux.sh',
      f'--embed_label={as_build_number}',

      '--runs_per_test=//tools/base/bazel:iml_to_build_consistency_test@2',

      '--jobs=500',
  ]

  targets = [
      '//tools/adt/idea/studio:android-studio',
      '//tools/adt/idea/studio:updater_deploy.jar',
      '//tools/vendor/google/aswb:aswb.linux.zip',
      '//tools/vendor/google/aswb:aswb.mac.zip',
      '//tools/vendor/google/aswb:aswb.mac_arm.zip',
      '//tools/adt/idea/native/installer:android-studio-bundle-data',
      '//tools/base/profiler/native/trace_processor_daemon',
      '//tools/base/deploy/deployer:deployer.runner_deploy.jar',
      '//tools/base/preview/screenshot:preview_screenshot_maven_repo.zip',
      '//tools/adt/idea/studio:test_studio',
      '//tools/vendor/google/game-tools/packaging:packaging-linux',
      '//tools/vendor/google/game-tools/packaging:packaging-win',
      '//tools/base/deploy/service:deploy.service_deploy.jar',
      '//tools/base/ddmlib:tools.ddmlib',
      '//tools/base/ddmlib:incfs',
      '//tools/base/lint/libs/lint-tests:lint-tests',
      '//tools/base/bazel:local_maven_repository_generator_deploy.jar',
      '//tools/base/build-system:documentation.zip',
      '//tools/vendor/google/adrt:android-studio-cros-skeleton.zip',
      '//tools/vendor/google/adrt:android-studio-nsis-prebuilt.zip',
      '//tools/vendor/google/asfp/studio:asfp',
      '//tools/vendor/google/asfp/studio:asfp-linux-deb.zip',
      '//tools/vendor/google/asfp/studio:asfp.deb',
      '//tools/vendor/intel:android-studio-intel-haxm.zip',
      '//tools/vendor/google/ml:aiplugin',
      '//prebuilts/studio/...',
      '//prebuilts/tools/...',
      '//tools/...',
      '//tools/vendor/google3/aswb/java/com/google/devtools/intellij/g3plugins:aswb_build_test',
  ]

  test_result = run_bazel_test(build_env, flags, targets)

  # If an uncommon exit code happens, copy extra worker logs.
  if test_result.exit_code > 9:
    copy_worker_logs(build_env)

  # TODO(b/342237310): Implement --very_flaky.

  workspace_path = pathlib.Path(os.environ.get('BUILD_WORKSPACE_DIRECTORY'))
  shutil.copy2(
      workspace_path / 'tools/base/build-system/supported-versions.properties',
      dist_path / 'agp-supported-versions.properties',
  )
  collect_logs(build_env, test_result.bes_path)

  # TODO(b/342237310): Copy artifacts.


def studio_win(build_env: bazel.BuildEnv):
  """Runs Windows pre/postsubmit tests."""
  # If DIST_DIR does not exist, create one.
  if not build_env.dist_dir:
    build_env.dist_dir = tempfile.mkdtemp('dist-dir')
  dist_path = pathlib.Path(build_env.dist_dir)

  profile_path = dist_path / f'winprof{build_env.build_number}.json.gz'

  flags = [
      f'--profile={profile_path}',

      '--build_tag_filters=-no_windows',
      '--test_tag_filters=-no_windows,-no_test_windows,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate',

      '--tool_tag=studio_win.cmd',
  ]

  targets = [
      '//tools/base/profiler/native/trace_processor_daemon',
      '//prebuilts/studio/...',
      '//prebuilts/tools/...',
      '//tools/...',
      '-//tools/vendor/google3/aswb/...',
      '-//tools/vendor/google/aswb/...',
  ]

  test_result = run_bazel_test(build_env, flags, targets)

  collect_logs(build_env, test_result.bes_path)

  # TODO(b/342237310): Copy artifacts.


def run_bazel_test(
    build_env: bazel.BuildEnv,
    flags: Sequence[str] = [],
    targets: Sequence[str] = [],
) -> BazelTestResult:
  """Runs the bazel test invocation."""
  flags = flags.copy()
  dist_path = pathlib.Path(build_env.dist_dir)
  invocation_id = str(uuid.uuid4())

  build_type = BuildType.from_build_number(build_env.build_number)
  bes_path = dist_path / f'bazel-{build_env.build_number}.bes'

  worker_instances = '2' if build_type == BuildType.LOCAL else 'auto'
  if build_type == BuildType.POSTSUBMIT:
    flags.extend([
        '--bes_keywords=ab-postsubmit',
        '--nocache_test_results',
    ])

  # TODO(b/342237310): Implement --very_flaky.

  if dist_path.exists():
    sponge_redirect_path = dist_path / 'upsalite_test_results.html'
    sponge_redirect_path.write_text(f'<head><meta http-equiv="refresh" content="0; url=\'https://fusion2.corp.google.com/invocations/{invocation_id}\'" /></head>')
    sponge_invocations_path = dist_path / 'sponge-invocations.txt'
    sponge_invocations_path.write_text(invocation_id)

  flags.extend([
      '--config=ci',
      '--config=ants',

      f'--invocation_id={invocation_id}',

      f'--build_event_binary_file={bes_path}',
      f'--build_metadata=ANDROID_BUILD_ID={build_env.build_number}',
      f'--build_metadata=ANDROID_TEST_INVESTIGATE="http://ab/tests/bazel/{invocation_id}"',
      f'--build_metadata=ab_build_id={build_env.build_number}',
      f'--build_metadata=ab_target={build_env.build_target_name}',

      f'--worker_max_instances={worker_instances}',

      '--experimental_enable_execution_graph_log',
      '--experimental_stream_log_file_uploads',
      '--experimental_execution_graph_log_dep_type=all',
  ])

  bazel_cmd = bazel.BazelCmd(build_env)
  bazel_cmd.startup_options = ['--max_idle_secs=60']
  result = bazel_cmd.test(*flags, '--', *targets)

  return BazelTestResult(
      exit_code=result.returncode,
      bes_path=bes_path,
  )


def copy_worker_logs(build_env: bazel.BuildEnv) -> None:
  """Copies worker logs into output."""
  bazel_cmd = bazel.BazelCmd(build_env)
  result = bazel_cmd.info('output_base')
  src_path = pathlib.Path(result.stdout.decode('utf-8').strip())
  dest_path = pathlib.Path(build_env.dist_dir) / 'bazel_logs'
  dest_path.mkdir(parents=True, exist_ok=True)
  for path in src_path.glob('*.log'):
    shutil.copy2(path, dest_path / path.name)


def collect_logs(build_env: bazel.BuildEnv, bes_path: pathlib.Path) -> None:
  """Runs the log collector."""
  build_type = BuildType.from_build_number(build_env.build_number)
  dist_path = pathlib.Path(build_env.dist_dir)
  error_log_path = dist_path / 'logs/build_error.log'
  perfgate_data_path = dist_path / 'perfgate_data.zip'

  args = [
      '//tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector',
      '--config=ci',
      '--',
      '-bes',
      str(bes_path),
      '-error_log',
      str(error_log_path),
      '-module_info',
      str(dist_path),
  ]
  if build_type == BuildType.POSTSUBMIT:
    args.append(f'-perfzip {perfgate_data_path}')

  bazel_cmd = bazel.BazelCmd(build_env)
  bazel_cmd.run(*args)
