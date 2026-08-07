package se.alipsa.md2pdf.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.SplitPane;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import se.alipsa.md2pdf.Md2PdfEngine;
import se.alipsa.md2pdf.Md2PdfException;
import se.alipsa.md2pdf.gui.widgets.Alerts;
import se.alipsa.md2pdf.gui.widgets.ExceptionAlert;

/**
 * Tab containing the Markdown source editor alongside a live HTML preview. Drives PDF rendering via
 * {@link #renderPdf()} and {@link #renderPdf(File)}.
 */
public class MarkdownTab extends BaseTab {

  private final MarkdownTextArea markdownArea;
  private final WebView webView = new WebView();

  /**
   * Creates the Markdown editor tab with a live split-pane preview.
   *
   * @param gui the main application window
   */
  public MarkdownTab(MarkdownToPdf gui) {
    super(gui, "Markdown");
    setClosable(false);

    markdownArea = new MarkdownTextArea(this);
    markdownArea.setPadding(new Insets(5));
    VBox.setVgrow(markdownArea, Priority.ALWAYS);

    VBox editorBox = new VBox(markdownArea);
    VBox.setVgrow(editorBox, Priority.ALWAYS);
    wireDragAndDrop(editorBox);

    SplitPane splitPane = new SplitPane(editorBox, webView);
    splitPane.setDividerPositions(0.5);

    BorderPane root = new BorderPane(splitPane);
    setContent(root);

    // Auto-update preview 500 ms after typing stops
    markdownArea
        .plainTextChanges()
        .successionEnds(Duration.ofMillis(500))
        .subscribe(change -> Platform.runLater(this::refreshPreview));
  }

  private void wireDragAndDrop(VBox editorBox) {
    editorBox.setOnDragOver(
        event -> {
          if (isSingleMarkdownFile(event.getDragboard())) {
            event.acceptTransferModes(TransferMode.COPY);
          }
          event.consume();
        });
    editorBox.setOnDragDropped(this::handleDragDropped);
  }

  private boolean isSingleMarkdownFile(Dragboard dragboard) {
    if (!dragboard.hasFiles()) {
      return false;
    }
    List<File> files = dragboard.getFiles();
    if (files.size() != 1) {
      return false;
    }
    String name = files.get(0).getName().toLowerCase(Locale.ROOT);
    return name.endsWith(".md") || name.endsWith(".markdown");
  }

  private void handleDragDropped(DragEvent event) {
    Dragboard dragboard = event.getDragboard();
    boolean success = false;
    if (isSingleMarkdownFile(dragboard)) {
      File droppedFile = dragboard.getFiles().get(0);
      boolean canProceed =
          !isChanged()
              || Alerts.confirm(
                  "Unsaved changes",
                  "The markdown file has unsaved changes.",
                  "Discard changes and open the dropped file?");
      if (canProceed) {
        Path path = droppedFile.toPath();
        loadFile(path);
        gui.setProjectMarkdownFile(path);
        setStatus("Loaded " + path.getFileName());
        success = true;
      }
    } else {
      setStatus("Drop a single .md file to open it");
    }
    event.setDropCompleted(success);
    event.consume();
  }

  /** Re-renders the HTML preview using the active style profile. */
  public void refreshPreview() {
    String html = buildHtmlWithCurrentStyle();
    webView.getEngine().loadContent(html);
  }

  private String buildHtmlWithCurrentStyle() {
    String text = markdownArea.getText();
    if (text == null || text.isBlank()) {
      return "<html><body></body></html>";
    }
    try {
      StyleTab styleTab = gui.getStyleTab();
      String css = styleTab != null ? styleTab.getActiveCss() : null;
      Md2PdfEngine engine = new Md2PdfEngine();
      var job = engine.markdown(text);
      if (css != null && !css.isBlank()) {
        job = job.addCss(css);
      }
      return HtmlUtils.injectSyntaxHighlighting(job.toHtml());
    } catch (Exception e) {
      return "<html><body><pre style='color:red'>Preview error: "
          + e.getMessage()
          + "</pre></body></html>";
    }
  }

  /** Opens a file chooser and loads the selected Markdown file into the editor. */
  public void promptAndLoad() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Select Markdown file");
    fc.setInitialDirectory(gui.getProjectDir());
    fc.getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Markdown files", "*.md", "*.markdown"));
    File targetFile = fc.showOpenDialog(gui.getStage());
    if (targetFile != null) {
      try {
        markdownArea.setText(Files.readString(targetFile.toPath()));
        file = targetFile;
        setText(targetFile.getName());
        gui.setProjectMarkdownFile(targetFile.toPath());
        contentSaved();
      } catch (IOException e) {
        ExceptionAlert.showAlert("Failed to load Markdown file", e);
      }
    }
  }

  /**
   * Renders the current Markdown content to PDF bytes using the active style profile.
   *
   * @return the raw PDF bytes
   * @throws Md2PdfException if rendering fails
   */
  public byte[] renderPdf() throws Md2PdfException {
    StyleTab styleTab = gui.getStyleTab();
    String css = styleTab != null ? styleTab.getActiveCss() : null;
    var job = new Md2PdfEngine().markdown(markdownArea.getText());
    if (css != null && !css.isBlank()) {
      job = job.addCss(css);
    }
    return job.toPdf();
  }

  /**
   * Renders the current Markdown content to a PDF file using the active style profile.
   *
   * @param toFile the destination file
   * @throws Md2PdfException if rendering fails
   */
  public void renderPdf(File toFile) throws Md2PdfException {
    StyleTab styleTab = gui.getStyleTab();
    String css = styleTab != null ? styleTab.getActiveCss() : null;
    var job = new Md2PdfEngine().markdown(markdownArea.getText());
    if (css != null && !css.isBlank()) {
      job = job.addCss(css);
    }
    job.toPdf(toFile);
  }

  /**
   * Returns the current Markdown rendered to an HTML string with the active style applied.
   *
   * @return the HTML string
   * @throws Md2PdfException if rendering fails
   */
  public String renderHtml() throws Md2PdfException {
    return buildHtmlWithCurrentStyle();
  }

  /**
   * Loads a Markdown file into the editor without prompting the user.
   *
   * @param markdownFile the path to load, or {@code null} to do nothing
   */
  public void loadFile(Path markdownFile) {
    if (markdownFile == null) {
      return;
    }
    try {
      markdownArea.setText(Files.readString(markdownFile));
      Path fileName = markdownFile.getFileName();
      setTitle(fileName != null ? fileName.toString() : markdownFile.toString());
      setFile(markdownFile.toFile());
      contentSaved();
    } catch (IOException e) {
      ExceptionAlert.showAlert("Failed to load " + markdownFile, e);
    }
  }

  @Override
  public CodeTextArea getCodeArea() {
    return markdownArea;
  }

  @Override
  public void save() {
    if (file != null) {
      try {
        Files.writeString(file.toPath(), markdownArea.getText());
        setStatus("Saved " + file);
        gui.setProjectMarkdownFile(file.toPath());
        contentSaved();
      } catch (IOException e) {
        setStatus("Failed to write " + file);
        ExceptionAlert.showAlert("Failed to write " + file, e);
      }
    } else {
      FileChooser fc = new FileChooser();
      fc.setTitle("Save markdown");
      fc.setInitialDirectory(gui.getProjectDir());
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown files", "*.md"));
      String projectName = gui.getActiveProject() != null ? gui.getActiveProject().getName() : null;
      if (projectName != null) {
        fc.setInitialFileName(projectName + ".md");
      }
      File targetFile = fc.showSaveDialog(gui.getStage());
      if (targetFile != null) {
        Path filePath = targetFile.toPath();
        try {
          setStatus("Writing " + filePath.toAbsolutePath());
          Files.writeString(filePath, markdownArea.getText());
          setFile(targetFile);
          contentSaved();
          gui.setProjectMarkdownFile(file.toPath());
          setStatus("Saved " + file);
        } catch (IOException e) {
          setStatus("Failed to write " + file);
          ExceptionAlert.showAlert("Failed to write " + filePath, e);
        }
      }
    }
  }

  @Override
  public void setFile(File file) {
    super.setFile(file);
  }

  @Override
  public void clear() {
    file = null;
    setText(defaultTitle);
    markdownArea.clear();
    webView.getEngine().loadContent("<html><body></body></html>");
  }
}
