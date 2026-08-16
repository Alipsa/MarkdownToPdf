package se.alipsa.md2pdf.gui.update;

/**
 * The outcome of an update check. {@link #UPDATE_AVAILABLE} always carries an {@link UpdateInfo};
 * {@link #UP_TO_DATE} and {@link #INDETERMINATE} never do.
 */
public enum UpdateCheckOutcome {
  /** A newer, non-draft, non-prerelease release with a usable asset for this platform exists. */
  UPDATE_AVAILABLE,
  /** A specific matching release was evaluated and found not newer than the running version. */
  UP_TO_DATE,
  /**
   * No matching release could be conclusively evaluated — e.g. none was found in the fetched page,
   * every candidate was a draft/prerelease or had an unparseable version, or a matching release was
   * found but its data could not be trusted (bad html_url) or had no asset for this platform. This
   * is NOT the same as "up to date": whether a real update exists is genuinely unknown.
   */
  INDETERMINATE
}
