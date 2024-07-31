"""Implements studio-win CI script."""

import pathlib
import tempfile

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import presubmit
from tools.base.bazel.ci import studio


def studio_win(build_env: bazel.BuildEnv):
  """Runs Windows pre/postsubmit tests."""
  build_type = studio.BuildType.from_build_number(build_env.build_number)
  if build_type == studio.BuildType.POSTSUBMIT:
    presubmit.generate_and_upload_hash_file(build_env)

  # If DIST_DIR does not exist, create one.
  if not build_env.dist_dir:
    build_env.dist_dir = tempfile.mkdtemp('dist-dir')
  dist_path = pathlib.Path(build_env.dist_dir)

  profile_path = dist_path / f'winprof{build_env.build_number}.json.gz'

  flags = [
      # TODO(b/173153395) Switch back to dynamic after Bazel issue is resolved.
      # See https://github.com/bazelbuild/bazel/issues/22482
      '--config=remote-exec',
      f'--profile={profile_path}',

      '--test_tag_filters=-noci:studio-win,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate-release',

      '--tool_tag=studio_win.cmd',
  ]

  targets = [
      '//tools/base/profiler/native/trace_processor_daemon',
      '//tools/adt/idea/studio:android-studio',
      '//prebuilts/studio/...',
      '//prebuilts/tools/...',
      '//tools/...',
      '-//tools/vendor/google3/aswb/...',
      '-//tools/vendor/google/aswb/...',
  ]

  test_result = studio.run_bazel_test(build_env, flags, targets)

  studio.copy_artifacts(
      build_env,
      [
          ('tools/vendor/google/skia/skiaparser.zip', ''),
          ('tools/vendor/google/skia/skia_test_support.zip', ''),
          ('tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon.exe', ''),
      ],
  )
  studio.collect_logs(build_env, test_result.bes_path)

  bazel.BazelCmd(build_env).shutdown()

  if test_result.exit_code in {
      bazel.EXITCODE_SUCCESS,
      bazel.EXITCODE_TEST_FAILURES,
  }:
    return

  raise studio.BazelTestError(exit_code=test_result.exit_code)
