package se.alipsa.md2pdf.gui;

import java.io.File;
import java.nio.file.Path;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;

/**
 * Base class for all editor tabs. Tracks the file backing the tab content, dirty state (unsaved
 * changes), and provides a shared reference to the main application window.
 */
public abstract class BaseTab extends Tab {

  /** The file currently backing this tab's content, or {@code null} if not yet saved. */
  protected File file = null;

  /** Whether the tab content has been modified since the last save. */
  protected boolean isChanged = false;

  private Tooltip saveToolTip = new Tooltip("Save");

  /** Reference to the main application window. */
  protected MarkdownToPdf gui;

  /**
   * Creates a tab with the given title and registers it with the main window.
   *
   * @param gui the main application window
   * @param title the initial tab title
   */
  public BaseTab(MarkdownToPdf gui, String title) {
    this.gui = gui;
    defaultTitle = title;
    setTitle(title);
  }

  /** The default (unsaved) title for this tab, used when clearing content. */
  protected String defaultTitle;

  /**
   * Displays a message in the application status bar.
   *
   * @param text the status message
   */
  protected void setStatus(String text) {
    gui.setStatus(text);
  }

  /**
   * Returns the code editor component for this tab, or {@code null} if the tab has no editable code
   * area.
   *
   * @return the active code area, may be {@code null}
   */
  public abstract CodeTextArea getCodeArea();

  /**
   * Marks the tab as containing unsaved changes by appending {@code *} to the title. No-op if
   * already marked.
   */
  public void contentChanged() {
    if (!getTitle().endsWith("*") && !isChanged) {
      setTitle(getTitle() + "*");
      isChanged = true;
    }
  }

  /** Marks the tab as saved by removing the {@code *} suffix from the title. */
  public void contentSaved() {
    setTitle(getTitle().replace("*", ""));
    isChanged = false;
  }

  /**
   * Returns the current tab title (the visible tab label).
   *
   * @return the tab label text
   */
  public String getTitle() {
    return getText();
  }

  /**
   * Sets the tab title and updates the save tooltip accordingly.
   *
   * @param title the new tab label
   */
  public void setTitle(String title) {
    setText(title);
    saveToolTip.setText("Save " + title.replace("*", ""));
  }

  /**
   * Returns whether this tab has unsaved changes.
   *
   * @return {@code true} if the content has been modified since the last save
   */
  public boolean isChanged() {
    return isChanged;
  }

  /** Opens a file chooser and loads the selected file into this tab. */
  public abstract void promptAndLoad();

  /** Saves the current tab content to the backing file, prompting for a path if unsaved. */
  public abstract void save();

  /**
   * Associates this tab with a file. Resets the title to the default when {@code file} is {@code
   * null}.
   *
   * @param file the file to associate, or {@code null} to clear the association
   */
  public void setFile(File file) {
    this.file = file;
    if (file == null) {
      setTitle(defaultTitle);
    }
  }

  /**
   * Associates this tab with a file given as a {@link Path}.
   *
   * @param dataFile the path to associate; ignored if {@code null}
   */
  public void setFile(Path dataFile) {
    if (dataFile != null) {
      file = dataFile.toFile();
    }
  }

  /** Clears the tab content and resets the title and file association. */
  public abstract void clear();
}
