# `ci` command.

`./ci target`

A python script for running continuous integration (CI) builds. Other scripts
may eventually be converted to `ci` targets to reduce duplication and avoid
error-prone shell scripts.

# Postsubmit testing

Other scripts in this directory are intended to be owned and maintained by
their respective teams. android-devtools-infra@ must review changes to
these scripts to verify they will not negatively effect shared
resources.

See go/studio-postsubmit for more details and how to set up a new
branch + target configuration.
