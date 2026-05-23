package se.alipsa.md2pdf.model;

import java.util.Properties;

/**
 * Holds all visual styling parameters for a markdown document.
 * Call {@link #toCss()} to get a CSS string suitable for {@code Job.addCss()}.
 */
public class StyleProfile {

  private String name;

  // Typography
  private String bodyFont = "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif";
  private int bodyFontSizePt = 11;
  private double lineHeight = 1.6;
  private String bodyColor = "#333333";

  // Headings
  private double h1SizeEm = 2.0;
  private double h2SizeEm = 1.5;
  private double h3SizeEm = 1.25;
  private String headingColor = "#000000";

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

  public StyleProfile(String name) {
    this.name = name;
  }

  /** Generates override CSS to pass to {@code Job.addCss()}. */
  public String toCss() {
    return "body {\n" +
        "  font-family: " + bodyFont + ";\n" +
        "  font-size: " + bodyFontSizePt + "pt;\n" +
        "  line-height: " + lineHeight + ";\n" +
        "  color: " + bodyColor + ";\n" +
        "}\n" +
        "h1 { font-size: " + h1SizeEm + "em; color: " + headingColor + "; }\n" +
        "h2 { font-size: " + h2SizeEm + "em; color: " + headingColor + "; }\n" +
        "h3 { font-size: " + h3SizeEm + "em; color: " + headingColor + "; }\n" +
        "a { color: " + linkColor + "; }\n" +
        "code {\n" +
        "  font-family: " + codeFont + ";\n" +
        "  font-size: " + codeFontSizePct + "%;\n" +
        "  background-color: " + codeBackground + ";\n" +
        "}\n" +
        "pre {\n" +
        "  font-family: " + codeFont + ";\n" +
        "  background-color: " + codeBackground + ";\n" +
        "}\n" +
        "pre code { background-color: transparent; }\n" +
        "blockquote {\n" +
        "  border-left: 0.25em solid " + blockquoteBorderColor + ";\n" +
        "  color: " + blockquoteTextColor + ";\n" +
        "}\n" +
        "@page {\n" +
        "  size: " + pageSize + " " + pageOrientation + ";\n" +
        "  margin: " + marginTop + " " + marginRight + " " + marginBottom + " " + marginLeft + ";\n" +
        "}\n";
  }

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
  }

  public static StyleProfile fromProperties(Properties props) {
    StyleProfile p = new StyleProfile(props.getProperty("name", "Unnamed"));
    p.bodyFont = props.getProperty("bodyFont", p.bodyFont);
    p.bodyFontSizePt = Integer.parseInt(props.getProperty("bodyFontSizePt", String.valueOf(p.bodyFontSizePt)));
    p.lineHeight = Double.parseDouble(props.getProperty("lineHeight", String.valueOf(p.lineHeight)));
    p.bodyColor = props.getProperty("bodyColor", p.bodyColor);
    p.h1SizeEm = Double.parseDouble(props.getProperty("h1SizeEm", String.valueOf(p.h1SizeEm)));
    p.h2SizeEm = Double.parseDouble(props.getProperty("h2SizeEm", String.valueOf(p.h2SizeEm)));
    p.h3SizeEm = Double.parseDouble(props.getProperty("h3SizeEm", String.valueOf(p.h3SizeEm)));
    p.headingColor = props.getProperty("headingColor", p.headingColor);
    p.linkColor = props.getProperty("linkColor", p.linkColor);
    p.codeFont = props.getProperty("codeFont", p.codeFont);
    p.codeFontSizePct = Integer.parseInt(props.getProperty("codeFontSizePct", String.valueOf(p.codeFontSizePct)));
    p.codeBackground = props.getProperty("codeBackground", p.codeBackground);
    p.blockquoteBorderColor = props.getProperty("blockquoteBorderColor", p.blockquoteBorderColor);
    p.blockquoteTextColor = props.getProperty("blockquoteTextColor", p.blockquoteTextColor);
    p.pageSize = props.getProperty("pageSize", p.pageSize);
    p.pageOrientation = props.getProperty("pageOrientation", p.pageOrientation);
    p.marginTop = props.getProperty("marginTop", p.marginTop);
    p.marginRight = props.getProperty("marginRight", p.marginRight);
    p.marginBottom = props.getProperty("marginBottom", p.marginBottom);
    p.marginLeft = props.getProperty("marginLeft", p.marginLeft);
    return p;
  }

  // --- Getters and setters ---

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getBodyFont() { return bodyFont; }
  public void setBodyFont(String bodyFont) { this.bodyFont = bodyFont; }

  public int getBodyFontSizePt() { return bodyFontSizePt; }
  public void setBodyFontSizePt(int bodyFontSizePt) { this.bodyFontSizePt = bodyFontSizePt; }

  public double getLineHeight() { return lineHeight; }
  public void setLineHeight(double lineHeight) { this.lineHeight = lineHeight; }

  public String getBodyColor() { return bodyColor; }
  public void setBodyColor(String bodyColor) { this.bodyColor = bodyColor; }

  public double getH1SizeEm() { return h1SizeEm; }
  public void setH1SizeEm(double h1SizeEm) { this.h1SizeEm = h1SizeEm; }

  public double getH2SizeEm() { return h2SizeEm; }
  public void setH2SizeEm(double h2SizeEm) { this.h2SizeEm = h2SizeEm; }

  public double getH3SizeEm() { return h3SizeEm; }
  public void setH3SizeEm(double h3SizeEm) { this.h3SizeEm = h3SizeEm; }

  public String getHeadingColor() { return headingColor; }
  public void setHeadingColor(String headingColor) { this.headingColor = headingColor; }

  public String getLinkColor() { return linkColor; }
  public void setLinkColor(String linkColor) { this.linkColor = linkColor; }

  public String getCodeFont() { return codeFont; }
  public void setCodeFont(String codeFont) { this.codeFont = codeFont; }

  public int getCodeFontSizePct() { return codeFontSizePct; }
  public void setCodeFontSizePct(int codeFontSizePct) { this.codeFontSizePct = codeFontSizePct; }

  public String getCodeBackground() { return codeBackground; }
  public void setCodeBackground(String codeBackground) { this.codeBackground = codeBackground; }

  public String getBlockquoteBorderColor() { return blockquoteBorderColor; }
  public void setBlockquoteBorderColor(String blockquoteBorderColor) { this.blockquoteBorderColor = blockquoteBorderColor; }

  public String getBlockquoteTextColor() { return blockquoteTextColor; }
  public void setBlockquoteTextColor(String blockquoteTextColor) { this.blockquoteTextColor = blockquoteTextColor; }

  public String getPageSize() { return pageSize; }
  public void setPageSize(String pageSize) { this.pageSize = pageSize; }

  public String getPageOrientation() { return pageOrientation; }
  public void setPageOrientation(String pageOrientation) { this.pageOrientation = pageOrientation; }

  public String getMarginTop() { return marginTop; }
  public void setMarginTop(String marginTop) { this.marginTop = marginTop; }

  public String getMarginRight() { return marginRight; }
  public void setMarginRight(String marginRight) { this.marginRight = marginRight; }

  public String getMarginBottom() { return marginBottom; }
  public void setMarginBottom(String marginBottom) { this.marginBottom = marginBottom; }

  public String getMarginLeft() { return marginLeft; }
  public void setMarginLeft(String marginLeft) { this.marginLeft = marginLeft; }

  @Override
  public String toString() {
    return name;
  }
}
