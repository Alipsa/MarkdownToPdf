package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.md2pdf.Md2PdfEngine;

public class CssCustomizationTest {

  @TempDir Path tempDir;

  @Test
  void testCssReplacesDefaultStylesheet() {
    String html = new Md2PdfEngine().markdown("# Report").css("body { color: red; }").toHtml();

    assertTrue(html.contains("body { color: red; }"));
    assertFalse(html.contains("line-height: 1.6"));
  }

  @Test
  void testAddCssAppendsToDefaultStylesheet() {
    String html = new Md2PdfEngine().markdown("# Report").addCss("h1 { color: red; }").toHtml();

    assertTrue(html.contains("line-height: 1.6"));
    assertTrue(html.indexOf("line-height: 1.6") < html.indexOf("h1 { color: red; }"));
  }

  @Test
  void testAddCssOverloads() throws Exception {
    Path cssPath = tempDir.resolve("style.css");
    Files.writeString(cssPath, "h1 { color: red; }", StandardCharsets.UTF_8);

    String html =
        new Md2PdfEngine()
            .markdown("# Report")
            .addCss(cssPath.toFile())
            .addCss(cssPath)
            .addCss(cssPath.toUri().toURL())
            .addCss(new ByteArrayInputStream("p { color: blue; }".getBytes(StandardCharsets.UTF_8)))
            .toHtml();

    assertTrue(html.contains("h1 { color: red; }"));
    assertTrue(html.contains("p { color: blue; }"));
  }

  @Test
  void testDefaultCssDoesNotSetBodyFontFamily() {
    String html =
        new Md2PdfEngine()
            .markdown(
                """
            # Math

            <math mode="display" xmlns="http://www.w3.org/1998/Math/MathML">
              <mi>x</mi>
            </math>
            """)
            .toHtml();

    assertFalse(html.contains("-apple-system"));
    assertFalse(html.contains("BlinkMacSystemFont"));
    assertFalse(html.contains("font-family:"));
  }
}
