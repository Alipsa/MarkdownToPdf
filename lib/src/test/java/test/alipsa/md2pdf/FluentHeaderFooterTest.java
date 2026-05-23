package test.alipsa.md2pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfEngine;

import java.io.File;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FluentHeaderFooterTest {

  @Test
  void testFluentPageHeaderAndFooterInHtml() {
    String html = new Md2PdfEngine()
        .markdown("# Report\n\nBody text")
        .pageHeader("Quarterly Report")
        .pageFooter("Page <span class=\"page-number\"></span> of <span class=\"total-pages\"></span>")
        .toHtml();

    assertTrue(html.contains("position: running(md2pdf-page-header)"));
    assertTrue(html.contains("@page { margin: 0.75in; }"));
    assertTrue(html.contains("Quarterly Report"));
    assertTrue(html.contains("page-number"));
    assertTrue(html.contains("total-pages"));
  }

  @Test
  void testFluentPageMarginsOverrideDefaultHeaderFooterMargin() {
    String html = new Md2PdfEngine()
        .markdown("# Report\n\nBody text")
        .pageHeader("Quarterly Report")
        .pageFooter("Page <span class=\"page-number\"></span> of <span class=\"total-pages\"></span>")
        .pageMargins("1in")
        .toHtml();

    assertTrue(html.contains("@page { margin: 1in; }"));
    assertTrue(html.contains("position: running(md2pdf-page-header)"));
  }

  @Test
  void testFluentPageMarginsWithExplicitSides() {
    String html = new Md2PdfEngine()
        .markdown("# Report\n\nBody text")
        .pageMargins("0.5in", "0.75in", "1in", "0.75in")
        .toHtml();

    assertTrue(html.contains("@page { margin: 0.5in 0.75in 1in 0.75in; }"));
  }

  @Test
  void testFluentPageHeaderAndFooterInPdf() throws Exception {
    File pdf = Paths.get("target/testFluentPageHeaderAndFooterInPdf.pdf").toFile();
    new Md2PdfEngine()
        .markdown("# Report\n\nBody text")
        .pageHeader("Quarterly Report")
        .pageFooter("Page <span class=\"page-number\"></span> of <span class=\"total-pages\"></span>")
        .toPdf(pdf);

    assertTrue(pdf.exists(), "PDF file was not created");
    assertTrue(pdf.length() > 0, "PDF file should not be empty");

    String text;
    try (PDDocument document = Loader.loadPDF(pdf)) {
      text = new PDFTextStripper().getText(document);
    }
    assertTrue(text.contains("Quarterly Report"), "PDF should contain the page header");
    String normalizedText = text.replaceAll("\\s+", " ");
    assertTrue(normalizedText.contains("Page"), "PDF should contain the page footer text");
    assertTrue(normalizedText.contains("1"), "PDF should contain page counter output");
  }
}
