if /i "%*" EQU "--help" (
  echo PACK [FLAG]       Packs classes into .jar and .addon archives.
  echo      -A           Packs only addon class files.
  echo      -D           Packs only database class files.
  exit /b 0
)
setlocal
  set err=0
  if exist "%trackingClasses%\*" (
    if /i "%*" EQU "" (
      call :packAddon
      call :packDatabase
    ) else if /i "%*" EQU "-A" (
      call :packAddon
    ) else if /i "%*" EQU "-D" (
      call :packDatabase
    ) else (
      echo Unexpected parameter.
      set err=1
    )
  ) else (
    echo Please use the BUILD command first.
    set err=1
  )
endlocal & exit /b %err%

:packAddon
  echo Packing addon files...
  rmdir /Q /S "%classes%" >nul 2>nul
  for /f "usebackq tokens=1,2,* delims==" %%i in ("%trackingRecord%") do (
    set "p=%%k"
    if "!p!" EQU "!p:src\aces\webctrl\centralizer\database=!" (
      robocopy /E "%trackingClasses%\%%i" "%classes%" >nul 2>nul
    )
  )
  robocopy /E "%src%" "%classes%" /XF "*.java" /XD "%src%\aces\webctrl\centralizer\database" >nul 2>nul
  copy /Y "%workspace%\LICENSE" "%root%\LICENSE" >nul 2>nul
  "%JDKBin%\jar.exe" -c -M -f "%addonFile%" -C "%root%" .
  if %ERRORLEVEL% EQU 0 (
    echo Packing successful.
  ) else (
    echo Packing unsuccessful.
    set err=1
  )
exit /b

:packDatabase
  echo Packing database files...
  set "databaseRoot=%workspace%\database_jar"
  rmdir /Q /S "%databaseRoot%" >nul 2>nul
  for /f "usebackq tokens=1,2,* delims==" %%i in ("%trackingRecord%") do (
    set "p=%%k"
    if "!p!" EQU "!p:src\aces\webctrl\centralizer\addon=!" (
      robocopy /E "%trackingClasses%\%%i" "%databaseRoot%" >nul 2>nul
    )
  )
  robocopy /E "%src%" "%databaseRoot%" /XF "*.java" /XD "%src%\aces\webctrl\centralizer\addon" >nul 2>nul
  copy /Y "%workspace%\LICENSE" "%workspace%\database\LICENSE.txt" >nul 2>nul
  "%JDKBin%\jar.exe" -c -M -f "%workspace%\database\database.jar" -C "%databaseRoot%" .
  if %ERRORLEVEL% EQU 0 (
    echo Packing successful.
  ) else (
    echo Packing unsuccessful.
    set err=1
  )
exit /b