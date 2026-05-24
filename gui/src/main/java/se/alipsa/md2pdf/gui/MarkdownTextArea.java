package se.alipsa.md2pdf.gui;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/** Syntax-highlighting code editor pre-configured for Markdown. */
public class MarkdownTextArea extends CodeTextArea {

  // Markdown patterns — order matters: more specific before more general
  private static final Pattern MD_PATTERN =
      Pattern.compile(
          "(?m)(?<HEADING>^#{1,6}\\s+[^\\n]*)" // # Heading
              + "|(?<CODEBLOCK>```[\\s\\S]*?```)" // ```code block```
              + "|(?<INLINECODE>`[^`\\n]+`)" // `inline code`
              + "|(?<BOLD>\\*{2}[^*\\n]+\\*{2}|_{2}[^_\\n]+_{2})" // **bold** or __bold__
              + "|(?<ITALIC>\\*[^*\\n]+\\*|_[^_\\n]+_)" // *italic* or _italic_
              + "|(?<LINK>!?\\[[^\\]]*\\]\\([^)]*\\))" // [link](url) or ![img](url)
              + "|(?<BLOCKQUOTE>(?m)^>\\s*[^\\n]*)" // > blockquote
              + "|(?<LISTITEM>(?m)^\\s*[-*+]\\s+|^\\s*\\d+\\.\\s+)" // - list or 1. ordered
              + "|(?<HRULE>(?m)^[-*_]{3,}\\s*$)" // --- or *** horizontal rule
          );

  /**
   * Creates the Markdown editor owned by the given tab.
   *
   * @param parentTab the tab that hosts this editor
   */
  public MarkdownTextArea(BaseTab parentTab) {
    super(parentTab);
  }

  @Override
  public void highlightSyntax() {
    if (getText().isEmpty()) return;
    setStyleSpans(0, computeHighlighting(getText()));
  }

  /**
   * Computes syntax-highlighting spans for the given Markdown text.
   *
   * @param text the raw Markdown string
   * @return a {@link StyleSpans} collection mapping character ranges to CSS class names
   */
  protected final StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = MD_PATTERN.matcher(text);
    int lastEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

    while (matcher.find()) {
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
      String styleClass = styleFor(matcher);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
    return spansBuilder.create();
  }

  private static String styleFor(Matcher m) {
    if (m.group("HEADING") != null) return "md_heading";
    if (m.group("CODEBLOCK") != null) return "md_codeblock";
    if (m.group("INLINECODE") != null) return "md_inlinecode";
    if (m.group("BOLD") != null) return "md_bold";
    if (m.group("ITALIC") != null) return "md_italic";
    if (m.group("LINK") != null) return "md_link";
    if (m.group("BLOCKQUOTE") != null) return "md_blockquote";
    if (m.group("LISTITEM") != null) return "md_listitem";
    if (m.group("HRULE") != null) return "md_hrule";
    return "default";
  }

  /**
   * Replaces the editor content without marking the parent tab as dirty.
   *
   * @param content the new Markdown text
   */
  public void setText(String content) {
    blockChange = true;
    replaceText(content);
    highlightSyntax();
    blockChange = false;
  }
}
