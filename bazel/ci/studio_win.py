"""Implements studio-win CI script."""

import pathlib
import tempfile

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import studio


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
      '--test_tag_filters=-no_windows,-no_test_windows,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate,-perfgate-release',

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

  test_result = studio.run_bazel_test(build_env, flags, targets)

  studio.collect_logs(build_env, test_result.bes_path)
  studio.copy_artifacts(
      build_env,
      [
          ('tools/vendor/google/skia/skiaparser.zip', ''),
          ('tools/vendor/google/skia/skia_test_support.zip', ''),
          ('tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon.exe', ''),
      ],
  )

  # TODO(b/342237310): Build Windows launcher.

  if test_result.exit_code in {
      bazel.EXITCODE_SUCCESS,
      bazel.EXITCODE_TEST_FAILURES,
  }:
    return

  raise studio.BazelTestError(test_result.exit_code)
