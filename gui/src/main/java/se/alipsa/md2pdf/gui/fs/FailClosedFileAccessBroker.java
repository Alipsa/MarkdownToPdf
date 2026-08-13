package se.alipsa.md2pdf.gui.fs;

import java.nio.file.Path;

/**
 * The broker used when a distribution-specific provider was registered but failed to load or
 * construct. Unlike {@link NoOpFileAccessBroker} — used when no provider was ever registered,
 * meaning this build needs no sandboxing at all — a broken provider is evidence that this build
 * does need one, so every path is reported inaccessible rather than freely readable. That keeps a
 * stored path classified {@link PersistedFileState#INACCESSIBLE} rather than {@link
 * PersistedFileState#MISSING}, so its preference survives instead of being silently discarded.
 */
final class FailClosedFileAccessBroker implements FileAccessBroker {

  @Override
  public boolean requiresUserSelectedOutputPath() {
    return false;
  }

  @Override
  public void remember(Path path) {
    // Nothing can be remembered: the provider that would have minted a token never loaded.
  }

  @Override
  public FileAccess restore(Path path) {
    return FileAccess.denied();
  }

  @Override
  public void forget(Path path) {
    // Nothing was remembered through this broker.
  }
}
