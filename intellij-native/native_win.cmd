@rem # Build Intellij native tools for Windows
setlocal enabledelayedexpansion

@echo native_win.cmd start time: %time%
@echo "Environment variables:"
@echo "==================================================="
set
@echo "==================================================="

set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3
set SCRIPT_DIR=%~dp0
@echo "Called with: OUTDIR=%OUTDIR%, DISTDIR=%DISTDIR%, BUILDNUMBER=%BUILDNUMBER%, SCRIPTDIR=%SCRIPTDIR%"

set /a EXITCODE=0

@echo "Building WinLauncher"
call %SCRIPT_DIR%build-win-launcher.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
if errorlevel 1 (
  set /A EXITCODE=EXITCODE+1
)
@echo native_win.cmd time: %time%

@echo "Building WinFsNotifier"
call %SCRIPT_DIR%build-win-fsnotifier.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
if errorlevel 1 (
  set /A EXITCODE=EXITCODE+1
)
@echo native_win.cmd time: %time%


@echo "All Done!"
exit /b %EXITCODE%