#!/usr/bin/env bash
# MarkdownToPdf installer for Linux.
#
#   bash md2pdf-install.sh [target-directory]
#
# The archive bundles its own Java runtime, so this script installs no JDK, detects no
# JDK, and records no JDK. If you are about to add version parsing here, don't — that
# code is what this packaging removed.
set -euo pipefail

SCRIPTDIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
APP_NAME="MarkdownToPdf"
SRC="$SCRIPTDIR/$APP_NAME"

RED=$'\033[0;31m'; GRN=$'\033[0;32m'; YEL=$'\033[0;33m'; NC=$'\033[0m'
info() { printf "${GRN}[INSTALL]${NC} %s\n" "$*"; }
warn() { printf "${YEL}[WARN]${NC}  %s\n" "$*" >&2; }
die()  { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; exit 1; }

# read at EOF returns 1, which under set -e kills the script — so a prompt in any
# non-TTY context (CI, a pipe, cron) aborts the install rather than defaulting.
NONINTERACTIVE=0
[ -t 0 ] || NONINTERACTIVE=1

DEST="${1:-${XDG_DATA_HOME:-$HOME/.local/share}/$APP_NAME}"

[ -d "$SRC" ] || die "$APP_NAME is not next to this script — run it from the unzipped archive."

# ── Linux host libraries ────────────────────────────────────────────
# jlink cannot bundle these; JavaFX needs GTK 3 and javafx.web needs the WebKit stack.
# Warns rather than aborts: the libraries may be present under names this check does not
# know, and a wrong guess must not block a working install.
check_host_libraries() {
  local missing=()
  for lib in libgtk-3.so.0 libgthread-2.0.so.0 libXtst.so.6; do
    ldconfig -p 2>/dev/null | grep -q "$lib" || missing+=("$lib")
  done
  [ "${#missing[@]}" -eq 0 ] && return 0
  warn "These shared libraries were not found: ${missing[*]}"
  warn "MarkdownToPdf needs GTK 3 and the WebKit dependencies, which no bundled runtime can supply."
  warn "  Debian/Ubuntu: sudo apt-get install libgtk-3-0 libxtst6"
  warn "  Fedora/RHEL:   sudo dnf install gtk3 libXtst"
  warn "  Arch:          sudo pacman -S gtk3 libxtst"
  warn "Continuing — if they are present under different names the app will start fine."
}

# ── existing installation ───────────────────────────────────────────
resolve_dest() {
  local source_real destination_real destination_parent destination_suffix
  source_real="$(cd "$SRC" && pwd -P)"
  if [ -d "$DEST" ]; then
    destination_real="$(cd "$DEST" && pwd -P)"
  else
    destination_suffix="$(basename -- "$DEST")"
    destination_parent="$(dirname -- "$DEST")"
    while [ ! -d "$destination_parent" ]; do
      destination_suffix="$(basename -- "$destination_parent")/$destination_suffix"
      destination_parent="$(dirname -- "$destination_parent")"
    done
    destination_parent="$(cd -- "$destination_parent" 2>/dev/null && pwd -P)" \
      || die "Cannot resolve the parent directory of destination $DEST."
    destination_real="$destination_parent/$destination_suffix"
  fi
  # Keep the root path as "/" while removing other trailing slashes. Otherwise the
  # comparison below turns "/" into "//" and misses an ancestor destination.
  while [ "$source_real" != "/" ] && [[ "$source_real" == */ ]]; do source_real="${source_real%/}"; done
  while [ "$destination_real" != "/" ] && [[ "$destination_real" == */ ]]; do destination_real="${destination_real%/}"; done

  # Refuse both installing onto the source and installing across either side of it. In
  # particular, an existing ancestor would be removed by rm -rf before the copy starts.
  if [ "$source_real" = "/" ] || [ "$destination_real" = "/" ] \
    || [ "$source_real" = "$destination_real" ] \
    || [[ "$source_real" == "$destination_real/"* ]] \
    || [[ "$destination_real" == "$source_real/"* ]]; then
    die "Source $source_real and destination $DEST overlap — refusing to remove or copy recursively."
  fi
  [ -d "$DEST" ] || return 0

  if [ "${MD2PDF_REPLACE_EXISTING:-0}" = "1" ]; then
    info "Replacing the existing installation at $DEST"
    rm -rf "$DEST"
    return 0
  fi

  if [ "$NONINTERACTIVE" -eq 1 ]; then
    die "An installation already exists at $DEST. Re-run with MD2PDF_REPLACE_EXISTING=1 to replace it."
  fi

  warn "Existing installation found at: $DEST"
  info "  [r]emove the old one and replace it"
  info "  [k]eep both by renaming the old one to ${DEST}-old"
  info "  [c]ancel"
  read -rp "  Choose (r/k/c): " choice
  case "$choice" in
    r|R) info "Removing $DEST"; rm -rf "$DEST" ;;
    k|K) [ -d "${DEST}-old" ] && die "Backup already exists: ${DEST}-old"
         info "Renaming to ${DEST}-old"; mv "$DEST" "${DEST}-old" ;;
    c|C) die "Installation cancelled — nothing was changed." ;;
    *)   die "Unrecognized choice: $choice" ;;
  esac
}

install_app() {
  info "Installing to $DEST"
  mkdir -p "$DEST"
  cp -R "$SRC/." "$DEST/" || die "Copy failed."

  # Not every unzip tool preserves Unix modes, and this covers more than the launcher:
  # runtime/bin/* and runtime/lib/jspawnhelper are unusable without the execute bit.
  chmod +x "$DEST"/*.sh
  chmod +x "$DEST"/runtime/bin/* 2>/dev/null || true
  [ -f "$DEST/runtime/lib/jspawnhelper" ] && chmod +x "$DEST/runtime/lib/jspawnhelper"

  if [ "${MD2PDF_SKIP_LAUNCHER:-0}" = "1" ]; then
    info "Skipping desktop launcher creation."
  else
    info "Creating the desktop launcher …"
    [ -x "$DEST/createLauncher.sh" ] \
      || die "createLauncher.sh is missing from $DEST — the archive looks incomplete."
    (cd "$DEST" && bash createLauncher.sh) \
      || die "createLauncher.sh failed — $APP_NAME is installed at $DEST but has no launcher. Retry with 'bash $DEST/createLauncher.sh', or start it with 'bash $DEST/run.sh'."
  fi

  echo ""
  info "Installation complete."
  info "Run with:  bash $DEST/run.sh"
}

check_host_libraries
resolve_dest
install_app
