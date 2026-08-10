# MarkdownToPdf Desktop Application

MarkdownToPdf is a JavaFX desktop application for editing Markdown documents
and exporting them to PDF (or HTML). It provides a live preview, a visual style editor
with named style profiles, and a CSS power-user mode.

## Features

- **Markdown editor** with syntax highlighting and live split-pane HTML preview
- **Style profiles** — visual form controls for fonts, colours, heading sizes, page
  margins and orientation; save and load named profiles
- **CSS editor** — toggle to a raw CSS editor to write rules not exposed in the form;
  switching back to the visual editor round-trips the CSS into the form controls
- **Load CSS file** — open any `.css` file directly into the CSS editor
- **PDF viewer** — embedded page-by-page viewer, save to file or open in the system
  viewer
- **Export** — export the current document as HTML or PDF via the File menu
- **Project management** — create / open / save `.jpr` project files that remember the
  Markdown source path and style profile
- **Find** — Ctrl+F text search inside the editor

## Requirements

The platform archives bundle their own Java 25 runtime, so no JDK is required. The `-no-jdk`
archive needs a **JavaFX-bundled JDK 25 or later** (e.g. [Liberica Full](https://bell-sw.com/pages/downloads/)
or [Azul Zulu FX](https://www.azul.com/downloads/)); a plain OpenJDK will not work.

### Linux prerequisites

The bundled runtime supplies Java, but not the system libraries JavaFX draws with.
GTK 3 and the WebKit dependencies must be present:

    Debian/Ubuntu: sudo apt-get install libgtk-3-0 libxtst6
    Fedora/RHEL:   sudo dnf install gtk3 libXtst
    Arch:          sudo pacman -S gtk3 libxtst

The installer checks for these and reports what is missing, but cannot install them.

## Download and install from a release

| Download | Install |
|---|---|
| `md2pdf-<version>-linux-x64.zip` | unzip, then `bash md2pdf-install.sh` |
| `md2pdf-<version>-macos-aarch64.zip` | unzip, then `zsh md2pdf-install.zsh` |
| `md2pdf-<version>-windows-x64.zip` | unzip, then double-click `md2pdf-install.cmd` |
| `md2pdf-<version>-no-jdk.zip` | unzip, then `java --enable-native-access=javafx.graphics,javafx.web,javafx.media -jar MarkdownToPdf.jar` |

The installer will:

1. Copy the application to the standard location: `~/.local/share/MarkdownToPdf` on Linux,
   `~/Applications/MarkdownToPdf.app` on macOS, `%LOCALAPPDATA%\Programs\MarkdownToPdf` on Windows.
2. Create the launcher / shortcut for the platform (`.desktop` entry on Linux, a Desktop
   shortcut on Windows, the `.app` bundle on macOS).
3. On macOS, remove the Gatekeeper quarantine attribute, so you do **not** have to
   right-click → **Open** the first time.

If a previous installation already exists at the target path and the terminal is not
interactive, the installer cancels. Re-run with `MD2PDF_REPLACE_EXISTING=1` to replace it.

## Building from source

Prerequisites: a JavaFX-bundled JDK 25+ and Maven 3.9.9+.

```bash
# Standard build (compile + test)
mvn install

# Build a platform archive (linux | macos | windows | no-jdk)
mvn install -DskipTests && ./gui/createApp.sh linux
```

The archive is created in `gui/target/` and named `md2pdf-<version>-<platform>.zip`.

### One-command build + install (macOS / Linux)

From the repository root, `install.sh` builds the project, packages the app bundle, and
installs it in one step:

```bash
./install.sh [installDir]
```

This runs `mvn install`, builds the platform archive (via `gui/createApp.sh`), and installs
it — defaulting to `~/Applications/MarkdownToPdf` on macOS or
`~/.local/share/MarkdownToPdf` on Linux. If `installDir` is supplied, it is the exact
destination directory, not its parent. An existing destination is prompted for removal,
renaming, or cancellation; repeating `./install.sh` therefore prompts instead of replacing
it silently. In non-interactive use, an existing destination is rejected unless
`MD2PDF_REPLACE_EXISTING=1` is explicitly set.

This is the source-checkout path. If you are installing a downloaded release zip instead,
use the platform-specific installer from the zip, described
[above](#download-and-install-from-a-release).

## Running

### Linux

```bash
bash ~/.local/share/MarkdownToPdf/run.sh
```

### macOS

Double-click `~/Applications/MarkdownToPdf.app`, or from a terminal:

```zsh
~/Applications/MarkdownToPdf.app/Contents/MacOS/markdownToPdf
```

### Windows

Double-click the `MarkdownToPdf` shortcut on the Desktop, or run:

```cmd
%LOCALAPPDATA%\Programs\MarkdownToPdf\run.cmd
```

## Recovery after a partial release

`release.sh` publishes to Maven Central in step 6, and that step cannot be undone or
repeated. Steps 7 and 8 — the tag and the GitHub release — are both reversible.

If a release fails after the deploy:

    git push --delete origin v<version>
    git tag -d v<version>
    gh release delete v<version> --yes    # only if a partial release was created
    ./release.sh --skip-deploy

`--skip-deploy` re-downloads the same CI artifacts and resumes from the tag. It works
from a clean checkout: nothing in steps 3-5 is built locally.

## Style Profiles

Style profiles are stored as `.properties` files under
`~/.config/md2pdf/profiles/` (Linux/macOS) or
`%APPDATA%\md2pdf\profiles\` (Windows).

The built-in **Default** profile can be loaded via the **Load ▼** button in the
Style tab; custom profiles can be saved there too.

## Project Files

A project file (`.jpr`) is a standard Java properties file that records:

| Key | Description |
|-----|-------------|
| `name` | Project name |
| `templateFile` | Relative path to the Markdown source file |
| `styleProfileName` | Name of the style profile to use |

## License

MIT — see [LICENSE](../LICENSE).
