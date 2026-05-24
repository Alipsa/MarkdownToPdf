package test.alipsa.md2pdf.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.model.StyleProfile;

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
  void h1BorderOnByDefault() {
    StyleProfile p = new StyleProfile("Default");
    assertTrue(p.isH1ShowBorder(), "H1 border should be on by default");
    assertTrue(p.toCss().contains("border-bottom: 1px solid"), "CSS must include H1 bottom border");
  }

  @Test
  void h1BorderCanBeDisabled() {
    StyleProfile p = new StyleProfile("NoBorder");
    p.setH1ShowBorder(false);
    assertTrue(p.toCss().contains("border-bottom: none"), "CSS must suppress H1 bottom border");
  }

  @Test
  void h1BorderRoundTrips() {
    StyleProfile original = new StyleProfile("Test");
    original.setH1ShowBorder(false);
    Properties props = new Properties();
    original.saveToProperties(props);
    StyleProfile loaded = StyleProfile.fromProperties(props);
    assertFalse(loaded.isH1ShowBorder(), "h1ShowBorder=false must survive properties round-trip");
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

  @Test
  void roundTripViaFromCss() {
    StyleProfile original = new StyleProfile("RoundTrip");
    original.setBodyFontSizePt(13);
    original.setBodyColor("#111111");
    original.setH1SizeEm(2.2);
    original.setH2SizeEm(1.7);
    original.setHeadingColor("#222222");
    original.setLinkColor("#ff6600");
    original.setCodeFontSizePct(90);
    original.setPageSize("Letter");
    original.setPageOrientation("landscape");
    original.setMarginTop("0.75in");
    original.setH1ShowBorder(false);

    String css = original.toCss();
    StyleProfile parsed = StyleProfile.fromCss(css);

    assertEquals(13, parsed.getBodyFontSizePt());
    assertEquals("#111111", parsed.getBodyColor());
    assertEquals(2.2, parsed.getH1SizeEm(), 0.001);
    assertEquals(1.7, parsed.getH2SizeEm(), 0.001);
    assertEquals("#222222", parsed.getHeadingColor());
    assertEquals("#ff6600", parsed.getLinkColor());
    assertEquals(90, parsed.getCodeFontSizePct());
    assertEquals("Letter", parsed.getPageSize());
    assertEquals("landscape", parsed.getPageOrientation());
    assertEquals("0.75in", parsed.getMarginTop());
    assertFalse(parsed.isH1ShowBorder());
  }

  @Test
  void partialCssMapsCorrectly() {
    String css = "body { font-size: 14pt; color: #abcdef; }";
    StyleProfile p = StyleProfile.fromCss(css);
    assertEquals(14, p.getBodyFontSizePt());
    assertEquals("#abcdef", p.getBodyColor());
    // Headings keep their defaults
    assertEquals(2.0, p.getH1SizeEm(), 0.001);
    assertEquals("#000000", p.getHeadingColor());
  }

  @Test
  void unknownSelectorsPreservedInExtraCss() {
    String css =
        "body { color: #333; }\ntable { border-collapse: collapse; }\nth { font-weight: bold; }";
    StyleProfile p = StyleProfile.fromCss(css);
    assertEquals("#333", p.getBodyColor());
    String extra = p.getExtraCss();
    assertTrue(extra.contains("table"), "unknown table selector must land in extraCss");
    assertTrue(extra.contains("th"), "unknown th selector must land in extraCss");
    // extraCss must survive toCss() round-trip
    String rebuilt = p.toCss();
    assertTrue(rebuilt.contains("table"), "extraCss must appear in toCss() output");
  }

  @Test
  void atPageParsedCorrectly() {
    String css = "@page { size: Letter landscape; margin: 0.75in 1in 0.75in 1in; }";
    StyleProfile p = StyleProfile.fromCss(css);
    assertEquals("Letter", p.getPageSize());
    assertEquals("landscape", p.getPageOrientation());
    assertEquals("0.75in", p.getMarginTop());
    assertEquals("1in", p.getMarginRight());
    assertEquals("0.75in", p.getMarginBottom());
    assertEquals("1in", p.getMarginLeft());
  }

  @Test
  void h1ShowBorderParsedFromCss() {
    String noBorder = "h1 { font-size: 2em; color: #000; border-bottom: none; }";
    StyleProfile p1 = StyleProfile.fromCss(noBorder);
    assertFalse(p1.isH1ShowBorder(), "border-bottom: none must set h1ShowBorder=false");

    String withBorder = "h1 { font-size: 2em; color: #000; border-bottom: 1px solid #eaecef; }";
    StyleProfile p2 = StyleProfile.fromCss(withBorder);
    assertTrue(p2.isH1ShowBorder(), "non-none border-bottom must set h1ShowBorder=true");
  }
}
