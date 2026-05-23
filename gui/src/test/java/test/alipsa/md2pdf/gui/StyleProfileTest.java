package test.alipsa.md2pdf.gui;

import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.model.StyleProfile;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class StyleProfileTest {

  @Test
  void defaultProfileCssContainsBodyFont() {
    StyleProfile p = new StyleProfile("Default");
    String css = p.toCss();
    assertTrue(css.contains("font-family"), "CSS must contain font-family");
  }

  @Test
  void defaultProfileCssContainsBodyFontSize() {
    StyleProfile p = new StyleProfile("Default");
    String css = p.toCss();
    assertTrue(css.contains("11pt"), "CSS must contain default body font size 11pt");
  }

  @Test
  void defaultProfileCssContainsPageRule() {
    StyleProfile p = new StyleProfile("Default");
    String css = p.toCss();
    assertTrue(css.contains("@page"), "CSS must contain @page rule for PDF");
    assertTrue(css.contains("A4"), "CSS must contain A4 page size");
    assertTrue(css.contains("portrait"), "CSS must contain portrait orientation");
  }

  @Test
  void customBodyColorAppearsInCss() {
    StyleProfile p = new StyleProfile("Custom");
    p.setBodyColor("#FF0000");
    assertTrue(p.toCss().contains("#FF0000"), "Custom body color must appear in CSS");
  }

  @Test
  void customHeadingSizeAppearsInCss() {
    StyleProfile p = new StyleProfile("Custom");
    p.setH1SizeEm(2.5);
    assertTrue(p.toCss().contains("2.5em"), "Custom H1 size must appear in CSS");
  }

  @Test
  void landscapeOrientationAppearsInCss() {
    StyleProfile p = new StyleProfile("Test");
    p.setPageOrientation("landscape");
    assertTrue(p.toCss().contains("landscape"), "landscape orientation must appear in CSS");
  }

  @Test
  void customMarginsAppearInCss() {
    StyleProfile p = new StyleProfile("Test");
    p.setMarginTop("2in");
    assertTrue(p.toCss().contains("2in"), "Custom top margin must appear in CSS");
  }

  @Test
  void propertiesRoundTrip() {
    StyleProfile original = new StyleProfile("TestProfile");
    original.setBodyFontSizePt(14);
    original.setBodyColor("#112233");
    original.setPageSize("Letter");
    original.setLineHeight(1.8);
    original.setH2SizeEm(1.3);
    original.setPageOrientation("landscape");

    Properties props = new Properties();
    original.saveToProperties(props);

    StyleProfile loaded = StyleProfile.fromProperties(props);
    assertEquals("TestProfile", loaded.getName());
    assertEquals(14, loaded.getBodyFontSizePt());
    assertEquals("#112233", loaded.getBodyColor());
    assertEquals("Letter", loaded.getPageSize());
    assertEquals(1.8, loaded.getLineHeight(), 0.001);
    assertEquals(1.3, loaded.getH2SizeEm(), 0.001);
    assertEquals("landscape", loaded.getPageOrientation());
  }

  @Test
  void defaultsMatchLibDefaultCss() {
    StyleProfile p = new StyleProfile("Default");
    // These defaults should match what the lib's DEFAULT_CSS uses
    assertEquals(1.6, p.getLineHeight(), 0.001);
    assertEquals("#333333", p.getBodyColor());
    assertEquals("#0366d6", p.getLinkColor());
    assertEquals("#f6f8fa", p.getCodeBackground());
  }
}
