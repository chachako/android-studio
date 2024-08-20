import dataclasses
import functools
import json
import re
import subprocess
from typing import List, Tuple
import urllib.parse
import urllib.request


@dataclasses.dataclass
class GerritChange:
  """Gerrit change data for a build."""
  change_id: str
  change_number: str
  patchset: str
  owner: str
  message: str
  topic: str
  tags: List[Tuple[str,str]]


def _curl(method: str, headers: List[str], url: str, *args: List[str]) -> str:
  """Makes a curl request and returns the response."""
  cmd = ['curl', '-X', method]
  for header in headers:
    cmd.extend(['-H', header])
  cmd.append(url)
  cmd.extend(args)
  result = subprocess.run(cmd, check=True, capture_output=True)
  return result.stdout.decode('utf-8')


def get_auth_header() -> List[str]:
  """Returns the Authorization header used to talk to GCE."""
  # TODO(b/353307795): Use GCE_METADATA_HOST once available.
  response = _curl(
      'GET',
      ['Metadata-Flavor: Google'],
      'http://metadata/computeMetadata/v1/instance/service-accounts/default/token',
  )
  token = json.loads(response)['access_token']
  return [f'Authorization: Bearer {token}']


def upload_to_gcs(src: str, bucket: str, name: str) -> None:
  """Uploads a file to GCS."""
  # curl is used instead of urllib.request due to the prebuilt Python binary
  # being built without SSL support.
  name = urllib.parse.quote(name, safe='')
  _curl(
      'POST',
      get_auth_header(),
      f'https://storage.googleapis.com/upload/storage/v1/b/{bucket}/o?uploadType=media&name={name}',
      '--data-binary',
      f'@{src}',
  )


def download_from_gcs(bucket: str, name: str, dst: str) -> bool:
  """Downloads a file from GCS and returns whether the download was successful."""
  name = urllib.parse.quote(name, safe='')
  try:
    _curl(
        'GET',
        get_auth_header(),
        f'https://storage.googleapis.com/storage/v1/b/{bucket}/o/{name}?alt=media',
        '--output',
        dst,
        '--fail-with-body',
    )
  except subprocess.CalledProcessError:
    return False
  return True


@functools.cache
def get_reference_build_id(bid: str, target: str) -> str:
  """Returns the reference build ID for the current build."""
  result = _curl(
      'GET',
      get_auth_header(),
      f'https://androidbuildinternal.googleapis.com/android/internal/build/v3/builds/{bid}/{target}',
  )

  return json.loads(result)['referenceBuildIds'][0]


@functools.cache
def get_gerrit_changes(bid: str) -> List[GerritChange]:
  """Returns the Gerrit change data for the current build."""
  result = _curl(
      'GET',
      get_auth_header(),
      f'https://androidbuildinternal.googleapis.com/android/internal/build/v3/changes/{bid}',
  )
  data = json.loads(result)

  changes = []
  for change in data['changes']:
    # Strip @google.com from the owner email.
    owner = change['owner']['email'].removesuffix('@google.com')

    # Extract tags from the commit message.
    revision = change['revisions'][0]
    message = revision['commit']['commitMessage']
    tags = []
    for line in message.splitlines():
      match = re.fullmatch(r'^([\w-]+):\s*(.+)$', line)
      if not match:
        continue
      tags.append((match.group(1), match.group(2)))

    gerrit_change = GerritChange(
        change_id=change['changeId'],
        change_number=change['changeNumber'],
        patchset=change['revisions'][0]['patchSet'],
        owner=owner,
        message=message,
        topic=change.get('topic', ''),
        tags=tags,
    )
    changes.append(gerrit_change)

  return changes