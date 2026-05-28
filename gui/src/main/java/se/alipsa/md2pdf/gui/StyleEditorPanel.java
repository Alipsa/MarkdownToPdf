package se.alipsa.md2pdf.gui;

import java.io.IOException;
import java.util.function.Consumer;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import se.alipsa.md2pdf.gui.widgets.Alerts;
import se.alipsa.md2pdf.gui.widgets.ExceptionAlert;
import se.alipsa.md2pdf.model.StyleProfile;
import se.alipsa.md2pdf.model.StyleProfileManager;

/**
 * Visual panel for editing a {@link StyleProfile}. Attach a callback via {@link
 * #setOnProfileChanged(Consumer)} to be notified whenever the user modifies a control or loads a
 * profile.
 */
public class StyleEditorPanel extends ScrollPane {

  private static final double LABEL_COL_WIDTH = 140;

  private final StyleProfileManager profileManager;
  private Consumer<StyleProfile> onProfileChanged = p -> {};

  // Profile management bar
  private final TextField profileNameField = new TextField();
  private final MenuButton loadMenuButton = new MenuButton("Load ▼");

  // Typography
  private final ComboBox<String> bodyFontCombo = new ComboBox<>();
  private final Spinner<Integer> bodyFontSizeSpinner = new Spinner<>(7, 28, 11);
  private final Slider lineHeightSlider = new Slider(1.0, 2.5, 1.6);
  private final ColorPicker bodyColorPicker = new ColorPicker();

  // Headings
  private final Spinner<Double> h1Spinner = new Spinner<>(0.8, 4.0, 2.0, 0.1);
  private final Spinner<Double> h2Spinner = new Spinner<>(0.8, 4.0, 1.5, 0.1);
  private final Spinner<Double> h3Spinner = new Spinner<>(0.8, 4.0, 1.25, 0.1);
  private final ColorPicker headingColorPicker = new ColorPicker();
  private final CheckBox h1BorderCheckBox = new CheckBox("Show border line under H1");

  // Links
  private final ColorPicker linkColorPicker = new ColorPicker();

  // Code
  private final ComboBox<String> codeFontCombo = new ComboBox<>();
  private final Spinner<Integer> codeSizeSpinner = new Spinner<>(50, 130, 85);
  private final ColorPicker codeBackgroundPicker = new ColorPicker();

  // Blockquotes
  private final ColorPicker blockquoteBorderPicker = new ColorPicker();
  private final ColorPicker blockquoteTextPicker = new ColorPicker();

  // Page
  private final ComboBox<String> pageSizeCombo = new ComboBox<>();
  private final ToggleGroup orientationGroup = new ToggleGroup();
  private final RadioButton portraitRadio = new RadioButton("Portrait");
  private final RadioButton landscapeRadio = new RadioButton("Landscape");
  private final Spinner<Double> marginTopSpinner = new Spinner<>(0.1, 6.0, 1.0, 0.1);
  private final Spinner<Double> marginRightSpinner = new Spinner<>(0.1, 6.0, 1.0, 0.1);
  private final Spinner<Double> marginBottomSpinner = new Spinner<>(0.1, 6.0, 1.0, 0.1);
  private final Spinner<Double> marginLeftSpinner = new Spinner<>(0.1, 6.0, 1.0, 0.1);
  private final ComboBox<String> marginUnitCombo = new ComboBox<>();

  /**
   * Creates the visual style editor pre-populated with the built-in Default profile.
   *
   * @param profileManager the profile manager used to load and save named profiles
   */
  public StyleEditorPanel(StyleProfileManager profileManager) {
    this.profileManager = profileManager;
    setFitToWidth(true);

    VBox content = new VBox(16);
    content.setPadding(new Insets(12));

    content
        .getChildren()
        .addAll(
            buildProfileBar(),
            buildSection("Typography", buildTypographyGrid()),
            buildSection("Headings", buildHeadingsGrid()),
            buildSection("Links", buildLinksGrid()),
            buildSection("Code & Preformatted", buildCodeGrid()),
            buildSection("Block Quotes", buildBlockquoteGrid()),
            buildSection("Page (PDF)", buildPageGrid()));

    setContent(content);
    configureControls();
    applyProfile(profileManager.getBuiltin("Default"));
    wireChangeListeners();
  }

  // ── Profile management bar ─────────────────────────────────────────────────

  private HBox buildProfileBar() {
    HBox bar = new HBox(8);
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(4, 0, 8, 0));

    Label nameLabel = new Label("Style profile:");
    profileNameField.setPrefWidth(160);
    profileNameField.setPromptText("Profile name");

    refreshLoadMenu();

    Button saveButton = new Button("Save");
    saveButton.setOnAction(e -> saveCurrentProfile());

    Button deleteButton = new Button("Delete");
    deleteButton.setOnAction(e -> deleteCurrentProfile());

    bar.getChildren().addAll(nameLabel, profileNameField, loadMenuButton, saveButton, deleteButton);
    return bar;
  }

  private void refreshLoadMenu() {
    loadMenuButton.getItems().clear();
    for (String name : profileManager.listNames()) {
      MenuItem item = new MenuItem(name);
      item.setOnAction(e -> loadProfileByName(name));
      loadMenuButton.getItems().add(item);
    }
  }

  private void loadProfileByName(String name) {
    try {
      StyleProfile p = profileManager.load(name);
      if (p != null) {
        applyProfile(p);
        onProfileChanged.accept(p);
      }
    } catch (IOException ex) {
      ExceptionAlert.showAlert("Failed to load profile '" + name + "'", ex);
    }
  }

  private void saveCurrentProfile() {
    String name = profileNameField.getText().trim();
    if (name.isEmpty()) {
      Alerts.warn("No profile name", "Please enter a name for the style profile.");
      return;
    }
    StyleProfile p = buildProfile();
    p.setName(name);
    try {
      profileManager.save(p);
      refreshLoadMenu();
      Alerts.info("Saved", "Style profile '" + name + "' saved.");
    } catch (IOException ex) {
      ExceptionAlert.showAlert("Failed to save profile", ex);
    }
  }

  private void deleteCurrentProfile() {
    String name = profileNameField.getText().trim();
    if (name.isEmpty()) {
      Alerts.warn("No profile name", "Please enter the name of the profile to delete.");
      return;
    }
    boolean confirmed =
        Alerts.confirm("Delete profile", "Delete '" + name + "'?", "This cannot be undone.");
    if (confirmed) {
      try {
        profileManager.delete(name);
        refreshLoadMenu();
      } catch (IOException ex) {
        ExceptionAlert.showAlert("Failed to delete profile", ex);
      }
    }
  }

  // ── Section builders ───────────────────────────────────────────────────────

  private VBox buildSection(String title, GridPane grid) {
    Label heading = new Label(title);
    heading.setFont(Font.font(null, FontWeight.BOLD, 13));
    heading.setPadding(new Insets(0, 0, 4, 0));
    VBox section = new VBox(4, heading, grid);
    section.setStyle("-fx-border-color: #dddddd; -fx-border-radius: 4; -fx-padding: 8;");
    return section;
  }

  private GridPane buildTypographyGrid() {
    GridPane g = grid();
    addRow(g, 0, "Body font", bodyFontCombo);
    addRow(g, 1, "Body size (pt)", row(bodyFontSizeSpinner, new Label("pt")));
    addRow(g, 2, "Line spacing", lineHeightSlider);
    addRow(g, 3, "Text colour", bodyColorPicker);
    return g;
  }

  private GridPane buildHeadingsGrid() {
    GridPane g = grid();
    addRow(g, 0, "H1 size (em)", row(h1Spinner, new Label("em")));
    addRow(g, 1, "H2 size (em)", row(h2Spinner, new Label("em")));
    addRow(g, 2, "H3 size (em)", row(h3Spinner, new Label("em")));
    addRow(g, 3, "Colour", headingColorPicker);
    addRow(g, 4, "H1 border", h1BorderCheckBox);
    return g;
  }

  private GridPane buildLinksGrid() {
    GridPane g = grid();
    addRow(g, 0, "Link colour", linkColorPicker);
    return g;
  }

  private GridPane buildCodeGrid() {
    GridPane g = grid();
    addRow(g, 0, "Font", codeFontCombo);
    addRow(g, 1, "Size (%)", row(codeSizeSpinner, new Label("%")));
    addRow(g, 2, "Background", codeBackgroundPicker);
    return g;
  }

  private GridPane buildBlockquoteGrid() {
    GridPane g = grid();
    addRow(g, 0, "Border colour", blockquoteBorderPicker);
    addRow(g, 1, "Text colour", blockquoteTextPicker);
    return g;
  }

  private GridPane buildPageGrid() {
    GridPane g = grid();
    addRow(g, 0, "Page size", pageSizeCombo);
    HBox orientationBox = new HBox(12, portraitRadio, landscapeRadio);
    orientationBox.setAlignment(Pos.CENTER_LEFT);
    addRow(g, 1, "Orientation", orientationBox);
    addRow(g, 2, "Margin top", row(marginTopSpinner, marginUnitCombo));
    addRow(g, 3, "Margin right", row(marginRightSpinner, new Label("")));
    addRow(g, 4, "Margin bottom", row(marginBottomSpinner, new Label("")));
    addRow(g, 5, "Margin left", row(marginLeftSpinner, new Label("")));
    return g;
  }

  // ── Control configuration ──────────────────────────────────────────────────

  private void configureControls() {
    bodyFontCombo
        .getItems()
        .addAll(
            "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif",
            "Arial, Helvetica, sans-serif",
            "Georgia, \"Times New Roman\", Times, serif",
            "\"Times New Roman\", Times, serif",
            "\"Courier New\", Courier, monospace",
            "Verdana, Geneva, sans-serif",
            "Tahoma, Geneva, sans-serif");
    bodyFontCombo.setEditable(true);
    bodyFontCombo.setPrefWidth(320);

    codeFontCombo
        .getItems()
        .addAll(
            "monospace",
            "\"Courier New\", Courier, monospace",
            "\"Consolas\", monospace",
            "\"Source Code Pro\", monospace",
            "\"Fira Code\", monospace");
    codeFontCombo.setEditable(true);
    codeFontCombo.setPrefWidth(260);

    pageSizeCombo.getItems().addAll("A4", "Letter", "A3", "A5", "Legal");
    pageSizeCombo.setPrefWidth(120);

    marginUnitCombo.getItems().addAll("in", "cm");
    marginUnitCombo.setValue("in");

    portraitRadio.setToggleGroup(orientationGroup);
    landscapeRadio.setToggleGroup(orientationGroup);

    makeSpinnerEditable(bodyFontSizeSpinner);
    makeSpinnerEditable(codeSizeSpinner);
    makeSpinnerEditable(h1Spinner);
    makeSpinnerEditable(h2Spinner);
    makeSpinnerEditable(h3Spinner);
    makeSpinnerEditable(marginTopSpinner);
    makeSpinnerEditable(marginRightSpinner);
    makeSpinnerEditable(marginBottomSpinner);
    makeSpinnerEditable(marginLeftSpinner);

    lineHeightSlider.setShowTickLabels(true);
    lineHeightSlider.setShowTickMarks(true);
    lineHeightSlider.setMajorTickUnit(0.5);
    lineHeightSlider.setMinorTickCount(4);
    lineHeightSlider.setPrefWidth(220);
  }

  private void wireChangeListeners() {
    bodyFontCombo.valueProperty().addListener((o, ov, nv) -> fireChange());
    bodyFontSizeSpinner.valueProperty().addListener((o, ov, nv) -> fireChange());
    lineHeightSlider.valueProperty().addListener((o, ov, nv) -> fireChange());
    bodyColorPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
    h1Spinner.valueProperty().addListener((o, ov, nv) -> fireChange());
    h2Spinner.valueProperty().addListener((o, ov, nv) -> fireChange());
    h3Spinner.valueProperty().addListener((o, ov, nv) -> fireChange());
    headingColorPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
    h1BorderCheckBox.selectedProperty().addListener((o, ov, nv) -> fireChange());
    linkColorPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
    codeFontCombo.valueProperty().addListener((o, ov, nv) -> fireChange());
    codeSizeSpinner.valueProperty().addListener((o, ov, nv) -> fireChange());
    codeBackgroundPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
    blockquoteBorderPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
    blockquoteTextPicker.valueProperty().addListener((o, ov, nv) -> fireChange());
    pageSizeCombo.valueProperty().addListener((o, ov, nv) -> fireChange());
    orientationGroup.selectedToggleProperty().addListener((o, ov, nv) -> fireChange());
    marginTopSpinner.valueProperty().addListener((o, ov, nv) -> fireChange());
    marginRightSpinner.valueProperty().addListener((o, ov, nv) -> fireChange());
    marginBottomSpinner.valueProperty().addListener((o, ov, nv) -> fireChange());
    marginLeftSpinner.valueProperty().addListener((o, ov, nv) -> fireChange());
    marginUnitCombo.valueProperty().addListener((o, ov, nv) -> fireChange());
  }

  private void fireChange() {
    onProfileChanged.accept(buildProfile());
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  /**
   * Sets the callback invoked whenever any style control changes.
   *
   * @param callback the callback to invoke with the updated profile
   */
  public void setOnProfileChanged(Consumer<StyleProfile> callback) {
    this.onProfileChanged = callback == null ? p -> {} : callback;
  }

  /**
   * Populates all controls from the given profile. Does NOT fire the change callback.
   *
   * @param p the profile to apply
   */
  public void applyProfile(StyleProfile p) {
    if (p == null) return;
    profileNameField.setText(p.getName());

    bodyFontCombo.setValue(p.getBodyFont());
    bodyFontSizeSpinner.getValueFactory().setValue(p.getBodyFontSizePt());
    lineHeightSlider.setValue(p.getLineHeight());
    bodyColorPicker.setValue(Color.web(p.getBodyColor()));

    h1Spinner.getValueFactory().setValue(p.getH1SizeEm());
    h2Spinner.getValueFactory().setValue(p.getH2SizeEm());
    h3Spinner.getValueFactory().setValue(p.getH3SizeEm());
    headingColorPicker.setValue(Color.web(p.getHeadingColor()));
    h1BorderCheckBox.setSelected(p.isH1ShowBorder());

    linkColorPicker.setValue(Color.web(p.getLinkColor()));

    codeFontCombo.setValue(p.getCodeFont());
    codeSizeSpinner.getValueFactory().setValue(p.getCodeFontSizePct());
    codeBackgroundPicker.setValue(Color.web(p.getCodeBackground()));

    blockquoteBorderPicker.setValue(Color.web(p.getBlockquoteBorderColor()));
    blockquoteTextPicker.setValue(Color.web(p.getBlockquoteTextColor()));

    pageSizeCombo.setValue(p.getPageSize());
    if ("landscape".equalsIgnoreCase(p.getPageOrientation())) {
      landscapeRadio.setSelected(true);
    } else {
      portraitRadio.setSelected(true);
    }

    String unit = parseUnit(p.getMarginTop());
    marginUnitCombo.setValue(unit);
    marginTopSpinner.getValueFactory().setValue(parseMarginValue(p.getMarginTop()));
    marginRightSpinner.getValueFactory().setValue(parseMarginValue(p.getMarginRight()));
    marginBottomSpinner.getValueFactory().setValue(parseMarginValue(p.getMarginBottom()));
    marginLeftSpinner.getValueFactory().setValue(parseMarginValue(p.getMarginLeft()));
  }

  /**
   * Builds a {@link StyleProfile} from the current control values.
   *
   * @return the profile represented by the current control values
   */
  public StyleProfile buildProfile() {
    StyleProfile p = new StyleProfile(profileNameField.getText().trim());
    p.setBodyFont(bodyFontCombo.getValue());
    p.setBodyFontSizePt(bodyFontSizeSpinner.getValue());
    p.setLineHeight(Math.round(lineHeightSlider.getValue() * 100.0) / 100.0);
    p.setBodyColor(toHex(bodyColorPicker.getValue()));

    p.setH1SizeEm(h1Spinner.getValue());
    p.setH2SizeEm(h2Spinner.getValue());
    p.setH3SizeEm(h3Spinner.getValue());
    p.setHeadingColor(toHex(headingColorPicker.getValue()));
    p.setH1ShowBorder(h1BorderCheckBox.isSelected());

    p.setLinkColor(toHex(linkColorPicker.getValue()));

    p.setCodeFont(codeFontCombo.getValue());
    p.setCodeFontSizePct(codeSizeSpinner.getValue());
    p.setCodeBackground(toHex(codeBackgroundPicker.getValue()));

    p.setBlockquoteBorderColor(toHex(blockquoteBorderPicker.getValue()));
    p.setBlockquoteTextColor(toHex(blockquoteTextPicker.getValue()));

    p.setPageSize(pageSizeCombo.getValue());
    p.setPageOrientation(landscapeRadio.isSelected() ? "landscape" : "portrait");

    String unit = marginUnitCombo.getValue();
    p.setMarginTop(marginTopSpinner.getValue() + unit);
    p.setMarginRight(marginRightSpinner.getValue() + unit);
    p.setMarginBottom(marginBottomSpinner.getValue() + unit);
    p.setMarginLeft(marginLeftSpinner.getValue() + unit);
    return p;
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private static GridPane grid() {
    GridPane g = new GridPane();
    g.setHgap(10);
    g.setVgap(8);
    ColumnConstraints labelCol = new ColumnConstraints(LABEL_COL_WIDTH);
    labelCol.setHalignment(HPos.RIGHT);
    ColumnConstraints valueCol = new ColumnConstraints();
    valueCol.setHgrow(Priority.ALWAYS);
    g.getColumnConstraints().addAll(labelCol, valueCol);
    return g;
  }

  private static void addRow(GridPane g, int row, String labelText, javafx.scene.Node control) {
    Label label = new Label(labelText + ":");
    GridPane.setHalignment(label, HPos.RIGHT);
    g.add(label, 0, row);
    g.add(control, 1, row);
  }

  private static HBox row(javafx.scene.Node... nodes) {
    HBox box = new HBox(6, nodes);
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  private static <T extends Number & Comparable<T>> void makeSpinnerEditable(Spinner<T> spinner) {
    spinner.setEditable(true);
    spinner.setPrefWidth(90);
  }

  private static String toHex(Color color) {
    return String.format(
        "#%02X%02X%02X",
        (int) Math.round(color.getRed() * 255),
        (int) Math.round(color.getGreen() * 255),
        (int) Math.round(color.getBlue() * 255));
  }

  private static double parseMarginValue(String margin) {
    if (margin == null || margin.isBlank()) return 1.0;
    try {
      return Double.parseDouble(margin.replaceAll("[^0-9.]", ""));
    } catch (NumberFormatException e) {
      return 1.0;
    }
  }

  private static String parseUnit(String margin) {
    if (margin == null || margin.isBlank()) return "in";
    return margin.replaceAll("[0-9.]", "").trim().isEmpty()
        ? "in"
        : margin.replaceAll("[0-9.]", "").trim();
  }
}
