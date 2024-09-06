"""Tests for ci module."""

from absl.testing import absltest
from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import ci
from tools.base.bazel.ci import query_checks


class CITest(absltest.TestCase):
  """Tests for the CI wrapper."""

  def test_run_with_exception(self):
    ci_ = ci.CI(build_env=bazel.BuildEnv(bazel_path='', bazel_version='7'))
    exception = query_checks.BuildGraphException(title='', go_link='', body='')

    def failing_func(_: bazel.BuildEnv):
      raise exception

    ci_.run(failing_func)

    self.assertEqual([exception], ci_.exceptions)
    self.assertTrue(ci_.has_errors(), 'expected has_errors() = True')

  def test_run(self):
    ci_ = ci.CI(build_env=bazel.BuildEnv(bazel_path='', bazel_version='7'))

    def func(_: bazel.BuildEnv):
      return

    ci_.run(func)

    self.assertFalse(ci_.has_errors(), 'expected has_errors() = False')


if __name__ == '__main__':
  absltest.main()
