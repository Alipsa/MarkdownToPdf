package test.alipsa.md2pdf.gui.fs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.md2pdf.gui.fs.FileAccess;
import se.alipsa.md2pdf.gui.fs.PersistedFileState;

public class PersistedFileStateTest {

  @TempDir Path dir;

  @Test
  public void aReadableFileIsLoadable() throws IOException {
    Path file = Files.writeString(dir.resolve("project.jpr"), "name=demo");

    assertEquals(PersistedFileState.LOADABLE, PersistedFileState.of(FileAccess.granted(), file));
  }

  @Test
  public void aDeletedFileIsMissing() {
    Path file = dir.resolve("gone.jpr");

    assertEquals(PersistedFileState.MISSING, PersistedFileState.of(FileAccess.granted(), file));
  }

  @Test
  public void aFileAccessWasRefusedForIsInaccessibleRatherThanMissing() throws IOException {
    // The regression this class exists for. Under a sandbox, a path the process has not been
    // granted access to reports Files.exists() == false even though the file is there. Treating
    // that as "missing" made the caller delete the user's stored project.
    Path file = Files.writeString(dir.resolve("project.jpr"), "name=demo");

    assertEquals(PersistedFileState.INACCESSIBLE, PersistedFileState.of(FileAccess.denied(), file));
  }

  @Test
  public void anUnreachablePathIsNeverReportedAsMissingEvenWhenItReallyIsGone() {
    // Access was refused, so the caller has no evidence either way and must not conclude the file
    // was deleted.
    Path file = dir.resolve("gone.jpr");

    assertEquals(PersistedFileState.INACCESSIBLE, PersistedFileState.of(FileAccess.denied(), file));
  }

  @Test
  public void aMissingParentIsInaccessibleRatherThanMissing() {
    // The regression Copilot flagged: the default no-op broker grants unconditionally, so
    // unplugging the drive holding a project makes Files.exists() report false exactly as it
    // would for a deleted file. A parent directory that is gone too is evidence the whole volume
    // is unreachable, not that this one file was deleted.
    Path file = dir.resolve("unmounted-volume/project.jpr");

    assertEquals(
        PersistedFileState.INACCESSIBLE, PersistedFileState.of(FileAccess.granted(), file));
  }

  @Test
  public void theBrokerHandsBackAccessThatClassifyingCanUse() throws IOException {
    Path file = Files.writeString(dir.resolve("project.jpr"), "name=demo");
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();

    try (FileAccess access = broker.restore(file)) {
      assertEquals(PersistedFileState.LOADABLE, PersistedFileState.of(access, file));
    }

    assertTrue(broker.restoreRequests().contains(file));
  }
}
