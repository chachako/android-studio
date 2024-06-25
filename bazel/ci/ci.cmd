set WORKSPACE=%~dp0\..\..\..\..
set BAZELISK=%WORKSPACE%\prebuilts\tools\windows-x86_64\bazel\bazelisk.exe
%BAZELISK% --max_idle_secs=60 run //tools/base/bazel/ci -- %1