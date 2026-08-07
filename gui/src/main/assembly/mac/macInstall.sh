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

if [[ "${SOURCE_APP:A}" == "${TARGET_APP:A}" ]]; then
  echo "$APP_NAME is already installed at $TARGET_APP (this script is running from there). Nothing to do."
  exit 0
fi

# ── Java detection ──────────────────────────────────────────────────────────
# MD2PDF_JAVA_HOME, if set to a JDK home containing bin/java, overrides the
# JDK on PATH — useful when the default JVM isn't JavaFX-bundled but a
# suitable one is installed elsewhere (e.g. a non-default sdkman candidate).

javaBin() {
  if [[ -n "$MD2PDF_JAVA_HOME" && -x "$MD2PDF_JAVA_HOME/bin/java" ]]; then
    echo "$MD2PDF_JAVA_HOME/bin/java"
  else
    echo "java"
  fi
}

javaMajorVersion() {
  "$(javaBin)" -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1
}

javaIsSuitable() {
  local bin
  bin=$(javaBin)
  if [[ "$bin" == "java" ]]; then
    command -v java >/dev/null 2>&1 || return 1
  fi
  local v
  v=$(javaMajorVersion)
  case "$v" in
    (""|*[!0-9]*) return 1 ;;
  esac
  [[ "$v" -ge 21 ]] || return 1
  "$bin" --list-modules 2>/dev/null | grep -q '^javafx.controls' || return 1
  return 0
}

installJdkViaPkg() {
  local arch apiArch apiUrl response url sha1 tmpDir tmpPkg actualSha1
  arch=$(uname -m)
  case "$arch" in
    arm64) apiArch="arm" ;;
    x86_64) apiArch="x86" ;;
    *)
      echo "Unsupported architecture: $arch. Install a JavaFX-bundled JDK manually from https://bell-sw.com/pages/downloads/?version=java"
      return 1
      ;;
  esac

  echo "Looking up the latest Liberica Full JDK 21 for macOS ($apiArch)..."
  apiUrl="https://api.bell-sw.com/v1/liberica/releases?version-feature=21&os=macos&arch=${apiArch}&bitness=64&package-type=pkg&bundle-type=jdk-full&version-modifier=latest"
  response=$(curl -fsSL "$apiUrl")
  if [[ -z "$response" || "$response" == "[]" ]]; then
    echo "Could not find a Liberica JDK release. Install one manually from https://bell-sw.com/pages/downloads/?version=java"
    return 1
  fi
  url=$(echo "$response" | grep -o '"downloadUrl":"[^"]*"' | head -1 | cut -d'"' -f4)
  sha1=$(echo "$response" | grep -o '"sha1":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [[ -z "$url" || -z "$sha1" ]]; then
    echo "Unexpected response from the BellSoft API. Install a JDK manually from https://bell-sw.com/pages/downloads/?version=java"
    return 1
  fi

  tmpDir=$(mktemp -d) || return 1
  tmpPkg="$tmpDir/liberica-jdk21-full.pkg"
  echo "Downloading $url"
  if ! curl -fL -o "$tmpPkg" "$url"; then
    echo "Download failed."
    rm -rf "$tmpDir"
    return 1
  fi

  actualSha1=$(shasum -a 1 "$tmpPkg" | cut -d' ' -f1)
  if [[ "$actualSha1" != "$sha1" ]]; then
    echo "Checksum mismatch (expected $sha1, got $actualSha1). Aborting install."
    rm -rf "$tmpDir"
    return 1
  fi

  echo "Installing (you may be asked for your password)..."
  sudo installer -pkg "$tmpPkg" -target /
  local installResult=$?
  rm -rf "$tmpDir"
  return $installResult
}

if javaIsSuitable; then
  echo "Found a suitable JDK: $(javaMajorVersion)"
else
  # A stale/unsuitable MD2PDF_JAVA_HOME would otherwise pin javaBin() to the
  # same bad path through the install attempt below, masking a successful
  # install of a working JDK on PATH — drop it for the rest of this run.
  unset MD2PDF_JAVA_HOME
  echo "No JavaFX-bundled JDK 21+ was found."
  read -q "REPLY?Install Liberica Full JDK 21 now? (y/n) "
  echo
  if [[ "$REPLY" == "y" ]]; then
    if command -v brew >/dev/null 2>&1; then
      if brew tap bell-sw/liberica && brew install --cask liberica-jdk21-full; then
        :
      else
        echo "Homebrew install failed."
      fi
    else
      installJdkViaPkg
    fi
    hash -r
    if javaIsSuitable; then
      echo "Java installed successfully: $(javaMajorVersion)"
    else
      echo "Java installation did not complete. Open a new terminal and re-run this script, or install manually from https://bell-sw.com/pages/downloads/?version=java"
    fi
  else
    echo "Skipping Java install. Install a JavaFX-bundled JDK 21+ manually before launching MarkdownToPdf: https://bell-sw.com/pages/downloads/?version=java"
  fi
fi

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

mkdir -p "$TARGET_DIR"

echo "Copying $APP_NAME to $TARGET_DIR"
if ! cp -R "$SOURCE_APP" "$TARGET_DIR/"; then
  echo "Copy failed."
  exit 1
fi

echo "Removing quarantine attribute"
xattr -dr com.apple.quarantine "$TARGET_APP" 2>/dev/null || true

echo "Ensuring bundled scripts are executable"
chmod +x "$TARGET_APP/Contents/MacOS/markdownToPdf"
for f in "$TARGET_APP"/*.sh(N) "$TARGET_APP"/*.zsh(N); do
  chmod +x "$f"
done

echo "Installed $APP_NAME to $TARGET_DIR"
if javaIsSuitable; then
  echo "Java: OK ($(javaMajorVersion))"
else
  echo "Java: not found — install a JavaFX-bundled JDK 21+ before launching MarkdownToPdf."
fi
echo "You can now launch it from Applications (or Spotlight)."
