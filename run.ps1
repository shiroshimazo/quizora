param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$JavaFxHome = $env:JAVAFX_HOME,
    [string]$AntHome = $env:ANT_HOME,
    [switch]$BuildOnly
)

$ErrorActionPreference = 'Stop'
if (!$JavaHome) { $JavaHome = Join-Path $env:ProgramFiles 'Apache NetBeans\jdk' }
if (!$AntHome) { $AntHome = Join-Path $env:ProgramFiles 'Apache NetBeans\extide\ant' }
if (!$JavaFxHome) {
    $sdkLine = Get-Content (Join-Path $PSScriptRoot 'nbproject\project.properties') |
        Where-Object { $_ -like 'javafx.sdk.dir=*' } | Select-Object -First 1
    $JavaFxHome = $sdkLine.Substring('javafx.sdk.dir='.Length).Replace('${user.home}', $env:USERPROFILE)
}

$java = Join-Path $JavaHome 'bin\java.exe'
$ant = Join-Path $AntHome 'bin\ant.bat'
$modules = Join-Path $JavaFxHome 'lib'
foreach ($required in @($java, (Join-Path $JavaHome 'bin\javac.exe'), $ant,
        (Join-Path $modules 'javafx.controls.jar'), (Join-Path $modules 'javafx.fxml.jar'))) {
    if (!(Test-Path -LiteralPath $required)) {
        throw "Missing dependency: $required. Set -JavaHome, -JavaFxHome, or -AntHome to your installation."
    }
}

$previousJavaHome = $env:JAVA_HOME
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $JavaHome
    & $ant -quiet "-Djavafx.sdk.dir=$JavaFxHome" jar
    if ($LASTEXITCODE -ne 0) { throw "Build failed (exit $LASTEXITCODE)." }
    if (!$BuildOnly) {
        & $java --module-path $modules --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics,ALL-UNNAMED -jar dist/quizora.jar
        if ($LASTEXITCODE -ne 0) { throw "Quizora exited with code $LASTEXITCODE." }
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
