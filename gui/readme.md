# MarkdownToPdf

MarkdownToPdf is a JavaFX desktop application for editing Markdown documents and
exporting them to PDF (or HTML). It provides a live preview, a visual style editor
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

MarkdownToPdf requires **Java 21 or later** with **JavaFX bundled**.
Standard OpenJDK distributions do not include JavaFX; use one of:

- [Liberica Full JDK](https://bell-sw.com/pages/downloads/) (recommended)
- [Azul Zulu with JavaFX](https://www.azul.com/downloads/)
- [GraalVM](https://www.graalvm.org/) (includes JavaFX on some distributions)

## Building

Prerequisites: JDK 21+, Maven 3.9.9+.

```bash
# Standard build (compile + test + install)
mvn install

# Build a standalone fat-jar (includes all dependencies except JavaFX)
mvn install && mvn package -P fatjar -pl gui
```

The fat-jar is created in `gui/target/` and named
`MarkdownToPdf-<version>-jar-with-dependencies.jar`.

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

If you downloaded a release zip rather than building from source, run `./macInstall.sh`
(included at the root of the zip, next to `MarkdownToPdf.app`) after unzipping. It copies
the app into `~/Applications`, removes the macOS quarantine attribute so you don't have to
right-click → Open, and makes sure the bundled scripts are executable. It also checks for a
JavaFX-bundled JDK 21+ and, if none is found and you agree to the prompt, installs Liberica
Full JDK 21 via Homebrew or a checksum-verified download from BellSoft — that step may ask
for your `sudo` password.

If you already have a JavaFX-bundled JDK installed somewhere that isn't your default `java`
(e.g. a non-default [sdkman](https://sdkman.io/) candidate), point both `macInstall.sh` and
the app at it by setting `MD2PDF_JAVA_HOME` in `~/.zshrc` to that JDK's home directory — the
one containing `bin/java`:

```zsh
export MD2PDF_JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.5-librca"
```

It must go in `~/.zshrc` rather than just being exported in your terminal, because
`markdownToPdf` sources `~/.zshrc` before checking for Java — that's what lets a
double-clicked `.app` (which doesn't otherwise inherit your shell environment) see the
override too. `run.zsh` and `macInstall.sh`'s own JDK detection also respect it.

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

If you installed by dragging the app manually rather than running `macInstall.sh`, the first
time you open the app you may need to right-click and choose **Open** to mark it as a trusted
application.

### One-command build + install (macOS / Linux)

From the repository root, `install.sh` builds the project, packages the app bundle, and
installs it in one step:

```bash
./install.sh [installDir]
```

This runs `mvn install`, builds `MarkdownToPdf.app` (via `gui/createApp.sh`), and unzips it
into `installDir` — defaulting to `~/Applications` on macOS or `~/.local/share` on Linux.
On Linux it also runs the installed app's `createLauncher.sh` to create a `.desktop` launcher.

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

MIT — see [LICENSE](https://github.com/Alipsa/MarkdownToPdf/blob/main/LICENSE).
