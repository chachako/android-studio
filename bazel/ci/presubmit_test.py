"""Tests for presubmit."""

import pathlib
from typing import List, Tuple
from unittest import mock

from absl.testing import absltest

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import bazel_diff
from tools.base.bazel.ci import fake_build_env
from tools.base.bazel.ci import fake_gce
from tools.base.bazel.ci import gce
from tools.base.bazel.ci import presubmit


# --build_metadata flags that are always generated but do not change for tests.
DEFAULT_BUILD_METADATA_FLAGS = [
    '--build_metadata=gerrit_owner=owner@google.com',
    '--build_metadata=gerrit_change_id=changeid0',
    '--build_metadata=gerrit_change_number=0',
    '--build_metadata=gerrit_change_patchset=0',
]


class PresubmitTest(absltest.TestCase):
  """Tests for the presubmit module."""

  def _mock_generate_hash_file(self, contents: str) -> mock.Mock:
    def func(build_env: bazel.BuildEnv, path: str) -> None:
      del build_env
      pathlib.Path(path).write_text(contents)
    return self.enter_context(
        mock.patch.object(bazel_diff, 'generate_hash_file', func))

  def _mock_get_impacted_targets(self, targets: List[str]) -> mock.Mock:
    def func(
        build_env: bazel.BuildEnv,
        starting_hashes_path: str,
        final_hashes_path: str,
        output_path: pathlib.Path,
    ) -> None:
      del build_env, starting_hashes_path, final_hashes_path
      output_path.write_text('\n'.join(targets))
    return self.enter_context(
        mock.patch.object(bazel_diff, 'get_impacted_targets', func))

  def _setup_for_bazel_diff(self, tags: List[Tuple[str,str]]) -> None:
    parent_hash_path = self.build_env.tmp_path / 'parent.json'
    parent_hash_path.write_text('parent-hash-file')
    gce.upload_to_gcs(
        parent_hash_path,
        'adt-byob',
        'bazel-diff-hashes/789-studio-test.json',
    )
    self.gce.add_change('owner', 'message', tags)

  def setUp(self):
    super().setUp()
    self.build_env = self.enter_context(fake_build_env.make_fake_build_env())
    self.gce = self.enter_context(fake_gce.make_fake_gce(self, self.build_env))

  def test_generate_and_upload_hash_file(self):
    self._mock_generate_hash_file('hash-file')
    presubmit.generate_and_upload_hash_file(self.build_env)
    downloaded = self.build_env.tmp_path / 'downloaded'
    gce.download_from_gcs(
        'adt-byob',
        'bazel-diff-hashes/P123-studio-test.json',
        downloaded,
    )
    self.assertEqual(downloaded.read_text(), 'hash-file')

  def test_find_test_targets(self):
    self._setup_for_bazel_diff([])
    self._mock_get_impacted_targets([
        'target1',
        'target2',
        'target3',
        'target4',
    ])
    self.build_env.bazel_query.return_value.stdout = b'target2\ntarget3'
    self.gce.changes[0].topic = 'topic'

    targets = presubmit.find_test_targets(
        self.build_env,
        ['base_target1', 'base_target2'],
        'includefilter,-excludefilter',
    )
    self.assertTrue(targets.found)
    self.assertEqual(set(targets.targets), set(['target2', 'target3']))
    self.assertEqual(targets.flags, [
        '--build_metadata=selective_presubmit_found=True',
        '--build_metadata=selective_presubmit_impacted_target_count=2',
    ] + DEFAULT_BUILD_METADATA_FLAGS + [
        '--build_metadata=gerrit_topic=topic',
    ])
    self.build_env.bazel_query.assert_called_once_with(
        'base_target1 union '
        'attr(tags, "includefilter", base_target1) union '
        'base_target2 union '
        'attr(tags, "includefilter", base_target2) except '
        'attr(tags, "excludefilter", base_target1) except '
        'attr(tags, "manual", base_target1) except '
        'attr(target_compatible_with, "@platforms//:incompatible", base_target1) except '
        'attr(tags, "excludefilter", base_target2) except '
        'attr(tags, "manual", base_target2) except '
        'attr(target_compatible_with, "@platforms//:incompatible", base_target2)',
    )

  def test_find_test_targets_with_default_tag(self):
    self._setup_for_bazel_diff([('Presubmit-Test', 'default')])
    self._mock_get_impacted_targets(['target1', 'target2'])
    self.build_env.bazel_query.return_value.stdout = b'target2'

    targets = presubmit.find_test_targets(self.build_env, ['base_target'], '')
    self.assertFalse(targets.found)
    self.assertEqual(targets.targets, ['base_target'])
    self.assertEqual(targets.flags, [
        '--build_metadata=selective_presubmit_found=False',
        '--build_metadata=selective_presubmit_impacted_target_count=0',
    ] + DEFAULT_BUILD_METADATA_FLAGS)

  def test_find_test_targets_with_other_build_target(self):
    self._setup_for_bazel_diff([('Presubmit-Test', 'studio-other:target3')])
    self._mock_get_impacted_targets(['target1', 'target2'])
    self.build_env.bazel_query.return_value.stdout = b'target2'

    targets = presubmit.find_test_targets(self.build_env, ['base_target'], '')
    self.assertTrue(targets.found)
    self.assertEqual(targets.targets, ['target2'])
    self.assertEqual(targets.flags, [
        '--build_metadata=selective_presubmit_found=True',
        '--build_metadata=selective_presubmit_impacted_target_count=1',
    ] + DEFAULT_BUILD_METADATA_FLAGS)

  def test_find_test_targets_with_explicit_targets(self):
    self._setup_for_bazel_diff([
        ('Presubmit-Test', 'target3'),
        ('Presubmit-Test', 'target4'),
    ])
    self._mock_get_impacted_targets(['target1', 'target2'])
    self.build_env.bazel_query.return_value.stdout = b'target2'

    targets = presubmit.find_test_targets(self.build_env, ['base_target'], '')
    self.assertTrue(targets.found)
    self.assertEqual(targets.targets, ['target2', 'target3', 'target4'])
    self.assertEqual(targets.flags, [
        '--build_metadata=selective_presubmit_found=True',
        '--build_metadata=selective_presubmit_impacted_target_count=1',
    ] + DEFAULT_BUILD_METADATA_FLAGS)

  def test_generate_runs_per_test_flags(self):
    self._setup_for_bazel_diff([
        ('Presubmit-Runs-Per-Test', 'studio-test:target1@10'),
        ('Presubmit-Runs-Per-Test', 'target2@20'),
        ('Presubmit-Runs-Per-Test', 'studio-other:target3@30'),
    ])
    self.assertEqual(
        presubmit.generate_runs_per_test_flags(self.build_env),
        ['--runs_per_test=target1@10', '--runs_per_test=target2@20'],
    )


if __name__ == '__main__':
  absltest.main()