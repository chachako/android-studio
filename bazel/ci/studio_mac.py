"""Implements studio-mac CI scripts."""

import pathlib
from typing import List

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import studio

_ARTIFACTS = [
    ('tools/adt/idea/studio/android-studio.linux.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio.win.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio.mac.zip', 'artifacts'),
]


def studio_mac(build_env: bazel.BuildEnv) -> None:
  """Runs studio-mac target."""
  flags = build_flags(
      build_env,
      'ci:studio-mac',
  )
  targets = [
      '//tools/...',
      '-//tools/idea/...',
      '-//tools/vendor/google/aswb/...',
      '-//tools/vendor/google3/aswb/...',
      '-//tools/adt/idea/aswb/...',
      '//tools/base/profiler/native/trace_processor_daemon',
  ]
  result = studio.run_bazel_test(build_env, flags, targets)
  if studio.is_build_successful(result):
    if build_env.dist_dir:
      studio.copy_artifacts(
          build_env,
          [
              (
                  'tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon',
                  '',
              ),
              ('tools/vendor/google/skia/skiaparser.zip', ''),
              ('tools/vendor/google/skia/skia_test_support.zip', ''),
          ],
      )
    if result.exit_code != bazel.EXITCODE_NO_TESTS_FOUND:
      return

  raise studio.BazelTestError(exit_code=result.exit_code)


def studio_mac_arm(build_env: bazel.BuildEnv) -> None:
  """Runs studio-mac-arm target."""
  flags = build_flags(
      build_env,
      'ci:studio-mac-arm',
  )
  targets = [
      '//tools/base/bazel/...',
      '//tools/vendor/google/skia:skiaparser',
      '//tools/vendor/google/skia:skia_test_support',
      '//tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/...',
  ]
  result = studio.run_bazel_test(build_env, flags, targets)
  if studio.is_build_successful(result):
    if build_env.dist_dir:
      studio.copy_artifacts(
          build_env,
          [
              (
                  'tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native/libjni_object_tagger.dylib',
                  '',
              ),
              ('tools/vendor/google/skia/skiaparser.zip', ''),
              ('tools/vendor/google/skia/skia_test_support.zip', ''),
          ],
      )
    if result.exit_code != bazel.EXITCODE_NO_TESTS_FOUND:
      return

  raise studio.BazelTestError(exit_code=result.exit_code)


def build_flags(
    build_env: bazel.BuildEnv,
    test_tag_filters: str = '',
) -> List[str]:
  """Returns the flags to use for testing."""
  dist_path = pathlib.Path(build_env.dist_dir)
  profile_path = dist_path / f'profile-{build_env.build_number}.json.gz'

  return [
      f'--profile={profile_path}',
      f'--test_tag_filters={test_tag_filters}',
      '--tool_tag=studio_mac.sh',
  ]
