#!/usr/bin/env bash
# Creates the .desktop launcher for MarkdownToPdf. Fails loudly: the installer
# relies on the exit status to know whether the launcher was actually created.
set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "${DIR}" || exit 1

APPLICATIONS_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
LAUNCHER="${APPLICATIONS_DIR}/MarkdownToPdf.desktop"

# A fresh account may not have this directory yet, and the redirect below would
# otherwise fail with "No such file or directory".
mkdir -p "${APPLICATIONS_DIR}"

{
echo "[Desktop Entry]
Name=MarkdownToPdf
Exec=${DIR}/run.sh
Comment=MarkdownToPdf, a PDF Document Development Environment
Terminal=false
Icon=${DIR}/MarkdownToPdf-rounded.png
Type=Application
Categories=Development"
} > "${LAUNCHER}"

chmod +x "${LAUNCHER}"

# The desktop symlink is a convenience; a missing ~/Desktop (or a localised one)
# must not fail the install.
DESKTOP_DIR="$HOME/Desktop"
if command -v xdg-user-dir >/dev/null 2>&1; then
  DESKTOP_DIR="$(xdg-user-dir DESKTOP 2>/dev/null || echo "$HOME/Desktop")"
fi
if [[ -d "${DESKTOP_DIR}" ]]; then
  rm -f "${DESKTOP_DIR}/MarkdownToPdf.desktop"
  ln -s "${LAUNCHER}" "${DESKTOP_DIR}/MarkdownToPdf.desktop"
  echo "Launcher shortcuts created!"
else
  echo "Launcher created at ${LAUNCHER} (no desktop directory found, skipped the desktop shortcut)."
fi
