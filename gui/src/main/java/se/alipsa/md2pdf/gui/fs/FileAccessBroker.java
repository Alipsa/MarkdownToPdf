package se.alipsa.md2pdf.gui.fs;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import org.apache.logging.log4j.LogManager;

/**
 * Grants the application continued access to file paths it stores and re-reads in a later session.
 *
 * <p>On an ordinary desktop this is not a concern: a path remains readable for as long as the file
 * is there, so the default implementation does nothing and reports every path as accessible.
 *
 * <p>It matters when the application runs inside a sandbox that only grants access to files the
 * user picked in a file dialog during the current session. There, a stored path is unreadable on
 * the next launch — and, importantly, {@link java.nio.file.Files#exists} reports it as absent
 * rather than failing — unless the application holds a token for it. A distribution that needs such
 * tokens supplies an implementation through {@link ServiceLoader}; nothing platform specific is
 * needed here.
 *
 * <p>The application calls {@link #remember} as it stores a path while access is still granted, and
 * {@link #restore} before reading a stored path back.
 */
public interface FileAccessBroker {

  /**
   * Records that this path should stay accessible in later sessions. Called while the application
   * still has access to it, which for a sandboxed build is the moment the user chose it.
   *
   * @param path the path about to be persisted
   */
  void remember(Path path);

  /**
   * Regains access to a previously remembered path.
   *
   * @param path a path read back from stored settings
   * @return whether the path can now be read; {@code false} means access could not be regained, and
   *     the caller must not conclude from a failed {@code Files.exists} that the file is gone
   */
  boolean restore(Path path);

  /**
   * Discards anything held for this path.
   *
   * @param path the path to stop tracking
   */
  void forget(Path path);

  /**
   * Returns the broker for this build: the one registered through {@link ServiceLoader}, or a no-op
   * broker when none is.
   *
   * @return the broker to use, never {@code null}
   */
  static FileAccessBroker get() {
    return firstOrNoop(ServiceLoader.load(FileAccessBroker.class));
  }

  /**
   * Returns the first available broker, or a no-op broker when there is none — or when resolving
   * one fails.
   *
   * <p>A provider that cannot be found or constructed surfaces as {@link
   * ServiceConfigurationError}, an {@code Error} rather than an exception. Since the application
   * resolves its broker while constructing the JavaFX {@code Application}, letting that propagate
   * would abort startup before any window or log file exists — in precisely the build that ships a
   * provider. Degrading to the no-op broker instead costs the user their reopened projects and
   * nothing else.
   *
   * @param candidates the brokers to choose from
   * @return the first candidate, or a no-op broker if there is none or it could not be loaded
   */
  static FileAccessBroker firstOrNoop(Iterable<FileAccessBroker> candidates) {
    try {
      Iterator<FileAccessBroker> it = candidates.iterator();
      if (it.hasNext()) {
        return it.next();
      }
    } catch (ServiceConfigurationError e) {
      LogManager.getLogger(FileAccessBroker.class)
          .warn("Could not load a FileAccessBroker; stored file paths may not reopen", e);
    }
    return new NoOpFileAccessBroker();
  }
}
