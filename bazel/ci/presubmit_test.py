"""Tests for presubmit."""

import pathlib
import subprocess
from typing import Iterable, List
from unittest import mock

from absl.testing import absltest
from absl.testing import parameterized

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import bazel_diff
from tools.base.bazel.ci import fake_build_env
from tools.base.bazel.ci import fake_gce
from tools.base.bazel.ci import gce
from tools.base.bazel.ci import presubmit


class PresubmitTest(parameterized.TestCase):
  """Tests for the presubmit module."""

  def _mock_generate_hash_file(self, contents: str) -> mock.Mock:
    def func(
        build_env: bazel.BuildEnv,
        external_repos: Iterable[str],
        path: str,
    ) -> None:
      del build_env, external_repos
      pathlib.Path(path).write_text(contents)
    return self.enter_context(
        mock.patch.object(bazel_diff, 'generate_hash_file', side_effect=func))

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
        mock.patch.object(bazel_diff, 'get_impacted_targets', side_effect=func))

  def setUp(self):
    super().setUp()
    self.build_env = self.enter_context(fake_build_env.make_fake_build_env())
    self.gce = self.enter_context(fake_gce.make_fake_gce(self, self.build_env))

  def test_generate_and_upload_hash_file(self):
    mock_generate = self._mock_generate_hash_file('hash-file')
    presubmit.generate_and_upload_hash_file(self.build_env)
    downloaded = self.build_env.tmp_path / 'downloaded'
    gce.download_from_gcs(
        'adt-byob',
        'bazel-diff-hashes/P123-studio-test.json',
        downloaded,
    )
    self.assertEqual(downloaded.read_text(), 'hash-file')
    mock_generate.assert_called_once_with(
        self.build_env,
        presubmit._LOCAL_REPOSITORIES,
        mock.ANY,
    )

  @parameterized.named_parameters(
      dict(
          testcase_name='basic',
          tags=[],
          impacted_targets=['target1', 'target2', 'target3', 'target4'],
          query_targets=['target2', 'target3'],
          expected_found=True,
          expected_targets=['target2', 'target3'],
          expected_selected_target_count=2,
      ),
      dict(
          testcase_name='with_default_presubmit_test',
          tags=[('Presubmit-Test', 'default')],
          impacted_targets=['target1', 'target2'],
          query_targets=['target2'],
          expected_found=False,
          expected_targets=['base_target1', 'base_target2'],
          expected_selected_target_count=0,
      ),
      dict(
          testcase_name='with_other_target_name',
          tags=[('Presubmit-Test', 'studio-other:target3')],
          impacted_targets=['target1', 'target2'],
          query_targets=['target2'],
          expected_found=True,
          expected_targets=['target2'],
          expected_selected_target_count=1,
      ),
      dict(
          testcase_name='with_multiple_explicit_targets',
          tags=[
              ('Presubmit-Test', 'target3'),
              ('Presubmit-Test', 'target4'),
          ],
          impacted_targets=['target1', 'target2'],
          query_targets=['target2'],
          expected_found=True,
          expected_targets=['target2', 'target3', 'target4'],
          expected_selected_target_count=1,
      ),
  )
  def test_find_test_targets(
      self,
      tags,
      impacted_targets,
      query_targets,
      expected_found,
      expected_targets,
      expected_selected_target_count,
  ):
    self._mock_generate_hash_file('hash-file')
    self._mock_get_impacted_targets(impacted_targets)
    self.build_env.bazel_query.return_value.stdout = '\n'.join(query_targets).encode('utf-8')

    parent_hash_path = self.build_env.tmp_path / 'parent.json'
    parent_hash_path.write_text('parent-hash-file')
    gce.upload_to_gcs(
        parent_hash_path,
        'adt-byob',
        'bazel-diff-hashes/789-studio-test.json',
    )
    self.gce.add_change('owner', 'message', tags)
    self.gce.changes[0].topic = 'topic'

    targets = presubmit.find_test_targets(
        self.build_env,
        ['base_target1', 'base_target2'],
        'includefilter,-excludefilter',
    )
    self.assertEqual(targets.found, expected_found)
    self.assertEqual(set(targets.targets), set(expected_targets))
    self.assertEqual(targets.flags, [
        f'--build_metadata=selective_presubmit_found={expected_found}',
        f'--build_metadata=selective_presubmit_impacted_target_count={expected_selected_target_count}',
        '--build_metadata=gerrit_owner=owner@google.com',
        '--build_metadata=gerrit_change_id=changeid0',
        '--build_metadata=gerrit_change_number=0',
        '--build_metadata=gerrit_change_patchset=0',
        '--build_metadata=gerrit_topic=topic',
    ])
    if expected_found:
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

  def test_generate_runs_per_test_flags(self):
    self.gce.add_change('owner', 'message', [
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