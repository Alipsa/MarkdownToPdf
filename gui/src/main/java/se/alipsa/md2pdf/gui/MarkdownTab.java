package se.alipsa.md2pdf.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import se.alipsa.md2pdf.Md2PdfException;
import se.alipsa.md2pdf.Md2PdfEngine;

import se.alipsa.md2pdf.gui.widgets.ExceptionAlert;
import se.alipsa.md2pdf.model.StyleProfile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class MarkdownTab extends BaseTab {

  private final MarkdownTextArea markdownArea;
  private final WebView webView = new WebView();

  public MarkdownTab(MarkdownToPdf gui) {
    super(gui, "Markdown");
    setClosable(false);

    markdownArea = new MarkdownTextArea(this);
    markdownArea.setPadding(new Insets(5));
    VBox.setVgrow(markdownArea, Priority.ALWAYS);

    VBox editorBox = new VBox(markdownArea);
    VBox.setVgrow(editorBox, Priority.ALWAYS);

    SplitPane splitPane = new SplitPane(editorBox, webView);
    splitPane.setDividerPositions(0.5);

    BorderPane root = new BorderPane(splitPane);
    setContent(root);

    // Auto-update preview 500 ms after typing stops
    markdownArea.plainTextChanges()
        .successionEnds(Duration.ofMillis(500))
        .subscribe(change -> Platform.runLater(this::refreshPreview));
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
      StyleProfile profile = gui.getStyleTab() != null ? gui.getStyleTab().getActiveProfile() : null;
      Md2PdfEngine engine = new Md2PdfEngine();
      var job = engine.markdown(text);
      if (profile != null) {
        job = job.addCss(profile.toCss());
      }
      return job.toHtml();
    } catch (Exception e) {
      return "<html><body><pre style='color:red'>Preview error: " + e.getMessage() + "</pre></body></html>";
    }
  }

  public void promptAndLoad() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Select Markdown file");
    fc.setInitialDirectory(gui.getProjectDir());
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown files", "*.md", "*.markdown"));
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

  public byte[] renderPdf() throws Md2PdfException {
    StyleProfile profile = gui.getStyleTab() != null ? gui.getStyleTab().getActiveProfile() : null;
    var job = new Md2PdfEngine().markdown(markdownArea.getText());
    if (profile != null) {
      job = job.addCss(profile.toCss());
    }
    return job.toPdf();
  }

  public void renderPdf(File toFile) throws Md2PdfException {
    StyleProfile profile = gui.getStyleTab() != null ? gui.getStyleTab().getActiveProfile() : null;
    var job = new Md2PdfEngine().markdown(markdownArea.getText());
    if (profile != null) {
      job = job.addCss(profile.toCss());
    }
    job.toPdf(toFile);
  }

  public String renderHtml() throws Md2PdfException {
    return buildHtmlWithCurrentStyle();
  }

  public void loadFile(Path markdownFile) {
    if (markdownFile == null) {
      return;
    }
    try {
      markdownArea.setText(Files.readString(markdownFile));
      setTitle(markdownFile.getFileName().toString());
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
