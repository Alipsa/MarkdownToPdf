package test.alipsa.md2pdf;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfException;
import se.alipsa.md2pdf.Md2PdfEngine;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SvgImageTest {

  @Test
  public void testSVGIMage() throws Md2PdfException {
    Md2PdfEngine engine = new Md2PdfEngine();

    String md = """
      # A big SVG circle
      <svg xmlns="http://www.w3.org/2000/svg">
        <circle cx="150" cy="90" r="80" stroke="black" stroke-width="3" fill="blue" />
      </svg>
      """;

    String html = engine.markdown(md).toHtml();
    //System.out.println(html);
    assertTrue(html.contains("<h1>A big SVG circle</h1>"));
    assertTrue(html.contains("<circle cx=\"150\" cy=\"90\" r=\"80\" stroke=\"black\" stroke-width=\"3\""));

    Path path = Paths.get("target/svgImage.pdf");
    engine.markdown(md).toPdf(path);
    //System.out.println("Wrote " + path.toAbsolutePath());
    File file = path.toFile();
    assertTrue(file.exists());
  }
}
