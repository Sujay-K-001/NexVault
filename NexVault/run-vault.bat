@echo off
setlocal EnableExtensions EnableDelayedExpansion
title NexVault
cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem  NexVault launcher: checks Java, fetches dependencies (first run only),
rem  compiles, and starts the app. Extra arguments are passed to NexVault, e.g.
rem     run-vault.bat --browser-default      (use your normal browser)
rem     run-vault.bat --test                 (build and run the automated tests)
rem ---------------------------------------------------------------------------

set "SQLITE_VER=3.46.1.0"
set "SLF4J_VER=2.0.13"
set "MAVEN=https://repo1.maven.org/maven2"
set "LIB=%~dp0lib"
set "OUT=%~dp0build\classes"

echo.
echo  ==========================================
echo    NexVault - local password vault
echo  ==========================================
echo.

rem ---- 1. Java 21+ JDK ----------------------------------------------------
set "JAVAC=javac"
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "JAVA=%JAVA_HOME%\bin\java.exe"
)
"%JAVAC%" -version > "%TEMP%\nexvault_jv.txt" 2>&1
if errorlevel 1 goto :nojdk
set "JV=0"
for /f "tokens=2" %%v in ('findstr /b /c:"javac " "%TEMP%\nexvault_jv.txt"') do set "JV=%%v"
del "%TEMP%\nexvault_jv.txt" >nul 2>&1
for /f "tokens=1 delims=." %%m in ("!JV!") do set "JM=%%m"
set "JMN=0"
set /a JMN=!JM! 2>nul
if !JMN! LSS 21 goto :oldjdk
echo  [ok] JDK !JV! found

rem ---- 2. Dependencies (downloaded once into lib\) -------------------------
if not exist "%LIB%" mkdir "%LIB%"
call :fetch "sqlite-jdbc-%SQLITE_VER%.jar" "%MAVEN%/org/xerial/sqlite-jdbc/%SQLITE_VER%/sqlite-jdbc-%SQLITE_VER%.jar" || goto :fetchfail
call :fetch "slf4j-api-%SLF4J_VER%.jar" "%MAVEN%/org/slf4j/slf4j-api/%SLF4J_VER%/slf4j-api-%SLF4J_VER%.jar" || goto :fetchfail
call :fetch "slf4j-nop-%SLF4J_VER%.jar" "%MAVEN%/org/slf4j/slf4j-nop/%SLF4J_VER%/slf4j-nop-%SLF4J_VER%.jar" || goto :fetchfail
set "CP=%OUT%;%LIB%\sqlite-jdbc-%SQLITE_VER%.jar;%LIB%\slf4j-api-%SLF4J_VER%.jar;%LIB%\slf4j-nop-%SLF4J_VER%.jar"

rem ---- 2b. Typefaces (optional): Urbanist + Space Grotesk, SIL Open Font License. The UI falls back to system fonts if missing.
set "FONTDIR=%~dp0src\main\resources\web\fonts"
set "FONTCDN=https://cdn.jsdelivr.net/npm/@fontsource"
if not exist "%FONTDIR%" mkdir "%FONTDIR%"
for %%w in (400 500 600) do call :fetchfont "Urbanist-%%w.woff2" "%FONTCDN%/urbanist@5/files/urbanist-latin-%%w-normal.woff2"
for %%w in (400 500 600 700) do call :fetchfont "SpaceGrotesk-%%w.woff2" "%FONTCDN%/space-grotesk@5/files/space-grotesk-latin-%%w-normal.woff2"
dir /b "%FONTDIR%\*odber*" >nul 2>&1 || echo  [info] Logo font not found: put Godber.woff2 (or .otf/.ttf) in src\main\resources\web\fonts. A serif fallback is used meanwhile.

rem ---- 3. Compile ----------------------------------------------------------
echo  [..] Building NexVault
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
if exist "%~dp0build\sources.txt" del "%~dp0build\sources.txt"
for /r "%~dp0src\main\java" %%f in (*.java) do (
    set "p=%%f"
    echo "!p:\=/!">>"%~dp0build\sources.txt"
)
"%JAVAC%" -encoding UTF-8 --release 21 -d "%OUT%" @"%~dp0build\sources.txt"
if errorlevel 1 goto :buildfail
xcopy "%~dp0src\main\resources" "%OUT%" /e /i /y /q >nul
echo  [ok] Build complete

if /i "%~1"=="--test" goto :tests

rem ---- 4. Run ---------------------------------------------------------------
echo.
echo  NexVault is starting. A window will open in a few seconds.
echo  Keep THIS window open while you use NexVault.
echo  Close it, press Ctrl+C, or use Quit inside the app to stop.
echo.
"%JAVA%" -Dfile.encoding=UTF-8 -cp "%CP%" com.nexvault.Main %*
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" goto :runfail
exit /b 0

:tests
echo  [..] Running automated tests
set "TOUT=%~dp0build\test-classes"
if exist "%TOUT%" rmdir /s /q "%TOUT%"
mkdir "%TOUT%"
"%JAVAC%" -encoding UTF-8 --release 21 -cp "%OUT%" -d "%TOUT%" "%~dp0src\test\java\com\nexvault\TestMain.java"
if errorlevel 1 goto :buildfail
"%JAVA%" -cp "%OUT%;%TOUT%" com.nexvault.TestMain
set "RC=%ERRORLEVEL%"
echo.
if "%RC%"=="0" (echo  All tests passed.) else (echo  Some tests FAILED.)
pause
exit /b %RC%

rem ---------------------------------------------------------------- helpers
:fetchfont
if exist "%FONTDIR%\%~1" exit /b 0
echo  [..] Downloading font %~1 (first run only)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\fetch.ps1" -Url "%~2" -Out "%FONTDIR%\%~1" -NoHash -Magic wOF2
if errorlevel 1 echo  [warn] Could not download %~1. The interface will use a fallback font.
exit /b 0

:fetch
if exist "%LIB%\%~1" exit /b 0
echo  [..] Downloading %~1 (first run only)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\fetch.ps1" -Url "%~2" -Out "%LIB%\%~1"
exit /b %ERRORLEVEL%

rem ---------------------------------------------------------------- errors
:nojdk
echo.
echo  [ERROR] A Java 21 JDK was not found.
echo.
echo  NexVault needs the JDK (not just the JRE) version 21 or newer, because it
echo  compiles itself on first launch. Install it free from:
echo      https://adoptium.net/temurin/releases/?version=21
echo  Choose "JDK", Windows x64, and tick "Set JAVA_HOME" / "Add to PATH" in the
echo  installer. Then run this file again.
goto :end

:oldjdk
echo.
echo  [ERROR] Found Java !JV! but NexVault needs Java 21 or newer.
echo  Install a JDK 21+ from https://adoptium.net/temurin/releases/?version=21
echo  and make sure it is first on your PATH (or set JAVA_HOME), then retry.
goto :end

:fetchfail
echo.
echo  [ERROR] Could not download a required library.
echo  Check your internet connection or proxy, then run this file again.
echo  Offline? Copy these three files into the "lib" folder next to this launcher:
echo      %MAVEN%/org/xerial/sqlite-jdbc/%SQLITE_VER%/sqlite-jdbc-%SQLITE_VER%.jar
echo      %MAVEN%/org/slf4j/slf4j-api/%SLF4J_VER%/slf4j-api-%SLF4J_VER%.jar
echo      %MAVEN%/org/slf4j/slf4j-nop/%SLF4J_VER%/slf4j-nop-%SLF4J_VER%.jar
goto :end

:buildfail
echo.
echo  [ERROR] The build failed. The compiler messages above show why.
goto :end

:runfail
echo.
echo  [ERROR] NexVault stopped with exit code %RC%.
if "%RC%"=="2" echo  It looks like NexVault is already running. Switch to its window or close it first.
echo  Messages above explain what happened.
goto :end

:end
echo.
pause
exit /b 1
