@rem Invoked by Android Build Launchcontrol for continuous builds.
@rem Windows Android Studio Remote Bazel Execution Script.
setlocal enabledelayedexpansion
set PATH=c:\tools\msys64\usr\bin;%PATH%

@rem The current directory the executing script is in.
set SCRIPTDIR=%~dp0
call :normalize_path "%SCRIPTDIR%..\..\.." BASEDIR


@rem Positional arguments:
set OUTDIR=%_arg1%
set DISTDIR=%_arg2%
set BUILDNUMBER=%_arg3%

if not defined DISTDIR (
  set DISTDIR=%TEMP%
)

if not defined BUILDNUMBER (
  set BUILD_TYPE=LOCAL
) else if "%BUILDNUMBER:~0,1%"=="P" (
  @rem It is a presubmit build if the build number starts with "P"
  set BUILD_TYPE=PRESUBMIT
) else (
  set BUILD_TYPE=POSTSUBMIT
)

@echo "Called with: OUTDIR=%OUTDIR%, DISTDIR=%DISTDIR%, BUILDNUMBER=%BUILDNUMBER%, SCRIPTDIR=%SCRIPTDIR%, BASEDIR=%BASEDIR%"
@echo "Build type: %BUILD_TYPE%"

:run_bazel_test
setlocal
  @rem Run tests multiple times to aid flake detection.
  if %BUILD_TYPE%==POSTSUBMIT (
    set AB_POSTSUBMIT=--bes_keywords=ab-postsubmit
    set NOCACHE=--nocache_test_results
    set FLAKY_ATTEMPTS=--flaky_test_attempts=2
    set CONDITIONAL_FLAGS=!NOCACHE! !FLAKY_ATTEMPTS! !ANTS! !AB_POSTSUBMIT!
  )

  set TESTTAGFILTERS=qa_smoke,ui_test,-no_windows,-no_test_windows,-qa_fast,-qa_unreliable,-perfgate

  @rem Generate a UUID for use as the Bazel invocation ID
  for /f "tokens=*" %%f in ('uuidgen') do (
    set INVOCATIONID=%%f
  )
  if exist %DISTDIR%\ (
    echo ^<head^>^<meta http-equiv="refresh" content="0; url='https://fusion2.corp.google.com/invocations/%INVOCATIONID%'" /^>^</head^> > %DISTDIR%\upsalite_test_results.html
  )

  set TARGETS=
  for /f %%i in (%SCRIPTDIR%/../targets.win) do set TARGETS=!TARGETS! %%i

  @echo studio_win.cmd time: %time%
  @rem Run Bazel
  call %SCRIPTDIR%/../bazel.cmd ^
  --max_idle_secs=60 ^
  test ^
  --config=ci ^
  --config=ants ^
  --tool_tag=qa_studio_win.cmd ^
  --build_tag_filters=-no_windows ^
  --invocation_id=%INVOCATIONID% ^
  --build_event_binary_file=%DISTDIR%\bazel-%BUILDNUMBER%.bes ^
  --test_tag_filters=%TESTTAGFILTERS% ^
  --build_metadata=ANDROID_BUILD_ID=%BUILDNUMBER% ^
  --build_metadata=ANDROID_TEST_INVESTIGATE="http://ab/tests/bazel/%INVOCATIONID%" ^
  --build_metadata=ab_build_id=%BUILDNUMBER% ^
  --build_metadata=ab_target=qa-win_smoke ^
  --profile=%DISTDIR%\winprof%BUILDNUMBER%.json.gz ^
  %CONDITIONAL_FLAGS% ^
  -- ^
  //tools/base/profiler/native/trace_processor_daemon ^
  %TARGETS%
endlocal & set /a EXITCODE=%ERRORLEVEL%

@echo qa_smoke_win.cmd time: %time%

if not exist %DISTDIR%\ goto endscript

@echo qa_smoke_win.cmd time: %time%

:endscript
@rem On windows we must explicitly shut down bazel. Otherwise file handles remain open.
@echo qa_smoke_win.cmd time: %time%
call %SCRIPTDIR%/../bazel.cmd shutdown
@echo qa_smoke_win.cmd time: %time%

set /a BAZEL_EXITCODE_TEST_FAILURES=3

if %EXITCODE% equ %BAZEL_EXITCODE_TEST_FAILURES% (
  exit /b 0
)
exit /b %EXITCODE%

@rem Normalizes a path from Arg 1 and store the result into Arg 2.
:normalize_path
  set %2=%~dpfn1
  exit /b

