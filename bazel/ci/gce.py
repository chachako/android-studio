import dataclasses
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
  owner: str
  message: str
  tags: List[Tuple[str,str]]


def get_auth_header() -> str:
  """Returns the Authorization header used to talk to GCE."""
  # TODO(b/353307795): Use GCE_METADATA_HOST once available.
  request = urllib.request.Request(
      f'http://metadata/computeMetadata/v1/instance/service-accounts/default/token',
      headers={'Metadata-Flavor': 'Google'},
  )
  response = urllib.request.urlopen(request)
  token = json.load(response)['access_token']
  return f'Authorization: Bearer {token}'


def upload_to_gcs(src: str, bucket: str, name: str) -> None:
  """Uploads a file to GCS."""
  # curl is used instead of urllib.request due to the prebuilt Python binary
  # being built without SSL support.
  name = urllib.parse.quote(name, safe='')
  cmd = [
      'curl',
      '-H',
      get_auth_header(),
      '-X',
      'POST',
      f'https://storage.googleapis.com/upload/storage/v1/b/{bucket}/o?uploadType=media&name={name}',
      '--data-binary',
      f'@{src}',
  ]
  subprocess.run(cmd, check=True)


def download_from_gcs(bucket: str, name: str, dst: str) -> bool:
  """Downloads a file from GCS and returns whether the download was successful."""
  name = urllib.parse.quote(name, safe='')
  cmd = [
      'curl',
      '--fail-with-body',
      '-H',
      get_auth_header(),
      '-X',
      'GET',
      f'https://storage.googleapis.com/storage/v1/b/{bucket}/o/{name}?alt=media',
      '--output',
      dst,
  ]
  result = subprocess.run(cmd)
  return result.returncode == 0


def get_reference_build_id(bid: str, target: str) -> str:
  """Returns the reference build ID for the current build."""
  url = f'https://androidbuildinternal.googleapis.com/android/internal/build/v3/builds/{bid}/{target}'
  cmd = [
      'curl',
      url,
      '-H',
      get_auth_header(),
  ]
  result = subprocess.run(cmd, check=True, capture_output=True)

  return json.loads(result.stdout.decode('utf-8'))['referenceBuildIds'][0]


def get_gerrit_changes(bid: str) -> List[GerritChange]:
  """Returns the Gerrit change data for the current build."""
  url = f'https://androidbuildinternal.googleapis.com/android/internal/build/v3/changes/{bid}'
  cmd = [
      'curl',
      url,
      '-H',
      get_auth_header(),
  ]
  result = subprocess.run(cmd, check=True, capture_output=True)
  data = json.loads(result.stdout.decode('utf-8'))

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
        owner=owner,
        message=message,
        tags=tags,
    )
    changes.append(gerrit_change)

  return changes