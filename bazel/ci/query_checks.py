"""Implements checks on the build graph using 'bazel query'."""
import bazel

class BuildGraphException(BaseException):
  """Represents a build graph exception."""
  title: str
  go_link: str
  body: str

  def __init__(self, title: str, go_link: str, body: str):
    self.title = title
    self.go_link = go_link
    self.body = body

  def __str__(self) -> str:
    return f"""#############################
# {self.title} - {self.go_link}
{self.body}
"""

def no_local_genrules(build_env: bazel.BuildEnv):
  """Verify targets are not using local=True.

  Build actions, like custom genrule targets, should use sandbox execution to
  avoid modifying the source workspace files.

  Raises:
    BuildGraphException: If build targets are using local=True.
  """
  bazel_cmd = bazel.BazelCmd(build_env)
  result = bazel_cmd.query('attr("local", "1", //...)')
  query_targets = result.stdout.decode('utf8').splitlines(keepends=True)
  with open('tools/base/bazel/ci/data/allowlist-targets-local-strategy.txt', encoding='utf8') as f:
    golden_targets = f.readlines()
  new_targets = list(set(query_targets) - set(golden_targets))

  if new_targets:
    raise BuildGraphException(
        title='Disallow local strategy',
        go_link='go/foo',
        body='ERROR: The following targets are using local=1\n'+''.join(new_targets),
    )


def require_cpu_tags(build_env: bazel.BuildEnv):
  """Require certain targets have cpu:N tags."""
  bazel_cmd = bazel.BazelCmd(build_env)
  result = bazel_cmd.query(
    'attr(tags, "ci:studio-mac", //...) except attr(tags, "cpu:[0-9]+", //...)')
  if not result.stdout:
    return

  raise BuildGraphException(
    title='MacOS test must have cpu:[0-9] tag',
    go_link='go/studio-ci#macos',
    body='ERROR: The following targets are missing a cpu:N tag.\n'+result.stdout
  )


def gradle_requires_cpu4_or_more(build_env: bazel.BuildEnv):
  """Tests running on MacOS using Gradle must declare cpu:4 or higher."""
  bazel_cmd = bazel.BazelCmd(build_env)
  studio_mac_tags = 'attr(tags, "ci:studio-mac", //...)'
  high_cpu_tags = 'attr(tags, "cpu:([4-9]|[1-9][1-9])", //...)'
  paths_to_gradle = f'allpaths({studio_mac_tags} except {high_cpu_tags}, //tools/base/build-system:gradle-distrib)'

  result = bazel_cmd.query(paths_to_gradle, '--output=minrank')
  if not result.stdout:
    return
  query_targets = result.stdout.decode('utf8').splitlines()
  # --output=minrank will prefix each target with a number, where 0 represents
  # root targets.
  query_targets = [s.removeprefix('0') for s in query_targets if s.startswith('0')]
  msg = 'ERROR: The following targets depend on //tools/base/build-system:gradle-distrib and must have the tag cpu:4 or greater.'
  raise BuildGraphException(
        title='Gradle tests need cpu:4 or greater',
        go_link='go/studio-ci#macos',
        body=(
            'ERROR: The follow targets depend on //tools/base/build-system:gradle-distrib'
            ' and must have cpu:4 or greater\n'
        ) + '\n'.join(query_targets)
    )
