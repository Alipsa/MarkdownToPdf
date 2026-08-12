package se.alipsa.md2pdf.gui.fs;

/**
 * A period during which a file path may be read, handed back by {@link
 * FileAccessBroker#restore(java.nio.file.Path)}.
 *
 * <p>Access is a resource rather than a fact, because on some platforms granting it takes a
 * matching release: macOS pairs {@code startAccessingSecurityScopedResource} with {@code
 * stopAccessingSecurityScopedResource}, and a process that never stops accumulates grants for its
 * whole lifetime. Returning something closeable means a caller cannot forget the second half.
 *
 * <p>Close it once the file no longer needs reading. Where that is depends on the caller: a one-off
 * read closes immediately, while a document the editor writes back to must hold access open for as
 * long as it stays open.
 */
public interface FileAccess extends AutoCloseable {

  /**
   * Returns whether access was actually granted. A caller that gets {@code false} learns nothing
   * about the file — in particular it must not conclude the file is absent.
   *
   * @return whether the path may be read
   */
  boolean isGranted();

  /**
   * Ends this period of access. Narrowed from {@link AutoCloseable#close()} so callers need no
   * checked exception handling, and safe to call more than once.
   */
  @Override
  void close();

  /**
   * Returns access that is always granted and needs no release, which is what an unsandboxed build
   * uses.
   *
   * @return granted access
   */
  static FileAccess granted() {
    return ConstantFileAccess.GRANTED;
  }

  /**
   * Returns access that was refused.
   *
   * @return denied access
   */
  static FileAccess denied() {
    return ConstantFileAccess.DENIED;
  }
}
