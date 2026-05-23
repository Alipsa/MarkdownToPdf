package se.alipsa.md2pdf.model;

import se.alipsa.md2pdf.gui.widgets.Alerts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Project {

  private String name;
  private Path markdownFile;
  private String styleProfileName = "Default";

  public Project() {
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Path getMarkdownFile() {
    return markdownFile;
  }

  public void setMarkdownFile(Path markdownFile) {
    this.markdownFile = markdownFile;
  }

  public String getStyleProfileName() {
    return styleProfileName;
  }

  public void setStyleProfileName(String styleProfileName) {
    this.styleProfileName = styleProfileName == null ? "Default" : styleProfileName;
  }

  @Override
  public String toString() {
    return name;
  }

  public static Project load(Path projectPath) throws IOException {
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(projectPath)) {
      props.load(in);
    }
    Project p = new Project();
    p.setName(props.getProperty("name"));
    String templateFile = props.getProperty("templateFile");
    if (templateFile == null) {
      Alerts.warn("Problem loading project file", "templateFile for project " + p.getName() + " does not exist");
    } else {
      p.setMarkdownFile(absolutePath(Paths.get(templateFile), projectPath));
    }
    p.setStyleProfileName(props.getProperty("styleProfileName", "Default"));
    return p;
  }

  public static void save(Project p, Path projectFilePath) throws IOException {
    Properties props = new Properties();
    if (p.getName() != null) props.setProperty("name", p.getName());
    if (p.getMarkdownFile() != null) {
      props.setProperty("templateFile", pathRelativeTo(p.getMarkdownFile(), projectFilePath).toString());
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
    Path projectDir = projectFilePath.getParent().toAbsolutePath();
    return projectDir.relativize(path);
  }

  private static Path absolutePath(Path path, Path projectFilePath) {
    if (!Files.isDirectory(projectFilePath)) {
      projectFilePath = projectFilePath.getParent();
    }
    return projectFilePath.resolve(path).normalize();
  }
}
