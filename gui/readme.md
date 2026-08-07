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

MarkdownToPdf requires **Java 21 or later** with **JavaFX bundled**. Standard OpenJDK
distributions do not include JavaFX; use one of:

- [Liberica Full JDK](https://bell-sw.com/pages/downloads/) (recommended)
- [Azul Zulu FX](https://www.azul.com/downloads/)

## Download and install from a release

The easiest way to get started is the bundled installer script
(`md2pdf-install.sh`) that ships at the root of every release zip. It is the single
installer for all three platforms — on Windows run it from **Git Bash**.

```bash
$ bash md2pdf-install.sh
[INSTALL] Found MarkdownToPdf.app in current directory – using local copy.
[INSTALL] Java 23 with JavaFX OK (/usr/bin/java)
[INSTALL] Installing to /Users/you/Applications/MarkdownToPdf.app ...
```

The script will:

1. Download the latest release from GitHub and unpack it (if you haven't already).
2. Verify that a JavaFX-bundled Java ≥ 21 is available. If not, it offers to install
   [Liberica Full JDK 21](https://bell-sw.com/pages/downloads/?version=java) — via
   Homebrew on macOS when available, otherwise a checksum-verified download from
   BellSoft — or prompts you for the path to an existing JDK.
3. Copy the application bundle to the standard location: `~/Applications/` on macOS,
   `~/.local/share/MarkdownToPdf` on Linux, `~/MarkdownToPdf` on Windows.
4. Create the launcher / shortcut for the platform (`.desktop` entry on Linux, a
   Desktop shortcut on Windows).
5. On macOS, remove the Gatekeeper quarantine attribute, so you do **not** have to
   right-click → **Open** the first time.

If a previous installation already exists at the target path you are asked whether to
remove the old one, keep both (the old one is renamed …-old), or abort.

### Which JDK the installed app launches with

If the `java` on your `PATH` is already a JavaFX-bundled JDK 21+, the launchers just
use it. Otherwise the installer records the JDK it validated (or installed) in a
`md2pdf.env` file at the root of the installation:

```
MD2PDF_JAVA_HOME="/Users/you/.sdkman/candidates/java/21.0.5-librca"
```

All launchers — `run.sh`, `run.zsh`, `run.cmd`, and the macOS `.app` entry point —
read that file, so the app starts with the same JDK the installer checked. Delete the
file to fall back to `PATH`, or edit it to point somewhere else.

You can also override it yourself by exporting `MD2PDF_JAVA_HOME` (pointing at a JDK
home directory, the one containing `bin/java`); an environment variable always wins
over `md2pdf.env`. On macOS, set it in `~/.zshrc` rather than only in your terminal —
the `.app` entry point sources `~/.zshrc` before checking for Java, which is what lets
a double-clicked app see the override:

```zsh
export MD2PDF_JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.5-librca"
```

## Building from source

Prerequisites: JDK 21+, Maven 3.9.9+.

```bash
# Standard build (compile + test)
mvn install

# Build a standalone fat-jar (includes all dependencies except JavaFX)
mvn install && mvn package -P fatjar -pl gui
```

The fat-jar is created in `gui/target/` and named
`MarkdownToPdf-<version>-jar-with-dependencies.jar`.

### One-command build + install (macOS / Linux)

From the repository root, `install.sh` builds the project, packages the app bundle, and
installs it in one step:

```bash
./install.sh [installDir]
```

This runs `mvn install`, builds `MarkdownToPdf.app` (via `gui/createApp.sh`), and unzips it
into `installDir` — defaulting to `~/Applications` on macOS or `~/.local/share` on Linux.
On Linux it also runs the installed app's `createLauncher.sh` to create a `.desktop` launcher.

This is the source-checkout path. If you are installing a downloaded release zip instead,
use `md2pdf-install.sh` from the zip, described
[above](#download-and-install-from-a-release).

## Running

The launch scripts pick up the newest `MarkdownToPdf-*-with-dependencies.jar` from
the same directory automatically.

### Linux

```bash
./run.sh
```

To create a `.desktop` launcher shortcut:

```bash
./createLauncher.sh
```

### macOS

Double-click `MarkdownToPdf.app`, or from a terminal:

```zsh
./run.zsh
```

If you downloaded a release zip rather than building from source, run
`bash md2pdf-install.sh` (at the root of the zip, next to `MarkdownToPdf.app`) after
unzipping — see [Download and install from a release](#download-and-install-from-a-release)
above. It handles the quarantine attribute, the JDK check and the install location for you.

The `.app` bundle structure expected on disk:

```
MarkdownToPdf.app/
  Contents/
    MacOS/
      markdownToPdf          # entry point called by macOS
    Resources/
      md2pdf.icns
    Info.plist
  run.zsh
  MarkdownToPdf-<version>-with-dependencies.jar
```

If you installed by dragging the app manually rather than running `md2pdf-install.sh`, the
first time you open the app you may need to right-click and choose **Open** to mark it as a
trusted application.

### Windows

Double-click `run.cmd`, or open a Command Prompt and run:

```cmd
run.cmd
```

To create a Desktop shortcut, open PowerShell and run:

```powershell
.\createShortcut.ps1
```

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
