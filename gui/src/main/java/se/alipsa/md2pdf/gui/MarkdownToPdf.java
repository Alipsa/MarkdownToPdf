package se.alipsa.md2pdf.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.FileAppender;
import se.alipsa.md2pdf.Md2PdfException;
import se.alipsa.md2pdf.gui.widgets.Alerts;
import se.alipsa.md2pdf.gui.widgets.ExceptionAlert;
import se.alipsa.md2pdf.model.Project;
import se.alipsa.md2pdf.model.StyleProfileManager;

/**
 * Main JavaFX application class. Builds the primary window with a Markdown editor tab, a Style tab,
 * and a PDF output tab; wires project and style management.
 */
public class MarkdownToPdf extends Application {

  private static JWindow splashWindow;

  private final DateTimeFormatter dateFormat =
      DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm:ss");
  private final DateTimeFormatter isoFormat = DateTimeFormatter.ISO_DATE_TIME;

  private PDFViewer pdfViewer;
  MarkdownTab markdownTab;
  StyleTab styleTab;
  Tab pdfTab;
  private final TabPane tabPane = new TabPane();

  private Stage searchWindow;
  private final TextField statusField = new TextField();
  private Stage stage;
  private Scene scene;
  private final ComboBox<Project> projectCombo = new ComboBox<>();
  private ComboBox<String> styleCombo;
  private Button viewPdfButton;
  private Button viewExternalButton;

  private final StyleProfileManager profileManager = new StyleProfileManager();
  private static Image appIcon;
  private static URL styleSheetUrl;
  private final List<String> searchStrings = new UniqueList<>();

  /** Creates the JavaFX application instance. */
  public MarkdownToPdf() {}

  /**
   * Application entry point.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    showStartupSplash();
    try {
      launch(args);
    } finally {
      hideStartupSplash();
    }
  }

  @Override
  public void start(Stage primaryStage) {
    this.stage = primaryStage;
    showMainWindow(primaryStage);
  }

  private static void showStartupSplash() {
    if (GraphicsEnvironment.isHeadless()) {
      return;
    }

    try {
      SwingUtilities.invokeAndWait(MarkdownToPdf::createAndShowStartupSplash);
    } catch (Exception ignored) {
      // Splash is best-effort; JavaFX startup should continue even if Swing is unavailable.
    }
  }

  private static void createAndShowStartupSplash() {
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setBackground(new Color(247, 249, 251));
    content.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 217, 226)),
            BorderFactory.createEmptyBorder(28, 36, 28, 36)));

    URL logoUrl = MarkdownToPdf.class.getResource("/MarkdownToPdf-rounded.png");
    if (logoUrl != null) {
      ImageIcon icon = new ImageIcon(logoUrl);
      java.awt.Image scaledImage =
          icon.getImage().getScaledInstance(96, 96, java.awt.Image.SCALE_SMOOTH);
      JLabel logoLabel = new JLabel(new ImageIcon(scaledImage));
      logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
      content.add(logoLabel);
      content.add(Box.createRigidArea(new Dimension(0, 14)));
    }

    JLabel title = new JLabel("MarkdownToPdf");
    title.setAlignmentX(Component.CENTER_ALIGNMENT);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
    title.setForeground(new Color(31, 41, 55));

    JLabel message = new JLabel("Starting editor...");
    message.setAlignmentX(Component.CENTER_ALIGNMENT);
    message.setFont(message.getFont().deriveFont(13f));
    message.setForeground(new Color(75, 85, 99));

    JProgressBar progress = new JProgressBar();
    progress.setAlignmentX(Component.CENTER_ALIGNMENT);
    progress.setIndeterminate(true);
    progress.setMaximumSize(new Dimension(160, 10));
    progress.setPreferredSize(new Dimension(160, 10));

    content.add(title);
    content.add(Box.createRigidArea(new Dimension(0, 8)));
    content.add(message);
    content.add(Box.createRigidArea(new Dimension(0, 18)));
    content.add(progress);

    splashWindow = new JWindow();
    splashWindow.setContentPane(content);
    splashWindow.pack();
    splashWindow.setLocationRelativeTo(null);
    splashWindow.setAlwaysOnTop(true);
    splashWindow.setVisible(true);
  }

  private static void hideStartupSplash() {
    if (splashWindow == null) {
      return;
    }

    SwingUtilities.invokeLater(
        () -> {
          splashWindow.setVisible(false);
          splashWindow.dispose();
          splashWindow = null;
        });
  }

  private static Logger logger() {
    return LoggerHolder.LOGGER;
  }

  private static class LoggerHolder {
    private static final Logger LOGGER = LogManager.getLogger(MarkdownToPdf.class);
  }

  private void showMainWindow(Stage primaryStage) {
    markdownTab = new MarkdownTab(this);
    styleTab = new StyleTab(this, profileManager);
    pdfTab = createPdfTab();

    // Wire style changes → preview refresh
    styleTab.setOnProfileChanged(markdownTab::refreshPreview);

    tabPane.getTabs().addAll(markdownTab, styleTab, pdfTab);

    BorderPane root = new BorderPane();
    root.setCenter(tabPane);
    statusField.setDisable(true);
    root.setBottom(statusField);

    HBox topBox = new HBox();
    MenuBar menuBar = createMenu();
    topBox.getChildren().add(menuBar);
    topBox.getChildren().add(createProjectBar());
    HBox.setHgrow(menuBar, Priority.ALWAYS);
    root.setTop(topBox);

    scene = new Scene(root, 1100, 820);
    scene.getStylesheets().add(getStyleSheet().toExternalForm());
    getLogo();

    primaryStage.setOnCloseRequest(
        t -> {
          if (markdownTab.isChanged()) {
            boolean exitAnyway =
                Alerts.confirm(
                    "Unsaved changes",
                    "The markdown file has unsaved changes.",
                    "Exit without saving?");
            if (!exitAnyway) {
              t.consume();
              return;
            }
          }
          endProgram();
        });

    if (appIcon != null) {
      primaryStage.getIcons().add(appIcon);
    }
    if (Taskbar.isTaskbarSupported()) {
      var taskbar = Taskbar.getTaskbar();
      if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
        var dockIcon =
            Toolkit.getDefaultToolkit()
                .getImage(getClass().getResource("/MarkdownToPdf-rounded.png"));
        taskbar.setIconImage(dockIcon);
      }
    }
    primaryStage.setResizable(true);
    primaryStage.setTitle("MarkdownToPdf");
    primaryStage.setScene(scene);
    primaryStage.show();
    hideStartupSplash();
  }

  /**
   * Returns the application icon image, loading it from resources on first call.
   *
   * @return the icon {@link Image}, or {@code null} if the resource is missing
   */
  public static Image getLogo() {
    if (appIcon == null) {
      try (InputStream is = MarkdownToPdf.class.getResourceAsStream("/MarkdownToPdf-rounded.png")) {
        appIcon = is == null ? null : new Image(is);
      } catch (IOException e) {
        logger().warn("Failed to load app icon", e);
      }
    }
    return appIcon;
  }

  /**
   * Returns the URL of the application stylesheet, loading it from resources on first call.
   *
   * @return the stylesheet {@link URL}
   */
  public static URL getStyleSheet() {
    if (styleSheetUrl == null) {
      styleSheetUrl = MarkdownToPdf.class.getResource("/default-theme.css");
    }
    return styleSheetUrl;
  }

  /** Exits the JavaFX application and terminates the process shortly after shutdown. */
  public void endProgram() {
    Platform.exit();
    Timer timer = new Timer();
    timer.schedule(
        new TimerTask() {
          public void run() {
            System.exit(0);
          }
        },
        200);
  }

  // ── Project bar ────────────────────────────────────────────────────────────

  private Node createProjectBar() {
    HBox hbox = new HBox(6);
    hbox.setAlignment(Pos.CENTER_LEFT);
    hbox.setPadding(new Insets(3, 4, 0, 4));
    hbox.setStyle("-fx-border-color: lightgray");

    viewPdfButton = new Button("View PDF");
    viewPdfButton.setOnAction(a -> run());

    viewExternalButton = new Button("View external");
    viewExternalButton.setTooltip(new Tooltip("Open PDF in default system viewer"));
    viewExternalButton.setOnAction(a -> viewExternal());

    Label projectLabel = new Label("Project:");
    projectLabel.setPadding(new Insets(4, 0, 0, 8));

    try {
      populateProjectCombo(projectCombo);
    } catch (Exception e) {
      ExceptionAlert.showAlert("Failed to load projects from preferences", e);
    }
    projectCombo.setOnAction(
        a -> {
          Project p = projectCombo.getValue();
          if (p != null) setActiveProject(p);
        });

    Label styleLabel = new Label("Style:");
    styleLabel.setPadding(new Insets(4, 0, 0, 8));

    styleCombo = new ComboBox<>();
    styleCombo.getItems().addAll(profileManager.listNames());
    styleCombo.setValue("Default");
    styleCombo.setOnAction(
        a -> {
          String name = styleCombo.getValue();
          if (name != null) {
            styleTab.applyProfile(name);
            markdownTab.refreshPreview();
          }
        });

    hbox.getChildren()
        .addAll(
            viewPdfButton, viewExternalButton,
            projectLabel, projectCombo,
            styleLabel, styleCombo);
    return hbox;
  }

  private MenuBar createMenu() {
    MenuBar menuBar = new MenuBar();
    menuBar.setPadding(new Insets(5));

    // File menu
    Menu fileMenu = new Menu("File");
    MenuItem newMi = new MenuItem("New");
    newMi.setOnAction(a -> newDocument());
    MenuItem openMi = new MenuItem("Open…");
    openMi.setOnAction(a -> markdownTab.promptAndLoad());
    MenuItem saveMi = new MenuItem("Save");
    saveMi.setOnAction(a -> markdownTab.save());
    MenuItem saveAsMi = new MenuItem("Save As…");
    saveAsMi.setOnAction(
        a -> {
          markdownTab.setFile((File) null);
          markdownTab.save();
        });
    MenuItem exportHtmlMi = new MenuItem("Export HTML…");
    exportHtmlMi.setOnAction(a -> exportHtml());
    MenuItem exportPdfMi = new MenuItem("Export PDF…");
    exportPdfMi.setOnAction(a -> exportPdf());
    fileMenu
        .getItems()
        .addAll(
            newMi,
            openMi,
            saveMi,
            saveAsMi,
            new SeparatorMenuItem(),
            exportHtmlMi,
            exportPdfMi,
            new SeparatorMenuItem(),
            new MenuItem("Exit") {
              {
                setOnAction(
                    e -> {
                      if (!markdownTab.isChanged()
                          || Alerts.confirm("Exit", "Unsaved changes", "Exit without saving?")) {
                        endProgram();
                      }
                    });
              }
            });

    // Project menu
    Menu projectMenu = new Menu("Project");
    MenuItem newProjectMi = new MenuItem("New project…");
    newProjectMi.setOnAction(a -> createProject());
    MenuItem openProjectMi = new MenuItem("Open project…");
    openProjectMi.setOnAction(a -> openProject());
    MenuItem saveProjectMi = new MenuItem("Save project");
    saveProjectMi.setOnAction(
        a -> {
          if (getActiveProject() != null) {
            try {
              saveProject(getActiveProject());
            } catch (IOException e) {
              ExceptionAlert.showAlert("Failed to save project", e);
            }
          }
        });
    projectMenu.getItems().addAll(newProjectMi, openProjectMi, saveProjectMi);

    // Edit menu
    Menu editMenu = new Menu("Edit");
    MenuItem undoMi = new MenuItem("Undo  Ctrl+Z");
    undoMi.setOnAction(a -> undo());
    MenuItem redoMi = new MenuItem("Redo  Ctrl+Y");
    redoMi.setOnAction(a -> redo());
    MenuItem findMi = new MenuItem("Find  Ctrl+F");
    findMi.setOnAction(a -> displayFind());
    editMenu.getItems().addAll(undoMi, redoMi, new SeparatorMenuItem(), findMi);

    // Help menu
    Menu helpMenu = new Menu("Help");
    MenuItem viewLogMi = new MenuItem("View log file");
    viewLogMi.setOnAction(this::viewLogFile);
    MenuItem aboutMi = new MenuItem("About");
    aboutMi.setOnAction(a -> showAbout());
    helpMenu.getItems().addAll(viewLogMi, aboutMi);

    menuBar.getMenus().addAll(fileMenu, projectMenu, editMenu, helpMenu);
    return menuBar;
  }

  // ── Document operations ────────────────────────────────────────────────────

  private void newDocument() {
    if (markdownTab.isChanged()) {
      boolean proceed =
          Alerts.confirm(
              "New document",
              "The current file has unsaved changes.",
              "Discard changes and create a new document?");
      if (!proceed) return;
    }
    markdownTab.clear();
    setStatus("New document");
  }

  private void exportHtml() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Export HTML");
    fc.setInitialDirectory(getProjectDir());
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML files", "*.html"));
    File file = fc.showSaveDialog(stage);
    if (file != null) {
      scene.setCursor(Cursor.WAIT);
      try {
        String html = markdownTab.renderHtml();
        Files.writeString(file.toPath(), html);
        setStatus("Exported HTML to " + file.getName());
      } catch (Md2PdfException | IOException e) {
        ExceptionAlert.showAlert("Failed to export HTML", e);
      } finally {
        scene.setCursor(Cursor.DEFAULT);
      }
    }
  }

  private void exportPdf() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Export PDF");
    fc.setInitialDirectory(getProjectDir());
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
    File file = fc.showSaveDialog(stage);
    if (file != null) {
      scene.setCursor(Cursor.WAIT);
      try {
        markdownTab.renderPdf(file);
        setStatus("Exported PDF to " + file.getName());
      } catch (Md2PdfException e) {
        ExceptionAlert.showAlert("Failed to export PDF", e);
      } finally {
        scene.setCursor(Cursor.DEFAULT);
      }
    }
  }

  // ── PDF rendering ──────────────────────────────────────────────────────────

  void run() {
    scene.setCursor(Cursor.WAIT);
    try {
      byte[] pdf = markdownTab.renderPdf();
      pdfViewer.load(pdf);
      tabPane.getSelectionModel().select(pdfTab);
    } catch (Md2PdfException | IOException e) {
      ExceptionAlert.showAlert("Failed to render PDF", e);
    } finally {
      scene.setCursor(Cursor.DEFAULT);
    }
  }

  void viewExternal() {
    scene.setCursor(Cursor.WAIT);
    try {
      File tmpFile = File.createTempFile("md2pdf_", ".pdf");
      markdownTab.renderPdf(tmpFile);
      openInExternalApp(tmpFile);
      tmpFile.deleteOnExit();
    } catch (IOException | Md2PdfException e) {
      ExceptionAlert.showAlert("Failed to render PDF", e);
    } finally {
      scene.setCursor(Cursor.DEFAULT);
    }
  }

  private void openInExternalApp(File file) {
    Task<Void> task =
        new Task<>() {
          @Override
          protected Void call() throws Exception {
            Desktop.getDesktop().open(file);
            return null;
          }
        };
    task.setOnFailed(e -> ExceptionAlert.showAlert("Failed to open " + file, task.getException()));
    new Thread(task).start();
  }

  // ── Project management ─────────────────────────────────────────────────────

  private void createProject() {
    TextInputDialog dialog = new TextInputDialog("My Project");
    dialog.setTitle("New Project");
    dialog.setHeaderText("Create a new project");
    dialog.setContentText("Project name:");
    Optional<String> result = dialog.showAndWait();
    result.ifPresent(
        name -> {
          if (name.isBlank()) return;
          FileChooser fc = new FileChooser();
          fc.setTitle("Save project file");
          fc.setInitialDirectory(getProjectDir());
          fc.setInitialFileName(name + ".jpr");
          fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Project files", "*.jpr"));
          File projectFile = fc.showSaveDialog(stage);
          if (projectFile != null) {
            Project p = new Project();
            p.setName(name);
            String styleName = styleCombo != null ? styleCombo.getValue() : "Default";
            p.setStyleProfileName(styleName);
            try {
              saveProject(p, projectFile.toPath().toString());
              projectCombo.getItems().add(p);
              projectCombo.setValue(p);
            } catch (IOException e) {
              ExceptionAlert.showAlert("Failed to save project", e);
            }
          }
        });
  }

  private void openProject() {
    FileChooser fc = new FileChooser();
    fc.setInitialDirectory(getProjectDir());
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Project files", "*.jpr"));
    File projectFile = fc.showOpenDialog(stage);
    if (projectFile != null) {
      try {
        Project p = Project.load(projectFile.toPath());
        projectCombo.getItems().add(p);
        projectCombo.setValue(p);
        Preferences projects = preferences().node("projects");
        projects.node(p.getName()).put("projectFile", projectFile.toPath().toString());
        projects.flush();
        setActiveProject(p);
      } catch (Exception e) {
        ExceptionAlert.showAlert("Failed to load " + projectFile, e);
      }
    }
  }

  private void setActiveProject(Project p) {
    logger().info("Activating project: {}", p.getName());
    markdownTab.loadFile(p.getMarkdownFile());
    String styleName = p.getStyleProfileName();
    if (styleName != null && !styleName.isBlank()) {
      if (styleCombo != null) styleCombo.setValue(styleName);
      styleTab.applyProfile(styleName);
      markdownTab.refreshPreview();
    }
    Preferences projects = preferences().node("projects");
    String path = projects.node(p.getName()).get("projectFile", null);
    if (path != null) {
      setProjectDir(new File(path).getParentFile());
      projectCombo.setTooltip(new Tooltip(path));
    }
  }

  void populateProjectCombo(ComboBox<Project> projectCombo)
      throws BackingStoreException, IOException {
    Preferences projects = preferences().node("projects");
    ObservableList<Project> list = projectCombo.getItems();
    for (String name : projects.childrenNames()) {
      String path = projects.node(name).get("projectFile", null);
      if (path == null) {
        projects.node(name).removeNode();
        continue;
      }
      Path projectFilePath = Paths.get(path);
      if (Files.exists(projectFilePath)) {
        try {
          list.add(Project.load(projectFilePath));
        } catch (Exception e) {
          ExceptionAlert.showAlert("Failed to load project from " + projectFilePath, e);
        }
      } else {
        logger().info("{} does not exist, removing preference", projectFilePath);
        projects.node(name).removeNode();
      }
    }
  }

  void saveProject(Project p, String path) throws IOException {
    Preferences projects = preferences().node("projects");
    projects.node(p.getName()).put("projectFile", path);
    Project.save(p, Paths.get(path));
  }

  void saveProject(Project p) throws IOException {
    Preferences projects = preferences().node("projects");
    String projectFilePref = projects.node(p.getName()).get("projectFile", null);
    Path projectFilePath;
    if (projectFilePref == null) {
      FileChooser fc = new FileChooser();
      fc.setTitle("Save project file");
      fc.setInitialDirectory(getProjectDir());
      fc.setInitialFileName(p.getName() + ".jpr");
      File file = fc.showSaveDialog(stage);
      if (file == null) return;
      projectFilePath = file.toPath();
    } else {
      projectFilePath = Paths.get(projectFilePref);
    }
    // Capture current style profile name before saving
    if (styleCombo != null && styleCombo.getValue() != null) {
      p.setStyleProfileName(styleCombo.getValue());
    }
    Project.save(p, projectFilePath);
    projects.node(p.getName()).put("projectFile", projectFilePath.toString());
  }

  // ── PDF output tab ─────────────────────────────────────────────────────────

  private Tab createPdfTab() {
    Tab tab = new Tab("PDF output");
    tab.setClosable(false);
    BorderPane root = new BorderPane();

    VBox buttonPane = new VBox(5);
    buttonPane.setPadding(new Insets(5));
    buttonPane.setAlignment(Pos.BASELINE_LEFT);

    Button reloadButton = new Button("Reload");
    reloadButton.setOnAction(a -> run());
    Button saveButton = new Button("Save…");
    saveButton.setOnAction(
        a -> {
          FileChooser fc = new FileChooser();
          fc.setInitialDirectory(getProjectDir());
          fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
          File file = fc.showSaveDialog(stage);
          if (file != null) {
            try {
              writeToFile(file, pdfViewer.getContent());
            } catch (IOException e) {
              ExceptionAlert.showAlert("Failed to save " + file, e);
            }
          }
        });
    buttonPane.getChildren().addAll(reloadButton, saveButton);
    root.setLeft(buttonPane);

    pdfViewer = new PDFViewer(this);
    root.setCenter(pdfViewer);
    tab.setContent(root);
    return tab;
  }

  // ── Edit operations ────────────────────────────────────────────────────────

  private void undo() {
    CodeTextArea area = markdownTab.getCodeArea();
    if (area != null) area.undo();
  }

  private void redo() {
    CodeTextArea area = markdownTab.getCodeArea();
    if (area != null) area.redo();
  }

  /** Opens the Find dialog, or brings it to front if already open. */
  public void displayFind() {
    if (searchWindow != null) {
      searchWindow.toFront();
      searchWindow.requestFocus();
      return;
    }

    VBox vBox = new VBox();
    vBox.setPadding(new Insets(3));
    FlowPane pane = new FlowPane();
    vBox.getChildren().add(pane);
    Label resultLabel = new Label();
    resultLabel.setPadding(new Insets(1));
    vBox.getChildren().add(resultLabel);
    pane.setPadding(new Insets(5));
    pane.setHgap(5);
    pane.setVgap(5);

    Button findButton = new Button("Find");
    ComboBox<String> searchInput = new ComboBox<>();
    searchInput.setEditable(true);
    searchInput.setOnKeyPressed(
        e -> {
          if (e.getCode() == KeyCode.ENTER) findButton.fire();
        });
    if (!searchStrings.isEmpty()) {
      searchStrings.forEach(s -> searchInput.getItems().add(s));
      searchInput.setValue(searchStrings.get(searchStrings.size() - 1));
    }

    findButton.setOnAction(
        e -> {
          CodeTextArea codeArea = markdownTab.getCodeArea();
          if (codeArea == null) {
            resultLabel.setText("No active editor");
            return;
          }
          int caretPos = codeArea.getCaretPosition();
          String text = codeArea.getText().substring(caretPos);
          String searchWord = searchInput.getValue();
          if (searchWord == null) searchWord = searchInput.getEditor().getText();
          if (searchWord == null || searchWord.isBlank()) {
            resultLabel.setText("Nothing to search for");
            return;
          }
          searchStrings.add(searchWord);
          if (!searchInput.getItems().contains(searchWord)) searchInput.getItems().add(searchWord);
          if (text.contains(searchWord)) {
            int place = text.indexOf(searchWord);
            codeArea.moveTo(place);
            codeArea.selectRange(caretPos + place, caretPos + place + searchWord.length());
            codeArea.requestFollowCaret();
            resultLabel.setText("Found on line " + (codeArea.getCurrentParagraph() + 1));
          } else {
            resultLabel.setText("\"" + searchWord + "\" not found");
          }
        });

    Button toTopButton = new Button("Go to start");
    toTopButton.setOnAction(
        a -> {
          CodeTextArea codeArea = markdownTab.getCodeArea();
          if (codeArea != null) {
            codeArea.moveTo(0);
            codeArea.requestFollowCaret();
          }
        });

    pane.getChildren().addAll(searchInput, findButton, toTopButton);
    Scene scene = new Scene(vBox);
    searchWindow = new Stage();
    searchWindow.setOnCloseRequest(event -> searchWindow = null);
    searchWindow.setTitle("Find");
    searchWindow.setScene(scene);
    searchWindow.sizeToScene();
    searchWindow.show();
    searchWindow.toFront();
    searchWindow.setAlwaysOnTop(true);
  }

  // ── About / log ────────────────────────────────────────────────────────────

  private void showAbout() {
    StringBuilder content = new StringBuilder();
    String version = "unknown";
    String buildTime = "unknown";
    String batikVersion = "unknown";
    String jsoupVersion = "unknown";
    String openHtmlVersion = "unknown";
    Properties props = new Properties();
    try (InputStream is = getClass().getResourceAsStream("/MarkdownToPdf.properties")) {
      if (is != null) {
        props.load(is);
        version = props.getProperty("Implementation-Version", version);
        String dt = props.getProperty("Build-Time");
        if (dt != null) {
          try {
            buildTime = dateFormat.format(ZonedDateTime.parse(dt.trim(), isoFormat));
          } catch (DateTimeParseException e) {
            buildTime = dt;
          }
        }
        batikVersion = props.getProperty("Batik-Version", batikVersion);
        jsoupVersion = props.getProperty("Jsoup-Version", jsoupVersion);
        openHtmlVersion = props.getProperty("Openhtmltopdf-Version", openHtmlVersion);
      }
    } catch (IOException e) {
      ExceptionAlert.showAlert("Error reading MarkdownToPdf.properties", e);
    }
    content
        .append("MarkdownToPdf version: ")
        .append(version)
        .append("\nBuilt: ")
        .append(buildTime)
        .append("\n\nOpenHTMLtoPDF version: ")
        .append(openHtmlVersion)
        .append("\nBatik version: ")
        .append(batikVersion)
        .append("\nJsoup version: ")
        .append(jsoupVersion)
        .append("\n\nJava Runtime Version: ")
        .append(System.getProperty("java.runtime.version"))
        .append(" (")
        .append(System.getProperty("os.arch"))
        .append(")");

    Alert dialog = new Alert(Alert.AlertType.INFORMATION, content.toString());
    dialog.setHeaderText("About MarkdownToPdf");
    dialog.show();
  }

  private void viewLogFile(javafx.event.ActionEvent actionEvent) {
    try {
      org.apache.logging.log4j.core.Logger l =
          (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
      Map.Entry<String, Appender> appenderEntry =
          l.get().getAppenders().entrySet().stream()
              .filter(e -> "MarkdownToPdfLog".equals(e.getKey()))
              .findAny()
              .orElse(null);
      if (appenderEntry == null) {
        Alerts.warn("Log file not found", "No appender named 'MarkdownToPdfLog' found.");
        return;
      }
      File logFile = new File(((FileAppender) appenderEntry.getValue()).getFileName());
      if (!logFile.exists()) {
        Alerts.warn("Log file not found", logFile.getAbsolutePath());
        return;
      }
      Alerts.info(logFile.getAbsolutePath(), Files.readString(logFile.toPath()));
    } catch (Exception e) {
      ExceptionAlert.showAlert("Failed to show log file", e);
    }
  }

  // ── Public accessors / helpers ─────────────────────────────────────────────

  /**
   * Returns the Style tab.
   *
   * @return the {@link StyleTab}
   */
  public StyleTab getStyleTab() {
    return styleTab;
  }

  /**
   * Returns the primary application {@link Stage}.
   *
   * @return the primary stage
   */
  public Stage getStage() {
    return stage;
  }

  /**
   * Displays a message in the bottom status bar.
   *
   * @param status the status message
   */
  public void setStatus(String status) {
    statusField.setText(status);
  }

  /**
   * Returns the currently selected project, or {@code null} if none is selected.
   *
   * @return the active {@link Project}
   */
  public Project getActiveProject() {
    return projectCombo.getValue();
  }

  /**
   * Updates the Markdown file path on the currently active project.
   *
   * @param file the new Markdown file path; ignored if no project is active or {@code file} is null
   */
  public void setProjectMarkdownFile(Path file) {
    Project p = projectCombo.getValue();
    if (p != null && file != null) p.setMarkdownFile(file);
  }

  /**
   * Returns the current working directory used as the initial directory for file choosers.
   *
   * @return the project directory
   */
  public File getProjectDir() {
    File dir = new File(System.getProperty("user.dir"));
    if (dir.isFile()) return dir.getParentFile();
    if (!dir.exists()) {
      Project active = projectCombo.getValue();
      if (active != null) {
        String path = preferences().node(active.getName()).get("projectFile", null);
        if (path != null) return new File(path).getParentFile();
      }
    }
    return dir;
  }

  /**
   * Sets the working directory by updating the {@code user.dir} system property.
   *
   * @param dir the directory to set; if {@code dir} is a file, its parent is used
   */
  public void setProjectDir(File dir) {
    if (dir != null && dir.isFile()) dir = dir.getParentFile();
    if (dir != null) System.setProperty("user.dir", dir.getAbsolutePath());
  }

  /**
   * Writes raw bytes to a file.
   *
   * @param file the destination file
   * @param content the bytes to write
   * @return the path written to
   * @throws IOException if writing fails
   */
  public static Path writeToFile(File file, byte[] content) throws IOException {
    return Files.write(file.toPath(), content);
  }

  private Preferences preferences() {
    return Preferences.userRoot().node(this.getClass().getName());
  }
}
