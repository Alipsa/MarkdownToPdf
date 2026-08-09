#!/usr/bin/env bash
# Builds the Linux archive and runs the app from the staged install.
set -euo pipefail

if command -v jdk25; then
  # shellcheck disable=SC1091
  source jdk25
fi

DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
cd "$DIR/.." || exit 1

mvn install -DskipTests || exit 1
./gui/createApp.sh linux || exit 1

VERSION="$(mvn -q org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=revision -DforceStdout)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
unzip -q "gui/target/md2pdf-$VERSION-linux-x64.zip" -d "$WORK"
INSTALL_DIR="$WORK/install"
MD2PDF_REPLACE_EXISTING=1 MD2PDF_SKIP_LAUNCHER=1 \
  bash "$WORK/md2pdf-install.sh" "$INSTALL_DIR" < /dev/null
"$INSTALL_DIR/run.sh"
