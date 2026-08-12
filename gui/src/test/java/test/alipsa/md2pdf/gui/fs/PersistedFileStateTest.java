package test.alipsa.md2pdf.gui.fs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.md2pdf.gui.fs.PersistedFileState;

public class PersistedFileStateTest {

  @TempDir Path dir;

  @Test
  public void aReadableFileIsLoadable() throws IOException {
    Path file = Files.writeString(dir.resolve("project.jpr"), "name=demo");
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();

    assertEquals(PersistedFileState.LOADABLE, PersistedFileState.of(broker, file));
  }

  @Test
  public void aDeletedFileIsMissing() {
    Path file = dir.resolve("gone.jpr");
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();

    assertEquals(PersistedFileState.MISSING, PersistedFileState.of(broker, file));
  }

  @Test
  public void aFileTheBrokerCannotReachIsInaccessibleRatherThanMissing() throws IOException {
    // The regression this class exists for. Under a sandbox, a path the process has not been
    // granted access to reports Files.exists() == false even though the file is there. Treating
    // that as "missing" made the caller delete the user's stored project.
    Path file = Files.writeString(dir.resolve("project.jpr"), "name=demo");
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();
    broker.setRestorable(false);

    assertEquals(PersistedFileState.INACCESSIBLE, PersistedFileState.of(broker, file));
  }

  @Test
  public void anUnreachablePathIsNeverReportedAsMissingEvenWhenItReallyIsGone() {
    // Access could not be regained, so the caller has no evidence either way and must not
    // conclude the file was deleted.
    Path file = dir.resolve("gone.jpr");
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();
    broker.setRestorable(false);

    assertEquals(PersistedFileState.INACCESSIBLE, PersistedFileState.of(broker, file));
  }

  @Test
  public void classifyingAsksTheBrokerToRestoreAccess() throws IOException {
    Path file = Files.writeString(dir.resolve("project.jpr"), "name=demo");
    RecordingFileAccessBroker broker = new RecordingFileAccessBroker();

    PersistedFileState.of(broker, file);

    assertTrue(broker.restoreRequests().contains(file));
  }
}
