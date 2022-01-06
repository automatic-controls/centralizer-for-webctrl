if /i "%*" EQU "--help" (
  echo INSTALL           Launches the database installation script.
  exit /b 0
) else if "%*" NEQ "" (
  echo Unexpected parameter.
  exit /b 1
)
if exist "%workspace%\database\winsw.exe" (
  cscript "%workspace%\database\install.vbs" >nul
) else (
  echo Please install WinSW before using this command.
  echo https://github.com/winsw/winsw
  echo Place the WinSW executable at:
  echo %workspace%\database\winsw.exe
)
exit /b %ERRORLEVEL%