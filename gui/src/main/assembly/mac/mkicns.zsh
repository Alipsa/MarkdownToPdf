#!/usr/bin/env zsh
SCRIPT_PATH="${0:A:h}"
echo "creating md2pdf.icns file"
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  if ! command -v png2icns &> /dev/null; then
    echo "png2icns could not be found, please install it to create the .icns file"
    echo "e.g: sudo apt install icnsutils"
    exit 1
  fi
  png2icns md2pdf.icns md2pdf.iconset/icon_*.png
elif [[ "$OSTYPE" == "darwin"* ]]; then
  # Mac OSX
  iconutil -c icns "$SCRIPT_PATH"/md2pdf.iconset
else
  echo "unsupported OS: $OSTYPE"
fi
