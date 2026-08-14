package se.alipsa.md2pdf.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HtmlUtilsTest {

  @Test
  void syntaxHighlightingPreservesHeadWithTurkishCapitalI() {
    String html = "<html><head><style>font-family: 'İstanbul Sans';</style></head><body/></html>";

    String highlighted = HtmlUtils.injectSyntaxHighlighting(html);

    assertTrue(highlighted.contains("</head>"));
    assertTrue(highlighted.indexOf("<script>") < highlighted.indexOf("</head>"));
  }
}
