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
   * Classifies a stored path in the light of the access obtained for it.
   *
   * <p>The caller opens the access rather than this method, because the answer alone is not enough:
   * a {@link #LOADABLE} path still has to be read, and that read must happen while the access is
   * open.
   *
   * @param access access obtained from {@link FileAccessBroker#restore(Path)} for {@code path}
   * @param path a path read back from stored settings
   * @return what the caller may conclude about {@code path}
   */
  public static PersistedFileState of(FileAccess access, Path path) {
    if (!access.isGranted()) {
      return INACCESSIBLE;
    }
    return Files.exists(path) ? LOADABLE : MISSING;
  }
}
