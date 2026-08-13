package test.alipsa.md2pdf.gui.fs;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.ServiceConfigurationError;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.fs.FileAccessBroker;

public class FileAccessBrokerTest {

  private static final Path ANY = Path.of("/tmp/whatever.md");

  @Test
  public void withNoProviderTheNoOpBrokerIsUsed() {
    FileAccessBroker broker = FileAccessBroker.firstOrNoop(List.of());
    assertNotNull(broker);
    assertFalse(
        broker.requiresUserSelectedOutputPath(),
        "the ordinary desktop build must retain its temporary preview workflow");
    assertTrue(
        broker.restore(ANY).isGranted(),
        "without a provider every path must be treated as accessible");
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
    assertTrue(
        broker.requiresUserSelectedOutputPath(),
        "a distribution-specific broker signals that an external file needs a chooser grant");
    assertFalse(broker.restore(ANY).isGranted());
  }

  @Test
  public void theFirstProviderWins() {
    RecordingFileAccessBroker first = new RecordingFileAccessBroker();
    RecordingFileAccessBroker second = new RecordingFileAccessBroker();

    assertSame(first, FileAccessBroker.firstOrNoop(List.of(first, second)));
  }

  @Test
  public void aBrokenProviderFailsClosedInsteadOfKillingStartup() {
    // ServiceLoader reports a missing or unconstructable provider as an Error, and the broker is
    // resolved from a field initialiser on the Application subclass — so without a fallback the
    // whole application fails to launch, before any UI or logging exists, in exactly the build
    // that registers a provider. Thrown from hasNext(), matching where ServiceLoader's own lazy
    // lookup actually defers provider resolution to.
    Iterable<FileAccessBroker> broken =
        () ->
            new Iterator<FileAccessBroker>() {
              @Override
              public boolean hasNext() {
                throw new ServiceConfigurationError("provider blew up");
              }

              @Override
              public FileAccessBroker next() {
                throw new NoSuchElementException();
              }
            };

    FileAccessBroker broker = FileAccessBroker.firstOrNoop(broken);

    assertNotNull(broker);
    assertFalse(
        broker.requiresUserSelectedOutputPath(),
        "a broken provider must not change the ordinary desktop preview workflow");
    // Unlike an absent provider, a broken one is evidence this build needs sandboxing — falling
    // back to the no-op broker would grant every path and let an unreachable stored path be
    // classified MISSING and discarded instead of INACCESSIBLE.
    assertFalse(
        broker.restore(ANY).isGranted(), "a broken provider must fail closed, not grant access");
  }

  @Test
  public void aProviderWithAMissingSuperclassFailsClosedToo() {
    // A provider whose superclass or interface is absent from the classpath (e.g. a native-helper
    // base class not shipped alongside it) surfaces as LinkageError rather than
    // ServiceConfigurationError. Startup must be just as resilient to it, and just as careful not
    // to grant access it cannot actually vouch for.
    Iterable<FileAccessBroker> broken =
        () ->
            new Iterator<FileAccessBroker>() {
              @Override
              public boolean hasNext() {
                throw new NoClassDefFoundError("MissingNativeHelper");
              }

              @Override
              public FileAccessBroker next() {
                throw new NoSuchElementException();
              }
            };

    FileAccessBroker broker = FileAccessBroker.firstOrNoop(broken);

    assertNotNull(broker);
    assertFalse(
        broker.restore(ANY).isGranted(), "a broken provider must fail closed, not grant access");
  }

  @Test
  public void getResolvesAProviderFromTheClasspath() {
    // gui/src/test/resources/META-INF/services registers RecordingFileAccessBroker. This is the
    // mechanism the store build uses to inject its security-scoped bookmark implementation, so it
    // is worth proving end to end rather than only through firstOrNoop.
    assertInstanceOf(RecordingFileAccessBroker.class, FileAccessBroker.get());
  }
}
