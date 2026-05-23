package test.alipsa.md2pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.ImageUtil;
import se.alipsa.md2pdf.Md2PdfException;
import se.alipsa.md2pdf.Md2PdfEngine;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeaderFooterImageTest {

  @Test
  public void testHeaderFooterImage() throws Exception {
    Md2PdfEngine engine = new Md2PdfEngine();
    String alice2 = ImageUtil.asDataUrl("/images/alice2.png");

    String css = """
        @page {
          size: 4.18in 6.88in;
          margin: 0.25in;
          border: thin solid black;
          padding: 1em;
        }

        div.content { page-break-after: always; }
        div.lastpage { page-break-after: avoid; }

        .dropcap {
          float: left;
          line-height: 80%;
          width: .7em;
          font-size: 400%;
        }

        .figure {
          text-align: center;
        }
        """;

    String md = """
        <div class="content" id="page1">

        # CHAPTER I

        ## Down the Rabbit-Hole

        <p>
        <span class="dropcap">A</span>lice was beginning to get very tired of
        sitting by her sister on the bank, and of having nothing to do: once or
        twice she had peeped into the book her sister was reading, but it had
        no pictures or conversations in it, `and what is the use of a book,'
        thought Alice `without pictures or conversation?'
        </p>

        So she was considering in her own mind (as well as she could,
        for the hot day made her feel very sleepy and stupid), whether the
        pleasure of making a daisy-chain would be worth the trouble of
        getting up and picking the daisies, when suddenly a White Rabbit
        with pink eyes ran close by her.

        </div>
        <div class="lastpage" id="page2">
        <p class="figure">
          <img src="%s" width="200px" height="300px" alt="" />
          <br />
          <b>White Rabbit checking watch</b>
        </p>
        </div>
        """.formatted(alice2);

    var job = engine.markdown(md)
        .css(css)
        .pageHeader("Alice's Adventures in Wonderland")
        .pageFooter("Page <span class=\"page-number\"></span> of <span class=\"total-pages\"></span>");

    String html = job.toHtml();
    assertTrue(html.contains("Alice"));
    assertTrue(html.contains("class=\"dropcap\""));
    assertTrue(html.contains("White Rabbit checking watch"));
    assertTrue(html.contains("class=\"page-number\""));

    Path path = Paths.get("target/headerFooter.pdf");
    job.toPdf(path);
    File file = path.toFile();
    assertTrue(file.exists());
    assertTrue(file.length() > 0, "PDF file should not be empty");

    try (PDDocument document = Loader.loadPDF(file)) {
      String pdfText = new PDFTextStripper().getText(document);
      assertTrue(pdfText.contains("Alice's Adventures in Wonderland"));
      assertTrue(pdfText.contains("Down the Rabbit"));
      assertTrue(pdfText.contains("White Rabbit checking watch"));
      assertTrue(containsImage(document), "PDF should contain the Alice image");
    }
  }

  private boolean containsImage(PDDocument document) throws Exception {
    for (PDPage page : document.getPages()) {
      PDResources resources = page.getResources();
      for (var name : resources.getXObjectNames()) {
        if (resources.getXObject(name) instanceof PDImageXObject) {
          return true;
        }
      }
    }
    return false;
  }
}
