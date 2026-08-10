package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.update.VersionComparator;

public class VersionComparatorTest {

  @Test
  void patchVersionIsNewer() {
    assertTrue(VersionComparator.isNewer("0.1.2", "0.1.1"));
  }

  @Test
  void minorVersionIsNewer() {
    assertTrue(VersionComparator.isNewer("0.2.0", "0.1.9"));
  }

  @Test
  void olderVersionIsNotNewer() {
    assertFalse(VersionComparator.isNewer("0.1.1", "0.1.2"));
  }

  @Test
  void equalVersionsAreNotNewer() {
    assertFalse(VersionComparator.isNewer("0.1.1", "0.1.1"));
  }

  @Test
  void missingTrailingSegmentsTreatedAsZero() {
    assertTrue(VersionComparator.isNewer("0.2", "0.1.9"));
    assertFalse(VersionComparator.isNewer("0.1", "0.1.0"));
  }

  @Test
  void releaseIsNewerThanMatchingSnapshot() {
    assertTrue(VersionComparator.isNewer("0.1.1", "0.1.1-SNAPSHOT"));
  }

  @Test
  void snapshotIsNotNewerThanMatchingRelease() {
    assertFalse(VersionComparator.isNewer("0.1.1-SNAPSHOT", "0.1.1"));
  }

  @Test
  void nonNumericCandidateNeverNewer() {
    assertFalse(VersionComparator.isNewer("not-a-version", "0.1.1"));
  }

  @Test
  void nonNumericCurrentNeverTriggersUpdate() {
    assertFalse(VersionComparator.isNewer("0.1.2", "dev"));
  }

  @Test
  void nullOrBlankInputsNeverThrowAndNeverReportNewer() {
    assertFalse(VersionComparator.isNewer(null, "0.1.1"));
    assertFalse(VersionComparator.isNewer("0.1.2", null));
    assertFalse(VersionComparator.isNewer("", ""));
  }
}
