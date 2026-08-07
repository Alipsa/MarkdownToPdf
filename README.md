# MarkdownToPdf — Markdown to PDF toolset
[![Maven Central](https://img.shields.io/maven-central/v/se.alipsa/md2pdf)](https://central.sonatype.com/artifact/se.alipsa/md2pdf)
[![javadoc](https://javadoc.io/badge2/se.alipsa/md2pdf/0.1.0/javadoc.svg)](https://javadoc.io/doc/se.alipsa/md2pdf)

MarkdownToPdf is a Java-based toolkit for converting Markdown to PDF. It consists of
two modules:

| Module | Artifact | Description |
|--------|----------|-------------|
| **[lib](lib/)** | `se.alipsa:md2pdf` | Core library — turn any Markdown string, file or stream into a PDF byte-array |
| **[gui](gui/)**  | standalone desktop app | JavaFX editor with live preview, visual style editor and project management |

Both modules require **JDK 21 or later**. The GUI additionally requires a JDK that
bundles JavaFX (e.g. [Liberica Full](https://bell-sw.com/pages/downloads/) or
[Azul Zulu FX](https://www.azul.com/downloads/)).

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
[gui/readme.md](gui/readme.md).

## Building from source

```bash
# Full build: compile, test, spotless format check, SpotBugs
mvn verify

# Build and install to local Maven repo
mvn install

# Build the GUI standalone fat-jar
mvn install && mvn package -P fatjar -pl gui

# Auto-format all code (Google Java Format)
mvn spotless:apply
```

## License

MIT — see [LICENSE](LICENSE).
