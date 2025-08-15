@ECHO OFF
SETLOCAL

SET DIR=%~dp0
SET APP_BASE_NAME=%~n0
SET APP_HOME=%DIR%

SET DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

IF NOT "%JAVA_HOME%"=="" GOTO findJavaFromJavaHome

SET JAVACMD=java.exe
%JAVACMD% -version >NUL 2>&1
IF "%ERRORLEVEL%"=="0" GOTO execute

ECHO.
ECHO ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
ECHO Please set the JAVA_HOME variable in your environment to match the
ECHO location of your Java installation.
EXIT /B 1

:findJavaFromJavaHome
SET JAVACMD=%JAVA_HOME%\bin\java.exe

:execute
SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
%JAVACMD% %DEFAULT_JVM_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
ENDLOCAL
