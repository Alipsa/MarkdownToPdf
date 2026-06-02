#!/usr/bin/env bash
# Exit immediately on any failure
set -e
SCRIPTDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPTDIR"
defaultInstallDir="$HOME/.local/share"
case $OSTYPE in darwin*) defaultInstallDir="$HOME/Applications" ;; esac
installDir=${1:-$defaultInstallDir}
if command -v jdk21; then
  . jdk21
fi
mvn install
pushd gui
source ./createApp.sh skipInstructions
popd
unzip -o "$SCRIPTDIR/gui/target/md2pdf-gui.zip" -d "$installDir"
case $OSTYPE in
  linux*)
    bash "$installDir/MarkdownToPdf.app/createLauncher.sh"
    ;;
esac
echo "installed!"