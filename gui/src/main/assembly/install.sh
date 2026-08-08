#!/usr/bin/env bash
# MarkdownToPdf – cross-platform installer (macOS / Linux / Git Bash on Windows)
# Place this script at the root of the extracted release zip or run it
# standalone and it will download the latest release automatically.
set -euo pipefail

MIN_JAVA_VERSION="21"
REPO="Alipsa/MarkdownToPdf"
APP_NAME="MarkdownToPdf.app"
JDK_DOWNLOAD_PAGE="https://bell-sw.com/pages/downloads/?version=java"

# Home of the JDK the installed app should use. Left empty when the `java` on
# PATH is already suitable, in which case the launchers just use PATH. When set,
# it is written to md2pdf.env in the install directory and the launchers pick it
# up as MD2PDF_JAVA_HOME.
RESOLVED_JAVA_HOME=""

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
    # 1. Honour an explicit MD2PDF_JAVA_HOME / JAVA_HOME override
    local override=""
    if [ -n "${MD2PDF_JAVA_HOME:-}" ]; then
        override="$MD2PDF_JAVA_HOME"
    elif [ -n "${JAVA_HOME:-}" ]; then
        override="$JAVA_HOME"
    fi
    if [ -n "$override" ]; then
        if _jdk_is_suitable "${override}/bin/java"; then
            info "Using the JDK at ${override}"
            RESOLVED_JAVA_HOME="$override"
            return 0
        fi
        # A stale or non-JavaFX override must not mask a working JDK on PATH or
        # one we are about to install, so drop it for the rest of this run.
        warn "${override} is not a usable JavaFX-bundled JDK ${MIN_JAVA_VERSION}+ – ignoring it."
    fi

    # 2. Try java on PATH
    if _jdk_is_suitable "$(command -v java 2>/dev/null || true)"; then
        info "Using the java found on PATH."
        RESOLVED_JAVA_HOME=""
        return 0
    fi

    # 3. Offer BellSoft Full JDK install / manual path entry
    echo ""
    warn "No JavaFX-bundled JDK ${MIN_JAVA_VERSION}+ was found."
    read -rp "  Install BellSoft Liberica Full JDK ${MIN_JAVA_VERSION} now? [y/N] " reply
    case "$reply" in
        [yY][eE][sS]|[yY])
            _install_bellsoft_full_jdk
            ;;
        *)
            echo ""
            read -rp "  Enter the path to a JavaFX-bundled JDK ${MIN_JAVA_VERSION}+ directory (contains bin/java): " manual_home
            die_on_empty "$manual_home" "Empty path given."
            _jdk_is_suitable "${manual_home}/bin/java" \
              || die "${manual_home} is not a usable JavaFX-bundled JDK ${MIN_JAVA_VERSION}+."
            RESOLVED_JAVA_HOME="$manual_home"
            ;;
    esac
}

_java_major_version() {
    local java_bin="${1:-java}"
    local version_line major
    version_line=$("$java_bin" -version 2>&1 | head -1 || true)
    # Strip a legacy 1.x prefix, then take the feature version.
    major=$(printf '%s\n' "$version_line" \
            | sed -nE 's/.*version "(1\.)?([0-9]+).*/\2/p')
    # Print 0 rather than failing so callers can compare numerically.
    printf '%s\n' "${major:-0}"
}

# Returns 0 when $1 is a java binary of at least MIN_JAVA_VERSION that bundles
# JavaFX. Prints nothing on success; the caller reports.
_jdk_is_suitable() {
    local java_bin="${1:-}"
    [ -n "$java_bin" ] && [ -x "$java_bin" ] || return 1

    local jv
    jv=$(_java_major_version "$java_bin")
    if [ "$jv" -lt "$MIN_JAVA_VERSION" ]; then
        if [ "$jv" -gt 0 ]; then
            warn "Found Java $jv at $java_bin but need >= ${MIN_JAVA_VERSION}."
        fi
        return 1
    fi

    if ! "$java_bin" --list-modules 2>/dev/null | grep -q '^javafx.controls'; then
        warn "Java $jv at $java_bin does not bundle JavaFX (javafx.controls is missing)."
        return 1
    fi

    info "Java $jv with JavaFX OK ($java_bin)"
    return 0
}

_install_bellsoft_full_jdk() {
    if [ "$OS" = "macos" ] && command -v brew >/dev/null 2>&1; then
        info "Installing Liberica Full JDK ${MIN_JAVA_VERSION} via Homebrew …"
        if brew install --cask "liberica-jdk${MIN_JAVA_VERSION}-full"; then
            _adopt_installed_jdk && return 0
        fi
        warn "Homebrew install did not yield a usable JDK – falling back to a direct download."
    fi
    _install_bellsoft_from_api
    _adopt_installed_jdk \
      || die "The JDK was installed but could not be located afterwards. Open a new terminal and re-run this script, or install manually from ${JDK_DOWNLOAD_PAGE}"
}

# Locate a usable JDK after an install, and remember it for the launchers.
_adopt_installed_jdk() {
    hash -r 2>/dev/null || true

    # Something we extracted ourselves takes priority.
    if [ -n "${_EXTRACTED_JDK_HOME:-}" ] && _jdk_is_suitable "${_EXTRACTED_JDK_HOME}/bin/java"; then
        RESOLVED_JAVA_HOME="$_EXTRACTED_JDK_HOME"
        return 0
    fi

    if _jdk_is_suitable "$(command -v java 2>/dev/null || true)"; then
        RESOLVED_JAVA_HOME=""
        return 0
    fi

    # A Homebrew cask installs into /Library/Java/JavaVirtualMachines without
    # touching PATH; java_home knows where it landed.
    if [ "$OS" = "macos" ] && [ -x /usr/libexec/java_home ]; then
        local jh
        jh=$(/usr/libexec/java_home -v "$MIN_JAVA_VERSION" 2>/dev/null || true)
        if [ -n "$jh" ] && _jdk_is_suitable "${jh}/bin/java"; then
            RESOLVED_JAVA_HOME="$jh"
            return 0
        fi
    fi

    return 1
}

_install_bellsoft_from_api() {
    local api_os api_arch bitness pkg_type
    case "$OS" in
      macos)   api_os="macos"   ;;
      linux)   api_os="linux"   ;;
      windows) api_os="windows" ;;
    esac
    case "$OS" in
      windows) pkg_type="zip"    ;;
      *)       pkg_type="tar.gz" ;;
    esac

    local machine
    case "$OS" in
      windows) machine="x86_64"      ;;
      *)       machine="$(uname -m)" ;;
    esac
    bitness="64"
    case "$machine" in
        arm64|aarch64) api_arch="arm" ;;
        x86_64|amd64)  api_arch="x86" ;;
        *)             die "Unsupported architecture $machine – install a JDK manually from ${JDK_DOWNLOAD_PAGE}" ;;
    esac

    # BellSoft's current API: /v1/liberica/releases returning objects with a
    # "downloadUrl" and a "sha1" field.
    local api_url response dl_url expected_sha1
    api_url="https://api.bell-sw.com/v1/liberica/releases?version-feature=${MIN_JAVA_VERSION}&os=${api_os}&arch=${api_arch}&bitness=${bitness}&package-type=${pkg_type}&bundle-type=jdk-full&version-modifier=latest"
    info "Looking up the latest Liberica Full JDK ${MIN_JAVA_VERSION} for ${api_os}/${api_arch} …"
    response=$(curl -fsSL "$api_url" 2>/dev/null || true)
    if [ -z "$response" ] || [ "$response" = "[]" ]; then
        die "Could not find a Liberica Full JDK release. Install one manually from ${JDK_DOWNLOAD_PAGE}"
    fi
    dl_url=$(printf '%s' "$response" | grep -o '"downloadUrl"[[:space:]]*:[[:space:]]*"[^"]*"' \
             | head -n1 | sed -E 's/.*:[[:space:]]*"(.*)"/\1/' || true)
    expected_sha1=$(printf '%s' "$response" | grep -o '"sha1"[[:space:]]*:[[:space:]]*"[^"]*"' \
             | head -n1 | sed -E 's/.*:[[:space:]]*"(.*)"/\1/' || true)
    die_on_empty "$dl_url" "Unexpected response from the BellSoft API. Install a JDK manually from ${JDK_DOWNLOAD_PAGE}"

    local dl_name tmp_dir
    dl_name=$(basename "$dl_url")
    tmp_dir=$(mktemp -d) || die "Could not create a temporary directory."
    # shellcheck disable=SC2064  # expand tmp_dir now, it never changes
    trap "rm -rf '$tmp_dir'" RETURN

    info "Downloading ${dl_name} …"
    curl -fL "$dl_url" -o "${tmp_dir}/${dl_name}" || die "BellSoft JDK download failed."

    if [ -n "$expected_sha1" ]; then
        local actual_sha1
        actual_sha1=$(_sha1_of "${tmp_dir}/${dl_name}")
        if [ -z "$actual_sha1" ]; then
            warn "No sha1 tool available – skipping checksum verification."
        elif [ "$actual_sha1" != "$expected_sha1" ]; then
            die "Checksum mismatch for ${dl_name} (expected ${expected_sha1}, got ${actual_sha1}). Aborting."
        else
            info "Checksum verified."
        fi
    else
        warn "The BellSoft API did not return a checksum – skipping verification."
    fi

    mkdir -p "${HOME}/.jdks"
    local before_extract
    before_extract="${tmp_dir}/before"
    find "${HOME}/.jdks" -mindepth 1 -maxdepth 1 -type d -print | sort > "$before_extract"

    info "Extracting to ${HOME}/.jdks …"
    case "$dl_name" in
        *.tar.gz) tar -xzf "${tmp_dir}/${dl_name}" -C "${HOME}/.jdks" || die "Extraction failed." ;;
        *.zip)    unzip -qo "${tmp_dir}/${dl_name}" -d "${HOME}/.jdks" || die "Extraction failed." ;;
        *)        die "Unrecognized archive format: $dl_name" ;;
    esac

    local extracted_dir java_bin
    extracted_dir=$(find "${HOME}/.jdks" -mindepth 1 -maxdepth 1 -type d -print \
        | sort \
        | comm -13 "$before_extract" - \
        | head -n1)
    die_on_empty "$extracted_dir" "Could not locate the BellSoft JDK after extraction."

    java_bin=$(find "$extracted_dir" -path "*/bin/java" -type f -perm -u+x | head -n1)
    die_on_empty "$java_bin" "Could not locate bin/java in the extracted BellSoft JDK."

    _EXTRACTED_JDK_HOME="$(cd "$(dirname "$java_bin")/.." && pwd)"
    info "Liberica Full JDK installed at ${_EXTRACTED_JDK_HOME}"
}

_sha1_of() {
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 1 "$1" | cut -d' ' -f1
    elif command -v sha1sum >/dev/null 2>&1; then
        sha1sum "$1" | cut -d' ' -f1
    fi
}

# ── resolve install destination, handle existing installation ────
_resolve_dest() {
    local default_dest
    case "$OS" in
      macos)   default_dest="${HOME}/Applications/${APP_NAME}" ;;
      linux)   default_dest="${HOME}/.local/share/MarkdownToPdf" ;;
      windows) default_dest="${HOME}/MarkdownToPdf"            ;;
    esac

    # Refuse to install a directory onto itself (e.g. running the script from
    # inside an existing installation).
    if [ -d "$default_dest" ] \
       && [ "$(cd "$APP_NAME" && pwd -P)" = "$(cd "$default_dest" && pwd -P)" ]; then
        die "${APP_NAME} is already installed at ${default_dest} and this script is running from there – nothing to do."
    fi

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
                if [ -d "$old_dest" ]; then
                    die "Cannot keep both because backup already exists: $old_dest"
                fi
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

    _write_java_env

    case "$OS" in
      macos)   _finalize_mac ;;
      linux)   _finalize_linux ;;
      windows) _finalize_windows ;;
    esac
}

# Record the JDK the launchers should use. Without this the installer would
# validate one JDK and the launchers would then start a different one, because
# they invoke a bare `java`.
_write_java_env() {
    local env_file="${INSTALL_DEST}/md2pdf.env"
    if [ -z "$RESOLVED_JAVA_HOME" ]; then
        # java on PATH is fine – don't pin the app to a specific JDK.
        rm -f "$env_file"
        return
    fi
    info "Recording the JDK to launch with in ${env_file}"
    {
        echo "# Written by md2pdf-install.sh – the JDK MarkdownToPdf launches with."
        echo "# Delete this file to fall back to the java on PATH, or edit the path"
        echo "# to point at a different JavaFX-bundled JDK ${MIN_JAVA_VERSION}+."
        echo "MD2PDF_JAVA_HOME=\"${RESOLVED_JAVA_HOME}\""
    } > "$env_file"
}

_finalize_mac() {
    chmod +x "${INSTALL_DEST}/run.zsh" 2>/dev/null || true
    chmod +x "${INSTALL_DEST}/Contents/MacOS/markdownToPdf" 2>/dev/null || true

    # Gatekeeper flags anything downloaded from a browser; without this the user
    # has to right-click → Open the first time.
    info "Removing the macOS quarantine attribute …"
    xattr -dr com.apple.quarantine "$INSTALL_DEST" 2>/dev/null || true

    echo ""
    info "Installation complete!"
    info "Open ${APP_NAME} via Finder or Spotlight."
    read -rp "  Open now? [y/N] " reply
    case "$reply" in y|Y|yes) open "${INSTALL_DEST}" ;; esac
}

_finalize_linux() {
    chmod +x "${INSTALL_DEST}"/*.sh 2>/dev/null || true

    info "Creating desktop launcher and shortcuts …"
    if [ ! -x "${INSTALL_DEST}/createLauncher.sh" ]; then
        die "createLauncher.sh is missing from ${INSTALL_DEST} – the release archive looks incomplete."
    fi
    if ! (cd "$INSTALL_DEST" && bash createLauncher.sh); then
        die "createLauncher.sh failed – the app is installed at ${INSTALL_DEST} but has no desktop launcher. Run 'bash ${INSTALL_DEST}/createLauncher.sh' to retry, or start it with 'bash ${INSTALL_DEST}/run.sh'."
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
