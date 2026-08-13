package se.alipsa.md2pdf.gui.fs;

/**
 * {@link FileAccess} whose answer never changes and whose release does nothing. Covers both
 * outcomes for builds that hold no platform resource, so they allocate nothing per call.
 */
enum ConstantFileAccess implements FileAccess {
  GRANTED(true),
  DENIED(false);

  private final boolean granted;

  ConstantFileAccess(boolean granted) {
    this.granted = granted;
  }

  @Override
  public boolean isGranted() {
    return granted;
  }

  @Override
  public void close() {
    // Nothing was held.
  }
}
