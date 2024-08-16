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

    self.dist_path = root_dir / 'dist'
    self.dist_path.mkdir()
    self.tmp_path = root_dir / 'tmp'
    self.tmp_path.mkdir()

    self.dist_dir = str(self.dist_path)
    self.tmp_dir = str(self.tmp_path)


@contextlib.contextmanager
def make_fake_build_env() -> Iterator[bazel.BuildEnv]:
  """Yields a fake build environment for testing."""
  with contextlib.ExitStack() as es:
    root_dir = es.enter_context(tempfile.TemporaryDirectory())
    yield FakeBuildEnv(pathlib.Path(root_dir))