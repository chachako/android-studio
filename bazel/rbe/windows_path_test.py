from absl.testing import absltest

import os


def write_directory_contents(out_dir, path):
  with open(out_dir, 'w') as f:
    for dirname, _, filenames in os.walk(path):
      for filename in filenames:
        f.write(os.path.join(dirname, filename) + '\n')


class FooTest(absltest.TestCase):

  def test_write_directory_contents(self):
    """Write the Windows toolchain directory contents to the undeclared outputs directory."""
    out_dir = os.environ.get('TEST_UNDECLARED_OUTPUTS_DIR')
    write_directory_contents(
        os.path.join(out_dir, 'windows-sdk.txt'),
        'C:\\Program Files (x86)\\Windows Kits')

    write_directory_contents(
        os.path.join(out_dir, 'visual-studio.txt'),
        'C:\\Program Files (x86)\\Microsoft Visual Studio')


if __name__ == '__main__':
  absltest.main()