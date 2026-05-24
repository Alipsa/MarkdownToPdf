package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfEngine;

public class FluentFontTest {

  @Test
  void testFluentFontRegistrationEmbedsFont() throws Exception {
    Md2PdfEngine engine = new Md2PdfEngine();
    URL fontUrl = getClass().getResource("/fonts/Jersey25-Regular.ttf");
    assertNotNull(fontUrl, "Test font is missing");

    File pdf = Paths.get("target/testFluentFontRegistrationEmbedsFont.pdf").toFile();
    engine
        .markdown("Custom font text")
        .css("body { font-family: \"Jersey 25\"; }")
        .font(new File(fontUrl.toURI()), "Jersey 25")
        .toPdf(pdf);

    assertTrue(pdf.exists(), "PDF file was not created");
    assertTrue(pdf.length() > 0, "PDF file should not be empty");
    assertTrue(containsEmbeddedFont(pdf, "Jersey25"), "Jersey 25 font should be embedded");
  }

  private boolean containsEmbeddedFont(File pdf, String fontNamePart) throws Exception {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      for (var page : document.getPages()) {
        PDResources resources = page.getResources();
        for (var fontName : resources.getFontNames()) {
          PDFont font = resources.getFont(fontName);
          if (font.getName().contains(fontNamePart) && font.isEmbedded()) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
