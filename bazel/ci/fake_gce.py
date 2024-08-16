"""Implements a fake GCE for testing."""

import contextlib
import json
import pathlib
import re
import subprocess
from typing import Iterator, Dict, List
from unittest import mock

from absl.testing import absltest

from tools.base.bazel.ci import fake_build_env
from tools.base.bazel.ci import gce


class FakeGCE:
  """Fake GCE for testing."""

  def __init__(self, test_case: absltest.TestCase, build_env: fake_build_env.FakeBuildEnv):
    self.test_case = test_case
    self.build_env = build_env
    self.auth_token = 'fake-token'
    self.reference_build_id = '789'
    self.changes: List[gce.GerritChange] = []
    self.files: Dict[str, bytes] = {}

  def curl(self, method: str, headers: List[str], url: str, *args: List[str]) -> str:
    """Fakes the curl method."""
    bid = self.build_env.build_number
    target = self.build_env.build_target_name
    method_url = f'{method} {url}'

    # Auth token.
    if method_url == 'GET http://metadata/computeMetadata/v1/instance/service-accounts/default/token':
      return json.dumps({
          'access_token': self.auth_token,
      })

    # Reference build.
    if method_url == f'GET https://androidbuildinternal.googleapis.com/android/internal/build/v3/builds/{bid}/{target}':
      self.test_case.assertEqual(headers, [f'Authorization: Bearer {self.auth_token}'])
      return json.dumps({
          'referenceBuildIds': [self.reference_build_id],
      })

    # Upload to GCS.
    if match := re.fullmatch(
        r'POST https:\/\/storage\.googleapis\.com\/upload\/storage\/v1\/b\/(.+?)\/o\?uploadType=media&name=(.+)',
        method_url,
    ):
      self.test_case.assertEqual(headers, [f'Authorization: Bearer {self.auth_token}'])
      self.test_case.assertEqual(args[0], '--data-binary')
      self.test_case.assertEqual(args[1][0], '@')
      file_key = f'{match.group(1)}/{match.group(2)}'
      file_value = pathlib.Path(args[1][1:]).read_bytes()
      self.files[file_key] = file_value
      return '{}'

    # Download from GCS.
    if match := re.fullmatch(
        r'GET https:\/\/storage\.googleapis\.com\/storage\/v1\/b\/(.+?)\/o\/(.+?)\?alt=media',
        method_url,
    ):
      self.test_case.assertEqual(headers, [f'Authorization: Bearer {self.auth_token}'])
      self.test_case.assertEqual(args[0], '--output')
      self.test_case.assertEqual(args[2], '--fail-with-body')
      file_key = f'{match.group(1)}/{match.group(2)}'
      if file_key not in self.files:
        raise subprocess.CalledProcessError(1, [])
      pathlib.Path(args[1]).write_bytes(self.files[file_key])
      return '{}'

    # TODO(b/353307795): Implement other GCE calls.
    raise NotImplementedError(f'Unsupported curl call: {method_url}')


@contextlib.contextmanager
def make_fake_gce(test_case: absltest.TestCase, build_env: fake_build_env.FakeBuildEnv) -> Iterator[FakeGCE]:
  """Yields a fake GCE for testing."""
  with contextlib.ExitStack() as es:
    fake_gce = FakeGCE(test_case, build_env=build_env)
    es.enter_context(
        mock.patch.object(
            gce,
            '_curl',
            side_effect=fake_gce.curl,
        )
    )
    yield fake_gce