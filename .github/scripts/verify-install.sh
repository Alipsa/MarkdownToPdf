#!/usr/bin/env bash
# Asserts an installed MarkdownToPdf is complete and runnable.
#
#   verify-install.sh <linux|macos|windows> <install-dir> <expected-javafx-version>
#
# Runs against the installed tree, never the build tree, and always on the bundled
# runtime with the ambient JDK scrubbed — otherwise a missing runtime would be masked
# by whatever java happens to be on PATH.
set -euo pipefail

PLATFORM="${1:?usage: verify-install.sh <platform> <install-dir> <javafx-version>}"
DEST="${2:?usage: verify-install.sh <platform> <install-dir> <javafx-version>}"
FX_VERSION="${3:?usage: verify-install.sh <platform> <install-dir> <javafx-version>}"

SCRIPTS="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
ok()   { printf 'ok: %s\n' "$*"; }

# Windows environment variables hold native paths (C:\Users\…), which bash file tests do
# not understand. Anything coming from the Windows environment has to be converted before
# it is used as a path here — including the install directory this script is handed.
unixpath() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -u "$1"; else printf '%s' "$1"; fi
}
DEST="$(unixpath "$DEST")"

case "$PLATFORM" in
  macos)   ROOT="$DEST/Contents"; APP="$ROOT/app"; JAVA="$ROOT/runtime/bin/java" ;;
  linux)   ROOT="$DEST";          APP="$DEST";     JAVA="$DEST/runtime/bin/java" ;;
  windows) ROOT="$DEST";          APP="$DEST";     JAVA="$DEST/runtime/bin/java.exe" ;;
  *) fail "unknown platform: $PLATFORM" ;;
esac

[ -d "$DEST" ] || fail "nothing installed at $DEST"
[ -x "$JAVA" ] || fail "$JAVA missing or not executable"
ok "runtime present"

# The unzip tool may not preserve Unix modes; the installer re-applies them. If this
# fails the installer's chmod pass regressed.
if [ "$PLATFORM" != "windows" ]; then
  [ -x "$ROOT/runtime/lib/jspawnhelper" ] \
    || fail "runtime/lib/jspawnhelper is not executable — the installer's chmod pass is incomplete"
  ok "execute bits re-applied"
fi

version="$("$JAVA" -version 2>&1 | head -1 | cut -d'"' -f2)"
case "$version" in 21.*) ok "runtime java $version" ;; *) fail "expected java 21, got $version" ;; esac

modules="$("$JAVA" --list-modules)"
printf '%s\n' "$modules" | grep -q '^javafx.web@' || fail "javafx.web is not in the runtime"
actual_fx="$(printf '%s\n' "$modules" | sed -n 's/^javafx\.controls@//p')"
[ "$actual_fx" = "$FX_VERSION" ] \
  || fail "runtime JavaFX is $actual_fx but javafx.version is $FX_VERSION — they must match"
ok "javafx $actual_fx matches the POM"

[ -f "$APP/MarkdownToPdf.jar" ] || fail "$APP/MarkdownToPdf.jar missing"
[ -d "$APP/lib" ] || fail "$APP/lib missing"
"$SCRIPTS/check-lib-classpath.sh" "$APP/MarkdownToPdf.jar" "$APP/lib"

case "$PLATFORM" in
  linux)
    launcher="${XDG_DATA_HOME:-$HOME/.local/share}/applications/MarkdownToPdf.desktop"
    [ -f "$launcher" ] || fail "no .desktop launcher at $launcher"
    grep -q "^Exec=$DEST/run.sh$" "$launcher" || fail "$launcher does not exec $DEST/run.sh"
    [ -x "$DEST/run.sh" ] || fail "run.sh is not executable"
    ok "desktop launcher"
    ;;
  macos)
    exe="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$ROOT/Info.plist")"
    [ -x "$ROOT/MacOS/$exe" ] || fail "CFBundleExecutable=$exe but MacOS/$exe is missing or not executable"
    for key in CFBundleVersion CFBundleShortVersionString; do
      v="$(/usr/libexec/PlistBuddy -c "Print :$key" "$ROOT/Info.plist")"
      printf '%s' "$v" | grep -Eq '^[0-9]+(\.[0-9]+){0,2}$' \
        || fail "$key=$v is not one to three period-separated integers — Apple rejects it"
    done
    /usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$ROOT/Info.plist" > /dev/null \
      || fail "CFBundleIdentifier is missing — codesign cannot sign the bundle without it"
    codesign --verify --deep --strict "$DEST" || fail "codesign verification failed"
    ok "bundle metadata and signature"
    ;;
  windows)
    [ -f "$DEST/run.cmd" ] || fail "run.cmd missing"
    [ -x "$ROOT/runtime/bin/javaw.exe" ] || fail "runtime/bin/javaw.exe missing"
    userprofile="$(unixpath "${USERPROFILE:-$HOME}")"
    shortcut_found=0
    for d in "$userprofile/Desktop" "$userprofile/OneDrive/Desktop"; do
      [ -f "$d/MarkdownToPdf.lnk" ] && shortcut_found=1
      [ -f "$d/MarkdownToPdf.cmd" ] && shortcut_found=1
    done
    [ "$shortcut_found" -eq 1 ] || fail "neither a .lnk nor a stub .cmd was created on the Desktop"
    ok "windows shortcut or stub"
    ;;
esac

# The real test: run the app's own code on the installed runtime, with nothing from the
# ambient environment available to stand in for it.
# Compiled here with the full JDK, run below on the bundled runtime: the runtime has no
# jdk.compiler, so source-file mode fails on it outright. This has to happen before the
# scrubbing, since it is the one step that legitimately needs JAVA_HOME. compile-smoke.sh
# prints the classpath in the form the local java expects.
SMOKE="$(mktemp -d)"
trap 'rm -rf "$SMOKE"' EXIT
CP="$("$SCRIPTS/compile-smoke.sh" "$APP/MarkdownToPdf.jar" "$SMOKE")"

# PATH is set explicitly rather than left to env's built-in default, so that what is kept
# (xvfb-run, the system utilities) and what is dropped (any JDK on the caller's PATH) is
# visible in the script rather than a property of env.
scrub() {
  if [ "$PLATFORM" = "windows" ]; then
    # Clear JAVA_HOME so the bundled runtime is used, not the runner's setup-java JDK.
    # shellcheck disable=SC1007
    JAVA_HOME= "$@"
  else
    env -i PATH=/usr/bin:/bin HOME="$HOME" DISPLAY="${DISPLAY:-}" "$@"
  fi
}

scrub "$JAVA" -cp "$CP" EngineSmoke || fail "engine smoke failed"

if [ "$PLATFORM" = "linux" ]; then
  scrub xvfb-run -a "$JAVA" -cp "$CP" ToolkitSmoke || fail "toolkit smoke failed"
else
  scrub "$JAVA" -cp "$CP" ToolkitSmoke || fail "toolkit smoke failed"
fi

# Everything above proves the installed *files* are right and that the app's code runs on
# the installed runtime. It does not prove the launcher works, so it is the last thing
# checked — after this, a pass means the thing the user double-clicks actually starts.
"$SCRIPTS/verify-launcher.sh" "$PLATFORM" "$DEST"

printf '\nPASS: %s install at %s\n' "$PLATFORM" "$DEST"
