package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.update.UpdateCheckOutcome;
import se.alipsa.md2pdf.gui.update.UpdateCheckResult;
import se.alipsa.md2pdf.gui.update.UpdateChecker;
import se.alipsa.md2pdf.gui.update.UpdatePlatform;

public class UpdateCheckerTest {

  private static String releaseJson(String tag, String... assetLines) {
    return releaseJson(tag, false, false, assetLines);
  }

  private static String releaseJson(
      String tag, boolean draft, boolean prerelease, String... assetLines) {
    StringBuilder assets = new StringBuilder();
    for (int i = 0; i < assetLines.length; i++) {
      if (i > 0) {
        assets.append(',');
      }
      assets.append(assetLines[i]);
    }
    return """
        {
          "tag_name": "%s",
          "draft": %s,
          "prerelease": %s,
          "html_url": "https://github.com/Alipsa/MarkdownToPdf/releases/tag/%s",
          "assets": [%s]
        }
        """
        .formatted(tag, draft, prerelease, tag, assets);
  }

  private static String asset(String name, String url) {
    return """
        {"name": "%s", "browser_download_url": "%s"}
        """
        .formatted(name, url)
        .strip();
  }

  // GET /releases returns a top-level array, not a single release — releases/latest is
  // repo-wide and would let an unrelated lib release (tagged md2pdf-v*) shadow the actual
  // latest gui release, or return no platform zips at all.
  private static String releasesArray(String... releaseJsons) {
    return "[" + String.join(",", releaseJsons) + "]";
  }

  @Test
  void updateAvailableWithMatchingAssetIsReturned() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, result.outcome());
    assertTrue(result.updateInfo().isPresent());
    var info = result.updateInfo().get();
    assertEquals("0.1.2", info.latestVersion());
    assertEquals("MarkdownToPdf-v0.1.2", info.tagName());
    assertEquals("md2pdf-0.1.2-linux-x64.zip", info.assetName());
    assertEquals("https://example.com/md2pdf-0.1.2-linux-x64.zip", info.downloadUrl());
    assertEquals("https://example.com/SHA256SUMS", info.checksumsUrl());
    assertEquals(
        "https://github.com/Alipsa/MarkdownToPdf/releases/tag/MarkdownToPdf-v0.1.2",
        info.releaseHtmlUrl());
  }

  @Test
  void alreadyLatestVersionIsUpToDate() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.1",
            asset("md2pdf-0.1.1-linux-x64.zip", "https://example.com/md2pdf-0.1.1-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertEquals(UpdateCheckOutcome.UP_TO_DATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  @Test
  void updateAvailableButNoAssetForThisPlatformIsIndeterminate() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset(
                "md2pdf-0.1.2-macos-aarch64.zip",
                "https://example.com/md2pdf-0.1.2-macos-aarch64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  @Test
  void missingChecksumsAssetStillReturnsUpdate() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, result.outcome());
    assertTrue(result.updateInfo().isPresent());
    assertNull(result.updateInfo().get().checksumsUrl());
  }

  @Test
  void missingHtmlUrlIsIndeterminate() {
    String release =
        """
        {
          "tag_name": "MarkdownToPdf-v0.1.2",
          "assets": [%s]
        }
        """
            .formatted(
                asset(
                    "md2pdf-0.1.2-linux-x64.zip",
                    "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  @Test
  void authorProfileShapedHtmlUrlIsIndeterminate() {
    String release =
        """
        {
          "tag_name": "MarkdownToPdf-v0.1.2",
          "html_url": "https://github.com/someuser",
          "assets": [%s]
        }
        """
            .formatted(
                asset(
                    "md2pdf-0.1.2-linux-x64.zip",
                    "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  @Test
  void unsupportedPlatformIsIndeterminate() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.UNSUPPORTED, releasesArray(release));

    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  @Test
  void libReleaseInTheArrayIsIgnored() {
    // A newer lib release (md2pdf-v*) must never shadow the actual latest gui release.
    String libRelease =
        releaseJson(
            "md2pdf-v9.9.9",
            asset("md2pdf-9.9.9-sources.jar", "https://example.com/md2pdf-9.9.9-sources.jar"));
    String guiRelease =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate(
            "0.1.1", UpdatePlatform.LINUX_X64, releasesArray(libRelease, guiRelease));

    assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, result.outcome());
    assertEquals("0.1.2", result.updateInfo().get().latestVersion());
  }

  @Test
  void picksHighestVersionAmongMatchingPrefixRegardlessOfArrayOrder() {
    // GitHub sorts /releases by the tagged commit's date, not publish time, so a gui release
    // cut from an older commit is not guaranteed to sort above a newer one — "first match" is
    // not a safe selection rule. The older release is placed first here on purpose.
    String olderGuiRelease =
        releaseJson(
            "MarkdownToPdf-v0.1.1",
            asset("md2pdf-0.1.1-linux-x64.zip", "https://example.com/md2pdf-0.1.1-linux-x64.zip"));
    String newerGuiRelease =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate(
            "0.1.0", UpdatePlatform.LINUX_X64, releasesArray(olderGuiRelease, newerGuiRelease));

    assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, result.outcome());
    assertEquals("0.1.2", result.updateInfo().get().latestVersion());
  }

  @Test
  void noMatchingPrefixInArrayIsIndeterminate() {
    String libRelease =
        releaseJson(
            "md2pdf-v9.9.9",
            asset("md2pdf-9.9.9-sources.jar", "https://example.com/md2pdf-9.9.9-sources.jar"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate(
            "0.1.0", UpdatePlatform.LINUX_X64, releasesArray(libRelease));

    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  // ── Finding 1: prereleases and drafts must never be advertised as updates ──────────────

  @Test
  void draftReleaseIsSkippedInFavorOfOlderNonDraftRelease() {
    String draftNewer =
        releaseJson(
            "MarkdownToPdf-v0.4.0",
            true,
            false,
            asset("md2pdf-0.4.0-linux-x64.zip", "https://example.com/md2pdf-0.4.0-linux-x64.zip"));
    String olderReal =
        releaseJson(
            "MarkdownToPdf-v0.2.0",
            asset("md2pdf-0.2.0-linux-x64.zip", "https://example.com/md2pdf-0.2.0-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate(
            "0.1.0", UpdatePlatform.LINUX_X64, releasesArray(draftNewer, olderReal));

    assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, result.outcome());
    assertEquals("0.2.0", result.updateInfo().get().latestVersion());
  }

  @Test
  void prereleaseIsSkippedInFavorOfOlderNonPrereleaseRelease() {
    String prereleaseNewer =
        releaseJson(
            "MarkdownToPdf-v0.4.0-rc1",
            false,
            true,
            asset(
                "md2pdf-0.4.0-rc1-linux-x64.zip",
                "https://example.com/md2pdf-0.4.0-rc1-linux-x64.zip"));
    String olderReal =
        releaseJson(
            "MarkdownToPdf-v0.2.0",
            asset("md2pdf-0.2.0-linux-x64.zip", "https://example.com/md2pdf-0.2.0-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate(
            "0.1.0", UpdatePlatform.LINUX_X64, releasesArray(prereleaseNewer, olderReal));

    assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, result.outcome());
    assertEquals("0.2.0", result.updateInfo().get().latestVersion());
  }

  @Test
  void onlyDraftCandidateAvailableIsIndeterminate() {
    String draftOnly =
        releaseJson(
            "MarkdownToPdf-v0.4.0",
            true,
            false,
            asset("md2pdf-0.4.0-linux-x64.zip", "https://example.com/md2pdf-0.4.0-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate("0.1.0", UpdatePlatform.LINUX_X64, releasesArray(draftOnly));

    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  // ── Finding 2: one unparseable tag must not permanently poison selection ────────────────

  @Test
  void unparseableFirstCandidateDoesNotPoisonSelectionOfLaterWellFormedNewerRelease() {
    String unparseableFirst =
        releaseJson(
            "MarkdownToPdf-v0.4.0.RC1",
            asset(
                "md2pdf-0.4.0.RC1-linux-x64.zip",
                "https://example.com/md2pdf-0.4.0.RC1-linux-x64.zip"));
    String wellFormedNewer =
        releaseJson(
            "MarkdownToPdf-v0.4.0",
            asset("md2pdf-0.4.0-linux-x64.zip", "https://example.com/md2pdf-0.4.0-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate(
            "0.1.0", UpdatePlatform.LINUX_X64, releasesArray(unparseableFirst, wellFormedNewer));

    assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, result.outcome());
    assertEquals("0.4.0", result.updateInfo().get().latestVersion());
  }

  // ── Finding 3: a non-array top-level response must be diagnosable, not silently mis-scanned ─

  @Test
  void nonArrayTopLevelResponseIsIndeterminate() {
    // A single release object (not the expected top-level array) whose only "[" is the nested
    // assets array. Scanning from the first "[" would lock onto assets and find no tag_name.
    String singleReleaseObject =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    UpdateCheckResult result =
        UpdateChecker.parseAndEvaluate("0.1.0", UpdatePlatform.LINUX_X64, singleReleaseObject);

    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }
}
