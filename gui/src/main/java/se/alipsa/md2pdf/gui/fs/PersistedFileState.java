package se.alipsa.md2pdf.gui.fs;

import java.nio.file.Files;
import java.nio.file.Path;

/** What a caller may conclude about a file path read back from stored settings. */
public enum PersistedFileState {

  /** Access was regained and the file is there. */
  LOADABLE,

  /** Access was regained and the file is genuinely gone, so the stored path is stale. */
  MISSING,

  /**
   * Access could not be regained, so nothing is known about the file. In particular it must not be
   * assumed deleted: a sandbox reports a path it has not granted as absent, and treating that as
   * {@link #MISSING} would discard a setting the user still wants.
   */
  INACCESSIBLE;

  /**
   * Classifies a stored path, asking the broker to regain access first.
   *
   * @param broker the broker for this build
   * @param path a path read back from stored settings
   * @return what the caller may conclude about {@code path}
   */
  public static PersistedFileState of(FileAccessBroker broker, Path path) {
    if (!broker.restore(path)) {
      return INACCESSIBLE;
    }
    return Files.exists(path) ? LOADABLE : MISSING;
  }

  /**
   * Returns whether the stored path may be discarded. Only true for {@link #MISSING}: the file was
   * reachable and is not there, which is the one case that proves the setting is stale.
   *
   * @return whether the caller may forget this path
   */
  public boolean canForget() {
    return this == MISSING;
  }
}
