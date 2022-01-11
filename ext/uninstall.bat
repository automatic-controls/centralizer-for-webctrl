if /i "%*" EQU "--help" (
  echo UNINSTALL         Launches the database uninstallation script.
  exit /b 0
) else if "%*" NEQ "" (
  echo Unexpected parameter.
  exit /b 1
)
if not exist "%workspace%\database\winsw.exe" (
  echo Please install WinSW before using this command.
  echo https://github.com/winsw/winsw
  echo Place the WinSW executable at:
  echo %workspace%\database\winsw.exe
  exit /b 1
)
cscript "%workspace%\database\uninstall.vbs" >nul
exit /b %ERRORLEVEL%