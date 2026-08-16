package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.update.UpdateCheckOutcome;
import se.alipsa.md2pdf.gui.update.UpdateCheckResult;
import se.alipsa.md2pdf.gui.update.UpdateInfo;

class UpdateCheckResultTest {

  private static final UpdateInfo SOME_INFO =
      new UpdateInfo(
          "99.0.0",
          "MarkdownToPdf-v99.0.0",
          "md2pdf-99.0.0-linux-x64.zip",
          "https://example.com/asset.zip",
          "https://example.com/SHA256SUMS",
          "https://github.com/Alipsa/MarkdownToPdf/releases/tag/MarkdownToPdf-v99.0.0");

  @Test
  void updateAvailableCarriesInfo() {
    UpdateCheckResult result = UpdateCheckResult.updateAvailable(SOME_INFO);
    assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, result.outcome());
    assertEquals(Optional.of(SOME_INFO), result.updateInfo());
  }

  @Test
  void upToDateCarriesNoInfo() {
    UpdateCheckResult result = UpdateCheckResult.upToDate();
    assertEquals(UpdateCheckOutcome.UP_TO_DATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  @Test
  void indeterminateCarriesNoInfo() {
    UpdateCheckResult result = UpdateCheckResult.indeterminate();
    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  @Test
  void constructingUpdateAvailableWithoutInfoIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new UpdateCheckResult(UpdateCheckOutcome.UPDATE_AVAILABLE, Optional.empty()));
  }

  @Test
  void constructingUpToDateWithInfoIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new UpdateCheckResult(UpdateCheckOutcome.UP_TO_DATE, Optional.of(SOME_INFO)));
  }

  @Test
  void constructingIndeterminateWithInfoIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new UpdateCheckResult(UpdateCheckOutcome.INDETERMINATE, Optional.of(SOME_INFO)));
  }
}
