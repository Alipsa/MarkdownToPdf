#!/usr/bin/env bash
# Verifies that each native installer refuses destinations overlapping its source tree.
#
#   test-installer-overlap.sh <linux|macos|windows> <path-to-unpacked-zip-root>
set -euo pipefail

PLATFORM="${1:?usage: test-installer-overlap.sh <platform> <path-to-unpacked-zip-root>}"
SRC="${2:?usage: test-installer-overlap.sh <platform> <path-to-unpacked-zip-root>}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
ok()   { printf 'ok: %s\n' "$*"; }

case "$PLATFORM" in
  linux)
    installer=(bash "$SRC/md2pdf-install.sh")
    source_dir="$SRC/MarkdownToPdf"
    marker="$source_dir/MarkdownToPdf.jar"
    ;;
  macos)
    installer=(zsh "$SRC/md2pdf-install.zsh")
    source_dir="$SRC/MarkdownToPdf.app"
    marker="$source_dir/Contents/Info.plist"
    ;;
  windows)
    command -v cmd >/dev/null 2>&1 || fail "cmd.exe is unavailable"
    command -v cygpath >/dev/null 2>&1 || fail "cygpath is unavailable"
    source_dir="$SRC/MarkdownToPdf"
    marker="$source_dir/MarkdownToPdf.jar"
    ;;
  *) fail "unknown platform: $PLATFORM" ;;
esac

[ -f "$marker" ] || fail "unpacked archive is missing the source marker: $marker"

run_installer() {
  local destination="$1"
  case "$PLATFORM" in
    linux|macos)
      (cd "$SRC" && "${installer[@]}" "$destination" < /dev/null)
      ;;
    windows)
      local windows_destination
      windows_destination="$(cygpath -w "$destination")"
      if [[ "$destination" == */ ]]; then
        windows_destination="${windows_destination}\\"
      fi
      (cd "$SRC" && MSYS_NO_PATHCONV=1 cmd //c md2pdf-install.cmd "$windows_destination")
      ;;
  esac
}

expect_refusal() {
  local name="$1" destination="$2"
  local output="$WORK/$name.out"
  if run_installer "$destination" >"$output" 2>&1; then
    fail "$name destination was accepted"
  fi
  grep -Eiq 'overlap|same directory' "$output" \
    || fail "$name destination failed without an overlap diagnostic"
  [ -f "$marker" ] || fail "$name destination damaged the source tree"
  ok "$name destination refused"
}

expect_refusal self "$source_dir"
expect_refusal ancestor "$SRC"
expect_refusal child "$source_dir/child"

if [ "$PLATFORM" = "windows" ]; then
  # Exercise the Windows-specific trailing-separator case from a native shell.
  expect_refusal trailing "$SRC/"
fi

printf '\nPASS: %s installer overlap paths\n' "$PLATFORM"
