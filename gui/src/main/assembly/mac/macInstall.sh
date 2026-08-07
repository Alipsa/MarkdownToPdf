#!/usr/bin/env zsh
# Installs MarkdownToPdf.app into ~/Applications, removes the macOS quarantine
# attribute, and ensures bundled scripts are executable.
# Run this from the folder where md2pdf-gui.zip was unzipped (MarkdownToPdf.app
# must be next to this script).

DIR="${0:A:h}"
APP_NAME="MarkdownToPdf.app"
SOURCE_APP="$DIR/$APP_NAME"
TARGET_DIR="$HOME/Applications"
TARGET_APP="$TARGET_DIR/$APP_NAME"

if [[ ! -d "$SOURCE_APP" ]]; then
  echo "Could not find $APP_NAME next to this script ($DIR). Aborting."
  exit 1
fi

mkdir -p "$TARGET_DIR"

if [[ -d "$TARGET_APP" ]]; then
  echo "$TARGET_APP already exists."
  read -q "REPLY?Replace it? (y/n) "
  echo
  if [[ "$REPLY" != "y" ]]; then
    echo "Aborted, nothing was changed."
    exit 0
  fi
  rm -rf "$TARGET_APP"
fi

echo "Copying $APP_NAME to $TARGET_DIR"
cp -R "$SOURCE_APP" "$TARGET_DIR/"

echo "Removing quarantine attribute"
xattr -dr com.apple.quarantine "$TARGET_APP" 2>/dev/null || true

echo "Ensuring bundled scripts are executable"
chmod +x "$TARGET_APP/Contents/MacOS/markdownToPdf"
for f in "$TARGET_APP"/*.sh(N) "$TARGET_APP"/*.zsh(N); do
  chmod +x "$f"
done

echo "Installed $APP_NAME to $TARGET_DIR"
echo "You can now launch it from Applications (or Spotlight)."
