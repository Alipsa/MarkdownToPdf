package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.update.GitHubReleaseJson;
import se.alipsa.md2pdf.gui.update.GitHubReleaseJson.Asset;

public class GitHubReleaseJsonTest {

  // Trimmed to the shape that matters: a release-level html_url/tag_name before the assets
  // array, and each asset carrying its own nested uploader object with an html_url of its own —
  // a naive first-match regex over the whole body would risk grabbing the uploader's html_url
  // instead of the release's.
  private static final String FIXTURE =
      """
      {
        "tag_name": "v0.1.2",
        "html_url": "https:\\/\\/github.com\\/Alipsa\\/MarkdownToPdf\\/releases\\/tag\\/v0.1.2",
        "assets": [
          {
            "name": "md2pdf-0.1.2-linux-x64.zip",
            "browser_download_url": "https://github.com/Alipsa/MarkdownToPdf/releases/download/v0.1.2/md2pdf-0.1.2-linux-x64.zip",
            "uploader": {
              "login": "someone",
              "html_url": "https://github.com/someone"
            }
          },
          {
            "name": "SHA256SUMS",
            "browser_download_url": "https://github.com/Alipsa/MarkdownToPdf/releases/download/v0.1.2/SHA256SUMS",
            "uploader": {
              "login": "someone",
              "html_url": "https://github.com/someone"
            }
          }
        ]
      }
      """;

  @Test
  void extractsTagName() {
    assertEquals("v0.1.2", GitHubReleaseJson.extractTagName(FIXTURE));
  }

  @Test
  void extractsReleaseHtmlUrlNotUploaderHtmlUrl() {
    assertEquals(
        "https://github.com/Alipsa/MarkdownToPdf/releases/tag/v0.1.2",
        GitHubReleaseJson.extractHtmlUrl(FIXTURE));
  }

  @Test
  void extractsAllAssetsWithNameAndUrl() {
    List<Asset> assets = GitHubReleaseJson.extractAssets(FIXTURE);
    assertEquals(2, assets.size());
    assertEquals("md2pdf-0.1.2-linux-x64.zip", assets.get(0).name());
    assertEquals(
        "https://github.com/Alipsa/MarkdownToPdf/releases/download/v0.1.2/md2pdf-0.1.2-linux-x64.zip",
        assets.get(0).browserDownloadUrl());
    assertEquals("SHA256SUMS", assets.get(1).name());
  }

  @Test
  void missingFieldReturnsNullGracefully() {
    String json = "{\"tag_name\": \"v0.1.2\"}";
    assertNull(GitHubReleaseJson.extractHtmlUrl(json));
    assertTrue(GitHubReleaseJson.extractAssets(json).isEmpty());
  }

  @Test
  void emptyAssetsArrayReturnsEmptyList() {
    String json = "{\"tag_name\": \"v0.1.2\", \"assets\": []}";
    assertTrue(GitHubReleaseJson.extractAssets(json).isEmpty());
  }

  @Test
  void extractsBooleanFieldWhenTrue() {
    String json = "{\"tag_name\": \"v0.1.2\", \"draft\": true, \"assets\": []}";
    assertTrue(GitHubReleaseJson.extractBooleanBeforeAssets(json, "draft"));
  }

  @Test
  void extractsBooleanFieldWhenFalse() {
    String json = "{\"tag_name\": \"v0.1.2\", \"draft\": false, \"assets\": []}";
    assertFalse(GitHubReleaseJson.extractBooleanBeforeAssets(json, "draft"));
  }

  @Test
  void absentBooleanFieldDefaultsToFalseWithoutThrowing() {
    String json = "{\"tag_name\": \"v0.1.2\", \"assets\": []}";
    assertFalse(GitHubReleaseJson.extractBooleanBeforeAssets(json, "draft"));
    assertFalse(GitHubReleaseJson.extractBooleanBeforeAssets(json, "prerelease"));
  }

  @Test
  void booleanFieldNestedInsideAssetsIsNotConfusedWithReleaseLevelField() {
    // The release itself has no "draft" field, but an asset's nested object does. That nested
    // occurrence, which appears after "assets", must not be mistaken for the release's own flag.
    String json =
        """
        {
          "tag_name": "v0.1.2",
          "assets": [
            {
              "name": "md2pdf-0.1.2-linux-x64.zip",
              "uploader": {
                "login": "someone",
                "draft": true
              }
            }
          ]
        }
        """;
    assertFalse(GitHubReleaseJson.extractBooleanBeforeAssets(json, "draft"));
  }
}
