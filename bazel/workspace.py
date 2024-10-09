# This file prints a Bazel workspace status, and is configured
# from a bazelrc file. See the Bazel manual for more:
# https://docs.bazel.build/versions/main/user-manual.html#workspace_status
#
# This is written in Python to be platform independent.
import getpass
import os
import socket
import sys


def getuser():
  """Gets the logged in user.

  Fallback to os.getlogin() since getpass on Windows may raise an exception.

  Replace android-build with atp-dev. This is to get the kelloggs service to
  filter correctly because it only accepts atp-dev.
  """
  try:
    user = getpass.getuser()
  except:
    user = os.getlogin()
  if user == 'android-build':
    return 'atp-dev'
  return user

# Always use \n, even on Windows.
sys.stdout.reconfigure(newline='\n')

print('BUILD_USER %s' % getuser())
print('BUILD_USERNAME %s' % getuser())
print('BUILD_HOSTNAME %s' % socket.gethostname())

