@echo off
title Centralizer for WebCTRL Installation Manager
setlocal EnableDelayedExpansion
net session >nul 2>&1
if %ErrorLevel% NEQ 0 (
  echo Error - This script must be ran as administrator.
  echo Press any key to exit...
  pause >nul
  exit
)
if /i "%*" EQU "install" (
  call :install
) else if /i "%*" EQU "uninstall" (
  call :uninstall
) else if /i "%*" EQU "start" (
  call :start
) else if /i "%*" EQU "stop" (
  call :stop
) else (
  echo Please use the vbs scripts instead of invoking this file directly.
)
echo Press any key to exit...
pause >nul
exit

:start
  if not exist "%~dp0service.xml" (
    call :generateServiceXML
  )
  for /f "tokens=1 delims= " %%i in ('""%~dp0winsw" status "%~dp0service.xml""') do set "stat=%%i"
  if "%stat%" EQU "Active" (
    echo The service is already running.
  ) else if "%stat%" EQU "Inactive" (
    "%~dp0winsw" start "%~dp0service.xml"
  ) else if "%stat%" EQU "NonExistent" (
    echo The service is not installed.
  ) else (
    echo The service has unexpected status: %stat%.
  )
exit /b

:stop
  if not exist "%~dp0service.xml" (
    call :generateServiceXML
  )
  for /f "tokens=1 delims= " %%i in ('""%~dp0winsw" status "%~dp0service.xml""') do set "stat=%%i"
  if "%stat%" EQU "Active" (
    "%~dp0winsw" stop "%~dp0service.xml"
  ) else if "%stat%" EQU "Inactive" (
    echo The service is already stopped.
  ) else if "%stat%" EQU "NonExistent" (
    echo The service is not installed.
  ) else (
    echo The service has unexpected status: %stat%.
  )
exit /b

:install
  call :generateServiceXML
  for /f "tokens=1 delims= " %%i in ('""%~dp0winsw" status "%~dp0service.xml""') do set "stat=%%i"
  if "%stat%" EQU "NonExistent" (
    "%~dp0winsw" install "%~dp0service.xml"
    "%~dp0winsw" start "%~dp0service.xml"
  ) else (
    echo The service is already installed.
  )
exit /b

:uninstall
  if not exist "%~dp0service.xml" (
    call :generateServiceXML
  )
  for /f "tokens=1 delims= " %%i in ('""%~dp0winsw" status "%~dp0service.xml""') do set "stat=%%i"
  if "%stat%" EQU "NonExistent" (
    echo The service is not installed.
  ) else (
    echo Are you sure you want to uninstall Centralizer for WebCTRL?
    echo Type YES to initiate the uninstallation procedure.
    set /p "x=>"
    if /i "!x!" EQU "YES" (
      if "%stat%" EQU "Active" (
        "%~dp0winsw" stop "%~dp0service.xml"
        call :uninstallFunc
      ) else if "%stat%" EQU "Inactive" (
        call :uninstallFunc
      ) else (
        echo Unexpected status - %stat%
      )
    ) else (
      echo Uninstallation procedure aborted.
    )
  )
exit /b

:uninstallFunc
  "%~dp0winsw" uninstall "%~dp0service.xml"
  rmdir /S /Q "%HomeDrive%\Centralizer for WebCTRL"
  echo.
  echo To complete uninstallation, please delete "%~dp0".
  echo Or run "%~dp0install.vbs" to reinstall the service.
exit /b

:generateServiceXML
  (
    echo ^<service^>
    echo   ^<id^>centralizerDatabaseForWebCTRL^</id^>
    echo   ^<name^>Centralizer for WebCTRL^</name^>
    echo   ^<description^>This service manages a database which is intended to interface with the corresponding WebCTRL add-on.^</description^>
    echo   ^<env name="HomeDrive" value="%HomeDrive%"/^>
    echo   ^<executable^>%%BASE%%\jre\bin\java^</executable^>
    echo   ^<arguments^>-cp "%%BASE%%\*" aces.webctrl.centralizer.database.Main^</arguments^>
    echo   ^<stoptimeout^>60sec^</stoptimeout^>
    echo ^</service^>
  ) > "%~dp0service.xml"
exit /b
