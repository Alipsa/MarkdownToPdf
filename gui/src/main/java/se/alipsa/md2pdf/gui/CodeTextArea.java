package se.alipsa.md2pdf.gui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.scene.control.IndexRange;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;

/**
 * Abstract base for syntax-highlighted code editors. Handles indentation, find shortcut wiring, and
 * dirty-state tracking via the parent {@link BaseTab}.
 */
public abstract class CodeTextArea extends CodeArea {

  /** Two-space indent string used by the tab key handler. */
  public static final String INDENT = "  ";

  private final Pattern whiteSpace = Pattern.compile("^\\s+");

  /**
   * When {@code true}, text changes do not mark the parent tab as dirty. Set around programmatic
   * loads so that reading from a file is not treated as a user edit.
   */
  protected boolean blockChange = false;

  /** The tab that owns this editor; used to mark dirty state and open the find dialog. */
  protected BaseTab parentTab;

  /**
   * Constructs the editor, wires indentation key bindings, and subscribes to text changes.
   *
   * @param parentTab the tab that owns this editor
   */
  protected CodeTextArea(BaseTab parentTab) {
    this.parentTab = parentTab;
    getStylesheets().clear();
    getStyleClass().add("styled-text-area");
    getStyleClass().add("code-area");
    getStyleClass().add("codeTextArea");
    setUseInitialStyleForInsertion(true);
    setParagraphGraphicFactory(LineNumberFactory.get(this));
    multiPlainChanges()
        // do not emit an event until 400 ms have passed since the last emission of previous stream
        .successionEnds(Duration.ofMillis(400))
        // run the following code block when previous stream emits an event
        .subscribe(ignore -> highlightSyntax());

    InputMap<KeyEvent> im =
        InputMap.consume(
            EventPattern.keyPressed(KeyCode.TAB),
            e -> {
              String selected = selectedTextProperty().getValue();
              if (!"".equals(selected)) {
                IndexRange range = getSelection();
                int start = range.getStart();
                String s = indentText(selected);
                replaceSelection(s);
                selectRange(start, start + s.length());
              } else {
                String line = getText(getCurrentParagraph());
                int orgPos = getCaretPosition();
                moveTo(getCurrentParagraph(), 0);
                int start = getCaretPosition();
                int end = start + line.length();
                replaceText(start, end, INDENT + line);
                moveTo(orgPos + INDENT.length());
              }
            });
    Nodes.addInputMap(this, im);
    addEventHandler(
        KeyEvent.KEY_PRESSED,
        e -> {
          KeyCode keyCode = e.getCode();
          if (e.isControlDown() || e.isMetaDown()) {
            if (KeyCode.F.equals(keyCode)) {
              parentTab.gui.displayFind();
            }
          } else if (e.isShiftDown() && KeyCode.TAB.equals(keyCode)) {
            String selected = selectedTextProperty().getValue();
            if (!"".equals(selected)) {
              IndexRange range = getSelection();
              int start = range.getStart();
              String s = backIndentText(selected);
              replaceSelection(s);
              selectRange(start, start + s.length());
            } else {
              String line = getText(getCurrentParagraph());
              int orgPos = getCaretPosition();
              moveTo(getCurrentParagraph(), 0);
              int start = getCaretPosition();
              int end = start + line.length();
              if (line.startsWith(INDENT)) {
                replaceText(start, end, backIndentText(line));
                moveTo(orgPos - INDENT.length());
              }
            }
          } else if (KeyCode.ENTER.equals(keyCode)) {
            // Maintain indentation from the previous line
            Matcher m =
                whiteSpace.matcher(getParagraph(getCurrentParagraph() - 1).getSegments().get(0));
            if (m.find()) {
              Platform.runLater(() -> insertText(getCaretPosition(), m.group()));
            }
          }
        });

    plainTextChanges()
        .subscribe(
            ptc -> {
              if (parentTab != null && !parentTab.isChanged() && !blockChange) {
                parentTab.contentChanged();
              }
            });
  }

  /** Applies syntax-highlighting style spans to the current text content. */
  public abstract void highlightSyntax();

  /**
   * Indent (add space) to the selected area e.g. when pressing the tab button
   *
   * @param selected the text to indent
   * @return the indented text
   */
  protected String indentText(String selected) {
    if (selected == null || "".equals(selected)) {
      return INDENT;
    }
    String[] lines = selected.split("\n");
    List<String> tabbed = new ArrayList<>();
    for (String line : lines) {
      tabbed.add(INDENT + line);
    }
    return String.join("\n", tabbed);
  }

  /**
   * Removes one indent level from each line in {@code selected}.
   *
   * @param selected the text to un-indent
   * @return the un-indented text
   */
  protected String backIndentText(String selected) {
    String[] lines = selected.split("\n");
    List<String> untabbed = new ArrayList<>();
    for (String line : lines) {
      if (line.startsWith(INDENT)) {
        untabbed.add(line.substring(2));
      } else {
        untabbed.add(line);
      }
    }
    return String.join("\n", untabbed);
  }

  @Override
  public void clear() {
    blockChange = true;
    super.clear();
    blockChange = false;
  }
}
