# MarkdownToPdf — a Markdown to PDF library
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.alipsa/md2pdf/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.alipsa/md2pdf)
[![javadoc](https://javadoc.io/badge2/se.alipsa/md2pdf/javadoc.svg)](https://javadoc.io/doc/se.alipsa/md2pdf)

MarkdownToPdf is a Java library that converts Markdown to PDF.

Internally it uses [commonmark-java](https://github.com/commonmark/commonmark-java)
to render Markdown to HTML, [jsoup](https://jsoup.org/) to produce well-formed XHTML,
and [OpenHTMLtoPDF](https://github.com/openhtmltopdf/openhtmltopdf) to produce the PDF.
SVG support is provided by [Batik](https://xmlgraphics.apache.org/batik/).

Requires **JDK 21 or later**.

## Quick start

```java
import se.alipsa.md2pdf.Md2PdfEngine;

byte[] pdf = new Md2PdfEngine()
    .markdown("# Hello\n\nWorld!")
    .toPdf();

// Or write directly to a file:
new Md2PdfEngine()
    .markdown(Path.of("report.md"))
    .toPdf(Path.of("report.pdf"));
```

## Maven dependency

```xml
<dependency>
    <groupId>se.alipsa</groupId>
    <artifactId>md2pdf</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Builder configuration

The engine can be configured via a builder before use:

```java
Md2PdfEngine engine = Md2PdfEngine.builder()
    .tables(true)           // enable GFM table support (default: true)
    .softbreak("<br />\n")  // how soft line breaks are rendered (default: <br />\n)
    .build();
```

## Reading Markdown

Markdown can be provided as a `String`, `File`, `Path`, or `InputStream`:

```java
// From a string
byte[] pdf = engine.markdown("# Report\n\nSome content").toPdf();

// From a file (relative image/CSS paths resolve from the file's directory)
byte[] pdf = engine.markdown(new File("reports/report.md")).toPdf();

// From an input stream
try (InputStream is = MyClass.class.getResourceAsStream("/report.md")) {
    byte[] pdf = engine.markdown(is).toPdf();
}
```

## Output

Terminal methods on the Renderer produce PDF or HTML:

```java
Renderer job = engine.markdown("# Hello");

byte[]      pdf  = job.toPdf();               // byte array
            job.toPdf(Path.of("out.pdf"));     // file
            job.toPdf(outputStream);           // stream

String      html = job.toHtml();              // string
            job.toHtml(Path.of("out.html"));   // file
```

## Styling

Use `css(...)` to **replace** the default stylesheet entirely:

```java
byte[] pdf = engine.markdown("# Report")
    .css("body { font-family: serif; font-size: 12pt; }")
    .toPdf();
```

Use `addCss(...)` to **extend** the default stylesheet with overrides:

```java
byte[] pdf = engine.markdown("# Report")
    .addCss("h1 { color: #0057b8; }")
    .toPdf();
```

Both `css(...)` and `addCss(...)` also accept `File`, `Path`, `URL`, and `InputStream`:

```java
byte[] pdf = engine.markdown("# Report")
    .addCss(Path.of("style/overrides.css"))
    .toPdf();
```

## Images

### Relative images

When reading Markdown from a `File` or `Path`, relative image references are resolved
automatically from the Markdown file's directory:

```java
// logo.png is read from reports/
byte[] pdf = engine.markdown(Path.of("reports/report.md")).toPdf();
```

When using a Markdown string, set the base path explicitly:

```java
byte[] pdf = engine.markdown("# Report\n\n![Logo](logo.png)")
    .basePath(Path.of("reports"))
    .toPdf();
```

### SVG images

SVG is supported via Batik. You can embed SVG directly as a raw HTML block inside
your Markdown file (Markdown passes through raw HTML unchanged):

```markdown
## My chart

<div style="width:400px;height:300px">
  <svg xmlns="http://www.w3.org/2000/svg">
    <circle cx="150" cy="65" r="60" stroke="black" stroke-width="3" fill="red"/>
  </svg>
</div>
```

The SVG block must be associated with a block-level element (a `<div>`) with explicit
dimensions so that OpenHTMLtoPDF can allocate space for the Batik-rendered image.

## Page structure

A simple page header and footer can be added via the fluent API:

```java
byte[] pdf = engine.markdown("# Alice's Adventures in Wonderland\n\nDown the Rabbit-Hole")
    .pageHeader("Alice's Adventures in Wonderland")
    .pageFooter("Page <span class=\"page-number\"></span> of <span class=\"total-pages\"></span>")
    .pageMargins("0.75in")
    .toPdf();
```

When a header or footer is present and `pageMargins(...)` is omitted, md2pdf defaults
to `0.75in`.

For more control, define running elements in your CSS and reference them from `@page`:

```java
String css = """
    div.header {
        display: block;
        position: running(header);
        font-size: 9px;
        text-align: right;
    }
    div.footer {
        display: block;
        position: running(footer);
        font-size: 9px;
    }
    @page {
        @top-center   { content: element(header) }
        @bottom-right { content: element(footer) }
    }
    #pagenumber:before { content: counter(page); }
    #pagecount:before  { content: counter(pages); }
    """;
```

Then include the header and footer divs as raw HTML in your Markdown:

```markdown
<div class="header">Quarterly Report 2024</div>
<div class="footer">Page <span id="pagenumber"/> of <span id="pagecount"/></div>

# Chapter 1

Content here…
```

## PDF metadata

```java
byte[] pdf = engine.markdown("# Quarterly Report")
    .title("Quarterly Report")
    .author("Alipsa")
    .subject("Sales")
    .producer("Md2Pdf")
    .toPdf();
```

## Custom fonts

Register TTF font files and reference the family from CSS:

```java
byte[] pdf = engine.markdown("# Font Test\n\nCustom font text")
    .css("body { font-family: \"Jersey 25\"; }")
    .font(new File("fonts/Jersey25-Regular.ttf"), "Jersey 25")
    .toPdf();
```

`font(...)` also accepts `Path`, `URL`, and `InputStream`.

### Google Fonts

Google Fonts typically distribute `woff2` files, which OpenHTMLtoPDF does not support.
Use the TTF variant instead. You can find TTF URLs via the
[Google Fonts TTF list](https://gist.githubusercontent.com/karimnaaji/b6c9c9e819204113e9cabf290d580551/raw/ed71595a691320ba63e48335c7c77818336cb1c2/GoogleFonts.txt).

```java
byte[] pdf = engine.markdown("# Sofia font example\n\nHello world")
    .addCss("""
        @font-face {
            font-family: "Sofia";
            src: url(http://fonts.gstatic.com/s/sofia/v5/Imnvx0Ag9r6iDBFUY5_RaQ.ttf);
        }
        body { font-family: "Sofia"; }
        """)
    .toPdf();
```

## MarkdownToPdf GUI

A desktop application for interactive Markdown editing and PDF generation is available
in the [gui module](gui/readme.md).

## License

MIT — see [LICENSE](LICENSE).

Note that this library depends on OpenHTMLtoPDF (LGPL v2.1+) and Batik (Apache 2.0).
See the third-party section below for full details.

## Third-party libraries

| Library | Purpose | License |
|---------|---------|---------|
| [commonmark-java](https://github.com/commonmark/commonmark-java) | Markdown → HTML | BSD 2-Clause |
| [OpenHTMLtoPDF](https://github.com/openhtmltopdf/openhtmltopdf) | HTML/XHTML → PDF | LGPL 2.1+ |
| [jsoup](https://jsoup.org/) | HTML → well-formed XHTML | MIT |
| [Batik](https://xmlgraphics.apache.org/batik/) | SVG rendering | Apache 2.0 |
| [SLF4J](https://www.slf4j.org/) | Logging facade | MIT |

### Test dependencies

| Library | Purpose | License |
|---------|---------|---------|
| [JUnit Jupiter](https://junit.org/junit5/) | Test assertions | EPL 1.0 |
