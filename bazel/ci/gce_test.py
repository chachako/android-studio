"""Tests for gce."""

from absl.testing import absltest

from tools.base.bazel.ci import fake_build_env
from tools.base.bazel.ci import fake_gce
from tools.base.bazel.ci import gce


class GceTest(absltest.TestCase):
  """Tests for the gce module."""

  def setUp(self):
    super().setUp()
    self.build_env = self.enter_context(fake_build_env.make_fake_build_env())
    self.gce = self.enter_context(fake_gce.make_fake_gce(self.build_env))

  def test_get_auth_header(self):
    self.gce.auth_token = 'fake-token-2'
    header = gce.get_auth_header()
    self.assertEqual(header, ['Authorization: Bearer fake-token-2'])

  def test_get_reference_build_id(self):
    self.build_env.build_number = 'P456'
    self.gce.reference_build_id = '789'
    self.assertEqual(gce.get_reference_build_id('P456', 'studio-test'), '789')


if __name__ == '__main__':
  absltest.main()