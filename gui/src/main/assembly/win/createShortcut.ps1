# Creates the desktop shortcut for MarkdownToPdf.
# Targets runtime\bin\javaw.exe directly rather than cmd.exe /c run.cmd — now possible
# because the runtime is at a known path inside the install directory, and it removes
# the console window that the cmd.exe target flashed up on every launch.
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

if (Test-Path -Path "$env:USERPROFILE\Desktop") {
    $desktop = "$env:USERPROFILE\Desktop"
} elseif (Test-Path -Path "$env:USERPROFILE\OneDrive\Desktop") {
    $desktop = "$env:USERPROFILE\OneDrive\Desktop"
} else {
    Write-Error "Could not locate the Desktop folder; shortcut was not created."
    exit 1
}

$WScriptObj = New-Object -ComObject WScript.Shell
$shortcut = $WScriptObj.CreateShortcut("$desktop\MarkdownToPdf.lnk")
$shortcut.TargetPath = "$scriptDir\runtime\bin\javaw.exe"
$shortcut.Arguments = "--enable-native-access=javafx.graphics,javafx.web,javafx.media -Xmx8g -jar `"$scriptDir\MarkdownToPdf.jar`""
$shortcut.WorkingDirectory = "$scriptDir"
$shortcut.IconLocation = "$scriptDir\MarkdownToPdf-rounded.ico, 0"
$shortcut.Save()
