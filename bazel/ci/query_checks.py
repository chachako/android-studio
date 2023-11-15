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
  pass  # Implemented in follow-up change.
