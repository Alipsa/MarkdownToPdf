package test.alipsa.md2pdf.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.model.Project;

public class ProjectTest {

  @Test
  public void testStoreAndLoad() throws IOException {
    Path basePath = Paths.get(".").toAbsolutePath();
    Path path = basePath.resolve("target/MarkdownToPdfTest.prj");
    Project p = new Project();
    p.setName("Test project");
    p.setMarkdownFile(basePath.resolve("target/MarkdownToPdfTest.md"));
    p.setStyleProfileName("Minimal");

    Project.save(p, path);

    Project p2 = Project.load(path);
    assertEquals("Test project", p2.getName());
    assertEquals(basePath.resolve("target/MarkdownToPdfTest.md").normalize(), p2.getMarkdownFile());
    assertEquals("Minimal", p2.getStyleProfileName());

    Properties props = new Properties();
    try (InputStream is = Files.newInputStream(path)) {
      props.load(is);
    }
    assertEquals("Test project", props.getProperty("name"));
    assertEquals("MarkdownToPdfTest.md", props.getProperty("templateFile"));
    assertEquals("Minimal", props.getProperty("styleProfileName"));
  }

  @Test
  public void testDefaultStyleProfile() throws IOException {
    Path basePath = Paths.get(".").toAbsolutePath();
    Path path = basePath.resolve("target/MarkdownToPdfTestDefault.prj");
    Project p = new Project();
    p.setName("DefaultProfile project");
    p.setMarkdownFile(basePath.resolve("target/test.md"));

    Project.save(p, path);

    Project p2 = Project.load(path);
    assertEquals(
        "Default",
        p2.getStyleProfileName(),
        "Missing styleProfileName should default to 'Default'");
  }

  @Test
  public void testBackwardCompatLoadOldFormat() throws IOException {
    // Old .jpr files won't have styleProfileName — should default to "Default"
    Path path = Paths.get(".").toAbsolutePath().resolve("target/OldFormatTest.prj");
    Properties oldProps = new Properties();
    oldProps.setProperty("name", "Old project");
    oldProps.setProperty("templateFile", "test.md");
    // deliberately omit styleProfileName
    Files.createDirectories(path.getParent());
    try (var out = Files.newOutputStream(path)) {
      oldProps.store(out, "old format");
    }
    // Create the referenced file so load doesn't warn
    Files.createFile(path.getParent().resolve("test.md"));

    Project loaded = Project.load(path);
    assertEquals("Default", loaded.getStyleProfileName());

    Files.deleteIfExists(path.getParent().resolve("test.md"));
  }
}
