"""Implements a fake GCE for testing."""

import contextlib
import json
import pathlib
import re
import subprocess
from typing import Iterator, Dict, List, Tuple
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

    # Gerrit changes.
    if method_url == f'GET https://androidbuildinternal.googleapis.com/android/internal/build/v3/changes/{bid}':
      json_changes = []
      for change in self.changes:
        json_changes.append({
            'changeId': change.change_id,
            'changeNumber': change.change_number,
            'revisions': [{
                'patchSet': change.patchset,
                'commit': {
                    'commitMessage': change.message,
                },
            }],
            'owner': {
                'email': f'{change.owner}@google.com',
            },
            'topic': change.topic,
        })
      return json.dumps({'changes': json_changes})

    raise NotImplementedError(f'Unsupported curl call: {method_url}')

  def add_change(self, owner: str, message: str, tags: List[Tuple[str, str]]) -> gce.GerritChange:
    """Adds and returns a new fake Gerrit change.

    The change ID, change number, and patchset are automatically generated. All
    fields can be altered after creation.

    Args:
      owner: The owner of the change.
      message: The commit message of the change without tags.
      tags: The tags of the change.
    """
    index = len(self.changes)
    change = gce.GerritChange(
        change_id=f'changeid{index}',
        change_number=str(index),
        patchset=index,
        owner=f'{owner}@google.com',
        message=f'{message}\n\n' + '\n'.join(f'{k}: {v}' for k, v in tags),
        topic='',
        tags=tags,
    )
    self.changes.append(change)
    return change


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