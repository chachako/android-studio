"""Implements studio-linux CI scripts."""

import os
import pathlib
import shutil
import tempfile

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import studio


_TARGETS = [
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


_ARTIFACTS = [
    ('tools/adt/idea/studio/android-studio.linux.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio.win.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio.mac.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio.mac_arm.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio_build_manifest.textproto', 'artifacts'),
    ('tools/adt/idea/studio/android-studio_update_message.html', 'artifacts'),
    ('tools/adt/idea/studio/updater_deploy.jar', 'artifacts/android-studio-updater.jar'),
    ('tools/adt/idea/native/installer/android-studio-bundle-data.zip', 'artifacts'),
    ('tools/vendor/google/adrt/android-studio-cros-skeleton.zip', 'artifacts'),
    ('tools/vendor/google/adrt/android-studio-nsis-prebuilt.zip', 'artifacts'),
    ('tools/vendor/intel/android-studio-intel-haxm.zip', 'artifacts'),
    ('tools/vendor/google/asfp/studio/asfp.linux.zip', 'artifacts'),
    ('tools/vendor/google/asfp/studio/asfp_build_manifest.textproto', 'artifacts/asfp_build_manifest.textproto'),
    ('tools/vendor/google/asfp/studio/asfp-linux-deb.zip', 'artifacts'),
    ('tools/vendor/google/asfp/studio/asfp.deb', 'artifacts'),
    ('tools/vendor/google/aswb/android-studio-with-blaze-channel.deb', 'artifacts'),
    ('tools/vendor/google/aswb/android-studio-with-blaze.mac.zip', 'artifacts'),
    ('tools/vendor/google/aswb/android-studio-with-blaze.mac_arm.zip', 'artifacts'),
    ('tools/vendor/google/skia/skiaparser.zip', 'artifacts'),
    ('tools/vendor/google/skia/skia_test_support.zip', 'artifacts'),
    ('tools/vendor/google/ml/aiplugin*.zip', 'artifacts'),

    ('tools/base/sdklib/commandlinetools_*.zip', 'artifacts'),
    ('tools/base/ddmlib/tools.ddmlib.jar', 'artifacts/ddmlib.jar'),
    ('tools/base/annotations/annotations.jar', 'artifacts'),
    ('tools/base/common/tools.common.jar', 'artifacts'),
    ('tools/base/ddmlib/libincfs.jar', 'artifacts'),
    ('tools/base/lint/libs/lint-tests/lint-tests.jar', 'artifacts'),
    ('tools/base/deploy/deployer/deployer.runner_deploy.jar', 'artifacts/deployer.jar'),
    ('tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon', 'artifacts'),
    ('tools/vendor/google/game-tools/packaging/game-tools-linux.tar.gz', 'artifacts'),
    ('tools/vendor/google/game-tools/packaging/game-tools-win.zip', 'artifacts'),
    ('tools/base/deploy/service/deploy.service_deploy.jar', 'artifacts'),
    ('tools/base/gmaven/gmaven.zip', 'artifacts/gmaven_repo.zip'),
    ('tools/base/build-system/documentation.zip', 'artifacts/android_gradle_plugin_reference_docs.zip'),
    ('tools/base/firebase/testlab/testlab-gradle-plugin/testlab-gradle-plugin.zip', 'artifacts'),
    ('tools/base/preview/screenshot/preview_screenshot_maven_repo.zip', 'artifacts'),
]


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

  test_result = studio.run_bazel_test(build_env, flags, _TARGETS)

  # If an uncommon exit code happens, copy extra worker logs.
  if test_result.exit_code > 9:
    copy_worker_logs(build_env)

  # TODO(b/342237310): Implement --very_flaky.

  workspace_path = pathlib.Path(os.environ.get('BUILD_WORKSPACE_DIRECTORY'))
  shutil.copy2(
      workspace_path / 'tools/base/build-system/supported-versions.properties',
      dist_path / 'agp-supported-versions.properties',
  )
  studio.collect_logs(build_env, test_result.bes_path)

  if test_result.exit_code in {
      bazel.EXITCODE_SUCCESS,
      bazel.EXITCODE_TEST_FAILURES,
      bazel.EXITCODE_NO_TESTS_FOUND
  }:
    (dist_path / 'artifacts').mkdir(parents=True, exist_ok=True)
    studio.copy_artifacts(build_env, _ARTIFACTS)


def copy_worker_logs(build_env: bazel.BuildEnv) -> None:
  """Copies worker logs into output."""
  bazel_cmd = bazel.BazelCmd(build_env)
  result = bazel_cmd.info('output_base')
  src_path = pathlib.Path(result.stdout.decode('utf-8').strip())
  dest_path = pathlib.Path(build_env.dist_dir) / 'bazel_logs'
  dest_path.mkdir(parents=True, exist_ok=True)
  for path in src_path.glob('*.log'):
    shutil.copy2(path, dest_path / path.name)
