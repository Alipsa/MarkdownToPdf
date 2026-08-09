#!/usr/bin/env bash
set -euo pipefail

DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"

if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
  [ -x "$JAVA_BIN" ] || { echo "JAVA_HOME does not contain an executable Java: $JAVA_BIN"; exit 1; }
elif command -v java ; then
  JAVA_BIN="$(command -v java)"
else
  echo "Java not found in path"
  exit 1
fi

javaVersion="$(
  "$JAVA_BIN" -version 2>&1 |
    grep -m1 ' version ' |
    cut -d'"' -f2 |
    sed '/^1\./s///' |
    cut -d'.' -f1 || true
)"
if ! [[ "$javaVersion" =~ ^[0-9]+$ ]]; then
  echo "Could not determine Java major version from $JAVA_BIN"
  exit 1
fi
if (( javaVersion >= 25 )); then
  echo "Java $javaVersion OK"
else
  echo "Java version 25 or greater required"
  exit 1
fi

cd "$DIR/.."
mvn clean package
