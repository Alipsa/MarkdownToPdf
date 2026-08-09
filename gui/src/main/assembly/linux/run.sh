#!/usr/bin/env bash
# Launches MarkdownToPdf on the runtime bundled beside this script.
set -euo pipefail
DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
exec "$DIR/runtime/bin/java" --enable-native-access=javafx.graphics,javafx.web,javafx.media \
  -Xmx8g -jar "$DIR/MarkdownToPdf.jar"
