package se.alipsa.md2pdf.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Holds all visual styling parameters for a markdown document. Call {@link #toCss()} to get a CSS
 * string suitable for {@code Job.addCss()}. Call {@link #fromCss(String)} to parse a CSS string
 * back into a profile.
 */
public class StyleProfile {

  private String name;

  // Typography
  private String bodyFont =
      "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif";
  private int bodyFontSizePt = 11;
  private double lineHeight = 1.6;
  private String bodyColor = "#333333";

  // Headings
  private double h1SizeEm = 2.0;
  private double h2SizeEm = 1.5;
  private double h3SizeEm = 1.25;
  private String headingColor = "#000000";
  private boolean h1ShowBorder = true;

  // Links
  private String linkColor = "#0366d6";

  // Code
  private String codeFont = "monospace";
  private int codeFontSizePct = 85;
  private String codeBackground = "#f6f8fa";

  // Blockquotes
  private String blockquoteBorderColor = "#dfe2e5";
  private String blockquoteTextColor = "#6a737d";

  // Page (PDF)
  private String pageSize = "A4";
  private String pageOrientation = "portrait";
  private String marginTop = "1in";
  private String marginRight = "1in";
  private String marginBottom = "1in";
  private String marginLeft = "1in";

  // Passthrough CSS rules not mapped to form fields
  private String extraCss = "";

  /**
   * Creates a profile with the given name and all other settings at their defaults.
   *
   * @param name the profile name
   */
  public StyleProfile(String name) {
    this.name = name;
  }

  /** Generates override CSS to pass to {@code Job.addCss()}. */
  public String toCss() {
    return "body {\n"
        + "  font-family: "
        + bodyFont
        + ";\n"
        + "  font-size: "
        + bodyFontSizePt
        + "pt;\n"
        + "  line-height: "
        + lineHeight
        + ";\n"
        + "  color: "
        + bodyColor
        + ";\n"
        + "}\n"
        + "h1 { font-size: "
        + h1SizeEm
        + "em; color: "
        + headingColor
        + "; border-bottom: "
        + (h1ShowBorder ? "1px solid #eaecef" : "none")
        + "; }\n"
        + "h2 { font-size: "
        + h2SizeEm
        + "em; color: "
        + headingColor
        + "; }\n"
        + "h3 { font-size: "
        + h3SizeEm
        + "em; color: "
        + headingColor
        + "; }\n"
        + "a { color: "
        + linkColor
        + "; }\n"
        + "code {\n"
        + "  font-family: "
        + codeFont
        + ";\n"
        + "  font-size: "
        + codeFontSizePct
        + "%;\n"
        + "  background-color: "
        + codeBackground
        + ";\n"
        + "}\n"
        + "pre {\n"
        + "  font-family: "
        + codeFont
        + ";\n"
        + "  background-color: "
        + codeBackground
        + ";\n"
        + "}\n"
        + "pre code { background-color: transparent; }\n"
        + "blockquote {\n"
        + "  border-left: 0.25em solid "
        + blockquoteBorderColor
        + ";\n"
        + "  color: "
        + blockquoteTextColor
        + ";\n"
        + "}\n"
        + "@page {\n"
        + "  size: "
        + pageSize
        + " "
        + pageOrientation
        + ";\n"
        + "  margin: "
        + marginTop
        + " "
        + marginRight
        + " "
        + marginBottom
        + " "
        + marginLeft
        + ";\n"
        + "}\n"
        + (extraCss != null && !extraCss.isBlank() ? "\n" + extraCss + "\n" : "");
  }

  /**
   * Parses a CSS string into a StyleProfile. Known properties populate the corresponding fields;
   * everything else is stored verbatim in {@link #extraCss} so it survives a CSS round-trip.
   */
  public static StyleProfile fromCss(String css) {
    StyleProfile p = new StyleProfile("");
    if (css == null || css.isBlank()) return p;

    String stripped = css.replaceAll("/\\*[\\s\\S]*?\\*/", "");
    List<String[]> blocks = extractBlocks(stripped);

    StringBuilder extra = new StringBuilder();
    for (String[] block : blocks) {
      String sel = normalizeSelector(block[0]);
      String declText = block[1];
      Map<String, String> decls = parseDeclarations(declText);
      switch (sel) {
        case "body" -> applyBody(p, decls);
        case "h1" -> applyH1(p, decls);
        case "h2" -> applyH2(p, decls);
        case "h3" -> applyH3(p, decls);
        case "a" -> applyA(p, decls);
        case "code" -> applyCode(p, decls);
        case "pre" -> {} // derived from code settings
        case "pre code" -> {} // transparent background — derived
        case "blockquote" -> applyBlockquote(p, decls);
        case "@page" -> applyPage(p, decls);
        default -> extra.append(block[0].trim()).append(" {\n").append(declText).append("\n}\n");
      }
    }
    p.extraCss = extra.toString().trim();
    return p;
  }

  // -------------------------------------------------------------------------
  // CSS parsing helpers
  // -------------------------------------------------------------------------

  private static List<String[]> extractBlocks(String css) {
    List<String[]> blocks = new ArrayList<>();
    int i = 0;
    while (i < css.length()) {
      int open = css.indexOf('{', i);
      if (open < 0) break;
      String selector = css.substring(i, open).trim();
      int depth = 1;
      int j = open + 1;
      while (j < css.length() && depth > 0) {
        char c = css.charAt(j++);
        if (c == '{') depth++;
        else if (c == '}') depth--;
      }
      if (!selector.isEmpty()) {
        blocks.add(new String[] {selector, css.substring(open + 1, j - 1).trim()});
      }
      i = j;
    }
    return blocks;
  }

  private static String normalizeSelector(String sel) {
    return sel.replaceAll("\\s+", " ").trim().toLowerCase();
  }

  private static Map<String, String> parseDeclarations(String decls) {
    Map<String, String> map = new LinkedHashMap<>();
    for (String decl : decls.split(";")) {
      decl = decl.trim();
      if (decl.isEmpty()) continue;
      int colon = decl.indexOf(':');
      if (colon < 0) continue;
      String prop = decl.substring(0, colon).trim().toLowerCase();
      String value = decl.substring(colon + 1).trim();
      map.put(prop, value);
    }
    return map;
  }

  private static void applyBody(StyleProfile p, Map<String, String> decls) {
    if (decls.containsKey("font-family")) p.bodyFont = decls.get("font-family");
    if (decls.containsKey("font-size")) {
      String v = decls.get("font-size");
      if (v.endsWith("pt")) {
        try {
          p.bodyFontSizePt = Integer.parseInt(v.substring(0, v.length() - 2).trim());
        } catch (NumberFormatException ignored) {
        }
      }
    }
    if (decls.containsKey("line-height")) {
      try {
        p.lineHeight = Double.parseDouble(decls.get("line-height"));
      } catch (NumberFormatException ignored) {
      }
    }
    if (decls.containsKey("color")) p.bodyColor = decls.get("color");
  }

  private static void applyH1(StyleProfile p, Map<String, String> decls) {
    if (decls.containsKey("font-size")) {
      String v = decls.get("font-size");
      if (v.endsWith("em")) {
        try {
          p.h1SizeEm = Double.parseDouble(v.substring(0, v.length() - 2).trim());
        } catch (NumberFormatException ignored) {
        }
      }
    }
    if (decls.containsKey("color")) p.headingColor = decls.get("color");
    if (decls.containsKey("border-bottom")) {
      p.h1ShowBorder = !decls.get("border-bottom").trim().equalsIgnoreCase("none");
    }
  }

  private static void applyH2(StyleProfile p, Map<String, String> decls) {
    if (decls.containsKey("font-size")) {
      String v = decls.get("font-size");
      if (v.endsWith("em")) {
        try {
          p.h2SizeEm = Double.parseDouble(v.substring(0, v.length() - 2).trim());
        } catch (NumberFormatException ignored) {
        }
      }
    }
    if (decls.containsKey("color")) p.headingColor = decls.get("color");
  }

  private static void applyH3(StyleProfile p, Map<String, String> decls) {
    if (decls.containsKey("font-size")) {
      String v = decls.get("font-size");
      if (v.endsWith("em")) {
        try {
          p.h3SizeEm = Double.parseDouble(v.substring(0, v.length() - 2).trim());
        } catch (NumberFormatException ignored) {
        }
      }
    }
    if (decls.containsKey("color")) p.headingColor = decls.get("color");
  }

  private static void applyA(StyleProfile p, Map<String, String> decls) {
    if (decls.containsKey("color")) p.linkColor = decls.get("color");
  }

  private static void applyCode(StyleProfile p, Map<String, String> decls) {
    if (decls.containsKey("font-family")) p.codeFont = decls.get("font-family");
    if (decls.containsKey("font-size")) {
      String v = decls.get("font-size");
      if (v.endsWith("%")) {
        try {
          p.codeFontSizePct = Integer.parseInt(v.substring(0, v.length() - 1).trim());
        } catch (NumberFormatException ignored) {
        }
      }
    }
    if (decls.containsKey("background-color")) p.codeBackground = decls.get("background-color");
  }

  private static void applyBlockquote(StyleProfile p, Map<String, String> decls) {
    if (decls.containsKey("border-left")) {
      // "0.25em solid #dfe2e5" — last whitespace-separated token is the color
      String[] parts = decls.get("border-left").trim().split("\\s+");
      if (parts.length > 0) p.blockquoteBorderColor = parts[parts.length - 1];
    }
    if (decls.containsKey("color")) p.blockquoteTextColor = decls.get("color");
  }

  private static void applyPage(StyleProfile p, Map<String, String> decls) {
    if (decls.containsKey("size")) {
      String[] parts = decls.get("size").trim().split("\\s+");
      if (parts.length >= 1) p.pageSize = parts[0];
      if (parts.length >= 2) p.pageOrientation = parts[1];
    }
    if (decls.containsKey("margin")) {
      String[] parts = decls.get("margin").trim().split("\\s+");
      if (parts.length == 1) {
        p.marginTop = p.marginRight = p.marginBottom = p.marginLeft = parts[0];
      } else if (parts.length == 2) {
        p.marginTop = p.marginBottom = parts[0];
        p.marginRight = p.marginLeft = parts[1];
      } else if (parts.length == 3) {
        p.marginTop = parts[0];
        p.marginRight = p.marginLeft = parts[1];
        p.marginBottom = parts[2];
      } else if (parts.length >= 4) {
        p.marginTop = parts[0];
        p.marginRight = parts[1];
        p.marginBottom = parts[2];
        p.marginLeft = parts[3];
      }
    }
  }

  // -------------------------------------------------------------------------
  // Properties persistence
  // -------------------------------------------------------------------------

  /**
   * Serialises all profile fields into the given {@link Properties} map.
   *
   * @param props the properties map to write into
   */
  public void saveToProperties(Properties props) {
    props.setProperty("name", name);
    props.setProperty("bodyFont", bodyFont);
    props.setProperty("bodyFontSizePt", String.valueOf(bodyFontSizePt));
    props.setProperty("lineHeight", String.valueOf(lineHeight));
    props.setProperty("bodyColor", bodyColor);
    props.setProperty("h1SizeEm", String.valueOf(h1SizeEm));
    props.setProperty("h2SizeEm", String.valueOf(h2SizeEm));
    props.setProperty("h3SizeEm", String.valueOf(h3SizeEm));
    props.setProperty("headingColor", headingColor);
    props.setProperty("h1ShowBorder", String.valueOf(h1ShowBorder));
    props.setProperty("linkColor", linkColor);
    props.setProperty("codeFont", codeFont);
    props.setProperty("codeFontSizePct", String.valueOf(codeFontSizePct));
    props.setProperty("codeBackground", codeBackground);
    props.setProperty("blockquoteBorderColor", blockquoteBorderColor);
    props.setProperty("blockquoteTextColor", blockquoteTextColor);
    props.setProperty("pageSize", pageSize);
    props.setProperty("pageOrientation", pageOrientation);
    props.setProperty("marginTop", marginTop);
    props.setProperty("marginRight", marginRight);
    props.setProperty("marginBottom", marginBottom);
    props.setProperty("marginLeft", marginLeft);
    props.setProperty("extraCss", extraCss != null ? extraCss : "");
  }

  /**
   * Deserialises a {@link StyleProfile} from the given {@link Properties} map.
   *
   * @param props the properties map to read from
   * @return the populated profile
   */
  public static StyleProfile fromProperties(Properties props) {
    StyleProfile p = new StyleProfile(props.getProperty("name", "Unnamed"));
    p.bodyFont = props.getProperty("bodyFont", p.bodyFont);
    p.bodyFontSizePt =
        Integer.parseInt(props.getProperty("bodyFontSizePt", String.valueOf(p.bodyFontSizePt)));
    p.lineHeight =
        Double.parseDouble(props.getProperty("lineHeight", String.valueOf(p.lineHeight)));
    p.bodyColor = props.getProperty("bodyColor", p.bodyColor);
    p.h1SizeEm = Double.parseDouble(props.getProperty("h1SizeEm", String.valueOf(p.h1SizeEm)));
    p.h2SizeEm = Double.parseDouble(props.getProperty("h2SizeEm", String.valueOf(p.h2SizeEm)));
    p.h3SizeEm = Double.parseDouble(props.getProperty("h3SizeEm", String.valueOf(p.h3SizeEm)));
    p.headingColor = props.getProperty("headingColor", p.headingColor);
    p.h1ShowBorder = Boolean.parseBoolean(props.getProperty("h1ShowBorder", "true"));
    p.linkColor = props.getProperty("linkColor", p.linkColor);
    p.codeFont = props.getProperty("codeFont", p.codeFont);
    p.codeFontSizePct =
        Integer.parseInt(props.getProperty("codeFontSizePct", String.valueOf(p.codeFontSizePct)));
    p.codeBackground = props.getProperty("codeBackground", p.codeBackground);
    p.blockquoteBorderColor = props.getProperty("blockquoteBorderColor", p.blockquoteBorderColor);
    p.blockquoteTextColor = props.getProperty("blockquoteTextColor", p.blockquoteTextColor);
    p.pageSize = props.getProperty("pageSize", p.pageSize);
    p.pageOrientation = props.getProperty("pageOrientation", p.pageOrientation);
    p.marginTop = props.getProperty("marginTop", p.marginTop);
    p.marginRight = props.getProperty("marginRight", p.marginRight);
    p.marginBottom = props.getProperty("marginBottom", p.marginBottom);
    p.marginLeft = props.getProperty("marginLeft", p.marginLeft);
    p.extraCss = props.getProperty("extraCss", "");
    return p;
  }

  // --- Getters and setters ---

  /**
   * Returns the profile name.
   *
   * @return the profile name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the profile name.
   *
   * @param name the profile name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the CSS font-family stack for body text.
   *
   * @return the body font stack
   */
  public String getBodyFont() {
    return bodyFont;
  }

  /**
   * Sets the CSS font-family stack for body text.
   *
   * @param bodyFont the body font stack
   */
  public void setBodyFont(String bodyFont) {
    this.bodyFont = bodyFont;
  }

  /**
   * Returns the body font size in points.
   *
   * @return the body font size in pt
   */
  public int getBodyFontSizePt() {
    return bodyFontSizePt;
  }

  /**
   * Sets the body font size in points.
   *
   * @param bodyFontSizePt the body font size in pt
   */
  public void setBodyFontSizePt(int bodyFontSizePt) {
    this.bodyFontSizePt = bodyFontSizePt;
  }

  /**
   * Returns the CSS line-height multiplier for body text.
   *
   * @return the line height
   */
  public double getLineHeight() {
    return lineHeight;
  }

  /**
   * Sets the CSS line-height multiplier for body text.
   *
   * @param lineHeight the line height
   */
  public void setLineHeight(double lineHeight) {
    this.lineHeight = lineHeight;
  }

  /**
   * Returns the body text colour as a CSS hex string (e.g. {@code "#333333"}).
   *
   * @return the body colour
   */
  public String getBodyColor() {
    return bodyColor;
  }

  /**
   * Sets the body text colour.
   *
   * @param bodyColor the colour as a CSS hex string
   */
  public void setBodyColor(String bodyColor) {
    this.bodyColor = bodyColor;
  }

  /**
   * Returns the H1 heading size in em units.
   *
   * @return the H1 size in em
   */
  public double getH1SizeEm() {
    return h1SizeEm;
  }

  /**
   * Sets the H1 heading size in em units.
   *
   * @param h1SizeEm the H1 size in em
   */
  public void setH1SizeEm(double h1SizeEm) {
    this.h1SizeEm = h1SizeEm;
  }

  /**
   * Returns the H2 heading size in em units.
   *
   * @return the H2 size in em
   */
  public double getH2SizeEm() {
    return h2SizeEm;
  }

  /**
   * Sets the H2 heading size in em units.
   *
   * @param h2SizeEm the H2 size in em
   */
  public void setH2SizeEm(double h2SizeEm) {
    this.h2SizeEm = h2SizeEm;
  }

  /**
   * Returns the H3 heading size in em units.
   *
   * @return the H3 size in em
   */
  public double getH3SizeEm() {
    return h3SizeEm;
  }

  /**
   * Sets the H3 heading size in em units.
   *
   * @param h3SizeEm the H3 size in em
   */
  public void setH3SizeEm(double h3SizeEm) {
    this.h3SizeEm = h3SizeEm;
  }

  /**
   * Returns the shared colour for all heading levels (H1–H3).
   *
   * @return the heading colour as a CSS hex string
   */
  public String getHeadingColor() {
    return headingColor;
  }

  /**
   * Sets the shared colour for all heading levels (H1–H3).
   *
   * @param headingColor the heading colour as a CSS hex string
   */
  public void setHeadingColor(String headingColor) {
    this.headingColor = headingColor;
  }

  /**
   * Returns whether a bottom border is rendered under H1 headings.
   *
   * @return {@code true} if the H1 border is shown
   */
  public boolean isH1ShowBorder() {
    return h1ShowBorder;
  }

  /**
   * Sets whether a bottom border is rendered under H1 headings.
   *
   * @param h1ShowBorder {@code true} to show the border
   */
  public void setH1ShowBorder(boolean h1ShowBorder) {
    this.h1ShowBorder = h1ShowBorder;
  }

  /**
   * Returns the hyperlink colour as a CSS hex string.
   *
   * @return the link colour
   */
  public String getLinkColor() {
    return linkColor;
  }

  /**
   * Sets the hyperlink colour.
   *
   * @param linkColor the link colour as a CSS hex string
   */
  public void setLinkColor(String linkColor) {
    this.linkColor = linkColor;
  }

  /**
   * Returns the font-family stack used for inline code and preformatted blocks.
   *
   * @return the code font stack
   */
  public String getCodeFont() {
    return codeFont;
  }

  /**
   * Sets the font-family stack for inline code and preformatted blocks.
   *
   * @param codeFont the code font stack
   */
  public void setCodeFont(String codeFont) {
    this.codeFont = codeFont;
  }

  /**
   * Returns the code font size as a percentage of the body font size.
   *
   * @return the code font size in percent
   */
  public int getCodeFontSizePct() {
    return codeFontSizePct;
  }

  /**
   * Sets the code font size as a percentage of the body font size.
   *
   * @param codeFontSizePct the code font size in percent
   */
  public void setCodeFontSizePct(int codeFontSizePct) {
    this.codeFontSizePct = codeFontSizePct;
  }

  /**
   * Returns the background colour for code blocks as a CSS hex string.
   *
   * @return the code background colour
   */
  public String getCodeBackground() {
    return codeBackground;
  }

  /**
   * Sets the background colour for code blocks.
   *
   * @param codeBackground the background colour as a CSS hex string
   */
  public void setCodeBackground(String codeBackground) {
    this.codeBackground = codeBackground;
  }

  /**
   * Returns the left-border colour of blockquotes as a CSS hex string.
   *
   * @return the blockquote border colour
   */
  public String getBlockquoteBorderColor() {
    return blockquoteBorderColor;
  }

  /**
   * Sets the left-border colour for blockquotes.
   *
   * @param blockquoteBorderColor the border colour as a CSS hex string
   */
  public void setBlockquoteBorderColor(String blockquoteBorderColor) {
    this.blockquoteBorderColor = blockquoteBorderColor;
  }

  /**
   * Returns the text colour inside blockquotes as a CSS hex string.
   *
   * @return the blockquote text colour
   */
  public String getBlockquoteTextColor() {
    return blockquoteTextColor;
  }

  /**
   * Sets the text colour inside blockquotes.
   *
   * @param blockquoteTextColor the text colour as a CSS hex string
   */
  public void setBlockquoteTextColor(String blockquoteTextColor) {
    this.blockquoteTextColor = blockquoteTextColor;
  }

  /**
   * Returns the CSS page size keyword (e.g. {@code "A4"}, {@code "Letter"}).
   *
   * @return the page size
   */
  public String getPageSize() {
    return pageSize;
  }

  /**
   * Sets the CSS page size keyword.
   *
   * @param pageSize the page size (e.g. {@code "A4"})
   */
  public void setPageSize(String pageSize) {
    this.pageSize = pageSize;
  }

  /**
   * Returns the page orientation ({@code "portrait"} or {@code "landscape"}).
   *
   * @return the page orientation
   */
  public String getPageOrientation() {
    return pageOrientation;
  }

  /**
   * Sets the page orientation.
   *
   * @param pageOrientation {@code "portrait"} or {@code "landscape"}
   */
  public void setPageOrientation(String pageOrientation) {
    this.pageOrientation = pageOrientation;
  }

  /**
   * Returns the top page margin as a CSS length string (e.g. {@code "1in"}).
   *
   * @return the top margin
   */
  public String getMarginTop() {
    return marginTop;
  }

  /**
   * Sets the top page margin.
   *
   * @param marginTop the top margin as a CSS length string (e.g. {@code "1in"})
   */
  public void setMarginTop(String marginTop) {
    this.marginTop = marginTop;
  }

  /**
   * Returns the right page margin as a CSS length string.
   *
   * @return the right margin
   */
  public String getMarginRight() {
    return marginRight;
  }

  /**
   * Sets the right page margin.
   *
   * @param marginRight the right margin as a CSS length string
   */
  public void setMarginRight(String marginRight) {
    this.marginRight = marginRight;
  }

  /**
   * Returns the bottom page margin as a CSS length string.
   *
   * @return the bottom margin
   */
  public String getMarginBottom() {
    return marginBottom;
  }

  /**
   * Sets the bottom page margin.
   *
   * @param marginBottom the bottom margin as a CSS length string
   */
  public void setMarginBottom(String marginBottom) {
    this.marginBottom = marginBottom;
  }

  /**
   * Returns the left page margin as a CSS length string.
   *
   * @return the left margin
   */
  public String getMarginLeft() {
    return marginLeft;
  }

  /**
   * Sets the left page margin.
   *
   * @param marginLeft the left margin as a CSS length string
   */
  public void setMarginLeft(String marginLeft) {
    this.marginLeft = marginLeft;
  }

  /**
   * Returns any CSS rules that could not be mapped to a known form field. These are appended
   * verbatim to the output of {@link #toCss()}.
   *
   * @return the extra CSS, never {@code null}
   */
  public String getExtraCss() {
    return extraCss;
  }

  /**
   * Sets extra CSS rules to pass through verbatim. Pass {@code null} to clear.
   *
   * @param extraCss the extra CSS rules, or {@code null}
   */
  public void setExtraCss(String extraCss) {
    this.extraCss = extraCss != null ? extraCss : "";
  }

  @Override
  public String toString() {
    return name;
  }
}
