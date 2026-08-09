#!/usr/bin/env bash
# Builds the self-contained MarkdownToPdf archive for one platform.
#
# Must run natively on the target platform: jlink can only produce an image for the
# platform whose jmods it is given, and $JAVA_HOME is the only JDK we have.
#
#   ./createApp.sh linux | macos | windows | no-jdk
#
# Windows: run under Git Bash. Build scripts and installers have different audiences —
# only the installers have to be native to the platform's shell.
set -euo pipefail

DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" > /dev/null 2>&1 && pwd )"
TARGET="$DIR/target"

PLATFORM="${1:-}"
case "$PLATFORM" in
  linux)   ARCH_LABEL="linux-x64"      ; EXPECTED_ARCH="x86_64" ;;
  macos)   ARCH_LABEL="macos-aarch64"  ; EXPECTED_ARCH="arm64"  ;;
  windows) ARCH_LABEL="windows-x64"    ; EXPECTED_ARCH="x86_64" ;;
  no-jdk)  ARCH_LABEL="no-jdk"           ; EXPECTED_ARCH=""       ;;
  *) echo "usage: $0 <linux|macos|windows|no-jdk>" >&2; exit 2 ;;
esac

# The module set is hand-curated: jlink's --bind-services or a whole-JDK image would
# undo the size saving this packaging exists for. jdk.localedata is deliberately absent
# (~15 MB); the consequence is English-only locale formatting.
# jdk.crypto.ec is required for TLS: WebView can load https: resources in a preview. The
# smoke tests intentionally do not cover this lazily-loaded provider; they use local or
# inline content and open no sockets, so this module must not be inferred from their result.
MODULES="javafx.controls,javafx.swing,javafx.web,\
java.desktop,java.logging,java.management,java.naming,\
java.net.http,java.prefs,java.scripting,java.sql,java.xml,\
jdk.charsets,jdk.crypto.ec,jdk.unsupported,jdk.zipfs"

die() { echo "ERROR: $*" >&2; exit 1; }

# Git Bash ships neither zip nor unzip, so a Windows build fails here with a usable
# message rather than half-writing an archive.
require_tools() {
  local missing=()
  for t in unzip find awk sed; do
    command -v "$t" > /dev/null || missing+=("$t")
  done
  # A jlink image contains symlinks. zip -y stores them; 7z follows them, which inflates
  # the archive and produces a runtime that breaks on extraction — so 7z is acceptable
  # only on Windows, where the image has no symlinks to lose.
  if ! command -v zip > /dev/null; then
    if [ "$PLATFORM" = "windows" ] && command -v 7z > /dev/null; then
      :
    else
      missing+=("zip")
    fi
  fi
  [ "${#missing[@]}" -eq 0 ] || die "missing required tools: ${missing[*]}"
}

make_zip() {                  # make_zip <output.zip> <directory-whose-contents-to-zip>
  local zipfile="$1" srcdir="$2"
  rm -f "$zipfile"
  if command -v zip > /dev/null; then
    ( cd "$srcdir" && zip -q -r -y "$zipfile" . )
  else
    ( cd "$srcdir" && 7z a -tzip -bso0 -bsp0 "$zipfile" . )
  fi
}

require_arch() {
  [ -n "$EXPECTED_ARCH" ] || return 0
  local actual
  case "$PLATFORM" in
    windows) actual="${PROCESSOR_ARCHITECTURE:-unknown}"
             [ "$actual" = "AMD64" ] || die "expected AMD64, got $actual" ;;
    *)       actual="$(uname -m)"
             [ "$actual" = "$EXPECTED_ARCH" ] || die "expected $EXPECTED_ARCH, got $actual" ;;
  esac
}

# The version comes from the built jar's manifest rather than from the POM, so the
# archive name can never disagree with the jar inside it.
app_version() {
  local version
  version="$(unzip -p "$(app_jar)" META-INF/MANIFEST.MF | tr -d '\r' \
    | awk -F': ' '/^Implementation-Version: /{print $2; exit}')"
  # addDefaultImplementationEntries writes this; an empty value means the manifest is not
  # the one maven-jar-plugin produces, and every archive name downstream would be wrong.
  [ -n "$version" ] || die "no Implementation-Version in $(app_jar) manifest"
  printf '%s\n' "$version"
}

# Exactly one candidate, never "the first one". target/ accumulates: bump ${revision} without
# a clean and two versions sit side by side, and picking either one silently produces an
# archive whose name, contents and version need not agree.
app_jar() {
  local jars count
  jars="$(find "$TARGET" -maxdepth 1 -name 'MarkdownToPdf-*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort)"
  # A string rather than an array on purpose: macOS ships bash 3.2, where expanding an
  # empty array under `set -u` is an "unbound variable" error, and empty is a case this
  # function has to report rather than crash on.
  count="$(printf '%s' "$jars" | grep -c . || true)"
  case "$count" in
    0) die "no application jar in $TARGET — run 'mvn install' first" ;;
    1) printf '%s\n' "$jars" ;;
    *) die "$TARGET holds $count application jars:
$jars
Run 'mvn clean install' — packaging will not guess which one to ship." ;;
  esac
}

build_runtime() {
  local out="$1"
  [ -n "${JAVA_HOME:-}" ] || die "JAVA_HOME is not set"
  [ -d "$JAVA_HOME/jmods" ] \
    || die "$JAVA_HOME has no jmods/ — a Liberica *Full* JDK is required, not a JRE"

  local java_version java_major
  java_version="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 \
    | sed -E 's/.*"([^"]+)".*/\1/')"
  java_major="$(printf '%s\n' "$java_version" \
    | sed -E 's/^1\.([0-9]+).*/\1/; s/^([0-9]+).*/\1/')"
  case "$java_major" in
    ''|*[!0-9]*) die "cannot determine Java major version from $JAVA_HOME/bin/java" ;;
  esac
  [ "$java_major" -ge 25 ] \
    || die "$JAVA_HOME provides Java $java_version, but the GUI requires Java 25 or higher"

  rm -rf "$out"
  # JDK 25 supports the explicit ZIP compression-level syntax. Keep this aligned with the
  # JDK used by CI and release packaging rather than relying on the deprecated numeric form.
  "$JAVA_HOME/bin/jlink" \
    --module-path "$JAVA_HOME/jmods" \
    --add-modules "$MODULES" \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 \
    --output "$out"
}

require_tools
require_arch
[ -d "$TARGET/lib" ] || die "$TARGET/lib is missing — run 'mvn install' first"
VERSION="$(app_version)"
echo "Building MarkdownToPdf $VERSION for $ARCH_LABEL"

STAGE="$TARGET/stage-$ARCH_LABEL"
rm -rf "$STAGE"

# The application jar is copied under a fixed name so launchers, .desktop entries and
# Windows shortcuts can reference a path that survives a version bump. The version stays
# readable from the manifest's Implementation-Version.
stage_app() {                 # stage_app <dir-to-put-jar-and-lib-in>
  mkdir -p "$1"
  cp "$(app_jar)" "$1/MarkdownToPdf.jar"
  cp -R "$TARGET/lib" "$1/lib"
}

case "$PLATFORM" in
  linux)
    APPDIR="$STAGE/MarkdownToPdf"
    mkdir -p "$APPDIR"
    stage_app "$APPDIR"
    build_runtime "$APPDIR/runtime"
    cp "$DIR/src/main/assembly/linux/run.sh"           "$APPDIR/"
    cp "$DIR/src/main/assembly/linux/createLauncher.sh" "$APPDIR/"
    cp "$DIR/src/main/resources/MarkdownToPdf-rounded.png" "$APPDIR/"
    cp "$DIR/src/main/assembly/install.sh"             "$STAGE/md2pdf-install.sh"
    chmod +x "$APPDIR"/*.sh "$STAGE/md2pdf-install.sh"
    CHECK_JAR="$APPDIR/MarkdownToPdf.jar"
    CHECK_LIB="$APPDIR/lib"
    ;;
  macos)
    EXECUTABLE="markdownToPdf"
    BUNDLE_VERSION="${VERSION%%-*}"
    printf '%s' "$BUNDLE_VERSION" | grep -Eq '^[0-9]+(\.[0-9]+){0,2}$' \
      || die "BUNDLE_VERSION '$BUNDLE_VERSION' is not one to three period-separated integers"
    COMMIT="$(git -C "$DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)"

    CONTENTS="$STAGE/MarkdownToPdf.app/Contents"
    mkdir -p "$CONTENTS/MacOS" "$CONTENTS/Resources"
    stage_app "$CONTENTS/app"
    build_runtime "$CONTENTS/runtime"

    # The executable name and CFBundleExecutable come from one variable so they cannot
    # diverge again.
    cp "$DIR/src/main/assembly/mac/$EXECUTABLE" "$CONTENTS/MacOS/$EXECUTABLE"
    chmod +x "$CONTENTS/MacOS/$EXECUTABLE"
    cp "$DIR/src/main/assembly/mac/md2pdf.icns" "$CONTENTS/Resources/"
    sed -e "s|@EXECUTABLE@|$EXECUTABLE|g" \
        -e "s|@BUNDLE_VERSION@|$BUNDLE_VERSION|g" \
        -e "s|@FULL_VERSION@|$VERSION|g" \
        -e "s|@COMMIT@|$COMMIT|g" \
        "$DIR/src/main/assembly/mac/Info.plist" > "$CONTENTS/Info.plist"

    cp "$DIR/src/main/assembly/mac/md2pdf-install.zsh" "$STAGE/"
    chmod +x "$STAGE/md2pdf-install.zsh"

    bash "$DIR/src/main/assembly/mac/sign-app.sh" "$STAGE/MarkdownToPdf.app"
    CHECK_JAR="$CONTENTS/app/MarkdownToPdf.jar"
    CHECK_LIB="$CONTENTS/app/lib"
    ;;
  windows)
    APPDIR="$STAGE/MarkdownToPdf"
    mkdir -p "$APPDIR"
    stage_app "$APPDIR"
    build_runtime "$APPDIR/runtime"
    cp "$DIR/src/main/assembly/win/run.cmd"            "$APPDIR/"
    cp "$DIR/src/main/assembly/win/createShortcut.ps1" "$APPDIR/"
    cp "$DIR/src/main/resources/MarkdownToPdf-rounded.ico" "$APPDIR/"
    cp "$DIR/src/main/assembly/win/md2pdf-install.cmd" "$STAGE/"
    CHECK_JAR="$APPDIR/MarkdownToPdf.jar"
    CHECK_LIB="$APPDIR/lib"
    ;;
  no-jdk)
    stage_app "$STAGE"
    cp "$DIR/src/main/assembly/no-jdk/README.txt" "$STAGE/"
    CHECK_JAR="$STAGE/MarkdownToPdf.jar"
    CHECK_LIB="$STAGE/lib"
    ;;
  *) die "$PLATFORM packaging is not implemented yet" ;;
esac

"$DIR/../.github/scripts/check-lib-classpath.sh" "$CHECK_JAR" "$CHECK_LIB"

ZIP="$TARGET/md2pdf-${VERSION}-${ARCH_LABEL}.zip"
make_zip "$ZIP" "$STAGE"
echo "Built $ZIP"
