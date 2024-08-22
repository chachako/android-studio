"""Tests for presubmit."""

import pathlib
from unittest import mock

from absl.testing import absltest

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import bazel_diff
from tools.base.bazel.ci import fake_build_env
from tools.base.bazel.ci import fake_gce
from tools.base.bazel.ci import gce
from tools.base.bazel.ci import presubmit


class PresubmitTest(absltest.TestCase):
  """Tests for the presubmit module."""

  def _mock_generate_hash_file(self, contents: str) -> mock.Mock:
    def func(build_env: bazel.BuildEnv, path: str) -> None:
      del build_env
      pathlib.Path(path).write_text(contents)
    return self.enter_context(
        mock.patch.object(bazel_diff, 'generate_hash_file', func))

  def setUp(self):
    super().setUp()
    self.build_env = self.enter_context(fake_build_env.make_fake_build_env())
    self.gce = self.enter_context(fake_gce.make_fake_gce())

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