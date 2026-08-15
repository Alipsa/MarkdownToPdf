package se.alipsa.md2pdf.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class HtmlUtilsTest {

  @Test
  void syntaxHighlightingPreservesHeadWithTurkishCapitalI() {
    Locale originalLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.US);
      String html = "<html><head><style>font-family: 'İstanbul Sans';</style></head><body/></html>";

      String highlighted = HtmlUtils.injectSyntaxHighlighting(html);

      assertTrue(highlighted.contains("</head>"));
      assertTrue(highlighted.indexOf("<script>") < highlighted.indexOf("</head>"));
    } finally {
      Locale.setDefault(originalLocale);
    }
  }
}
