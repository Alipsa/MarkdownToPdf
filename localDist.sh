#!/usr/bin/env bash
# Builds the local distribution archive for the current platform, so you can verify
# it end to end. See gui/createApp.sh for the JDK/arch/tooling checks it runs before
# packaging — this script only has to pick the right platform argument for it.
set -euo pipefail
BASEDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
cd "$BASEDIR"

case "$OSTYPE" in
  darwin*)       PLATFORM=macos ;;
  linux*)        PLATFORM=linux ;;
  msys*|cygwin*) PLATFORM=windows ;;
  *) echo "Unsupported platform for a local distribution: $OSTYPE" >&2; exit 1 ;;
esac

mvn clean install -DskipTests
./gui/createApp.sh "$PLATFORM"

echo
ls gui/target/md2pdf-*.zip
echo "Unzip the archive somewhere clean and run the platform installer/launcher to verify end to end."
