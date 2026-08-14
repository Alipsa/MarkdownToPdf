package se.alipsa.md2pdf.gui.fs;

import java.nio.file.Path;

/**
 * The broker used when no distribution-specific one is registered, which is the case for every open
 * source build. It holds nothing and reports every path as accessible, so callers behave exactly as
 * they did before the broker existed.
 */
final class NoOpFileAccessBroker implements FileAccessBroker {

  @Override
  public boolean requiresUserSelectedOutputPath() {
    return false;
  }

  @Override
  public void remember(Path path) {
    // Nothing to remember: an unsandboxed process keeps access to whatever it can read.
  }

  @Override
  public FileAccess restore(Path path) {
    return FileAccess.granted();
  }

  @Override
  public void forget(Path path) {
    // Nothing was remembered.
  }
}
