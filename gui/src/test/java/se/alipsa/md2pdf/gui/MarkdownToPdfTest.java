package se.alipsa.md2pdf.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.md2pdf.model.Project;
import test.alipsa.md2pdf.gui.fs.RecordingFileAccessBroker;

/**
 * Exercises {@link MarkdownToPdf#populateProjects} and {@link MarkdownToPdf#rememberProjectPaths}
 * directly — the plain {@code List}/{@code Preferences}/{@code FileAccessBroker} logic factored out
 * of the ComboBox-bound methods that call them — rather than through a JavaFX control. Lives in the
 * production package because both are package-private.
 */
public class MarkdownToPdfTest {

  @TempDir Path dir;

  private final Preferences projectsNode =
      Preferences.userRoot().node("md2pdf-test-" + UUID.randomUUID());

  @AfterEach
  public void cleanup() throws BackingStoreException {
    projectsNode.removeNode();
  }

  @Test
  public void loadableProjectsAreAddedAndTheirPreferenceIsKept()
      throws IOException, BackingStoreException {
    Path projectFile = dir.resolve("demo.jpr");
    Project p = new Project();
    p.setName("demo");
    p.setMarkdownFile(dir.resolve("demo.md"));
    Project.save(p, projectFile);
    projectsNode.node("demo").put("projectFile", projectFile.toString());

    List<Project> list = new ArrayList<>();
    MarkdownToPdf.populateProjects(list, projectsNode, new RecordingFileAccessBroker());

    assertEquals(1, list.size());
    assertEquals("demo", list.get(0).getName());
    assertEquals("demo", childrenNames(projectsNode).get(0));
  }

  @Test
  public void aGenuinelyMissingProjectFileIsForgottenAndItsPreferenceDropped()
      throws BackingStoreException, IOException {
    Path projectFile = dir.resolve("gone.jpr");
    projectsNode.node("gone").put("projectFile", projectFile.toString());
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();

    MarkdownToPdf.populateProjects(new ArrayList<>(), projectsNode, broker);

    assertTrue(broker.forgotten().contains(projectFile));
    assertFalse(
        childrenNames(projectsNode).contains("gone"), "the stale preference must be removed");
  }

  @Test
  public void anInaccessibleProjectFileIsKeptWithoutBeingLoadedOrForgotten()
      throws BackingStoreException, IOException {
    Path projectFile = dir.resolve("locked.jpr");
    projectsNode.node("locked").put("projectFile", projectFile.toString());
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();
    broker.setRestorable(projectFile, false);
    List<Project> list = new ArrayList<>();

    MarkdownToPdf.populateProjects(list, projectsNode, broker);

    assertTrue(list.isEmpty(), "an inaccessible project must not be loaded");
    assertTrue(broker.forgotten().isEmpty(), "an inaccessible project must not be forgotten");
    assertTrue(
        childrenNames(projectsNode).contains("locked"),
        "the preference must survive while unreachable");
  }

  @Test
  public void distinguishesADeletedProjectFromAMerelyUnreachableOne()
      throws BackingStoreException, IOException {
    // The regression populateProjectCombo exists to fix: two stored projects that both fail
    // Files.exists() must not be treated the same way. One's access is denied (unreachable right
    // now, e.g. an unmounted volume or a sandbox that has not granted it); the other's is granted
    // but the file itself is gone.
    Path deleted = dir.resolve("deleted.jpr");
    projectsNode.node("deleted").put("projectFile", deleted.toString());
    Path unreachable = dir.resolve("unreachable.jpr");
    projectsNode.node("unreachable").put("projectFile", unreachable.toString());

    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();
    broker.setRestorable(unreachable, false);

    MarkdownToPdf.populateProjects(new ArrayList<>(), projectsNode, broker);

    List<String> remaining = childrenNames(projectsNode);
    assertFalse(remaining.contains("deleted"), "the deleted project's preference must be dropped");
    assertTrue(
        remaining.contains("unreachable"), "the unreachable project's preference must survive");
  }

  @Test
  public void aChildWithNoStoredPathIsRemoved() throws BackingStoreException, IOException {
    projectsNode.node("blank");

    MarkdownToPdf.populateProjects(
        new ArrayList<>(), projectsNode, new RecordingFileAccessBroker());

    assertFalse(childrenNames(projectsNode).contains("blank"));
  }

  @Test
  public void rememberProjectPathsRemembersBothTheProjectFileAndTheMarkdownFile() {
    Project p = new Project();
    p.setName("demo");
    Path markdownFile = dir.resolve("demo.md");
    p.setMarkdownFile(markdownFile);
    Path projectFile = dir.resolve("demo.jpr");
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();

    MarkdownToPdf.rememberProjectPaths(broker, p, projectFile);

    assertEquals(List.of(projectFile, markdownFile), broker.remembered());
  }

  @Test
  public void rememberProjectPathsSkipsTheMarkdownFileWhenThereIsNone() {
    Project p = new Project();
    p.setName("demo");
    Path projectFile = dir.resolve("demo.jpr");
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();

    MarkdownToPdf.rememberProjectPaths(broker, p, projectFile);

    assertEquals(List.of(projectFile), broker.remembered());
  }

  private static List<String> childrenNames(Preferences node) throws BackingStoreException {
    return List.of(node.childrenNames());
  }
}
