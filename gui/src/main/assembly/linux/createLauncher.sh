#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "${DIR}" || exit 1

LAUNCHER=~/.local/share/applications/MarkdownToPdf.desktop
{
echo "[Desktop Entry]
Name=MarkdownToPdf
Exec=${DIR}/run.sh
Comment=MarkdownToPdf, a PDF Document Development Environment
Terminal=false
Icon=${DIR}/MarkdownToPdf-rounded.png
Type=Application
Categories=Development"
} > ${LAUNCHER}

chmod +x run.sh
chmod +x ${LAUNCHER}
if [[ -f ~/Desktop/MarkdownToPdf.desktop ]]; then
  rm ~/Desktop/MarkdownToPdf.desktop
fi
ln -s ${LAUNCHER} ~/Desktop/MarkdownToPdf.desktop

echo "Launcher shortcuts created!"