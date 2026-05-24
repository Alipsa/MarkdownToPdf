package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfEngine;

public class EngineBuilderTest {

  @Test
  void testDefaultBuilderMatchesDefaultConstructorForTables() {
    String markdown =
        """
        | Name | Value |
        | --- | --- |
        | A | 1 |
        """;

    String defaultHtml = new Md2PdfEngine().markdown(markdown).toHtml();
    String builderHtml = Md2PdfEngine.builder().build().markdown(markdown).toHtml();

    assertTrue(defaultHtml.contains("<table>"));
    assertTrue(builderHtml.contains("<table>"));
  }

  @Test
  void testBuilderCanDisableTables() {
    String markdown =
        """
        | Name | Value |
        | --- | --- |
        | A | 1 |
        """;

    String html = Md2PdfEngine.builder().tables(false).build().markdown(markdown).toHtml();

    assertFalse(html.contains("<table>"));
    assertTrue(html.contains("| Name | Value |"));
  }

  @Test
  void testBuilderCanCustomizeSoftbreak() {
    String html = Md2PdfEngine.builder().softbreak("\n").build().markdown("first\nsecond").toHtml();

    assertTrue(html.contains("first\nsecond"));
    assertFalse(html.contains("first<br />"));
  }
}
