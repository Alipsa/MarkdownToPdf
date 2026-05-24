package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfEngine;
import se.alipsa.md2pdf.Md2PdfException;

public class ExternalCssTest {

  @Test
  public void testExternalCss() throws Md2PdfException, URISyntaxException, IOException {
    Md2PdfEngine engine = new Md2PdfEngine();

    File externalCssPath = new File(this.getClass().getResource("/style/mystyle.css").toURI());
    String markdown =
        """
        # Welcome Per
        Our latest product: [Greeting](https://some.url.se/)
        """;

    String css = Files.readString(externalCssPath.toPath());
    String html = engine.markdown(markdown).css(css).toHtml();
    assertTrue(html.contains("<h1>Welcome Per</h1>"));

    Path pdfFilePath = Paths.get("target/externalCss.pdf");
    engine.markdown(markdown).css(css).toPdf(pdfFilePath);
    assertTrue(pdfFilePath.toFile().exists());
  }
}
