#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "${DIR}" || exit 1

LAUNCHER=~/.local/share/applications/md2pdf.desktop
{
echo "[Desktop Entry]
Name=MarkdownToPdf
Exec=${DIR}/run.sh
Comment=MarkdownToPdf, a PDF Document Development Environment
Terminal=false
Icon=${DIR}/journo-rounded.png
Type=Application
Categories=Development"
} > ${LAUNCHER}

chmod +x run.sh
chmod +x ${LAUNCHER}
if [[ -f ~/Desktop/md2pdf.desktop ]]; then
  rm ~/Desktop/md2pdf.desktop
fi
ln -s ${LAUNCHER} ~/Desktop/md2pdf.desktop

echo "Launcher shortcuts created!"