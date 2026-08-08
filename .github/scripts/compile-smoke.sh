#!/usr/bin/env bash
# Compiles the smoke tests with a full JDK, for running on a runtime that has no compiler,
# and prints the classpath to run them with.
#
#   CP="$(compile-smoke.sh <app-jar> <output-dir> [jdk-home])"
#   "$some_java" -cp "$CP" EngineSmoke
#
# The bundled runtime deliberately omits jdk.compiler, so `java EngineSmoke.java` fails on
# it with "InternalError: Module jdk.compiler not in boot Layer". Compiling here and running
# the .class files there tests the runtime module set, which is the point, without shipping
# a compiler to every user.
#
# This script prints the classpath rather than leaving each caller to build one, because
# getting it right on Windows means both a ';' separator and native paths — Git Bash hands
# POSIX paths and colon lists to a native java.exe with heuristic, unreliable conversion.
# That logic belongs in one place.
set -euo pipefail

JAR="${1:?usage: compile-smoke.sh <app-jar> <output-dir> [jdk-home]}"
OUT="${2:?usage: compile-smoke.sh <app-jar> <output-dir> [jdk-home]}"
JDK_HOME="${3:-${JAVA_HOME:-}}"
SCRIPTS="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"

[ -n "$JDK_HOME" ] \
  || { echo "pass a jdk-home or set JAVA_HOME to a JavaFX-bundled JDK 21" >&2; exit 1; }
JAVAC="$JDK_HOME/bin/javac"
[ -x "$JAVAC" ] || [ -x "$JAVAC.exe" ] || { echo "no javac in $JDK_HOME/bin" >&2; exit 1; }
[ -f "$JAR" ] || { echo "no such jar: $JAR" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT"
# -proc:none: dependencies on the classpath carry annotation processors, and javac warns
# at length about implicitly running them. Diagnostics go to stderr so stdout stays clean
# for the classpath this prints.
"$JAVAC" -proc:none -cp "$JAR" -d "$OUT" \
  "$SCRIPTS/EngineSmoke.java" "$SCRIPTS/ToolkitSmoke.java" >&2

native() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}
if command -v cygpath > /dev/null 2>&1; then
  printf '%s;%s\n' "$(native "$JAR")" "$(native "$OUT")"
else
  printf '%s:%s\n' "$JAR" "$OUT"
fi
