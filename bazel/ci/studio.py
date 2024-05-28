"""Implements studio_* presubmit and postsubmit checks."""

import enum
import os
import pathlib
from typing import Sequence
import uuid

from tools.base.bazel.ci import bazel


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


def studio_linux(build_env: bazel.BuildEnv):
  """Runs Linux pre/postsubmit tests."""
  as_build_number = build_env.build_number.replace('P', '0')
  profile_path = os.path.join(build_env.dist_dir, f'profile-{build_env.build_number}.json.gz')

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

  run_bazel_test(build_env, flags, targets)


def studio_win(build_env: bazel.BuildEnv):
  """Runs Windows pre/postsubmit tests."""
  profile_path = os.path.join(build_env.dist_dir, f'winprof{build_env.build_number}.json.gz')

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

  run_bazel_test(build_env, flags, targets)


def run_bazel_test(
    build_env: bazel.BuildEnv,
    flags: Sequence[str] = [],
    targets: Sequence[str] = [],
) -> int:
  """Runs the bazel test invocation."""
  flags = flags.copy()
  dist_dir = pathlib.Path(build_env.dist_dir)
  invocation_id = str(uuid.uuid4())

  build_type = BuildType.from_build_number(build_env.build_number)
  bes_path = dist_dir / f'bazel-{build_env.build_number}.bes'

  worker_instances = '2' if build_type == BuildType.LOCAL else 'auto'
  if build_type == BuildType.POSTSUBMIT:
    flags.extend([
        '--bes_keywords=ab-postsubmit',
        '--nocache_test_results',
    ])

  # TODO(b/342237310): Implement --very_flaky.

  if dist_dir.exists():
    sponge_redirect_path = dist_dir / 'upsalite_test_results.html'
    sponge_redirect_path.write_text(f'<head><meta http-equiv="refresh" content="0; url=\'https://fusion2.corp.google.com/invocations/{invocation_id}\'" /></head>')
    sponge_invocations_path = dist_dir / 'sponge-invocations.txt'
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
  result = bazel_cmd.test(False, *flags, '--', *targets)

  # TODO(b/342237310): returncode will always be 0, so properly handle non-zero
  # exit codes in bazel_cmd.test().
  return result.returncode
