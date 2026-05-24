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
mvn install -P fatjar
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

The first time you open the app you may need to right-click and choose **Open** to
mark it as a trusted application.

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
