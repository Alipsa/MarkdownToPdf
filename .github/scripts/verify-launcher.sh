#!/usr/bin/env bash
# Starts the installed launcher and asserts the application is still running a few seconds
# later, then stops it.
#
#   verify-launcher.sh <linux|macos|windows> <install-dir>
#
# Checking that run.sh / run.cmd / the bundle executable *exist* cannot catch a launcher
# that points at the wrong java, quotes a path badly, or names a main class that is not
# there. Each of those leaves a file that is present and a program that dies on its first
# line. Starting it is the only assertion that separates the two.
#
# Called at the end of verify-install.sh, so every caller of that script gets this for free
# and there is only one entry point to remember. It is a separate file because it is the
# only check here that starts a GUI and has to clean processes up afterwards.
set -euo pipefail

PLATFORM="${1:?usage: verify-launcher.sh <platform> <install-dir>}"
DEST="${2:?usage: verify-launcher.sh <platform> <install-dir>}"

unixpath() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -u "$1"; else printf '%s' "$1"; fi
}
DEST="$(unixpath "$DEST")"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

# Seconds to wait before deciding the app is up. A launcher defect kills the process in
# well under a second; JavaFX toolkit startup on a cold runner is a few. Overridable so a
# slow machine does not need a code change.
SETTLE="${MD2PDF_LAUNCH_SETTLE:-10}"
LOG="$(mktemp)"
WORK="$(mktemp -d)"
trap 'rm -f "$LOG"; rm -rf "$WORK"' EXIT

# Nothing below identifies a process by image name. This script runs on a developer's own
# machine in Tasks 6-8, not only on a disposable runner, so "kill every java" is not an
# option — and an unrelated Java process must never be able to stand in as proof that this
# launcher worked.

case "$PLATFORM" in
  windows)
    # run.cmd uses `start`, so it returns at once and the JVM is not its child. Two
    # separate signals are needed: its exit status catches a wrong or badly quoted
    # javaw.exe path, and a process lookup catches a JVM that started and then died.
    #
    # The lookup matches this installation's own directory in the process command line.
    # PowerShell is used because reading a command line is the only way to do that and
    # wmic is gone from current Windows images. This is CI and developer tooling, so it
    # carries none of the shipped installer's no-PowerShell constraint.
    cat > "$WORK/find-app.ps1" <<'PS'
$path = $env:MD2PDF_DEST_W
Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" |
  Where-Object { $_.CommandLine -and $_.CommandLine.Contains($path) } |
  ForEach-Object { $_.ProcessId }
PS
    app_pids() {
      MD2PDF_DEST_W="$(cygpath -w "$DEST")" powershell -NoProfile -NonInteractive \
        -ExecutionPolicy Bypass -File "$(cygpath -w "$WORK/find-app.ps1")" | tr -d '\r'
    }
    kill_app() {
      local p
      for p in $(app_pids); do taskkill //F //PID "$p" > /dev/null 2>&1 || true; done
    }

    kill_app          # a leftover from an earlier run must not be counted as a pass
    ( cd "$DEST" && cmd //c run.cmd ) > "$LOG" 2>&1 \
      || { cat "$LOG" >&2; fail "run.cmd exited non-zero"; }
    sleep "$SETTLE"
    [ -n "$(app_pids)" ] || { cat "$LOG" >&2
      fail "no javaw.exe is running from $DEST — the app died on startup, or find-app.ps1 failed; run it by hand to tell those apart"; }
    kill_app
    ;;
  *)
    case "$PLATFORM" in
      # The bundle executable is read from Info.plist rather than hardcoded, so this fails
      # for the right reason if CFBundleExecutable and the file on disk ever disagree.
      macos) exe="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$DEST/Contents/Info.plist")"
             launcher=("$DEST/Contents/MacOS/$exe") ;;
      linux) launcher=(xvfb-run -a "$DEST/run.sh") ;;
      *) fail "unknown platform: $PLATFORM" ;;
    esac
    # Job control makes the background job a process-group leader, which is what lets the
    # cleanup below name exactly what this script started and nothing else.
    set -m
    "${launcher[@]}" > "$LOG" 2>&1 &
    pid=$!
    set +m          # the group is fixed at fork; this only silences job-control notices
    sleep "$SETTLE"
    if ! kill -0 "$pid" 2> /dev/null; then
      cat "$LOG" >&2
      fail "the launcher exited within ${SETTLE}s instead of running the app"
    fi
    # A negative PID signals the whole process group. On Linux $pid is xvfb-run, with the
    # JVM as its child and an Xvfb alongside; on macOS the launcher execs the JVM directly.
    # Either way this reaches every descendant and nothing outside the group.
    kill -- -"$pid" 2> /dev/null || true
    wait "$pid" 2> /dev/null || true
    ;;
esac

printf 'ok: launcher started and was still running after %ss\n' "$SETTLE"
