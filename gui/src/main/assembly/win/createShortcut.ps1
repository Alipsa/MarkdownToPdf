# Create a shortcut to Journo on the desktop
$scriptDir = split-path -parent $MyInvocation.MyCommand.Definition
# if the above does not work, try to use $scriptDir = $PSScriptRoot instead
$WScriptObj = New-Object -ComObject ("WScript.Shell")

if (Test-Path -Path $env:USERPROFILE\Desktop) {
    $shortCut="$env:USERPROFILE\Desktop\MarkdownToPdf.lnk"
} elseif (Test-Path -Path $env:USERPROFILE\OneDrive\Desktop ) {
    $shortCut="$env:USERPROFILE\OneDrive\Desktop\MarkdownToPdf.lnk"
}
$shortcut = $WscriptObj.CreateShortcut($shortCut)
$shortcut.TargetPath = "C:\Windows\System32\cmd.exe"
$shortcut.Arguments = "/c $scriptDir\run.cmd"
$shortcut.WorkingDirectory = "$scriptDir"
$shortcut.IconLocation = "$scriptDir\MarkdownToPdf-rounded.ico, 0"
$shortcut.Save()