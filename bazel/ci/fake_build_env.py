"""Implements a fake build environment for testing."""

import contextlib
import pathlib
import tempfile
from typing import Iterator

from tools.base.bazel.ci import bazel


class FakeBuildEnv(bazel.BuildEnv):
  """Fake build environment for testing."""

  def __init__(self, root_dir: pathlib.Path):
    super().__init__('', 'user')
    self.build_number = 'P123'
    self.build_target_name = 'studio-test'
    self.workspace_dir = str(root_dir / 'workspace')
    self.dist_dir = str(root_dir / 'dist')
    self.tmp_dir = str(root_dir / 'tmp')


@contextlib.contextmanager
def make_fake_build_env() -> Iterator[bazel.BuildEnv]:
  """Yields a fake build environment for testing."""
  with contextlib.ExitStack() as es:
    root_dir = es.enter_context(tempfile.TemporaryDirectory())
    yield FakeBuildEnv(pathlib.Path(root_dir))