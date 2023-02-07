@rem  Builds Android Studio Windows fsNotifier
@rem  Usage:
@rem   build-win-fsnotifier.cmd  <out_dir> <dist_dir> <build_number>
@rem The binary is built in <out_dir>, and fsnotifier.exe artifact copied to  <dist_dir>

@setlocal enabledelayedexpansion

set OUTDIR=%1
set DISTDIR=%2
set BUILDNUMBER=%3
set SCRIPT_DIR=%~dp0
for %%F in ("%SCRIPT_DIR%..\..") do set TOP=%%~dpF
set CMAKE="C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake"

if not exist %OUTDIR% (mkdir %OUTDIR%)
cd %OUTDIR%rm
if exist WinFsNotifier rmdir /s /q WinFsNotifier
mkdir WinFsNotifier && cd WinFsNotifier

set BUILD_NUMBER=%BUILDNUMBER%
IF 1%BUILD_NUMBER% NEQ +1%BUILD_NUMBER% set BUILD_NUMBER=9999
%CMAKE% %TOP%tools\idea\native\WinFsNotifier
%CMAKE% --build . --config Release -A x64 -- -clp:ShowCommandLine

cd ..\..
if not exist %DISTDIR% (mkdir %DISTDIR%)
xcopy /f /y  %OUTDIR%\WinFsNotifier\Release\fsnotifier.exe %DISTDIR%