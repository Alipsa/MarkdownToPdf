package test.alipsa.md2pdf.gui.fs;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.fs.FileAccessBroker;

public class FileAccessBrokerTest {

  private static final Path ANY = Path.of("/tmp/whatever.md");

  @Test
  public void withNoProviderTheNoOpBrokerIsUsed() {
    FileAccessBroker broker = FileAccessBroker.firstOrNoop(List.of());
    assertNotNull(broker);
    assertTrue(broker.restore(ANY), "without a provider every path must be treated as accessible");
  }

  @Test
  public void theNoOpBrokerIgnoresRememberAndForget() {
    FileAccessBroker broker = FileAccessBroker.firstOrNoop(List.of());
    assertDoesNotThrow(() -> broker.remember(ANY));
    assertDoesNotThrow(() -> broker.forget(ANY));
  }

  @Test
  public void aRegisteredProviderIsUsedInsteadOfTheNoOp() {
    RecordingFileAccessBroker provided = new RecordingFileAccessBroker();
    provided.setRestorable(false);

    FileAccessBroker broker = FileAccessBroker.firstOrNoop(List.of(provided));

    assertSame(provided, broker);
    assertFalse(broker.restore(ANY));
  }

  @Test
  public void theFirstProviderWins() {
    RecordingFileAccessBroker first = new RecordingFileAccessBroker();
    RecordingFileAccessBroker second = new RecordingFileAccessBroker();

    assertSame(first, FileAccessBroker.firstOrNoop(List.of(first, second)));
  }

  @Test
  public void getResolvesAProviderFromTheClasspath() {
    // gui/src/test/resources/META-INF/services registers RecordingFileAccessBroker. This is the
    // mechanism the store build uses to inject its security-scoped bookmark implementation, so it
    // is worth proving end to end rather than only through firstOrNoop.
    assertInstanceOf(RecordingFileAccessBroker.class, FileAccessBroker.get());
  }
}
