package se.alipsa.md2pdf.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import se.alipsa.md2pdf.Md2PdfEngine;
import se.alipsa.md2pdf.gui.widgets.ExceptionAlert;
import se.alipsa.md2pdf.model.StyleProfile;
import se.alipsa.md2pdf.model.StyleProfileManager;

/**
 * Tab hosting the visual {@link StyleEditorPanel} alongside a live HTML preview. A "Edit CSS"
 * toggle switches to a {@link CssTextArea} for direct CSS editing — useful for power users who want
 * to write CSS rules not exposed in the visual controls.
 */
public class StyleTab extends BaseTab {

  private static final String SAMPLE_MARKDOWN =
      "# Heading 1\n\n"
          + "A paragraph with **bold text**, *italic text*, and a [link example](#).\n\n"
          + "## Heading 2\n\n"
          + "> A blockquote demonstrating border and text colour styling.\n\n"
          + "### Heading 3\n\n"
          + "- First list item\n"
          + "- Second list item\n\n"
          + "Inline `code` and a preformatted block:\n\n"
          + "```groovy\n"
          + "def hello(String name) {\n"
          + "    println \"Hello, ${name}!\"\n"
          + "}\n"
          + "```\n";

  private final StyleEditorPanel editorPanel;
  private final StyleProfileManager profileManager;
  private final WebView previewView = new WebView();
  private final CssTextArea cssEditor;
  private final SplitPane split = new SplitPane();
  private final ToggleButton editCssButton = new ToggleButton("Edit CSS");
  private boolean cssMode = false;
  private Runnable externalCallback = () -> {};

  /**
   * Creates the Style tab with the visual editor, CSS editor, and live preview.
   *
   * @param gui the main application window
   * @param profileManager the profile manager used to load and save style profiles
   */
  public StyleTab(MarkdownToPdf gui, StyleProfileManager profileManager) {
    super(gui, "Style");
    setClosable(false);
    this.profileManager = profileManager;
    this.editorPanel = new StyleEditorPanel(profileManager);
    this.cssEditor = new CssTextArea(this);

    // Start in visual mode
    split.getItems().setAll(editorPanel, previewView);
    split.setDividerPositions(0.45);

    // CSS editor: debounced refresh 500 ms after typing stops
    cssEditor
        .plainTextChanges()
        .successionEnds(Duration.ofMillis(500))
        .subscribe(
            change ->
                Platform.runLater(
                    () -> {
                      refreshStylePreview(cssEditor.getText());
                      externalCallback.run();
                    }));

    Button loadCssButton = new Button("Load CSS…");
    loadCssButton.setOnAction(e -> loadCssFile());
    editCssButton.setOnAction(e -> toggleCssMode());
    HBox toolbar = new HBox(8, loadCssButton, editCssButton);
    toolbar.setAlignment(Pos.CENTER_RIGHT);
    toolbar.setPadding(new Insets(4, 8, 4, 4));

    BorderPane wrapper = new BorderPane();
    wrapper.setTop(toolbar);
    wrapper.setCenter(split);
    setContent(wrapper);

    // Visual editor: fire preview + external callback on any control change
    editorPanel.setOnProfileChanged(
        p -> {
          refreshStylePreview(p.toCss());
          externalCallback.run();
        });

    refreshStylePreview(profileManager.getBuiltin("Default").toCss());
  }

  private void toggleCssMode() {
    cssMode = editCssButton.isSelected();
    if (cssMode) {
      editCssButton.setText("Visual Editor");
      cssEditor.setText(editorPanel.buildProfile().toCss());
      split.getItems().setAll(cssEditor, previewView);
      split.setDividerPositions(0.5);
    } else {
      editCssButton.setText("Edit CSS");
      StyleProfile parsed = StyleProfile.fromCss(cssEditor.getText());
      parsed.setName(editorPanel.buildProfile().getName());
      editorPanel.applyProfile(parsed);
      split.getItems().setAll(editorPanel, previewView);
      split.setDividerPositions(0.45);
      refreshStylePreview(editorPanel.buildProfile().toCss());
      externalCallback.run();
    }
  }

  private void loadCssFile() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Load CSS file");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSS files", "*.css"));
    File f = fc.showOpenDialog(getTabPane() != null ? getTabPane().getScene().getWindow() : null);
    if (f == null) return;
    try {
      String css = Files.readString(f.toPath());
      if (!cssMode) {
        editCssButton.setSelected(true);
        cssMode = true;
        editCssButton.setText("Visual Editor");
        split.getItems().setAll(cssEditor, previewView);
        split.setDividerPositions(0.5);
      }
      cssEditor.setText(css);
      refreshStylePreview(css);
      externalCallback.run();
    } catch (IOException ex) {
      ExceptionAlert.showAlert("Failed to load CSS file", ex);
    }
  }

  /**
   * Returns the CSS to use for rendering. In CSS mode this is the raw editor text; in visual mode
   * it is the profile's generated CSS.
   *
   * @return the active CSS
   */
  public String getActiveCss() {
    return cssMode ? cssEditor.getText() : editorPanel.buildProfile().toCss();
  }

  /**
   * Returns the active visual profile, or {@code null} when in CSS mode.
   *
   * @return the active visual profile, or {@code null} in CSS mode
   */
  public StyleProfile getActiveProfile() {
    return cssMode ? null : editorPanel.buildProfile();
  }

  /**
   * Loads the named profile into the visual editor (and into the CSS editor if open).
   *
   * @param name the profile name
   */
  public void applyProfile(String name) {
    try {
      StyleProfile p = profileManager.load(name);
      if (p != null) {
        editorPanel.applyProfile(p);
        if (cssMode) {
          cssEditor.setText(p.toCss());
        }
        refreshStylePreview(p.toCss());
      }
    } catch (IOException ex) {
      ExceptionAlert.showAlert("Failed to load style profile '" + name + "'", ex);
    }
  }

  /**
   * Sets the callback fired whenever any style change occurs — whether from a visual control or a
   * CSS editor edit.
   *
   * @param callback the callback to invoke after a style change
   */
  public void setOnProfileChanged(Runnable callback) {
    this.externalCallback = callback == null ? () -> {} : callback;
  }

  private void refreshStylePreview(String css) {
    try {
      String html =
          new Md2PdfEngine().markdown(SAMPLE_MARKDOWN).addCss(css != null ? css : "").toHtml();
      previewView.getEngine().loadContent(HtmlUtils.injectSyntaxHighlighting(html));
    } catch (Exception e) {
      previewView
          .getEngine()
          .loadContent(
              "<html><body><pre style='color:red'>Preview error: "
                  + e.getMessage()
                  + "</pre></body></html>");
    }
  }

  // BaseTab boilerplate — StyleTab has no file to load/save

  @Override
  public CodeTextArea getCodeArea() {
    return cssMode ? cssEditor : null;
  }

  @Override
  public void promptAndLoad() {}

  @Override
  public void save() {}

  @Override
  public void clear() {
    StyleProfile def = profileManager.getBuiltin("Default");
    editorPanel.applyProfile(def);
    if (cssMode) {
      cssEditor.setText(def.toCss());
    }
    refreshStylePreview(def.toCss());
  }
}
