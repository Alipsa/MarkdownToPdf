#!/usr/bin/env bash
# Builds MarkdownToPdf from source and installs it on this machine.
# For a normal install, download a release archive instead — this is a developer
# convenience and requires a JavaFX-bundled JDK 25 to build with.
set -euo pipefail
CALLER_DIR="$(pwd -P)"
BASEDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
cd "$BASEDIR"

# An array, not a string: "$INSTALLER" would look for a command with a space in its name
# and $INSTALLER unquoted is the word-splitting that shellcheck (rightly) rejects.
case "$OSTYPE" in
  darwin*) PLATFORM=macos; LABEL=macos-aarch64; INSTALLER=(zsh md2pdf-install.zsh) ;;
  linux*)  PLATFORM=linux; LABEL=linux-x64;     INSTALLER=(bash md2pdf-install.sh) ;;
  *) echo "Unsupported platform for a source install: $OSTYPE" >&2; exit 1 ;;
esac

[ "$#" -le 1 ] || { echo "usage: $0 [installDir]" >&2; exit 2; }
INSTALL_DIR="${1:-}"
if [ -n "$INSTALL_DIR" ] && [[ "$INSTALL_DIR" != /* ]]; then
  INSTALL_DIR="$CALLER_DIR/$INSTALL_DIR"
fi

mvn install -DskipTests
./gui/createApp.sh "$PLATFORM"

VERSION="$(mvn -q -pl gui org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
unzip -q "gui/target/md2pdf-$VERSION-$LABEL.zip" -d "$WORK"
cd "$WORK"
if [ -n "$INSTALL_DIR" ]; then
  "${INSTALLER[@]}" "$INSTALL_DIR"
else
  "${INSTALLER[@]}"
fi
