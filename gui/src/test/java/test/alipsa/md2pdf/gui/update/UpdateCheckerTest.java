package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.update.UpdateChecker;
import se.alipsa.md2pdf.gui.update.UpdateInfo;
import se.alipsa.md2pdf.gui.update.UpdatePlatform;

public class UpdateCheckerTest {

  private static String releaseJson(String tag, String... assetLines) {
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
          "html_url": "https://github.com/Alipsa/MarkdownToPdf/releases/tag/%s",
          "assets": [%s]
        }
        """
        .formatted(tag, tag, assets);
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

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertTrue(result.isPresent());
    UpdateInfo info = result.get();
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
  void alreadyLatestVersionReturnsEmpty() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.1",
            asset("md2pdf-0.1.1-linux-x64.zip", "https://example.com/md2pdf-0.1.1-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release))
            .isEmpty());
  }

  @Test
  void updateAvailableButNoAssetForThisPlatformReturnsEmpty() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset(
                "md2pdf-0.1.2-macos-aarch64.zip",
                "https://example.com/md2pdf-0.1.2-macos-aarch64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release))
            .isEmpty());
  }

  @Test
  void missingChecksumsAssetStillReturnsUpdate() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release));

    assertTrue(result.isPresent());
    assertNull(result.get().checksumsUrl());
  }

  @Test
  void missingHtmlUrlReturnsEmpty() {
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

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release))
            .isEmpty());
  }

  @Test
  void authorProfileShapedHtmlUrlReturnsEmpty() {
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

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, releasesArray(release))
            .isEmpty());
  }

  @Test
  void unsupportedPlatformReturnsEmpty() {
    String release =
        releaseJson(
            "MarkdownToPdf-v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.UNSUPPORTED, releasesArray(release))
            .isEmpty());
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

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate(
            "0.1.1", UpdatePlatform.LINUX_X64, releasesArray(libRelease, guiRelease));

    assertTrue(result.isPresent());
    assertEquals("0.1.2", result.get().latestVersion());
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

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate(
            "0.1.0", UpdatePlatform.LINUX_X64, releasesArray(olderGuiRelease, newerGuiRelease));

    assertTrue(result.isPresent());
    assertEquals("0.1.2", result.get().latestVersion());
  }

  @Test
  void noMatchingPrefixInArrayReturnsEmpty() {
    String libRelease =
        releaseJson(
            "md2pdf-v9.9.9",
            asset("md2pdf-9.9.9-sources.jar", "https://example.com/md2pdf-9.9.9-sources.jar"));

    assertTrue(
        UpdateChecker.parseAndEvaluate("0.1.0", UpdatePlatform.LINUX_X64, releasesArray(libRelease))
            .isEmpty());
  }
}
