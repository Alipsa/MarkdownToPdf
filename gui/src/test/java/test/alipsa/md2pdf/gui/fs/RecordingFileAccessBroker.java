package test.alipsa.md2pdf.gui.fs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final Map<Path, Boolean> restorableByPath = new HashMap<>();
  private boolean restorable = true;

  public void setRestorable(boolean restorable) {
    this.restorable = restorable;
  }

  /**
   * Overrides restorability for one specific path, independently of every other path — needed to
   * drive a scenario with several stored projects that must be classified differently from one
   * another in the same call.
   *
   * @param path the path this override applies to
   * @param restorable whether {@link #restore} grants access to {@code path}
   */
  public void setRestorable(Path path, boolean restorable) {
    restorableByPath.put(path, restorable);
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
    boolean granted = restorableByPath.getOrDefault(path, restorable);
    return granted ? FileAccess.granted() : FileAccess.denied();
  }

  @Override
  public void forget(Path path) {
    forgotten.add(path);
  }
}
