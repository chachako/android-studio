"""Tests for gce."""

import pathlib

from absl.testing import absltest

from tools.base.bazel.ci import fake_build_env
from tools.base.bazel.ci import fake_gce
from tools.base.bazel.ci import gce


class GceTest(absltest.TestCase):
  """Tests for the gce module."""

  def setUp(self):
    super().setUp()
    self.build_env = self.enter_context(fake_build_env.make_fake_build_env())
    self.gce = self.enter_context(fake_gce.make_fake_gce(self, self.build_env))

  def test_get_auth_header(self):
    self.gce.auth_token = 'fake-token-2'
    header = gce.get_auth_header()
    self.assertEqual(header, ['Authorization: Bearer fake-token-2'])

  def test_get_reference_build_id(self):
    self.build_env.build_number = 'P456'
    self.gce.reference_build_id = '789'
    self.assertEqual(gce.get_reference_build_id('P456', 'studio-test'), '789')

  def test_upload_download(self):
    src = self.build_env.tmp_path / 'src.txt'
    src.write_text('hello')
    gce.upload_to_gcs(str(src), 'bucket', 'name.txt')
    dst = self.build_env.tmp_path / 'dst.txt'
    self.assertTrue(gce.download_from_gcs('bucket', 'name.txt', str(dst)))
    self.assertEqual(dst.read_text(), 'hello')

  def test_download_from_gcs_not_found(self):
    dst = self.build_env.tmp_path / 'dst.txt'
    self.assertFalse(gce.download_from_gcs('bucket', 'name.txt', str(dst)))

  def test_gerrit_changes(self):
    changes = [
        self.gce.add_change(
            'owner1',
            'commit message 1',
            [('Tag1', 'Value1'), ('Tag2', 'Value2')],
        ),
        self.gce.add_change(
            'owner2',
            'commit message 2',
            [('Tag3', 'Value3'), ('Tag4', 'Value4')],
        ),
    ]
    changes[0].topic = 'topic'

    fetched_changes = gce.get_gerrit_changes(self.build_env.build_number)
    self.assertEqual(fetched_changes, changes)
    self.assertEqual(fetched_changes[0].topic, 'topic')


if __name__ == '__main__':
  absltest.main()