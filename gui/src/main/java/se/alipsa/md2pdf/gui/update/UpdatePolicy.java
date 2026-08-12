package se.alipsa.md2pdf.gui.update;

/**
 * Decides whether this build is allowed to check for updates.
 *
 * <p>Update checks are on by default, which is what the open source distribution wants. Builds
 * distributed through a store that forbids pointing users at an out-of-band download — the Mac App
 * Store, whose review guideline 2.4.5 disallows it — turn them off by starting the application with
 * {@code -Dmd2pdf.update.enabled=false}. When disabled, no check runs and the Help menu offers no
 * update items.
 */
public final class UpdatePolicy {

  /** System property that turns update checking off when set to {@code false}. */
  public static final String ENABLED_PROPERTY = "md2pdf.update.enabled";

  private UpdatePolicy() {}

  /**
   * Returns whether this build may check for updates.
   *
   * <p>Only an explicit {@code false} disables them. Anything else — including an unrecognised
   * value or an empty one — leaves them on, because removing the update UI is a deliberate act for
   * one distribution and a typo should not quietly strip it from an ordinary build.
   *
   * @return {@code true} unless {@link #ENABLED_PROPERTY} is set to {@code false}
   */
  public static boolean isEnabled() {
    String value = System.getProperty(ENABLED_PROPERTY);
    return value == null || !"false".equalsIgnoreCase(value.trim());
  }
}
