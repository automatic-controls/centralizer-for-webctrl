if /i "%*" EQU "--help" (
  echo STATUS            Detemine whether the Windows service is currently active.
  exit /b 0
) else if "%*" NEQ "" (
  echo Unexpected parameter.
  exit /b 1
)
sc query centralizerDatabaseForWebCTRL >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  sc query centralizerDatabaseForWebCTRL | findstr /C:"STATE              : 4  RUNNING" >nul 2>nul
  if !ERRORLEVEL! EQU 0 (
    echo RUNNING
  ) else (
    echo STOPPED
  )
) else (
  echo NOT INSTALLED
)
exit /b 0