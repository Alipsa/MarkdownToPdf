package test.alipsa.md2pdf.gui.fs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import se.alipsa.md2pdf.gui.fs.FileAccess;
import se.alipsa.md2pdf.gui.fs.FileAccessBroker;

/**
 * A {@link FileAccessBroker} registered through {@code META-INF/services} so the tests can prove
 * that {@link FileAccessBroker#get()} really resolves a provider from the classpath — the mechanism
 * the Mac App Store build relies on to supply its security-scoped bookmark implementation.
 */
public class RecordingFileAccessBroker implements FileAccessBroker {

  private final List<Path> remembered = new ArrayList<>();
  private final List<Path> forgotten = new ArrayList<>();
  private final List<Path> restoreRequests = new ArrayList<>();
  private boolean restorable = true;

  public void setRestorable(boolean restorable) {
    this.restorable = restorable;
  }

  public List<Path> remembered() {
    return remembered;
  }

  public List<Path> forgotten() {
    return forgotten;
  }

  public List<Path> restoreRequests() {
    return restoreRequests;
  }

  @Override
  public void remember(Path path) {
    remembered.add(path);
  }

  @Override
  public FileAccess restore(Path path) {
    restoreRequests.add(path);
    return restorable ? FileAccess.granted() : FileAccess.denied();
  }

  @Override
  public void forget(Path path) {
    forgotten.add(path);
  }
}
