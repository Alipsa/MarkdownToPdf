package se.alipsa.md2pdf.gui;

import se.alipsa.md2pdf.gui.widgets.ExceptionAlert;
import se.alipsa.md2pdf.model.StyleProfile;
import se.alipsa.md2pdf.model.StyleProfileManager;

import java.io.IOException;

/**
 * Tab hosting the visual {@link StyleEditorPanel}.
 * Delegates profile loading/saving to {@link StyleProfileManager}.
 */
public class StyleTab extends BaseTab {

  private final StyleEditorPanel editorPanel;
  private final StyleProfileManager profileManager;

  public StyleTab(MarkdownToPdf gui, StyleProfileManager profileManager) {
    super(gui, "Style");
    setClosable(false);
    this.profileManager = profileManager;
    this.editorPanel = new StyleEditorPanel(profileManager);
    setContent(editorPanel);
  }

  /** Returns the profile currently shown in the editor. */
  public StyleProfile getActiveProfile() {
    return editorPanel.buildProfile();
  }

  /** Loads the named profile into the editor controls. */
  public void applyProfile(String name) {
    try {
      StyleProfile p = profileManager.load(name);
      if (p != null) {
        editorPanel.applyProfile(p);
      }
    } catch (IOException ex) {
      ExceptionAlert.showAlert("Failed to load style profile '" + name + "'", ex);
    }
  }

  /** Wires a callback so that any control change in the panel triggers the given action. */
  public void setOnProfileChanged(java.util.function.Consumer<StyleProfile> callback) {
    editorPanel.setOnProfileChanged(callback);
  }

  // BaseTab boilerplate — StyleTab has no file to load/save

  @Override
  public CodeTextArea getCodeArea() {
    return null;
  }

  @Override
  public void promptAndLoad() {
    // no file to load
  }

  @Override
  public void save() {
    // no file to save; profile saving is done via the panel's Save button
  }

  @Override
  public void clear() {
    editorPanel.applyProfile(profileManager.getBuiltin("Default"));
  }
}
