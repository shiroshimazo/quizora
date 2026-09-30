param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$JavaFxHome = $env:JAVAFX_HOME,
    [string]$AntHome = $env:ANT_HOME
)
$ErrorActionPreference = 'Stop'
if (!$JavaHome) { $JavaHome = Join-Path $env:ProgramFiles 'Apache NetBeans\jdk' }
if (!$JavaFxHome) {
    $sdkLine = Get-Content (Join-Path $PSScriptRoot 'nbproject\project.properties') |
        Where-Object { $_ -like 'javafx.sdk.dir=*' } | Select-Object -First 1
    $JavaFxHome = $sdkLine.Substring('javafx.sdk.dir='.Length).Replace('${user.home}', $env:USERPROFILE)
}
Push-Location $PSScriptRoot
try {
    & ./run.ps1 -JavaHome $JavaHome -JavaFxHome $JavaFxHome -AntHome $AntHome -BuildOnly
    $java = Join-Path $JavaHome 'bin\java.exe'
    & $java --enable-native-access=ALL-UNNAMED --class-path 'build/classes;lib/*' test/quizora/auth/PasswordRecoveryTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Recovery service checks failed.' }
    & $java --module-path (Join-Path $JavaFxHome 'lib') --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics,ALL-UNNAMED --class-path 'build/classes;lib/*' test/quizora/controllerAuthentication/PasswordRecoveryUiTest.java
    if ($LASTEXITCODE -ne 0) { throw 'Recovery UI checks failed.' }
} finally {
    Pop-Location
}
