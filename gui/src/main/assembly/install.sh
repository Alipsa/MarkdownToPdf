#!/usr/bin/env bash
# MarkdownToPdf – cross-platform installer (macOS / Linux / Git Bash on Windows)
# Place this script at the root of the extracted release zip or run it
# standalone and it will download the latest release automatically.
set -euo pipefail

MIN_JAVA_VERSION="21"
REPO="Alipsa/MarkdownToPdf"
APP_NAME="MarkdownToPdf.app"

# ── colour helpers ────────────────────────────────────────────────
RED='\033[0;31m'
GRN='\033[0;32m'
YEL='\033[1;33m'
NC='\033[0m'
info()  { printf "${GRN}[INSTALL]${NC} %s\n" "$*"; }
warn()  { printf "${YEL}[WARN]${NC}  %s\n" "$*" >&2; }
die()   { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; exit 1; }

# ── detect OS ────────────────────────────────────────────────────
detect_os() {
    local os_family
    os_family="$(uname -s)"
    case "$os_family" in
      Darwin*)     OS="macos" ;;
      Linux*)      OS="linux" ;;
      MINGW*|MSYS*|CYGWIN*) OS="windows" ;;   # Git Bash / Cygwin on Windows
      *)           die "Unsupported operating system: $os_family" ;;
    esac
}

# ── find or fetch the MarkdownToPdf.app bundle on disk ───────────
ensure_app_bundle() {
    if [ -d "${APP_NAME}" ]; then
        info "Found ${APP_NAME} in current directory – using local copy."
        return
    fi
    info "Downloading latest release from github.com/${REPO} …"
    _download_release
}

_download_release() {
    local token="${GITHUB_TOKEN:-}"
    local api_headers=()
    if [ -n "$token" ]; then
        api_headers=(-H "Authorization: Bearer $token")
    fi

    # Latest release redirect → extract tag
    local rel_url
    rel_url=$(curl -fsSL -I "https://github.com/${REPO}/releases/latest" 2>/dev/null \
              | grep -i '^location:' | sed 's/^[^:]*:\s*//;s/\r$//' || true)
    die_on_empty "$rel_url" "Could not resolve latest release URL."

    local tag="${rel_url##*/}"

    # Find the .zip asset on that release
    local api="https://api.github.com/repos/${REPO}/releases/tags/${tag}"
    local asset_name
    asset_name=$(curl "${api_headers[@]}" -fsSL "$api" 2>/dev/null \
              | sed -nE 's/.*"name"[[:space:]]*:[[:space:]]*"([^"]+\.zip)".*/\1/p' \
              | head -n1 || true)
    die_on_empty "$asset_name" "Could not find a .zip asset on the release."

    info "Downloading $asset_name …"
    local dl_url="https://github.com/${REPO}/releases/download/${tag}/${asset_name}"
    curl -fsSL "${dl_url}" -o "/tmp/${asset_name}" \
      || die "Download failed – try setting GITHUB_TOKEN env var."
    unzip -o "/tmp/${asset_name}" -d "." || die "Extraction failed."
    rm -f "/tmp/${asset_name}"
}

die_on_empty() { [ -n "${1:-}" ] || die "$2"; }

# ── Java detection & (optional) BellSoft install ─────────────────
check_or_install_java() {
    # 1. Honour JAVA_HOME if set
    if [ -n "${JAVA_HOME:-}" ]; then
        _validate_jdk "${JAVA_HOME}/bin/java"
        return 0
    fi

    # 2. Try java on PATH
    if command -v java >/dev/null 2>&1; then
        local java_bin jv
        java_bin="$(command -v java)"
        jv=$(_java_major_version "$java_bin")
        if [ "$jv" -ge "$MIN_JAVA_VERSION" ]; then
            info "Java $jv found on PATH."
            _validate_jdk "$java_bin"
            return 0
        fi
        warn "Found Java $jv but need >= ${MIN_JAVA_VERSION}."
    else
        warn "No java command found on PATH."
    fi

    # 3. Offer BellSoft Full JDK download / manual path entry
    echo ""
    read -rp "  Install BellSoft Full JDK ${MIN_JAVA_VERSION} now? [y/N] " reply
    case "$reply" in
        [yY][eE][sS]|[yY])
            _install_bellsoft_full_jdk
            ;;
        *)
            echo ""
            read -rp "  Enter the path to a JDK ${MIN_JAVA_VERSION}+ directory (contains bin/java): " manual_home
            die_on_empty "$manual_home" "Empty path given."
            if [ ! -x "${manual_home}/bin/java" ]; then
                die "${manual_home}/bin/java is not executable or does not exist."
            fi
            _validate_jdk "${manual_home}/bin/java"
            export JAVA_HOME="${manual_home}"
            ;;
    esac
}

_java_major_version() {
    local java_bin="${1:-java}"
    "$java_bin" -version 2>&1 | head -1 \
      | sed -E 's/.*["]([0-9]+)\..*/\1/' \
      | grep -oE '^[0-9]+'
}

_validate_jdk() {
    local java_bin="$1"
    if [ ! -x "$java_bin" ]; then
        die "Java binary not found or not executable: $java_bin"
    fi
    local jv=$("$java_bin" -version 2>&1 | head -1 \
               | sed -E 's/.*["]([0-9]+)\..*/\1/' \
               | grep -oE '^[0-9]+')
    [ "$jv" -ge "$MIN_JAVA_VERSION" ] || die "Java $jv is below required ${MIN_JAVA_VERSION}."

    info "Java $jv OK – checking for JavaFX …"
    if "$java_bin" --list-modules 2>/dev/null | grep -q 'javafx'; then
        info "JavaFX detected on this JDK."
    else
        warn "JavaFX modules not detected. The app may fail to start."
        read -rp "  Continue anyway? [y/N] " reply
        case "$reply" in y|Y|yes|Yes) ;; *) die "Aborted." ;; esac
    fi
}

_install_bellsoft_full_jdk() {
    local arch os_ext b_arch
    case "$OS" in
      macos)    arch="$(uname -m)";  os_ext="mac"   ;;
      linux)    arch="$(uname -m)";  os_ext="linux" ;;
      windows)  arch="x86_64";       os_ext="windows" ;;
    esac
    case "$arch" in
        arm64|aarch64) b_arch="aarch64" ;;
        x86_64|i386)   b_arch="x64"     ;;
        *)             die "Unsupported architecture $arch" ;;
    esac

    local dl_url
    dl_url=$(curl -fsSL "https://api.bell-sw.com/v1/downloads/jdk?os=${os_ext}&arch=${b_arch}&version=${MIN_JAVA_VERSION}&build=latest&releaseType=jdk&packageType=full" 2>/dev/null \
          | sed -nE 's/.*"downloadURL"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' \
          | head -n1 || true)
    die_on_empty "$dl_url" "Could not resolve a BellSoft Full JDK download URL."

    info "Downloading BellSoft Full JDK ${MIN_JAVA_VERSION} …"
    local dl_name
    dl_name=$(basename "$dl_url")
    curl -fsSL "$dl_url" -o "/tmp/${dl_name}" || die "BellSoft JDK download failed."

    mkdir -p "${HOME}/.jdks"
    local before_extract
    before_extract=$(mktemp)
    find "${HOME}/.jdks" -mindepth 1 -maxdepth 1 -type d -print | sort > "$before_extract"

    info "Extracting to ${HOME}/.jdks …"
    case "$dl_name" in
        *.tar.gz) tar -xzf "/tmp/${dl_name}" -C "${HOME}/.jdks";;
        *.zip)    unzip -qo "/tmp/${dl_name}" -d "${HOME}/.jdks";;
        *.exe)
            # BellSoft Windows is a self-extracting exe – fall back to manual install hint
            warn "BellSoft Windows JDK is an installer (.exe). Download it manually from:"
            warn "  https://bell-sw.com/pages/downloads/"
            rm -f "/tmp/${dl_name}"
            rm -f "$before_extract"
            die "Cannot auto-install the Windows BellSoft JDK from script – please run the .exe and retry."
            ;;
        *)  die "Unrecognized archive format: $dl_name" ;;
    esac
    rm -f "/tmp/${dl_name}"

    local extracted_dir jdk_dir java_bin
    extracted_dir=$(find "${HOME}/.jdks" -mindepth 1 -maxdepth 1 -type d -print \
        | sort \
        | comm -13 "$before_extract" - \
        | head -n1)
    rm -f "$before_extract"
    die_on_empty "$extracted_dir" "Could not locate BellSoft JDK after extraction."

    java_bin=$(find "$extracted_dir" -path "*/bin/java" -type f -perm -u+x | head -n1)
    die_on_empty "$java_bin" "Could not locate bin/java in extracted BellSoft JDK."
    jdk_dir="$(cd "$(dirname "$java_bin")/.." && pwd)"

    export JAVA_HOME="$jdk_dir"
    info "BellSoft Full JDK installed at ${JAVA_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    _validate_jdk "${JAVA_HOME}/bin/java"
}

# ── resolve install destination, handle existing installation ────
_resolve_dest() {
    local default_dest
    case "$OS" in
      macos)   default_dest="${HOME}/Applications/${APP_NAME}" ;;
      linux)   default_dest="${HOME}/.local/share/MarkdownToPdf" ;;
      windows) default_dest="${HOME}/MarkdownToPdf"            ;;
    esac

    # Check for an existing installation
    if [ -d "$default_dest" ]; then
        echo ""
        warn "Existing installation found at: $default_dest"
        info "What would you like to do?"
        info "  [r]emove the old one and replace it"
        info "  [k]eep both by renaming the old one to ${APP_NAME%.app}-old"
        info "  [c]ancel installation"
        read -rp "  Choose (r/k/c): " choice
        case "$choice" in
            r|R|remove)
                info "Removing existing installation …"
                rm -rf "$default_dest"
                ;;
            k|K|keep|rename)
                local old_dest="${default_dest}-old"
                info "Renaming existing to ${old_dest} …"
                mv "$default_dest" "$old_dest"
                ;;
            c|C|cancel) die "Installation cancelled – nothing was changed." ;;
            *)          die "Unrecognized choice: $choice" ;;
        esac
    fi

    INSTALL_DEST="$default_dest"
}

# ── platform-specific installation ───────────────────────────────
install_app() {
    mkdir -p "$INSTALL_DEST"
    info "Installing to ${INSTALL_DEST}"
    cp -R "${APP_NAME}/." "$INSTALL_DEST/" || die "Copy failed."

    case "$OS" in
      macos)   _finalize_mac ;;
      linux)   _finalize_linux ;;
      windows) _finalize_windows ;;
    esac
}

_finalize_mac() {
    chmod +x "${INSTALL_DEST}/run.zsh"
    chmod +x "${INSTALL_DEST}/MacOS/markdownToPdf"

    echo ""
    info "Installation complete!"
    info "Open ${APP_NAME} via Finder or Spotlight."
    read -rp "  Open now? [y/N] " reply
    case "$reply" in y|Y|yes) open "${INSTALL_DEST}" ;; esac
}

_finalize_linux() {
    info "Creating desktop launcher and shortcuts …"
    if [ -x "${INSTALL_DEST}/createLauncher.sh" ]; then
        bash -c "cd '${INSTALL_DEST}' && bash createLauncher.sh" \
          || warn "createLauncher.sh failed – you can run it manually."
    fi

    echo ""
    info "Installation complete!"
    info "Run with:  bash ${INSTALL_DEST}/run.sh"
}

_finalize_windows() {
    if [ ! -f "${INSTALL_DEST}/createShortcut.ps1" ]; then
        warn "createShortcut.ps1 not found – skipping shortcut creation."
        echo ""
        info "Installation complete!"
        info "Run with:  ${INSTALL_DEST}/run.cmd   (double-click or via cmd)"
        return
    fi

    info "Creating desktop shortcut …"

    # Convert the bash-style path to a native Windows path for PowerShell
    local ps1_path="${INSTALL_DEST}/createShortcut.ps1"
    if command -v cygpath >/dev/null 2>&1; then
        ps1_path="$(cygpath -w "$ps1_path")"
    fi

    # Prefer pwsh, fall back to powershell.exe
    local ps_cmd=""
    if command -v pwsh >/dev/null 2>&1; then
        ps_cmd="pwsh"
    elif command -v powershell >/dev/null 2>&1; then
        ps_cmd="powershell"
    else
        warn "No PowerShell found – desktop shortcut was not created."
        echo ""
        info "Installation complete!"
        info "Run with:  ${INSTALL_DEST}/run.cmd   (double-click or via cmd)"
        return
    fi

    $ps_cmd -ExecutionPolicy Bypass -File "$ps1_path" \
      || warn "Shortcut creation failed – you can run the .ps1 manually."

    echo ""
    info "Installation complete!"
    info "Run with:  ${INSTALL_DEST}/run.cmd   (double-click or via cmd)"
}

# ── main ─────────────────────────────────────────────────────────
main() {
    detect_os
    ensure_app_bundle
    check_or_install_java
    _resolve_dest
    install_app
}

main "$@"
