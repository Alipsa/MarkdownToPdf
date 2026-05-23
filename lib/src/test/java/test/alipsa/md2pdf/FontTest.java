package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfException;
import se.alipsa.md2pdf.Md2PdfEngine;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FontTest {

  @Test
  public void testLoadFont() throws Md2PdfException, IOException {
    Md2PdfEngine engine = new Md2PdfEngine();
    URL urlJersey = getClass().getResource("/fonts/Jersey25-Regular.ttf");
    URL urlJacquard = getClass().getResource("/fonts/Jacquard24-Regular.ttf");

    String md = """
      # Font Test
      Some text with custom fonts.
      
      <div style="font-family: Jersey25;">
        ### JERSEY FONT
        Alice was beginning to get very tired of
        sitting by her sister on the bank, and of having nothing to do: once or
        twice she had peeped into the book her sister was reading, but it had
        no pictures or conversations in it, `and what is the use of a book,'
        thought Alice `without pictures or conversation?'
      </div>
      <div style="font-family: Jacquard24;">
          ### JACQUARD FONT
          Alice was beginning to get very tired of
          sitting by her sister on the bank, and of having nothing to do: once or
          twice she had peeped into the book her sister was reading, but it had
          no pictures or conversations in it, `and what is the use of a book,'
          thought Alice `without pictures or conversation?'
      </div>
      """;

    var job = engine.markdown(md)
        .font(urlJersey, "Jersey25")
        .font(urlJacquard, "Jacquard24");

    String html = job.toHtml();
    assertNotNull(html);

    byte[] pdf = job.toPdf();
    assertNotNull(pdf);

    File file = new File("target/testLoadFont.pdf");
    job.toPdf(file);
    System.out.println("Wrote " + file.getAbsolutePath());

    // Make sure the fonts are embedded in the PDF
    Map<String, FontInfo> fonts = new HashMap<>();
    try(PDDocument pdDocument = Loader.loadPDF(file)) {
      var page = pdDocument.getPage(0);
      PDResources pageResources = page.getResources();
      for (var fontName : page.getResources().getFontNames()) {
        PDFont font = pageResources.getFont(fontName);
        //System.out.println(font.getName() + ", embedded: " + font.isEmbedded());
        if (font.getName().contains("Jersey25")) {
          fonts.put("Jersey25", new FontInfo(font.getName(), font.isEmbedded()));
        } else if (font.getName().contains("Jacquard24")) {
          fonts.put("Jacquard24", new FontInfo(font.getName(), font.isEmbedded()));
        }
      }
    }
    assertTrue(fonts.containsKey("Jersey25"), "Jersey25 Font is missing");
    assertTrue(fonts.get("Jersey25").embedded, "Jersey25 font is not embedded");
    assertTrue(fonts.containsKey("Jacquard24"), "Jacquard24 Font is missing");
    assertTrue(fonts.get("Jacquard24").embedded, "Jacquard24 font is not embedded");
  }

  class FontInfo {
    public String name;
    public Boolean embedded;

    public FontInfo(String name, Boolean isEmbedded) {
      this.name = name;
      this.embedded = isEmbedded;
    }
  }
}
