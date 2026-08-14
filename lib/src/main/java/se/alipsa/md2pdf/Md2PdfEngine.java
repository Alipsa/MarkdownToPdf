package se.alipsa.md2pdf;

import com.openhtmltopdf.extend.SVGDrawer;
import com.openhtmltopdf.mathmlsupport.MathMLDrawer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.openhtmltopdf.util.XRLog;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the core class of the MarkdownToPdf library, used to create html or pdf output.
 *
 * <p>Usage example:
 *
 * <pre>
 *   byte[] pdf = new Md2PdfEngine()
 *       .markdown("# Hello\n\nWorld")
 *       .css("body { font-family: sans-serif; }")
 *       .toPdf();
 * </pre>
 */
public class Md2PdfEngine {

  private static final Logger log = LoggerFactory.getLogger(Md2PdfEngine.class);

  private static final String DEFAULT_CSS =
      """
      body {
        line-height: 1.6;
        color: #333;
        max-width: 800px;
        margin: 0 auto;
        padding: 1em;
      }
      h1, h2, h3, h4, h5, h6 {
        margin-top: 1.5em;
        margin-bottom: 0.5em;
        font-weight: 600;
        line-height: 1.25;
      }
      h1 { font-size: 2em; border-bottom: 1px solid #eaecef; padding-bottom: 0.3em; }
      h2 { font-size: 1.5em; border-bottom: 1px solid #eaecef; padding-bottom: 0.3em; }
      a { color: #0366d6; text-decoration: none; }
      a:hover { text-decoration: underline; }
      code {
        background-color: #f6f8fa;
        border-radius: 3px;
        font-size: 85%;
        margin: 0;
        padding: 0.2em 0.4em;
      }
      pre {
        background-color: #f6f8fa;
        border-radius: 6px;
        font-size: 85%;
        line-height: 1.45;
        padding: 16px;
        white-space: pre-wrap;
        word-wrap: break-word;
      }
      pre code {
        background-color: transparent;
        border: 0;
        display: inline;
        line-height: inherit;
        margin: 0;
        overflow: visible;
        padding: 0;
        word-wrap: normal;
      }
      blockquote {
        border-left: 0.25em solid #dfe2e5;
        color: #6a737d;
        margin: 0;
        padding: 0 1em;
      }
      table {
        border-collapse: collapse;
        border-spacing: 0;
        display: block;
        width: 100%;
      }
      table th, table td {
        border: 1px solid #dfe2e5;
        padding: 6px 13px;
      }
      table tr:nth-child(2n) { background-color: #f6f8fa; }
      img { max-width: 100%; box-sizing: content-box; }
      """;

  private static final String PAGE_HEADER_FOOTER_CSS =
      """
      @page {
        @top-center { content: element(md2pdf-page-header) }
        @bottom-center { content: element(md2pdf-page-footer) }
      }
      .md2pdf-page-header {
        display: block;
        position: running(md2pdf-page-header);
        font-size: 9px;
        font-style: italic;
        text-align: right;
      }
      .md2pdf-page-footer {
        display: block;
        position: running(md2pdf-page-footer);
        font-size: 9px;
        font-style: italic;
        text-align: right;
      }
      .page-number:before {
        content: counter(page);
      }
      .total-pages:before {
        content: counter(pages);
      }
      """;

  private final Parser markdownParser;
  private final HtmlRenderer htmlRenderer;

  /** Creates a new engine with default settings (GFM tables enabled). */
  public Md2PdfEngine() {
    this(new Builder());
  }

  private Md2PdfEngine(Builder builder) {
    var parserBuilder = Parser.builder();
    var rendererBuilder = HtmlRenderer.builder().softbreak(builder.softbreak);
    if (builder.tables) {
      var tablesExtension = TablesExtension.create();
      parserBuilder.extensions(List.of(tablesExtension));
      rendererBuilder.extensions(List.of(tablesExtension));
    }
    markdownParser = parserBuilder.build();
    htmlRenderer = rendererBuilder.build();
  }

  /**
   * Create a builder for configuring Markdown parsing and HTML rendering.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Configure OpenHTMLtoPDF to route its logging through SLF4J.
   *
   * <p>OpenHTMLtoPDF logging is JVM-global, so applications should call this deliberately during
   * their startup rather than having an engine instance replace an existing logger unexpectedly.
   */
  public static void configureOpenHtmlToPdfLogging() {
    XRLog.setLoggerImpl(new Slf4jXRLogger());
  }

  /** Builder for configuring Markdown parsing and HTML rendering options. */
  public static class Builder {

    /** Creates a Builder with default settings (tables enabled). */
    public Builder() {}

    private boolean tables = true;
    private String softbreak = "<br />\n";

    /**
     * Enable or disable GitHub-flavored Markdown table support.
     *
     * @param tables true to enable table support
     * @return this builder for chaining
     */
    public Builder tables(boolean tables) {
      this.tables = tables;
      return this;
    }

    /**
     * Configure how soft line breaks are rendered in HTML.
     *
     * @param softbreak the HTML emitted for a soft break
     * @return this builder for chaining
     */
    public Builder softbreak(String softbreak) {
      this.softbreak = Objects.requireNonNull(softbreak, "softbreak");
      return this;
    }

    /**
     * Build the engine.
     *
     * @return a configured engine
     */
    public Md2PdfEngine build() {
      return new Md2PdfEngine(this);
    }
  }

  /**
   * Start a conversion job with the given markdown content.
   *
   * @param markdown the markdown content
   * @return a Renderer that can be configured further and rendered
   */
  public Renderer markdown(String markdown) {
    return new Renderer(markdown, currentDirectoryUri());
  }

  /**
   * Start a conversion job reading markdown from the given file.
   *
   * @param file the file containing markdown
   * @return a Renderer that can be configured further and rendered
   * @throws Md2PdfException if the file cannot be read
   */
  public Renderer markdown(File file) throws Md2PdfException {
    return new Renderer(readString(file), parentDirectoryUri(file.toPath()));
  }

  /**
   * Start a conversion job reading markdown from the given path.
   *
   * @param path the path to the markdown file
   * @return a Renderer that can be configured further and rendered
   * @throws Md2PdfException if the file cannot be read
   */
  public Renderer markdown(Path path) throws Md2PdfException {
    return markdown(path.toFile());
  }

  /**
   * Start a conversion job reading markdown from the given input stream.
   *
   * @param inputStream the input stream containing markdown
   * @return a Renderer that can be configured further and rendered
   * @throws Md2PdfException if the stream cannot be read
   */
  public Renderer markdown(InputStream inputStream) throws Md2PdfException {
    return new Renderer(readString(inputStream), currentDirectoryUri());
  }

  private static String currentDirectoryUri() {
    return Paths.get(".").toAbsolutePath().normalize().toUri().toString();
  }

  private static String parentDirectoryUri(Path path) {
    Path parent = path.toAbsolutePath().normalize().getParent();
    return parent != null ? parent.toUri().toString() : currentDirectoryUri();
  }

  private static String readString(File file) throws Md2PdfException {
    try {
      return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new Md2PdfException("Failed to read " + file, e);
    }
  }

  private static String readString(InputStream is) throws Md2PdfException {
    try (is;
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
      return sb.toString();
    } catch (IOException e) {
      throw new Md2PdfException("Failed to read from input stream", e);
    }
  }

  private static String readString(URL url) throws Md2PdfException {
    try (InputStream is = url.openStream()) {
      return readString(is);
    } catch (IOException e) {
      throw new Md2PdfException("Failed to read from " + url, e);
    }
  }

  private static byte[] readBytes(URL url) throws Md2PdfException {
    try (InputStream is = url.openStream()) {
      return is.readAllBytes();
    } catch (IOException e) {
      throw new Md2PdfException("Failed to read from " + url, e);
    }
  }

  private static byte[] readBytes(InputStream inputStream) throws Md2PdfException {
    try (inputStream) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      throw new Md2PdfException("Failed to read from input stream", e);
    }
  }

  /** A conversion job configured with markdown and optionally CSS. */
  public class Renderer {

    private final String markdown;
    private String baseUri;
    private String css;
    private String pageHeader;
    private String pageFooter;
    private String pageMarginCss;
    private PdfMetadata metadata;
    private final List<String> additionalCss = new ArrayList<>();
    private final List<FontSpec> fonts = new ArrayList<>();

    private Renderer(String markdown, String baseUri) {
      this.markdown = markdown;
      this.baseUri = baseUri;
    }

    /**
     * Set the base path used to resolve relative resources such as images and CSS URLs.
     *
     * @param basePath the base directory
     * @return this Renderer for chaining
     */
    public Renderer basePath(Path basePath) {
      this.baseUri =
          Objects.requireNonNull(basePath, "basePath")
              .toAbsolutePath()
              .normalize()
              .toUri()
              .toString();
      return this;
    }

    /**
     * Use the given CSS string for styling.
     *
     * @param css the CSS content
     * @return this Renderer for chaining
     */
    public Renderer css(String css) {
      this.css = css;
      return this;
    }

    /**
     * Read CSS from the given file.
     *
     * @param file the CSS file
     * @return this Renderer for chaining
     * @throws Md2PdfException if the file cannot be read
     */
    public Renderer css(File file) throws Md2PdfException {
      this.css = readString(file);
      return this;
    }

    /**
     * Read CSS from the given path.
     *
     * @param path the path to the CSS file
     * @return this Renderer for chaining
     * @throws Md2PdfException if the file cannot be read
     */
    public Renderer css(Path path) throws Md2PdfException {
      return css(path.toFile());
    }

    /**
     * Read CSS from the given URL.
     *
     * @param url the URL pointing to CSS content
     * @return this Renderer for chaining
     * @throws Md2PdfException if the URL cannot be read
     */
    public Renderer css(URL url) throws Md2PdfException {
      this.css = readString(url);
      return this;
    }

    /**
     * Read CSS from the given input stream.
     *
     * @param inputStream the input stream containing CSS
     * @return this Renderer for chaining
     * @throws Md2PdfException if the stream cannot be read
     */
    public Renderer css(InputStream inputStream) throws Md2PdfException {
      this.css = readString(inputStream);
      return this;
    }

    /**
     * Add CSS after the default or replacement stylesheet.
     *
     * @param css the CSS content to append
     * @return this Renderer for chaining
     */
    public Renderer addCss(String css) {
      additionalCss.add(css);
      return this;
    }

    /**
     * Read additional CSS from the given file.
     *
     * @param file the CSS file
     * @return this Renderer for chaining
     * @throws Md2PdfException if the file cannot be read
     */
    public Renderer addCss(File file) throws Md2PdfException {
      return addCss(readString(file));
    }

    /**
     * Read additional CSS from the given path.
     *
     * @param path the path to the CSS file
     * @return this Renderer for chaining
     * @throws Md2PdfException if the file cannot be read
     */
    public Renderer addCss(Path path) throws Md2PdfException {
      return addCss(path.toFile());
    }

    /**
     * Read additional CSS from the given URL.
     *
     * @param url the URL pointing to CSS content
     * @return this Renderer for chaining
     * @throws Md2PdfException if the URL cannot be read
     */
    public Renderer addCss(URL url) throws Md2PdfException {
      return addCss(readString(url));
    }

    /**
     * Read additional CSS from the given input stream.
     *
     * @param inputStream the input stream containing CSS
     * @return this Renderer for chaining
     * @throws Md2PdfException if the stream cannot be read
     */
    public Renderer addCss(InputStream inputStream) throws Md2PdfException {
      return addCss(readString(inputStream));
    }

    /**
     * Register a font file with the PDF renderer.
     *
     * <p>The family name should match the CSS {@code font-family} value that uses the font.
     *
     * @param file the font file
     * @param family the font family name
     * @return this Renderer for chaining
     */
    public Renderer font(File file, String family) {
      fonts.add(FontSpec.of(file, family));
      return this;
    }

    /**
     * Register a font file with the PDF renderer.
     *
     * <p>The family name should match the CSS {@code font-family} value that uses the font.
     *
     * @param path the path to the font file
     * @param family the font family name
     * @return this Renderer for chaining
     */
    public Renderer font(Path path, String family) {
      return font(path.toFile(), family);
    }

    /**
     * Register a font from a URL with the PDF renderer.
     *
     * <p>The family name should match the CSS {@code font-family} value that uses the font.
     *
     * @param url the URL pointing to font content
     * @param family the font family name
     * @return this Renderer for chaining
     * @throws Md2PdfException if the URL cannot be read
     */
    public Renderer font(URL url, String family) throws Md2PdfException {
      fonts.add(FontSpec.of(readBytes(url), family));
      return this;
    }

    /**
     * Register a font from an input stream with the PDF renderer.
     *
     * <p>The family name should match the CSS {@code font-family} value that uses the font.
     *
     * @param inputStream the input stream containing font content
     * @param family the font family name
     * @return this Renderer for chaining
     * @throws Md2PdfException if the stream cannot be read
     */
    public Renderer font(InputStream inputStream, String family) throws Md2PdfException {
      fonts.add(FontSpec.of(readBytes(inputStream), family));
      return this;
    }

    /**
     * Add an HTML fragment as a page header.
     *
     * <p>The header is rendered in the top page margin. Use {@code <span
     * class="page-number"></span>} and {@code <span class="total-pages"></span>} to include page
     * counters.
     *
     * @param html the header HTML fragment
     * @return this Renderer for chaining
     */
    public Renderer pageHeader(String html) {
      this.pageHeader = html;
      return this;
    }

    /**
     * Add an HTML fragment as a page footer.
     *
     * <p>The footer is rendered in the bottom page margin. Use {@code <span
     * class="page-number"></span>} and {@code <span class="total-pages"></span>} to include page
     * counters.
     *
     * @param html the footer HTML fragment
     * @return this Renderer for chaining
     */
    public Renderer pageFooter(String html) {
      this.pageFooter = html;
      return this;
    }

    /**
     * Set page margins using a CSS margin shorthand value.
     *
     * @param margin the CSS margin value, e.g. {@code 1in} or {@code 0.5in 0.75in}
     * @return this Renderer for chaining
     */
    public Renderer pageMargins(String margin) {
      this.pageMarginCss = Objects.requireNonNull(margin, "margin");
      return this;
    }

    /**
     * Set page margins using explicit top, right, bottom, and left values.
     *
     * @param top the top margin
     * @param right the right margin
     * @param bottom the bottom margin
     * @param left the left margin
     * @return this Renderer for chaining
     */
    public Renderer pageMargins(String top, String right, String bottom, String left) {
      return pageMargins(String.join(" ", top, right, bottom, left));
    }

    /**
     * Set the PDF document title.
     *
     * @param title the PDF title
     * @return this Renderer for chaining
     */
    public Renderer title(String title) {
      metadata().title = title;
      return this;
    }

    /**
     * Set the PDF document author.
     *
     * @param author the PDF author
     * @return this Renderer for chaining
     */
    public Renderer author(String author) {
      metadata().author = author;
      return this;
    }

    /**
     * Set the PDF document subject.
     *
     * @param subject the PDF subject
     * @return this Renderer for chaining
     */
    public Renderer subject(String subject) {
      metadata().subject = subject;
      return this;
    }

    /**
     * Set the PDF producer.
     *
     * @param producer the PDF producer
     * @return this Renderer for chaining
     */
    public Renderer producer(String producer) {
      metadata().producer = producer;
      return this;
    }

    private PdfMetadata metadata() {
      if (metadata == null) {
        metadata = new PdfMetadata();
      }
      return metadata;
    }

    private String buildHtml() {
      org.commonmark.node.Node document = markdownParser.parse(markdown);
      String bodyHtml = htmlRenderer.render(document);
      String effectiveCss = buildCss();
      String headerHtml =
          pageHeader != null ? "<div class=\"md2pdf-page-header\">" + pageHeader + "</div>\n" : "";
      String footerHtml =
          pageFooter != null ? "<div class=\"md2pdf-page-footer\">" + pageFooter + "</div>\n" : "";
      return """
          <!DOCTYPE html>
          <html>
          <head>
          <meta charset="UTF-8">
          <style>
          %s
          </style>
          </head>
          <body>
          %s%s
          %s
          </body>
          </html>
          """
          .formatted(effectiveCss, headerHtml, footerHtml, bodyHtml);
    }

    private String buildCss() {
      StringBuilder sb = new StringBuilder(css != null ? css : DEFAULT_CSS);
      if (pageHeader != null || pageFooter != null || pageMarginCss != null) {
        sb.append('\n').append(buildPageCss());
      }
      for (String cssToAdd : additionalCss) {
        sb.append('\n').append(cssToAdd);
      }
      return sb.toString();
    }

    private String buildPageCss() {
      StringBuilder sb = new StringBuilder();
      if (pageMarginCss != null) {
        sb.append("@page { margin: ").append(pageMarginCss).append("; }\n");
      } else if (pageHeader != null || pageFooter != null) {
        sb.append("@page { margin: 0.75in; }\n");
      }
      if (pageHeader != null || pageFooter != null) {
        sb.append(PAGE_HEADER_FOOTER_CSS);
      }
      return sb.toString();
    }

    // --- PDF output ---

    /**
     * Render the job to a PDF byte array.
     *
     * @return the PDF content as a byte array
     * @throws Md2PdfException if rendering fails
     */
    public byte[] toPdf() throws Md2PdfException {
      String html = buildHtml();
      String xhtml = htmlToXhtml(html);
      return xhtmlToPdf(xhtml, baseUri, fonts, metadata);
    }

    /**
     * Render the job to a PDF file.
     *
     * <p>The document is rendered completely before the destination is opened. This preserves an
     * existing file if rendering fails, and writes through symbolic and hard links without
     * replacing their directory entries. As with any direct file write, a failure while writing the
     * completed PDF can leave the destination partially written.
     *
     * <p>File output retains the completed PDF in memory. Use {@link #toPdf(OutputStream)} when
     * avoiding that final byte array is important.
     *
     * @param file the file to write the PDF to
     * @throws Md2PdfException if rendering or writing fails
     */
    public void toPdf(File file) throws Md2PdfException {
      Path target = Objects.requireNonNull(file, "file").toPath().toAbsolutePath();
      Path parent = target.getParent();
      if (parent == null) {
        throw new Md2PdfException("Cannot write a PDF to filesystem root " + target);
      }
      byte[] pdf = toPdf();
      try {
        Files.write(target, pdf);
        log.debug("toPdf: Wrote {}", target);
      } catch (IOException e) {
        throw new Md2PdfException(e);
      }
    }

    /**
     * Render the job to a PDF at the given path.
     *
     * @param path the path to write the PDF to
     * @throws Md2PdfException if rendering or writing fails
     */
    public void toPdf(Path path) throws Md2PdfException {
      toPdf(path.toFile());
    }

    /**
     * Render the job to a PDF at the given path.
     *
     * @param path the path (in String format) to write the PDF to
     * @throws Md2PdfException if rendering or writing fails
     */
    public void toPdf(String path) throws Md2PdfException {
      toPdf(Paths.get(path));
    }

    /**
     * Render the job to a PDF written to the given output stream.
     *
     * @param os the output stream to write the PDF to
     * @throws Md2PdfException if rendering or writing fails
     */
    public void toPdf(OutputStream os) throws Md2PdfException {
      try {
        BufferedOutputStream bos = new BufferedOutputStream(os);
        String html = buildHtml();
        String xhtml = htmlToXhtml(html);
        xhtmlToPdf(xhtml, bos, baseUri, fonts, metadata);
        bos.flush();
      } catch (IOException e) {
        throw new Md2PdfException(e);
      }
    }

    // --- HTML output ---

    /**
     * Render the job to an HTML string.
     *
     * @return the HTML content
     */
    public String toHtml() {
      return buildHtml();
    }

    /**
     * Render the job to an HTML file.
     *
     * @param file the file to write the HTML to
     * @throws Md2PdfException if writing fails
     */
    public void toHtml(File file) throws Md2PdfException {
      try {
        Files.writeString(file.toPath(), buildHtml(), StandardCharsets.UTF_8);
        log.debug("toHtml: Wrote {}", file.getAbsolutePath());
      } catch (IOException e) {
        throw new Md2PdfException(e);
      }
    }

    /**
     * Render the job to an HTML file at the given path.
     *
     * @param path the path to write the HTML to
     * @throws Md2PdfException if writing fails
     */
    public void toHtml(Path path) throws Md2PdfException {
      toHtml(path.toFile());
    }

    /**
     * Render the job to an HTML file at the given path.
     *
     * @param path the path to write the HTML to
     * @throws Md2PdfException if writing fails
     */
    public void toHtml(String path) throws Md2PdfException {
      toHtml(Paths.get(path));
    }

    /**
     * Render the job to an HTML written to the given output stream.
     *
     * @param os the output stream to write the HTML to
     * @throws Md2PdfException if writing fails
     */
    public void toHtml(OutputStream os) throws Md2PdfException {
      try {
        os.write(buildHtml().getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new Md2PdfException(e);
      }
    }
  }

  // Internal PDF rendering

  private BatikSVGDrawer svgDrawer;
  private MathMLDrawer mathMLDrawer;

  private synchronized byte[] xhtmlToPdf(
      String xhtml, String baseUri, List<FontSpec> fonts, PdfMetadata metadata)
      throws Md2PdfException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      xhtmlToPdf(xhtml, baos, baseUri, fonts, metadata);
      return baos.toByteArray();
    } catch (IOException e) {
      throw new Md2PdfException(e);
    }
  }

  private synchronized void xhtmlToPdf(
      String xhtml, OutputStream os, String baseUri, List<FontSpec> fonts, PdfMetadata metadata)
      throws Md2PdfException {
    try {
      var jsDoc = Jsoup.parse(xhtml);
      org.w3c.dom.Document doc = new W3CDom().fromJsoup(jsDoc);
      if (metadata != null) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
          renderXhtmlToPdf(doc, baos, baseUri, fonts);
          try (PDDocument pdDocument = Loader.loadPDF(baos.toByteArray())) {
            applyMetadata(pdDocument, metadata);
            pdDocument.save(os);
          }
        }
        return;
      }
      renderXhtmlToPdf(doc, os, baseUri, fonts);
    } catch (IOException e) {
      throw new Md2PdfException(e);
    }
  }

  private void renderXhtmlToPdf(
      org.w3c.dom.Document doc, OutputStream os, String baseUri, List<FontSpec> fonts)
      throws IOException {
    PdfRendererBuilder builder =
        new PdfRendererBuilder()
            .withW3cDocument(doc, baseUri)
            .useSVGDrawer(getSvgDrawer())
            .useMathMLDrawer(getMathMLDrawer())
            .toStream(os);
    for (FontSpec font : fonts) {
      font.apply(builder);
    }
    builder.run();
  }

  private static void applyMetadata(PDDocument document, PdfMetadata metadata) {
    PDDocumentInformation information = document.getDocumentInformation();
    if (metadata.title != null) {
      information.setTitle(metadata.title);
    }
    if (metadata.author != null) {
      information.setAuthor(metadata.author);
    }
    if (metadata.subject != null) {
      information.setSubject(metadata.subject);
    }
    if (metadata.producer != null) {
      information.setProducer(metadata.producer);
    }
  }

  private SVGDrawer getMathMLDrawer() {
    if (mathMLDrawer == null) {
      mathMLDrawer = new MathMLDrawer();
    }
    return mathMLDrawer;
  }

  private SVGDrawer getSvgDrawer() {
    if (svgDrawer == null) {
      svgDrawer = new BatikSVGDrawer();
    }
    return svgDrawer;
  }

  private static String htmlToXhtml(String html) {
    Document document = Jsoup.parse(html);
    document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
    return document.html();
  }

  private static class PdfMetadata {
    private String title;
    private String author;
    private String subject;
    private String producer;
  }

  private record FontSpec(File file, byte[] data, String family) {

    private static FontSpec of(File file, String family) {
      return new FontSpec(
          Objects.requireNonNull(file, "file"), null, Objects.requireNonNull(family, "family"));
    }

    private static FontSpec of(byte[] data, String family) {
      return new FontSpec(
          null, Objects.requireNonNull(data, "data"), Objects.requireNonNull(family, "family"));
    }

    private void apply(PdfRendererBuilder builder) {
      if (file != null) {
        builder.useFont(file, family);
      } else {
        builder.useFont(() -> new ByteArrayInputStream(data), family);
      }
    }
  }
}
