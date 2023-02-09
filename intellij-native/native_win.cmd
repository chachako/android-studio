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

@echo "Building breakgen64.dll"
call %SCRIPT_DIR%build-win-breakgen.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
if errorlevel 1 (
  set /A EXITCODE=EXITCODE+1
)
@echo native_win.cmd time: %time%

@echo "Building WinProcessListHelper"
call %SCRIPT_DIR%build-win-process-list-helper.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
if errorlevel 1 (
  set /A EXITCODE=EXITCODE+1
)
@echo native_win.cmd time: %time%

@echo "Building WinRestarter"
call %SCRIPT_DIR%build-win-restarter.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
if errorlevel 1 (
  set /A EXITCODE=EXITCODE+1
)
@echo native_win.cmd time: %time%


@rem Disabled. requires Active Template Library (ATL) that VS don't have installed currently
@rem
@rem @echo "Building WinShellIntegration"
@rem call %SCRIPT_DIR%build-win-shell-integration.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
@rem if errorlevel 1 (
@rem   set /A EXITCODE=EXITCODE+1
@rem )
@rem @echo native_win.cmd time: %time%

@rem Disabled. requires "MSVC v140 - VS 2015 C++ build tools" and "Windows XP support for C++"
@rem
@rem @echo "Building runnerw.exe"
@rem call %SCRIPT_DIR%build-win-runnerw.cmd %OUTDIR% %DISTDIR% %BUILDNUMBER%
@rem if errorlevel 1 (
@rem   set /A EXITCODE=EXITCODE+1
@rem )
@rem @echo native_win.cmd time: %time%

@echo "All Done!"
exit /b %EXITCODE%