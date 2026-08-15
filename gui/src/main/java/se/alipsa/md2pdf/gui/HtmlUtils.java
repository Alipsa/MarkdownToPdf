package se.alipsa.md2pdf.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Utilities for post-processing HTML strings loaded into WebView previews. */
class HtmlUtils {

  private static final String HIGHLIGHT_INJECTION;

  static {
    String css = loadResource("/highlight/github.min.css");
    String js = loadResource("/highlight/highlight.min.js");
    HIGHLIGHT_INJECTION =
        "<style>"
            + css
            + "</style>\n"
            + "<script>"
            + js
            + "</script>\n"
            + "<script>document.addEventListener('DOMContentLoaded',function(){hljs.highlightAll();});</script>\n";
  }

  private HtmlUtils() {}

  /**
   * Injects Highlight.js into the HTML so that fenced code blocks with a language specifier (e.g.
   * {@code ```groovy}) are rendered with syntax colouring in a WebView. The JS and CSS are bundled
   * resources — no network access required.
   */
  static String injectSyntaxHighlighting(String html) {
    if (html == null) return "";
    int idx = lastHeadEndTagStart(html);
    if (idx >= 0) {
      return html.substring(0, idx) + HIGHLIGHT_INJECTION + html.substring(idx);
    }
    return HIGHLIGHT_INJECTION + html;
  }

  private static int lastHeadEndTagStart(String html) {
    String closingHead = "</head>";
    for (int i = html.length() - closingHead.length(); i >= 0; i--) {
      if (html.regionMatches(true, i, closingHead, 0, closingHead.length())) {
        return i;
      }
    }
    return -1;
  }

  private static String loadResource(String path) {
    try (InputStream is = HtmlUtils.class.getResourceAsStream(path)) {
      if (is == null) return "";
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }
}
