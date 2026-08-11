package se.alipsa.md2pdf.gui.update;

/**
 * Compares dotted release versions such as {@code "0.1.2"}, without a semver library (per
 * CLAUDE.md's zero-new-dependency constraint). Deliberately fails safe: any version string this
 * cannot parse is treated as never newer than anything, so a malformed or unexpected string never
 * makes the app nag the user or crash — it just silently reports "no update".
 */
public final class VersionComparator {

  private VersionComparator() {}

  /**
   * Returns {@code true} if {@code candidate} is a strictly newer release than {@code current}. A
   * trailing {@code -SNAPSHOT}-style suffix is stripped from each side before the dotted numeric
   * segments are compared; if the numeric segments are otherwise equal, the side that had a suffix
   * stripped is treated as older (so a locally built {@code 0.1.1-SNAPSHOT} is reported as older
   * than a released {@code 0.1.1}, never as ahead of it).
   *
   * @param candidate the version to test
   * @param current the currently running version
   * @return {@code true} when {@code candidate} is newer than {@code current}
   */
  public static boolean isNewer(String candidate, String current) {
    int[] candidateParts = parse(candidate);
    int[] currentParts = parse(current);
    if (candidateParts == null || currentParts == null) {
      return false;
    }
    int cmp = compareParts(candidateParts, currentParts);
    if (cmp != 0) {
      return cmp > 0;
    }
    boolean candidateHadSuffix = hasSuffix(candidate);
    boolean currentHadSuffix = hasSuffix(current);
    return currentHadSuffix && !candidateHadSuffix;
  }

  private static int compareParts(int[] a, int[] b) {
    int length = Math.max(a.length, b.length);
    for (int i = 0; i < length; i++) {
      int av = i < a.length ? a[i] : 0;
      int bv = i < b.length ? b[i] : 0;
      if (av != bv) {
        return Integer.compare(av, bv);
      }
    }
    return 0;
  }

  private static boolean hasSuffix(String version) {
    return version != null && version.indexOf('-') >= 0;
  }

  /** Parses the dotted-numeric prefix of a version string, or {@code null} if it isn't one. */
  private static int[] parse(String version) {
    if (version == null || version.isBlank()) {
      return null;
    }
    String numeric =
        version.indexOf('-') >= 0 ? version.substring(0, version.indexOf('-')) : version;
    String[] segments = numeric.split("\\.");
    if (segments.length == 0) {
      return null;
    }
    int[] parts = new int[segments.length];
    for (int i = 0; i < segments.length; i++) {
      try {
        parts[i] = Integer.parseInt(segments[i].trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return parts;
  }
}
