#!/usr/bin/env bash
# Asserts the -no-jdk archive is what it claims: the app and its dependencies, no runtime,
# runnable on a JavaFX-bundled JDK that the user supplies.
#
#   verify-no-jdk.sh <unpacked-dir> <java-binary>
set -euo pipefail

DIR="${1:?usage: verify-no-jdk.sh <unpacked-dir> <java>}"
JAVA="${2:?usage: verify-no-jdk.sh <unpacked-dir> <java>}"
SCRIPTS="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# Both arguments are likely to arrive as native Windows paths on Git Bash — $JAVA_HOME as
# set by actions/setup-java is C:\hostedtoolcache\..., and `unzip -d` is commonly given the
# same. dirname, cd and the file tests below all need POSIX form.
unixpath() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -u "$1"; else printf '%s' "$1"; fi
}
DIR="$(unixpath "$DIR")"
JAVA="$(unixpath "$JAVA")"
[ -x "$JAVA" ] || fail "not an executable java binary: $JAVA"

[ -f "$DIR/MarkdownToPdf.jar" ] || fail "MarkdownToPdf.jar missing"
[ -f "$DIR/README.txt" ]        || fail "README.txt missing"
# An assembly slip that added one would produce a 100 MB "no-jdk" download.
[ ! -d "$DIR/runtime" ]         || fail "runtime/ is present — this is not a no-jdk archive"
for f in run.sh run.cmd run.zsh md2pdf-install.sh md2pdf-install.cmd md2pdf-install.zsh; do
  [ ! -e "$DIR/$f" ] || fail "$f is present — the no-jdk archive ships no launchers or installers"
done

"$SCRIPTS/check-lib-classpath.sh" "$DIR/MarkdownToPdf.jar" "$DIR/lib"

# Both smoke tests, on the user-supplied JDK. The engine smoke touches no JavaFX at all,
# so on its own it would pass on a plain OpenJDK and prove nothing about what this
# archive actually needs. The toolkit smoke is what demonstrates that this archive plus a
# JavaFX-bundled JDK is a working installation — and, with check-lib-classpath.sh having
# just asserted there is no javafx-* jar in lib/, that the JavaFX modules came from the
# JDK. That is the module resolution the archive depends on.
SMOKE="$(mktemp -d)"
trap 'rm -rf "$SMOKE"' EXIT
# Compile with the JDK that is about to run it, derived from the java binary — not with
# whatever JAVA_HOME happens to hold. This script's whole subject is "does the archive work
# on the JDK the user supplied", so compiling against a different one would test something
# nobody asked about.
JDK_HOME="$( cd -- "$( dirname -- "$JAVA" )/.." > /dev/null 2>&1 && pwd )"
CP="$("$SCRIPTS/compile-smoke.sh" "$DIR/MarkdownToPdf.jar" "$SMOKE" "$JDK_HOME")"

"$JAVA" -cp "$CP" EngineSmoke || fail "engine smoke failed"
if [ "$(uname -s)" = "Linux" ]; then
  xvfb-run -a "$JAVA" -cp "$CP" ToolkitSmoke || fail "toolkit smoke failed"
else
  "$JAVA" -cp "$CP" ToolkitSmoke || fail "toolkit smoke failed"
fi

printf '\nPASS: no-jdk archive at %s\n' "$DIR"
