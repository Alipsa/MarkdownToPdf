#!/usr/bin/env zsh
# MarkdownToPdf installer for macOS.
#
#   zsh md2pdf-install.zsh [target-directory]
set -euo pipefail

SCRIPTDIR="${0:A:h}"
APP_NAME="MarkdownToPdf.app"
SRC="$SCRIPTDIR/$APP_NAME"

RED=$'\033[0;31m'; GRN=$'\033[0;32m'; YEL=$'\033[0;33m'; NC=$'\033[0m'
info() { printf "${GRN}[INSTALL]${NC} %s\n" "$*"; }
warn() { printf "${YEL}[WARN]${NC}  %s\n" "$*" >&2; }
die()  { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; exit 1; }

NONINTERACTIVE=0
[ -t 0 ] || NONINTERACTIVE=1

DEST="${1:-$HOME/Applications/$APP_NAME}"
[ -d "$SRC" ] || die "$APP_NAME is not next to this script — run it from the unzipped archive."

resolve_dest() {
  local source_real destination_real destination_parent destination_suffix
  source_real="$(cd -- "$SRC" && pwd -P)"
  if [[ -d "$DEST" ]]; then
    destination_real="$(cd -- "$DEST" && pwd -P)"
  else
    destination_suffix="${DEST:t}"
    destination_parent="${DEST:h}"
    while [[ ! -d "$destination_parent" ]]; do
      [[ "$destination_parent" != "/" && "$destination_parent" != "." ]] \
        || die "Cannot resolve the parent directory of destination $DEST."
      destination_suffix="${destination_parent:t}/$destination_suffix"
      destination_parent="${destination_parent:h}"
    done
    if [[ "$destination_parent" == "/" ]]; then
      destination_real="/$destination_suffix"
    else
      destination_real="$(cd -- "$destination_parent" && pwd -P)/$destination_suffix"
    fi
  fi

  # Keep the root path as "/" while removing other trailing slashes. Otherwise the
  # comparison below turns "/" into "//" and misses an ancestor destination.
  while [[ "$source_real" != "/" && "$source_real" == */ ]]; do source_real="${source_real%/}"; done
  while [[ "$destination_real" != "/" && "$destination_real" == */ ]]; do destination_real="${destination_real%/}"; done

  if [[ "$source_real" == "/" || "$destination_real" == "/" \
    || "$source_real" == "$destination_real" \
    || "$source_real" == "$destination_real"/* \
    || "$destination_real" == "$source_real"/* ]]; then
    die "Source $source_real and destination $DEST overlap — refusing to remove or copy recursively."
  fi
}

resolve_dest

if [[ -d "$DEST" ]]; then
  if [[ "${MD2PDF_REPLACE_EXISTING:-0}" == "1" ]]; then
    info "Replacing the existing installation at $DEST"
    rm -rf "$DEST"
  elif (( NONINTERACTIVE )); then
    die "An installation already exists at $DEST. Re-run with MD2PDF_REPLACE_EXISTING=1 to replace it."
  else
    warn "Existing installation found at: $DEST"
    info "  [r]emove and replace   [k]eep both   [c]ancel"
    read -r "choice?  Choose (r/k/c): "
    case "$choice" in
      r|R) rm -rf "$DEST" ;;
      k|K) [[ -d "${DEST}-old" ]] && die "Backup already exists: ${DEST}-old"
           mv "$DEST" "${DEST}-old" ;;
      c|C) die "Installation cancelled — nothing was changed." ;;
      *)   die "Unrecognized choice: $choice" ;;
    esac
  fi
fi

info "Installing to $DEST"
mkdir -p "${DEST:h}"
cp -R "$SRC" "$DEST" || die "Copy failed."

# Not every unzip tool preserves Unix modes.
chmod +x "$DEST/Contents/MacOS/"*
chmod +x "$DEST/Contents/runtime/bin/"* 2>/dev/null || true
[[ -f "$DEST/Contents/runtime/lib/jspawnhelper" ]] && chmod +x "$DEST/Contents/runtime/lib/jspawnhelper"

# Signing is not notarization: without this the user has to right-click → Open the first
# time, because Gatekeeper flags anything a browser downloaded.
info "Removing the quarantine attribute …"
xattr -dr com.apple.quarantine "$DEST" 2>/dev/null || true

echo ""
info "Installation complete."
info "Open $APP_NAME from Finder or Spotlight."
if (( ! NONINTERACTIVE )); then
  read -r "reply?  Open now? [y/N] "
  case "$reply" in y|Y) open "$DEST" ;; esac
fi
