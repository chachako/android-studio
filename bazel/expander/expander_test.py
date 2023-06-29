import os
import unittest
from tools.base.bazel.expander import expander


def read_file(path):
  with open(path) as f:
    return f.read()


def get_path(name):
  return os.path.join(os.getenv("TEST_TMPDIR"), name)


def create_file(name, content):
  path = get_path(name)
  with open(path, "w") as f:
    f.write(content)
  return path


class ExpanderTest(unittest.TestCase):

  def test_rule(self):
    actual = read_file("tools/base/bazel/expander/actual.txt")
    expected = read_file("tools/base/bazel/expander/test_data/expected.txt")
    self.assertEqual(expected, actual)

  def test_simple(self):
    src = create_file("template.txt", "FOO 123\nBAR 456\nBAZ 789")
    dst = get_path("out.txt")
    expander.main(["--template", src, "--out", dst, "--replace", "FOO", "VAL"])
    self.assertEqual("VAL 123\nBAR 456\nBAZ 789", read_file(dst))

  def test_two(self):
    src = create_file("template.txt", "FOO 123\nBAR 456\nBAZ 789")
    dst = get_path("out.txt")
    expander.main(["--template", src, "--out", dst, "--replace", "FOO", "VAL",  "--replace", "BAR", "BAL"])
    self.assertEqual("VAL 123\nBAL 456\nBAZ 789", read_file(dst))

  def test_prefix(self):
    src = create_file("template.txt", "FOO 123\nFOOZ 456\nBAZ 789")
    dst = get_path("out.txt")
    expander.main(["--template", src, "--out", dst, "--replace", "FOO", "VAL",  "--replace", "FOOZ", "BAL"])
    self.assertEqual("VAL 123\nVALZ 456\nBAZ 789", read_file(dst))

  def test_nested(self):
    src = create_file("template.txt", "FOO 123\nBAR 456\nBAZ 789")
    dst = get_path("out.txt")
    expander.main(["--template", src, "--out", dst, "--replace", "FOO", "BAR",  "--replace", "BAR", "VAL"])
    self.assertEqual("BAR 123\nVAL 456\nBAZ 789", read_file(dst))

  def test_file(self):
    src = create_file("template.txt", "FOO 123\nBAR 456\nBAZ 789")
    foo = create_file("foo.txt", "FOO 123\nBAR 456\nBAZ 789")
    dst = get_path("out.txt")
    expander.main(["--template", src, "--out", dst, "--replace", "FOO", "$(inline %s)" % foo,  "--replace", "BAR", "VAL"])
    self.assertEqual("FOO 123\nBAR 456\nBAZ 789 123\nVAL 456\nBAZ 789", read_file(dst))


if __name__ == "__main__":
  unittest.main()
