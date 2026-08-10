# MarkdownToPdf — Markdown to PDF toolset
[![Maven Central](https://img.shields.io/maven-central/v/se.alipsa/md2pdf)](https://central.sonatype.com/artifact/se.alipsa/md2pdf)
[![javadoc](https://javadoc.io/badge2/se.alipsa/md2pdf/0.1.0/javadoc.svg)](https://javadoc.io/doc/se.alipsa/md2pdf)

MarkdownToPdf is a Java-based toolkit for converting Markdown to PDF. It consists of
two modules:

| Module          | Artifact               | Description                                                                   |
|-----------------|------------------------|-------------------------------------------------------------------------------|
| **[lib](lib/)** | `se.alipsa:md2pdf`     | Core library — turn any Markdown string, file or stream into a PDF byte-array |
| **[gui](gui/)** | standalone desktop app | JavaFX editor with live preview, visual style editor and project management   |

The `lib` module requires **JDK 21 or later**. Building the GUI requires **JDK 25 or later**.
The GUI desktop application is shipped as
self-contained platform archives that bundle their own Java 25 runtime, so end users do not
need a JDK installed. A separate `-no-jdk` archive is available for users who already have
a JavaFX-bundled JDK 25+ and prefer a smaller download.

The library's rendering pipeline is:
**commonmark-java** (Markdown → HTML) → **jsoup** (HTML → well-formed XHTML) →
**OpenHTMLtoPDF + Batik** (XHTML → PDF).

## Quick overview

```java
import se.alipsa.md2pdf.Md2PdfEngine;

byte[] pdf = new Md2PdfEngine()
    .markdown("# Hello\n\nWorld!")
    .css("body { font-family: serif; }")
    .pageMargins("1in")
    .toPdf();
```

Full API documentation, including styling, images, custom fonts, page headers/footers,
and Spring Boot integration lives in [lib/README.md](lib/README.md).

Installation instructions and usage for the desktop application live in
[gui/readme.md](gui/readme.md). If you are building from a source checkout instead, the
repository-root `./install.sh` builds and installs it in one step (macOS / Linux) — see
the [gui readme](gui/readme.md#one-command-build--install-macos--linux) for details.

### Linux prerequisites

The bundled runtime supplies Java, but not the system libraries JavaFX draws with.
GTK 3 and the WebKit dependencies must be present:

    Debian/Ubuntu: sudo apt-get install libgtk-3-0 libxtst6
    Fedora/RHEL:   sudo dnf install gtk3 libXtst
    Arch:          sudo pacman -S gtk3 libxtst

The installer checks for these and reports what is missing, but cannot install them.

### Downloads

| Download                             | Install                                                                    | Size    |
|--------------------------------------|----------------------------------------------------------------------------|---------|
| `md2pdf-<version>-linux-x64.zip`     | unzip, then `bash md2pdf-install.sh`                                       | ~100 MB |
| `md2pdf-<version>-macos-aarch64.zip` | unzip, then `zsh md2pdf-install.zsh`                                       | ~100 MB |
| `md2pdf-<version>-windows-x64.zip`   | unzip, then double-click `md2pdf-install.cmd`                              | ~100 MB |
| `md2pdf-<version>-no-jdk.zip`        | unzip, then `java --enable-native-access=javafx.graphics,javafx.web,javafx.media -jar MarkdownToPdf.jar` (needs a JavaFX-bundled JDK 25+) | ~15 MB  |

Verify a download against `SHA256SUMS` from the same release:

    Linux:   sha256sum -c SHA256SUMS
    macOS:   shasum -a 256 -c SHA256SUMS
    Windows: certutil -hashfile <file> SHA256   (compare the line by eye)

## Building from source

```bash
# Full build: compile, test, spotless format check, SpotBugs
mvn verify

# Build and install to local Maven repo
mvn install

# Build a platform archive (linux | macos | windows | no-jdk)
mvn install -DskipTests && ./gui/createApp.sh linux

# Auto-format all code (Google Java Format)
mvn spotless:apply
```

## License

MIT — see [LICENSE](LICENSE).
