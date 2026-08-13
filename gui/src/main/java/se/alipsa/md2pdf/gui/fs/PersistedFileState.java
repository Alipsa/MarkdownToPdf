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
   * <p>A granted access says nothing about whether the volume holding the file is actually mounted
   * — the no-op broker used by every open source build grants unconditionally, so unplugging a
   * drive makes {@link Files#exists} report {@code false} exactly as if the file had been deleted.
   * A missing parent directory is evidence of the former rather than the latter: the file's own
   * containment vanished too, not just the file, so that case is reported as {@link #INACCESSIBLE}
   * rather than {@link #MISSING}. This is a heuristic, not a guarantee — a mount point that a
   * desktop leaves behind as an empty directory after unmounting still passes this check.
   *
   * <p>{@link Files#exists} and {@link Files#notExists} are not complements: both report {@code
   * false} when existence could not be determined at all, for example because an ACL or a flaky
   * mount raised an I/O error partway through the check. That is a reachability problem, not
   * evidence of deletion, so it is reported as {@link #INACCESSIBLE} even when the parent directory
   * is visible.
   *
   * @param access access obtained from {@link FileAccessBroker#restore(Path)} for {@code path}
   * @param path a path read back from stored settings
   * @return what the caller may conclude about {@code path}
   */
  public static PersistedFileState of(FileAccess access, Path path) {
    if (!access.isGranted()) {
      return INACCESSIBLE;
    }
    if (Files.exists(path)) {
      return LOADABLE;
    }
    if (!Files.notExists(path)) {
      return INACCESSIBLE;
    }
    Path parent = path.getParent();
    return parent == null || Files.exists(parent) ? MISSING : INACCESSIBLE;
  }
}
