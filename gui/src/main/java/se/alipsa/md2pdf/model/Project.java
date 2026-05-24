package se.alipsa.md2pdf.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import se.alipsa.md2pdf.gui.widgets.Alerts;

/** Holds project-level metadata: the project name, Markdown source file, and style profile name. */
public class Project {

  private String name;
  private Path markdownFile;
  private String styleProfileName = "Default";

  /** Creates an empty project with default values. */
  public Project() {}

  /**
   * Returns the project name.
   *
   * @return the project name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the project name.
   *
   * @param name the project name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the path to the project's Markdown source file.
   *
   * @return the Markdown file path, or {@code null} if not yet set
   */
  public Path getMarkdownFile() {
    return markdownFile;
  }

  /**
   * Sets the path to the project's Markdown source file.
   *
   * @param markdownFile the Markdown file path
   */
  public void setMarkdownFile(Path markdownFile) {
    this.markdownFile = markdownFile;
  }

  /**
   * Returns the name of the style profile to use when rendering this project.
   *
   * @return the style profile name; defaults to {@code "Default"}
   */
  public String getStyleProfileName() {
    return styleProfileName;
  }

  /**
   * Sets the style profile name. Falls back to {@code "Default"} when {@code null} is passed.
   *
   * @param styleProfileName the style profile name, or {@code null} to reset to the default
   */
  public void setStyleProfileName(String styleProfileName) {
    this.styleProfileName = styleProfileName == null ? "Default" : styleProfileName;
  }

  @Override
  public String toString() {
    return name;
  }

  /**
   * Loads a project from a {@code .jpr} properties file.
   *
   * @param projectPath path to the project file
   * @return the populated {@link Project}
   * @throws IOException if the file cannot be read
   */
  public static Project load(Path projectPath) throws IOException {
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(projectPath)) {
      props.load(in);
    }
    Project p = new Project();
    p.setName(props.getProperty("name"));
    String templateFile = props.getProperty("templateFile");
    if (templateFile == null) {
      Alerts.warn(
          "Problem loading project file",
          "templateFile for project " + p.getName() + " does not exist");
    } else {
      p.setMarkdownFile(absolutePath(Paths.get(templateFile), projectPath));
    }
    p.setStyleProfileName(props.getProperty("styleProfileName", "Default"));
    return p;
  }

  /**
   * Saves a project to a {@code .jpr} properties file, creating parent directories if needed.
   *
   * @param p the project to save
   * @param projectFilePath the destination file path
   * @throws IOException if the file cannot be written
   */
  public static void save(Project p, Path projectFilePath) throws IOException {
    Properties props = new Properties();
    if (p.getName() != null) props.setProperty("name", p.getName());
    if (p.getMarkdownFile() != null) {
      props.setProperty(
          "templateFile", pathRelativeTo(p.getMarkdownFile(), projectFilePath).toString());
    }
    props.setProperty("styleProfileName", p.getStyleProfileName());
    Path dir = projectFilePath.getParent();
    if (dir != null && !Files.exists(dir)) {
      Files.createDirectories(dir);
    }
    try (OutputStream out = Files.newOutputStream(projectFilePath)) {
      props.store(out, "MarkdownToPdf project file");
    }
  }

  private static Path pathRelativeTo(Path path, Path projectFilePath) {
    Path parent = projectFilePath.getParent();
    Path projectDir = (parent != null ? parent : projectFilePath).toAbsolutePath();
    return projectDir.relativize(path);
  }

  private static Path absolutePath(Path path, Path projectFilePath) {
    if (!Files.isDirectory(projectFilePath)) {
      projectFilePath = projectFilePath.getParent();
      if (projectFilePath == null) return path.normalize();
    }
    return projectFilePath.resolve(path).normalize();
  }
}
