package se.alipsa.md2pdf.gui.update;

import java.util.Optional;

/**
 * The result of an update check: a tri-state {@link UpdateCheckOutcome} plus the {@link UpdateInfo}
 * when one is available.
 *
 * @param outcome the outcome of the check
 * @param updateInfo present only when {@code outcome} is {@link
 *     UpdateCheckOutcome#UPDATE_AVAILABLE}
 */
public record UpdateCheckResult(UpdateCheckOutcome outcome, Optional<UpdateInfo> updateInfo) {

  /**
   * Creates a result for a newer, usable release.
   *
   * @param info the available update
   * @return an {@link UpdateCheckOutcome#UPDATE_AVAILABLE} result carrying {@code info}
   */
  public static UpdateCheckResult updateAvailable(UpdateInfo info) {
    return new UpdateCheckResult(UpdateCheckOutcome.UPDATE_AVAILABLE, Optional.of(info));
  }

  /**
   * Creates a result for a conclusively evaluated release that is not newer than the running
   * version.
   *
   * @return an {@link UpdateCheckOutcome#UP_TO_DATE} result
   */
  public static UpdateCheckResult upToDate() {
    return new UpdateCheckResult(UpdateCheckOutcome.UP_TO_DATE, Optional.empty());
  }

  /**
   * Creates a result for when no matching release could be conclusively evaluated.
   *
   * @return an {@link UpdateCheckOutcome#INDETERMINATE} result
   */
  public static UpdateCheckResult indeterminate() {
    return new UpdateCheckResult(UpdateCheckOutcome.INDETERMINATE, Optional.empty());
  }
}
