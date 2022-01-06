if /i "%*" EQU "--help" (
  echo JRE               Creates a JRE runtime image for the database.
  exit /b 0
)
setlocal
  set err=0
  set "install=%workspace%\database"
  rmdir /Q /S "%install%\jre" 2>nul
  if not exist "%install%\database.jar" (
    echo Please use the PACK command first.
    set err=1
  ) else (
    echo Creating runtime image...
    for /f "delims=" %%i in ('jdeps --print-module-deps "%install%\database.jar"') do set "modules=%%i"
    jlink --output "%install%\jre" --add-modules !modules!
    if !ERRORLEVEL! EQU 0 (
      echo Operation successful.
    ) else (
      echo Operation unsuccessful.
      set err=1
    )
  )
endlocal & exit /b %err%