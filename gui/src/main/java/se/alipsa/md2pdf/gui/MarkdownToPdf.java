package se.alipsa.md2pdf.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
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
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
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
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.FileAppender;
import se.alipsa.md2pdf.Md2PdfException;
import se.alipsa.md2pdf.gui.fs.FileAccess;
import se.alipsa.md2pdf.gui.fs.FileAccessBroker;
import se.alipsa.md2pdf.gui.fs.PersistedFileState;
import se.alipsa.md2pdf.gui.update.UpdateChecker;
import se.alipsa.md2pdf.gui.update.UpdateInfo;
import se.alipsa.md2pdf.gui.update.UpdatePolicy;
import se.alipsa.md2pdf.gui.widgets.Alerts;
import se.alipsa.md2pdf.gui.widgets.ExceptionAlert;
import se.alipsa.md2pdf.model.Project;
import se.alipsa.md2pdf.model.StyleProfileManager;

/**
 * Main JavaFX application class. Builds the primary window with a Markdown editor tab, a Style tab,
 * and a PDF output tab; wires project and style management.
 */
public class MarkdownToPdf extends Application {

  private static final String JAVA2D_UI_SCALE = "sun.java2d.uiScale";
  private static volatile JWindow splashWindow;
  private static final int SPLASH_LOGO_SIZE = 96;

  private static final String PREF_AUTO_CHECK_UPDATES = "autoCheckForUpdates";
  private static final String PREF_LAST_UPDATE_CHECK = "lastUpdateCheck";
  private static final String PREF_DISMISSED_VERSION = "dismissedVersion";
  private static final long UPDATE_CHECK_INTERVAL_MILLIS = 20L * 60 * 60 * 1000; // 20 hours
  private final SimpleBooleanProperty updateCheckInProgress = new SimpleBooleanProperty(false);

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

  /**
   * Keeps stored file paths readable across sessions. A no-op in the open source build; a sandboxed
   * distribution registers a real implementation through {@link java.util.ServiceLoader}.
   */
  private final FileAccessBroker fileAccess = FileAccessBroker.get();

  /**
   * Access held for the Markdown file of the active project, for as long as the editor may write
   * back to it. Released at the start of every activation — whether or not the incoming project has
   * a Markdown file it can open — and when the application stops, so a project that failed to open
   * can never leave the previous one's grant open underneath it.
   */
  private FileAccess markdownAccess = FileAccess.granted();

  private final List<String> searchStrings = new UniqueList<>();

  /** Creates the JavaFX application instance. */
  public MarkdownToPdf() {}

  /**
   * Application entry point.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args) {
    configureJava2dUiScale();
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

  private static void configureJava2dUiScale() {
    if (System.getProperty(JAVA2D_UI_SCALE) != null) {
      return;
    }

    Optional<Double> scale =
        firstScale(
            parseScale(System.getenv("MARKDOWN_TO_PDF_UI_SCALE")),
            parseScale(System.getenv("GDK_SCALE")),
            parseScale(System.getenv("QT_SCALE_FACTOR")),
            parseMaxScale(System.getenv("KDE_SCREEN_SCALE_FACTORS")),
            parseDpiScale(System.getenv("XFT_DPI")));
    scale.ifPresent(value -> System.setProperty(JAVA2D_UI_SCALE, formatScale(value)));
  }

  @SafeVarargs
  private static Optional<Double> firstScale(Optional<Double>... scales) {
    for (Optional<Double> scale : scales) {
      if (scale.isPresent()) {
        return scale;
      }
    }
    return Optional.empty();
  }

  private static Optional<Double> parseScale(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    try {
      double scale = Double.parseDouble(value.trim());
      return scale >= 1.5 ? Optional.of(scale) : Optional.empty();
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static Optional<Double> parseMaxScale(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    double maxScale = 1;
    for (String token : value.split("[;,:=]")) {
      Optional<Double> scale = parseScale(token);
      if (scale.isPresent()) {
        maxScale = Math.max(maxScale, scale.get());
      }
    }
    return maxScale >= 1.5 ? Optional.of(maxScale) : Optional.empty();
  }

  private static Optional<Double> parseDpiScale(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    try {
      double dpi = Double.parseDouble(value.trim());
      if (dpi > 1000) {
        dpi = dpi / 1024;
      }
      double scale = dpi / 96;
      return scale >= 1.5 ? Optional.of(scale) : Optional.empty();
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static String formatScale(double scale) {
    double rounded = Math.rint(scale);
    if (Math.abs(scale - rounded) < 0.05) {
      return Integer.toString((int) rounded);
    }
    return String.format(Locale.ROOT, "%.2f", scale);
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
    double splashScale = splashScale();
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setBackground(new Color(247, 249, 251));
    content.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 217, 226)),
            BorderFactory.createEmptyBorder(
                scaled(28, splashScale),
                scaled(36, splashScale),
                scaled(28, splashScale),
                scaled(36, splashScale))));

    URL logoUrl = MarkdownToPdf.class.getResource("/MarkdownToPdf-rounded.png");
    if (logoUrl != null) {
      try {
        BufferedImage logo = ImageIO.read(logoUrl);
        int logoSize = scaled(SPLASH_LOGO_SIZE, splashScale);
        JLabel logoLabel = new JLabel(new HiDpiImageIcon(logo, logoSize, logoSize));
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(logoLabel);
        content.add(Box.createRigidArea(new Dimension(0, scaled(14, splashScale))));
      } catch (IOException ignored) {
        // The text splash is still useful if the optional logo cannot be read.
      }
    }

    JLabel title = new JLabel("MarkdownToPdf");
    title.setAlignmentX(Component.CENTER_ALIGNMENT);
    title.setFont(title.getFont().deriveFont(Font.BOLD, scaled(24f, splashScale)));
    title.setForeground(new Color(31, 41, 55));

    JLabel message = new JLabel("Starting editor...");
    message.setAlignmentX(Component.CENTER_ALIGNMENT);
    message.setFont(message.getFont().deriveFont(scaled(13f, splashScale)));
    message.setForeground(new Color(75, 85, 99));

    JProgressBar progress = new JProgressBar();
    progress.setAlignmentX(Component.CENTER_ALIGNMENT);
    progress.setIndeterminate(true);
    progress.setMaximumSize(new Dimension(scaled(160, splashScale), scaled(10, splashScale)));
    progress.setPreferredSize(new Dimension(scaled(160, splashScale), scaled(10, splashScale)));

    content.add(title);
    content.add(Box.createRigidArea(new Dimension(0, scaled(8, splashScale))));
    content.add(message);
    content.add(Box.createRigidArea(new Dimension(0, scaled(18, splashScale))));
    content.add(progress);

    splashWindow = new JWindow();
    splashWindow.setContentPane(content);
    splashWindow.pack();
    splashWindow.setLocationRelativeTo(null);
    splashWindow.setAlwaysOnTop(true);
    splashWindow.setVisible(true);
  }

  private static double splashScale() {
    double java2dScale = defaultGraphicsScale();
    if (java2dScale >= 1.5) {
      return 1;
    }

    return firstScale(
            parseScale(System.getProperty(JAVA2D_UI_SCALE)),
            parseScale(System.getenv("MARKDOWN_TO_PDF_UI_SCALE")),
            parseScale(System.getenv("GDK_SCALE")),
            parseScale(System.getenv("QT_SCALE_FACTOR")),
            parseMaxScale(System.getenv("KDE_SCREEN_SCALE_FACTORS")),
            parseDpiScale(System.getenv("XFT_DPI")),
            screenSizeScale())
        .orElse(1d);
  }

  private static double defaultGraphicsScale() {
    try {
      GraphicsConfiguration config =
          GraphicsEnvironment.getLocalGraphicsEnvironment()
              .getDefaultScreenDevice()
              .getDefaultConfiguration();
      return Math.max(
          config.getDefaultTransform().getScaleX(), config.getDefaultTransform().getScaleY());
    } catch (RuntimeException e) {
      return 1;
    }
  }

  private static Optional<Double> screenSizeScale() {
    try {
      Rectangle bounds =
          GraphicsEnvironment.getLocalGraphicsEnvironment()
              .getDefaultScreenDevice()
              .getDefaultConfiguration()
              .getBounds();
      int longEdge = Math.max(bounds.width, bounds.height);
      int shortEdge = Math.min(bounds.width, bounds.height);
      if (longEdge >= 3400 || shortEdge >= 1900) {
        return Optional.of(2d);
      }
    } catch (RuntimeException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private static int scaled(int value, double scale) {
    return (int) Math.round(value * scale);
  }

  private static float scaled(float value, double scale) {
    return (float) (value * scale);
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

  private static class HiDpiImageIcon implements Icon {
    private final BufferedImage image;
    private final int width;
    private final int height;

    HiDpiImageIcon(BufferedImage image, int width, int height) {
      this.image = image;
      this.width = width;
      this.height = height;
    }

    @Override
    public int getIconWidth() {
      return width;
    }

    @Override
    public int getIconHeight() {
      return height;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D g2 = (Graphics2D) graphics.create();
      try {
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(image, x, y, width, height, null);
      } finally {
        g2.dispose();
      }
    }
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

    Image logo = getLogo();
    if (logo != null) {
      primaryStage.getIcons().add(logo);
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
    if (UpdatePolicy.isEnabled() && preferences().getBoolean(PREF_AUTO_CHECK_UPDATES, true)) {
      checkForUpdates(false);
    }
  }

  /**
   * Returns the application icon image, loading it from resources on first call.
   *
   * @return the icon {@link Image}, or {@code null} if the resource is missing
   */
  public static Image getLogo() {
    return LogoHolder.INSTANCE;
  }

  /** Loads the app icon when {@link #getLogo()} is first called, and only once. */
  private static final class LogoHolder {
    static final Image INSTANCE = load();

    private static Image load() {
      try (InputStream is = MarkdownToPdf.class.getResourceAsStream("/MarkdownToPdf-rounded.png")) {
        return is == null ? null : new Image(is);
      } catch (IOException e) {
        logger().warn("Failed to load app icon", e);
        return null;
      }
    }
  }

  /**
   * Returns the URL of the application stylesheet, loading it from resources on first call.
   *
   * @return the stylesheet {@link URL}
   */
  public static URL getStyleSheet() {
    return StyleSheetHolder.INSTANCE;
  }

  /** Resolves the stylesheet URL when {@link #getStyleSheet()} is first called, and only once. */
  private static final class StyleSheetHolder {
    static final URL INSTANCE = MarkdownToPdf.class.getResource("/default-theme.css");
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
    viewExternalButton.setTooltip(new Tooltip("Render and open PDF in the default system viewer"));
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
    // A build distributed through a store that updates the application itself must not offer to
    // fetch releases from anywhere else, so it omits these items entirely rather than disabling
    // them.
    if (UpdatePolicy.isEnabled()) {
      MenuItem checkForUpdatesMi = new MenuItem("Check for Updates…");
      checkForUpdatesMi.setOnAction(a -> checkForUpdates(true));
      checkForUpdatesMi.disableProperty().bind(updateCheckInProgress);
      CheckMenuItem autoCheckUpdatesMi = new CheckMenuItem("Automatically check for updates");
      autoCheckUpdatesMi.setSelected(preferences().getBoolean(PREF_AUTO_CHECK_UPDATES, true));
      autoCheckUpdatesMi
          .selectedProperty()
          .addListener(
              (obs, wasSelected, isSelected) ->
                  preferences().putBoolean(PREF_AUTO_CHECK_UPDATES, isSelected));
      helpMenu.getItems().addAll(new SeparatorMenuItem(), checkForUpdatesMi, autoCheckUpdatesMi);
    }

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
    File file = showSaveDialog(fc);
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
    configurePdfSaveDialog(fc);
    File file = showSaveDialog(fc);
    if (file != null) {
      scene.setCursor(Cursor.WAIT);
      try {
        writeToFile(file, markdownTab.renderPdf());
        setStatus("Exported PDF to " + file.getName());
      } catch (Md2PdfException | IOException e) {
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
    File file;
    boolean needsUserSelectedPath = fileAccess.requiresUserSelectedOutputPath();
    if (needsUserSelectedPath) {
      FileChooser fc = new FileChooser();
      fc.setTitle("Save PDF to view externally");
      configurePdfSaveDialog(fc);
      file = showSaveDialog(fc);
      if (file == null) {
        return;
      }
    } else {
      try {
        file = File.createTempFile("md2pdf_", ".pdf");
        file.deleteOnExit();
      } catch (IOException e) {
        ExceptionAlert.showAlert("Failed to create temporary PDF", e);
        return;
      }
    }
    scene.setCursor(Cursor.WAIT);
    try {
      if (needsUserSelectedPath) {
        try {
          byte[] pdf = markdownTab.renderPdf();
          writeToFile(file, pdf);
        } catch (IOException e) {
          ExceptionAlert.showAlert("Failed to write PDF", e);
          return;
        }
        setStatus("Wrote PDF to " + file.getAbsolutePath());
      } else {
        markdownTab.renderPdf(file);
      }
      openInExternalApp(file);
    } catch (Md2PdfException e) {
      ExceptionAlert.showAlert("Failed to render PDF", e);
    } finally {
      scene.setCursor(Cursor.DEFAULT);
    }
  }

  private File pdfInitialDirectory() {
    File markdownFile = markdownTab.getFile();
    File markdownParent = markdownFile == null ? null : markdownFile.getParentFile();
    if (isUsablePdfDirectory(markdownParent)) {
      return markdownParent;
    }
    Project active = getActiveProject();
    if (active != null && active.getMarkdownFile() != null) {
      File projectMarkdownParent = active.getMarkdownFile().toFile().getParentFile();
      if (isUsablePdfDirectory(projectMarkdownParent)) {
        return projectMarkdownParent;
      }
    }
    File projectDirectory = getProjectDir();
    if (isUsablePdfDirectory(projectDirectory)) {
      return projectDirectory;
    }
    String home = System.getProperty("user.home");
    File homeDirectory = home == null ? null : new File(home);
    return isUsablePdfDirectory(homeDirectory) ? homeDirectory : null;
  }

  private boolean isUsablePdfDirectory(File directory) {
    return directory != null && directory.isDirectory();
  }

  private void configurePdfSaveDialog(FileChooser chooser) {
    File initialDirectory = pdfInitialDirectory();
    if (initialDirectory != null) {
      chooser.setInitialDirectory(initialDirectory);
    }
    chooser.setInitialFileName(suggestedPdfFileName());
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
  }

  File showSaveDialog(FileChooser chooser) {
    try {
      return chooser.showSaveDialog(stage);
    } catch (IllegalArgumentException e) {
      logger().warn("Save dialog rejected its initial directory; retrying without one", e);
      chooser.setInitialDirectory(null);
      try {
        return chooser.showSaveDialog(stage);
      } catch (IllegalArgumentException retryFailure) {
        ExceptionAlert.showAlert("Failed to open save dialog", retryFailure);
        return null;
      }
    }
  }

  private String suggestedPdfFileName() {
    File markdownFile = markdownTab.getFile();
    if (markdownFile == null) {
      return "document.pdf";
    }
    String name = markdownFile.getName();
    int extension = name.lastIndexOf('.');
    String baseName = extension > 0 ? name.substring(0, extension) : name;
    return baseName + ".pdf";
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
          File projectFile = showSaveDialog(fc);
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
        // Remembered and stored before the combo is updated: setValue below fires the same
        // action handler that activates a project chosen from the dropdown, so activation must
        // not run before there is a token to restore access with.
        Preferences projects = preferences().node("projects");
        rememberProjectPaths(p, projectFile.toPath());
        projects.node(p.getName()).put("projectFile", projectFile.toPath().toString());
        projects.flush();
        projectCombo.getItems().add(p);
        projectCombo.setValue(p);
      } catch (Exception e) {
        ExceptionAlert.showAlert("Failed to load " + projectFile, e);
      }
    }
  }

  private void setActiveProject(Project p) {
    logger().info("Activating project: {}", p.getName());
    // Released here rather than only on success: if this project has no Markdown file, restore
    // is denied, loading fails, or the user cancels relocation, the access held for whatever was
    // active before must not linger just because openProjectMarkdown never got to reassign it.
    holdMarkdownAccess(FileAccess.granted());
    Path markdownFile = p.getMarkdownFile();
    if (markdownFile != null) {
      openProjectMarkdown(p, markdownFile);
    }
    // The style profile, project directory and tooltip are applied whether or not the Markdown
    // file opened. The combo already shows this project, so stopping here would leave the window
    // describing one project while displaying another.
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

  /**
   * Opens a project's Markdown file, offering to locate it when it cannot be read.
   *
   * <p>Unlike the project file, this access is held open rather than released straight away: the
   * editor writes back to this file when the user saves, so access has to outlive the load. It is
   * released when another project is activated, or when the application stops.
   *
   * <p>The failure this recovers from is ordinary in a sandboxed build. Opening a project file
   * grants access to that file alone, not to the Markdown file named inside it, so a project
   * created on another machine arrives with no way to reach its own document. Letting the user
   * point at it grants access and lets the path be remembered for next time.
   *
   * @param p the project being activated
   * @param markdownFile the Markdown file it names
   */
  private void openProjectMarkdown(Project p, Path markdownFile) {
    FileAccess access = fileAccess.restore(markdownFile);
    if (access.isGranted() && markdownTab.loadFile(markdownFile)) {
      holdMarkdownAccess(access);
      return;
    }
    access.close();
    logger().warn("Could not open the Markdown file {} for project {}", markdownFile, p.getName());

    if (!Alerts.confirm(
        "Project " + p.getName(),
        "Cannot open " + markdownFile.getFileName(),
        markdownFile
            + "\n\ncould not be opened. It may have been moved, or this copy of the project may"
            + " have come from another machine.\n\nLocate it now?")) {
      return;
    }

    FileChooser fc = new FileChooser();
    fc.setTitle("Locate the Markdown file for " + p.getName());
    fc.getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Markdown files", "*.md", "*.markdown"));
    Path parent = markdownFile.getParent();
    if (parent != null && Files.isDirectory(parent)) {
      fc.setInitialDirectory(parent.toFile());
    }
    File chosen = fc.showOpenDialog(stage);
    if (chosen == null) {
      return;
    }

    // Choosing the file in a dialog is what grants access to it, so this is the one moment a token
    // for it can be created.
    Path located = chosen.toPath();
    fileAccess.remember(located);
    FileAccess relocated = fileAccess.restore(located);
    if (relocated.isGranted() && markdownTab.loadFile(located)) {
      p.setMarkdownFile(located);
      holdMarkdownAccess(relocated);
      persistRelocatedMarkdownFile(p);
    } else {
      relocated.close();
      Alerts.warn("Project " + p.getName(), "Could not open " + located + " either.");
    }
  }

  /**
   * Writes the project's newly located Markdown path back to its stored {@code .jpr} file. Without
   * this, {@link Project#setMarkdownFile} only updates the in-memory {@link Project}, so the next
   * launch reads the stale path again and prompts to locate it once more despite the new token
   * already being remembered.
   *
   * @param p the project whose Markdown file was just relocated
   */
  private void persistRelocatedMarkdownFile(Project p) {
    String projectFilePref =
        preferences().node("projects").node(p.getName()).get("projectFile", null);
    if (projectFilePref == null) {
      return;
    }
    Path projectFilePath = Paths.get(projectFilePref);
    // The project file's own access was released as soon as it was read at startup (or the last
    // time it was written), so it must be restored before writing to it again.
    try (FileAccess access = fileAccess.restore(projectFilePath)) {
      Project.save(p, projectFilePath);
    } catch (IOException e) {
      ExceptionAlert.showAlert(
          "Failed to save the located Markdown file to project " + p.getName(), e);
    }
  }

  /**
   * Takes ownership of the access held for the open Markdown file, releasing whatever was held
   * before.
   *
   * @param access access to the newly opened file
   */
  private void holdMarkdownAccess(FileAccess access) {
    if (access == markdownAccess) {
      // A broker that caches or ref-counts access per path is free to hand back the same
      // instance for the same path across two calls; closing it here would revoke the access
      // just installed instead of the access actually being replaced.
      return;
    }
    markdownAccess.close();
    markdownAccess = access;
  }

  @Override
  public void stop() {
    markdownAccess.close();
  }

  void populateProjectCombo(ComboBox<Project> projectCombo)
      throws BackingStoreException, IOException {
    populateProjects(projectCombo.getItems(), preferences().node("projects"), fileAccess);
  }

  /**
   * Loads projects from {@code projectsNode} into {@code list}, dropping the preference for one
   * that is genuinely missing and keeping one that is merely unreachable right now.
   *
   * <p>Factored out of {@link #populateProjectCombo(ComboBox)} as a plain function of a {@link
   * List}, a {@link Preferences} node and a {@link FileAccessBroker} — none of them JavaFX types —
   * so the classification this method drives can be exercised directly.
   *
   * @param list where loaded projects are added
   * @param projectsNode the {@code Preferences} node holding one child per stored project
   * @param fileAccess broker used to regain access to each stored path
   */
  static void populateProjects(
      List<Project> list, Preferences projectsNode, FileAccessBroker fileAccess)
      throws BackingStoreException, IOException {
    for (String name : projectsNode.childrenNames()) {
      String path = projectsNode.node(name).get("projectFile", null);
      if (path == null) {
        projectsNode.node(name).removeNode();
        continue;
      }
      Path projectFilePath = Paths.get(path);
      // A path that cannot be reached is not the same as one that is gone. Under a sandbox an
      // ungranted path reports Files.exists() == false, so testing existence alone would delete
      // every stored project on the first launch — and off a sandbox the same happens whenever the
      // volume holding them is not mounted.
      //
      // The project file is only read here, so access is released as soon as that read is done.
      try (FileAccess access = fileAccess.restore(projectFilePath)) {
        switch (PersistedFileState.of(access, projectFilePath)) {
          case LOADABLE -> {
            try {
              list.add(Project.load(projectFilePath));
            } catch (Exception e) {
              ExceptionAlert.showAlert("Failed to load project from " + projectFilePath, e);
            }
          }
          case MISSING -> {
            logger().info("{} does not exist, removing preference", projectFilePath);
            fileAccess.forget(projectFilePath);
            projectsNode.node(name).removeNode();
          }
          case INACCESSIBLE ->
              logger().info("{} is currently unreachable, keeping preference", projectFilePath);
        }
      }
    }
  }

  void saveProject(Project p, String path) throws IOException {
    Preferences projects = preferences().node("projects");
    // The project file must exist before it is remembered: a token can only be minted for a file
    // that is actually there, and this overload creates the file rather than updating it.
    Project.save(p, Paths.get(path));
    rememberProjectPaths(p, Paths.get(path));
    projects.node(p.getName()).put("projectFile", path);
  }

  private void rememberProjectPaths(Project p, Path projectFilePath) {
    rememberProjectPaths(fileAccess, p, projectFilePath);
  }

  /**
   * Records the paths this project stores, so a sandboxed build can still read them in a later
   * session. Called while the user's selection still grants access — once that lapses a token can
   * no longer be created.
   *
   * @param fileAccess broker used to remember each path
   * @param p the project being persisted
   * @param projectFilePath where the project file itself is written
   */
  static void rememberProjectPaths(FileAccessBroker fileAccess, Project p, Path projectFilePath) {
    fileAccess.remember(projectFilePath);
    Path markdownFile = p.getMarkdownFile();
    if (markdownFile != null) {
      fileAccess.remember(markdownFile);
    }
  }

  void saveProject(Project p) throws IOException {
    Preferences projects = preferences().node("projects");
    String projectFilePref = projects.node(p.getName()).get("projectFile", null);
    Path projectFilePath;
    boolean pathIsFreshlyChosen = projectFilePref == null;
    if (pathIsFreshlyChosen) {
      FileChooser fc = new FileChooser();
      fc.setTitle("Save project file");
      fc.setInitialDirectory(getProjectDir());
      fc.setInitialFileName(p.getName() + ".jpr");
      File file = showSaveDialog(fc);
      if (file == null) return;
      projectFilePath = file.toPath();
    } else {
      projectFilePath = Paths.get(projectFilePref);
    }
    // Capture current style profile name before saving
    if (styleCombo != null && styleCombo.getValue() != null) {
      p.setStyleProfileName(styleCombo.getValue());
    }
    if (pathIsFreshlyChosen) {
      // A path just chosen in the save dialog above is already accessible; no need to restore it.
      Project.save(p, projectFilePath);
      rememberProjectPaths(p, projectFilePath);
    } else {
      // A path read back from preferences had its access released as soon as it was last read
      // or written, so writing to it again needs access restored first.
      try (FileAccess access = fileAccess.restore(projectFilePath)) {
        Project.save(p, projectFilePath);
        rememberProjectPaths(p, projectFilePath);
      }
    }
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
          if (pdfViewer.getContent() == null) {
            Alerts.info("No PDF to save", "Render a PDF first.");
            return;
          }
          FileChooser fc = new FileChooser();
          configurePdfSaveDialog(fc);
          File file = showSaveDialog(fc);
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
    Properties props = loadBuildProperties();
    String version = props.getProperty("Implementation-Version", "unknown");
    String buildTime = "unknown";
    String dt = props.getProperty("Build-Time");
    if (dt != null) {
      try {
        buildTime = dateFormat.format(ZonedDateTime.parse(dt.trim(), isoFormat));
      } catch (DateTimeParseException e) {
        buildTime = dt;
      }
    }
    String batikVersion = props.getProperty("Batik-Version", "unknown");
    String jsoupVersion = props.getProperty("Jsoup-Version", "unknown");
    String openHtmlVersion = props.getProperty("Openhtmltopdf-Version", "unknown");

    StringBuilder content = new StringBuilder();
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

  /**
   * Loads {@code /MarkdownToPdf.properties} (generated at build time with {@code
   * Implementation-Version}, {@code Build-Time}, and library versions). Returns an empty {@link
   * Properties} — never {@code null} and never throws — if the resource is missing or unreadable;
   * callers fall back to per-key defaults. Shared by {@link #showAbout()} and {@link
   * #readCurrentVersion()} so the two can't drift on how the resource is read.
   */
  private Properties loadBuildProperties() {
    Properties props = new Properties();
    try (InputStream is = getClass().getResourceAsStream("/MarkdownToPdf.properties")) {
      if (is != null) {
        props.load(is);
      }
    } catch (IOException e) {
      logger().warn("Failed to read MarkdownToPdf.properties", e);
    }
    return props;
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

  // ── Updates ──────────────────────────────────────────────────────────────────

  /**
   * Checks GitHub for a newer release, unless {@link UpdatePolicy} forbids it for this build.
   * Background checks (triggered on startup) are throttled and silent on failure, and skip an
   * update the user already dismissed; a check triggered from the Help menu always runs and always
   * reports its result.
   *
   * @param interactive whether this check was explicitly requested by the user
   */
  private void checkForUpdates(boolean interactive) {
    // Guarded here as well as at both call sites: this is the only place a request leaves the
    // application, so a future caller cannot reintroduce one in a build that forbids it.
    if (!UpdatePolicy.isEnabled()) {
      return;
    }
    if (updateCheckInProgress.get()) {
      return;
    }
    if (!interactive) {
      long lastCheck = preferences().getLong(PREF_LAST_UPDATE_CHECK, 0);
      long elapsed = System.currentTimeMillis() - lastCheck;
      // elapsed < 0 means the clock moved backwards since the last check (or lastCheck was
      // never set) — treat that as "due", not as "just checked", so a clock adjustment can't
      // suppress checks indefinitely.
      if (lastCheck > 0 && elapsed >= 0 && elapsed < UPDATE_CHECK_INTERVAL_MILLIS) {
        return;
      }
    }
    Optional<String> currentVersionOpt = readCurrentVersion();
    if (currentVersionOpt.isEmpty()) {
      logger().warn("Could not determine the running version; skipping the update check.");
      if (interactive) {
        Alerts.warn(
            "Check for Updates",
            "Could not determine the running app version, so the update check was skipped.");
      }
      return;
    }
    String currentVersion = currentVersionOpt.get();
    updateCheckInProgress.set(true);
    Task<Optional<UpdateInfo>> task =
        new Task<>() {
          @Override
          protected Optional<UpdateInfo> call() throws Exception {
            return new UpdateChecker().checkForUpdate(currentVersion);
          }
        };
    task.setOnSucceeded(
        e -> {
          updateCheckInProgress.set(false);
          preferences().putLong(PREF_LAST_UPDATE_CHECK, System.currentTimeMillis());
          Optional<UpdateInfo> info = task.getValue();
          if (info.isPresent()) {
            String dismissedVersion = preferences().get(PREF_DISMISSED_VERSION, "");
            if (interactive || !dismissedVersion.equals(info.get().latestVersion())) {
              handleUpdateAvailable(info.get(), currentVersion);
            }
          } else if (interactive) {
            Alerts.info(
                "Check for Updates",
                "You are running the latest version (" + currentVersion + ").");
          }
        });
    task.setOnFailed(
        e -> {
          updateCheckInProgress.set(false);
          // Recorded on failure too, not just success — otherwise an offline machine pays a
          // fresh 10s-timeout request on every launch instead of being throttled like any other
          // outcome.
          preferences().putLong(PREF_LAST_UPDATE_CHECK, System.currentTimeMillis());
          logger().warn("Update check failed", task.getException());
          if (interactive) {
            ExceptionAlert.showAlert("Failed to check for updates", task.getException());
          }
        });
    Thread thread = new Thread(task);
    thread.setDaemon(true);
    thread.start();
  }

  private void handleUpdateAvailable(UpdateInfo info, String currentVersion) {
    setStatus("Update available: " + info.latestVersion() + " — see Help > Check for Updates");
    Alerts.confirmAsync(
        "Update available",
        "MarkdownToPdf "
            + info.latestVersion()
            + " is available (you have "
            + currentVersion
            + ").",
        "Open the release page in your browser?",
        "Open release page",
        "Skip this version",
        () -> openUri(info.releaseHtmlUrl()),
        () -> preferences().put(PREF_DISMISSED_VERSION, info.latestVersion()));
  }

  /**
   * Returns the running {@code Implementation-Version}, or {@link Optional#empty()} if it can't be
   * determined — never the literal string {@code "unknown"}, so callers can't mistake a detection
   * failure for a real version and silently report "you're up to date."
   */
  private Optional<String> readCurrentVersion() {
    return Optional.ofNullable(loadBuildProperties().getProperty("Implementation-Version"));
  }

  private static final Set<String> ALLOWED_URI_SCHEMES = Set.of("http", "https");

  private void openUri(String uri) {
    if (uri == null) {
      // Unreachable via the update-check path (parseAndEvaluate already rejects a null
      // html_url) — this is a programmer-error guard, not something to show the user a
      // fabricated stack trace for.
      logger().error("openUri called with a null URI");
      return;
    }
    URI target;
    try {
      target = URI.create(uri);
    } catch (IllegalArgumentException e) {
      ExceptionAlert.showAlert("Invalid update URL: " + uri, e);
      return;
    }
    if (!ALLOWED_URI_SCHEMES.contains(
        target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT))) {
      logger().warn("Refusing to open a non-http(s) URL: {}", uri);
      Alerts.warn("Blocked URL", "Refusing to open a non-http(s) URL: " + uri);
      return;
    }
    Task<Void> task =
        new Task<>() {
          @Override
          protected Void call() throws Exception {
            Desktop.getDesktop().browse(target);
            return null;
          }
        };
    task.setOnFailed(e -> ExceptionAlert.showAlert("Failed to open " + uri, task.getException()));
    Thread thread = new Thread(task);
    thread.setDaemon(true);
    thread.start();
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
   * <p>Called right after the file was chosen in a dialog or dropped onto the editor — exactly the
   * moment access to it can be granted — so this is also where it is remembered and where the
   * editor's held access is switched to it.
   *
   * @param file the new Markdown file path; ignored if no project is active or {@code file} is null
   */
  public void setProjectMarkdownFile(Path file) {
    Project p = projectCombo.getValue();
    if (p == null || file == null) {
      return;
    }
    p.setMarkdownFile(file);
    fileAccess.remember(file);
    holdMarkdownAccess(fileAccess.restore(file));
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
