"""Implements a fake GCE for testing."""

import contextlib
import json
from typing import Iterator, Dict, List
from unittest import mock

from tools.base.bazel.ci import fake_build_env
from tools.base.bazel.ci import gce


class FakeGCE:
  """Fake GCE for testing."""

  def __init__(self, build_env: fake_build_env.FakeBuildEnv):
    self.build_env = build_env
    self.auth_token = 'fake-token'
    self.reference_build_id = '789'
    self.changes: List[gce.GerritChange] = []
    self.files: Dict[str, str] = {}

  def curl(self, method: str, headers: List[str], url: str, *args: List[str]) -> str:
    """Fakes the curl method."""
    bid = self.build_env.build_number
    target = self.build_env.build_target_name
    method_url = f'{method} {url}'

    if method_url == 'GET http://metadata/computeMetadata/v1/instance/service-accounts/default/token':
      return json.dumps({
          'access_token': self.auth_token,
      })
    if method_url == f'GET https://androidbuildinternal.googleapis.com/android/internal/build/v3/builds/{bid}/{target}':
      return json.dumps({
          'referenceBuildIds': [self.reference_build_id],
      })
    # TODO(b/353307795): Implement other GCE calls.
    raise NotImplementedError(f'Unsupported curl call: {method_url}')


@contextlib.contextmanager
def make_fake_gce(build_env: fake_build_env.FakeBuildEnv) -> Iterator[FakeGCE]:
  """Yields a fake GCE for testing."""
  with contextlib.ExitStack() as es:
    fake_gce = FakeGCE(build_env=build_env)
    es.enter_context(
        mock.patch.object(
            gce,
            '_curl',
            side_effect=fake_gce.curl,
        )
    )
    yield fake_gce