"""A generalized version of expand_template

This behaves like ctx.actions.expand_template, but also can take
files to substitute with not just constant string.
"""

import re
import sys

def _read_file(path):
  with open(path, "r") as f:
    return f.read()


def _expand_template(template, out, replace):

  content = _read_file(template)

  for k,v in replace.items():
    nv = ""
    ix = 0
    for m in re.finditer("\\$\\(inline ([^)]+)\\)", v):
      nv += v[ix:m.start()]
      nv += _read_file(m.group(1))
      ix = m.end()
    nv += v[ix:]
    replace[k] = nv

  n = len(content)
  i = 0
  ret = ""
  while i < n:
    finds = [(content.find(var, i), var) for var in replace.keys()]
    first = [(ix, var) for ix, var in sorted(finds) if ix >= 0]
    if first:
      ix, var = first[0]
      ret += content[i:ix]
      ret += replace[var]
      i = ix + len(var)
    else:
      ret += content[i:]
      i = n

  with open(out, "w") as f:
    f.write(ret)


def main(argv):
  # Using argparse is not good because replacement arguments sometimes look like flags.
  template = None
  out = None
  replace = {}
  i = 0
  while i < len(argv):
    if argv[i] == "--template" and i + 1 < len(argv):
      template = argv[i+1]
      i += 2
    elif argv[i] == "--out" and i + 1 < len(argv):
      out = argv[i+1]
      i += 2
    elif argv[i] == "--replace" and i + 2 < len(argv):
      replace[argv[i+1]] = argv[i+2]
      i += 3
    else:
      print("Failed to parse arguments after: " + argv[i:])
      sys.exit(1)

  if not template:
      print("No template argument provided")
      sys.exit(1)

  if not out:
      print("No output file provided")
      sys.exit(1)

  if not replace:
      print("Nothing to replace")
      sys.exit(1)

  _expand_template(template, out, replace)

if __name__ == "__main__":
  main(sys.argv[1:])