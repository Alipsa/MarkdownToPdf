#!/usr/bin/env bash
###
### Create a joint zip release for macos, linux and windows
### This script should be run from a mac since SetFile only exists on Mac
###
set -e
DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
skipInstructions=${1:-false}
skipBuild=${2:-false}
if [[ "$skipBuild" == "false" ]]; then
  source "$DIR"/build.sh
fi

appName="MarkdownToPdf.app"
targetDir="$DIR/target/${appName}"
mkdir -p "$targetDir"
echo "creating app"
CONTENT_DIR="${targetDir}/Contents"
MACOS_DIR="${CONTENT_DIR}/MacOS"
RESOURCE_DIR="${CONTENT_DIR}/Resources"
mkdir -p "$MACOS_DIR"
mkdir -p "$RESOURCE_DIR"
cp "src/main/assembly/mac/Info.plist" "$CONTENT_DIR/"
cp "src/main/assembly/mac/md2pdf.icns" "${RESOURCE_DIR}/"
cp "src/main/assembly/mac/markdownToPdf" "${MACOS_DIR}/"
cp "$DIR"/target/MarkdownToPdf*.jar "$targetDir"/
cp src/main/assembly/mac/run.zsh "$targetDir"/
cp src/main/assembly/linux/* "$targetDir"/
cp src/main/resources/MarkdownToPdf-rounded.* "$targetDir"/
cp src/main/assembly/win/* "$targetDir"/

# Bundle install.sh one level above the .app so users can run it from the zip root
cp "src/main/assembly/install.sh" "$DIR/target/md2pdf-install.sh"

chmod +x "${MACOS_DIR}/markdownToPdf"
chmod +x  "$targetDir"/*.sh
chmod +x  "$targetDir"/*.zsh
chmod +x "$DIR/target/md2pdf-install.sh"

# cd to the target so we dont have to allow full disk access in Settings -> Privacy and Security
cd "${targetDir}/.."
if command -v SetFile; then
  SetFile -a B "${appName}"
else
  echo "Not building from a Mac so cannot set application props with SetFile"
fi

cd "$DIR/target"
zip -r md2pdf-gui.zip "${appName}" md2pdf-install.sh

echo "Done!"
if [[ "$skipInstructions" == "false" ]]; then
  echo "To install the MarkdownToPdf.zip do the following"
  echo "All platforms: Unzip the MarkdownToPdf.zip and run 'bash md2pdf-install.sh' from the unzipped folder."
  echo "(On Windows, run it from Git Bash. It installs $appName, checks for a JavaFX-bundled"
  echo " JDK 21+, and creates the launcher/shortcut for you.)"
fi
