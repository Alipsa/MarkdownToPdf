package se.alipsa.md2pdf.gui;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/** Syntax-highlighting code editor pre-configured for CSS. */
public class CssTextArea extends CodeTextArea {

  // Pattern order matters: more specific tokens before general ones.
  private static final Pattern CSS_PATTERN =
      Pattern.compile(
          "(?<COMMENT>/\\*[\\s\\S]*?\\*/)"
              + "|(?<ATRULE>@[a-zA-Z][a-zA-Z-]*)"
              + "|(?<HEX>#[0-9a-fA-F]{3,8}\\b)"
              + "|(?<STRING>\"[^\"\\n]*\"|'[^'\\n]*')"
              + "|(?<NUMBER>\\b\\d+(?:\\.\\d+)?(?:px|em|rem|pt|%|in|cm|mm|s|ms|deg|fr)?\\b)"
              + "|(?<PROPERTY>[a-zA-Z][a-zA-Z-]*(?=\\s*:))"
              + "|(?<BRACE>[{}])"
              + "|(?<SEMICOLON>;)");

  /**
   * Creates the CSS editor owned by the given tab.
   *
   * @param parentTab the tab that hosts this editor
   */
  public CssTextArea(BaseTab parentTab) {
    super(parentTab);
  }

  @Override
  public void highlightSyntax() {
    if (getText().isEmpty()) return;
    setStyleSpans(0, computeHighlighting(getText()));
  }

  private static StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = CSS_PATTERN.matcher(text);
    int lastEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
      spansBuilder.add(Collections.singleton(styleFor(matcher)), matcher.end() - matcher.start());
      lastEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
    return spansBuilder.create();
  }

  private static String styleFor(Matcher m) {
    if (m.group("COMMENT") != null) return "css_comment";
    if (m.group("ATRULE") != null) return "css_atrule";
    if (m.group("HEX") != null) return "css_hex";
    if (m.group("STRING") != null) return "css_string";
    if (m.group("NUMBER") != null) return "css_number";
    if (m.group("PROPERTY") != null) return "css_property";
    if (m.group("BRACE") != null) return "css_brace";
    if (m.group("SEMICOLON") != null) return "css_semicolon";
    return "default";
  }

  /**
   * Replaces the editor content without marking the parent tab as dirty.
   *
   * @param content the new CSS text
   */
  public void setText(String content) {
    blockChange = true;
    replaceText(content);
    highlightSyntax();
    blockChange = false;
  }
}
