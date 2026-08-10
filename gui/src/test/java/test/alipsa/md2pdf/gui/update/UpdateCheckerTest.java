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

  @Test
  void updateAvailableWithMatchingAssetIsReturned() {
    String json =
        releaseJson(
            "v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, json);

    assertTrue(result.isPresent());
    UpdateInfo info = result.get();
    assertEquals("0.1.2", info.latestVersion());
    assertEquals("v0.1.2", info.tagName());
    assertEquals("md2pdf-0.1.2-linux-x64.zip", info.assetName());
    assertEquals("https://example.com/md2pdf-0.1.2-linux-x64.zip", info.downloadUrl());
    assertEquals("https://example.com/SHA256SUMS", info.checksumsUrl());
    assertEquals(
        "https://github.com/Alipsa/MarkdownToPdf/releases/tag/v0.1.2", info.releaseHtmlUrl());
  }

  @Test
  void alreadyLatestVersionReturnsEmpty() {
    String json =
        releaseJson(
            "v0.1.1",
            asset("md2pdf-0.1.1-linux-x64.zip", "https://example.com/md2pdf-0.1.1-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    assertTrue(UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, json).isEmpty());
  }

  @Test
  void updateAvailableButNoAssetForThisPlatformReturnsEmpty() {
    String json =
        releaseJson(
            "v0.1.2",
            asset(
                "md2pdf-0.1.2-macos-aarch64.zip",
                "https://example.com/md2pdf-0.1.2-macos-aarch64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    assertTrue(UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, json).isEmpty());
  }

  @Test
  void missingChecksumsAssetStillReturnsUpdate() {
    // The current latest release (v0.1.0) ships no platform zip and no SHA256SUMS — a release
    // that adds the platform zip before (or without) SHA256SUMS must still be able to notify.
    // Verifying the checksum is the self-apply follow-up's concern, not this check-only PR's.
    String json =
        releaseJson(
            "v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    Optional<UpdateInfo> result =
        UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, json);

    assertTrue(result.isPresent());
    assertNull(result.get().checksumsUrl());
  }

  @Test
  void missingHtmlUrlReturnsEmpty() {
    String json =
        """
        {
          "tag_name": "v0.1.2",
          "assets": [%s]
        }
        """
            .formatted(
                asset(
                    "md2pdf-0.1.2-linux-x64.zip",
                    "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    assertTrue(UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, json).isEmpty());
  }

  @Test
  void authorProfileShapedHtmlUrlReturnsEmpty() {
    // Guards extractScalarBeforeAssets's reliance on GitHub's field ordering: if a future
    // response ever put an author/uploader object (which also carries an "html_url") before the
    // release's own field, this must be treated the same as a missing html_url, not silently
    // surfaced as the release page.
    String json =
        """
        {
          "tag_name": "v0.1.2",
          "html_url": "https://github.com/someuser",
          "assets": [%s]
        }
        """
            .formatted(
                asset(
                    "md2pdf-0.1.2-linux-x64.zip",
                    "https://example.com/md2pdf-0.1.2-linux-x64.zip"));

    assertTrue(UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.LINUX_X64, json).isEmpty());
  }

  @Test
  void unsupportedPlatformReturnsEmpty() {
    String json =
        releaseJson(
            "v0.1.2",
            asset("md2pdf-0.1.2-linux-x64.zip", "https://example.com/md2pdf-0.1.2-linux-x64.zip"),
            asset("SHA256SUMS", "https://example.com/SHA256SUMS"));

    assertTrue(UpdateChecker.parseAndEvaluate("0.1.1", UpdatePlatform.UNSUPPORTED, json).isEmpty());
  }
}
