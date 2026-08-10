package se.alipsa.md2pdf.gui.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Checks GitHub Releases for a MarkdownToPdf version newer than the one currently running. Uses
 * only {@link HttpClient} and {@link GitHubReleaseJson}'s hand-written field extraction — no JSON
 * or HTTP library dependency is added, per CLAUDE.md's zero-new-dependency constraint for the
 * {@code gui} module.
 */
public class UpdateChecker {

  /** System property that overrides the GitHub API URL, for manual QA against a local fixture. */
  public static final String API_URL_PROPERTY = "md2pdf.update.apiUrl";

  private static final String DEFAULT_API_URL =
      "https://api.github.com/repos/Alipsa/MarkdownToPdf/releases/latest";

  private static final Logger LOGGER = LogManager.getLogger(UpdateChecker.class);

  /**
   * Fetches the latest GitHub release and returns update info if it is newer than {@code
   * currentVersion} and ships an asset for this platform.
   *
   * @throws UpdateCheckException on any network, HTTP-status or interrupt failure
   */
  public Optional<UpdateInfo> checkForUpdate(String currentVersion) throws UpdateCheckException {
    String apiUrl = System.getProperty(API_URL_PROPERTY, DEFAULT_API_URL);
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    HttpResponse<String> response;
    try (HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UpdateCheckException("Failed to reach " + apiUrl, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpdateCheckException("Interrupted while checking for updates", e);
    }
    if (response.statusCode() != 200) {
      throw new UpdateCheckException(
          "GitHub returned HTTP " + response.statusCode() + " for " + apiUrl);
    }
    return parseAndEvaluate(currentVersion, UpdatePlatform.detectCurrent(), response.body());
  }

  /**
   * Pure evaluation of a GitHub releases/latest JSON response against the currently running version
   * and platform. No network access — used directly by tests.
   *
   * <p>Only the platform's own release asset and the release page URL are required; the {@code
   * SHA256SUMS} checksum asset is captured when present but not required, since nothing in this
   * check-only feature downloads or verifies it yet (that lands with the self-apply follow-up) —
   * requiring it here would silently suppress every notification against a release that predates
   * checksum publishing, such as the current {@code v0.1.0}.
   */
  public static Optional<UpdateInfo> parseAndEvaluate(
      String currentVersion, UpdatePlatform platform, String responseJson) {
    if (platform == UpdatePlatform.UNSUPPORTED) {
      LOGGER.info("Skipping update check: no release archive for this platform.");
      return Optional.empty();
    }
    String tagName = GitHubReleaseJson.extractTagName(responseJson);
    if (tagName == null) {
      LOGGER.info("Skipping update check: release response had no tag_name.");
      return Optional.empty();
    }
    String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
    if (!VersionComparator.isNewer(latestVersion, currentVersion)) {
      LOGGER.info(
          "No update available: latest release {} is not newer than the running {}.",
          latestVersion,
          currentVersion);
      return Optional.empty();
    }
    // extractHtmlUrl takes the first "html_url" before "assets", which is the release's own
    // field only because it precedes the "assets" array in GitHub's current (but spec-unordered)
    // response — nothing stops a future field reorder from handing back the uploader's profile
    // URL instead. A release page URL always contains "/releases/"; a profile URL never does, so
    // this converts a reorder from silently opening the wrong page into a skipped notification.
    String htmlUrl = GitHubReleaseJson.extractHtmlUrl(responseJson);
    if (htmlUrl == null || !htmlUrl.contains("/releases/")) {
      LOGGER.info(
          "Skipping update check: release {} had no usable html_url ({}).", tagName, htmlUrl);
      return Optional.empty();
    }
    List<GitHubReleaseJson.Asset> assets = GitHubReleaseJson.extractAssets(responseJson);
    String expectedAssetName = "md2pdf-" + latestVersion + platform.assetSuffix();
    String downloadUrl = findAssetUrl(assets, expectedAssetName);
    if (downloadUrl == null) {
      LOGGER.info(
          "Release {} is newer but ships no '{}' asset for this platform.",
          tagName,
          expectedAssetName);
      return Optional.empty();
    }
    String checksumsUrl = findAssetUrl(assets, "SHA256SUMS");
    return Optional.of(
        new UpdateInfo(
            latestVersion, tagName, expectedAssetName, downloadUrl, checksumsUrl, htmlUrl));
  }

  private static String findAssetUrl(List<GitHubReleaseJson.Asset> assets, String name) {
    for (GitHubReleaseJson.Asset asset : assets) {
      if (name.equals(asset.name())) {
        return asset.browserDownloadUrl();
      }
    }
    return null;
  }
}
