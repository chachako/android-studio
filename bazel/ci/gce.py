import json
import subprocess
import urllib.request


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