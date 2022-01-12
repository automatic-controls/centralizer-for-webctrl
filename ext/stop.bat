if /i "%*" EQU "--help" (
  echo STOP              Stops the Windows service for the database application.
  exit /b 0
) else if "%*" NEQ "" (
  echo Unexpected parameter.
  exit /b 1
)
setlocal
  set "winsw=%workspace%\database\winsw.exe"
  if not exist "%winsw%" (
    echo Please install WinSW before using this command.
    echo https://github.com/winsw/winsw
    echo Place the WinSW executable at:
    echo %winsw%
    exit /b 1
  )
  cscript "%workspace%\database\stop.vbs" >nul
exit /b %ERRORLEVEL%