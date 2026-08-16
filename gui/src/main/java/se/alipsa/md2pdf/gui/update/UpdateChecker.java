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

  /** Creates an update checker. */
  public UpdateChecker() {}

  /** System property that overrides the GitHub API URL, for manual QA against a local fixture. */
  public static final String API_URL_PROPERTY = "md2pdf.update.apiUrl";

  /**
   * gui releases are tagged {@code MarkdownToPdf-v<version>}; lib releases share the same
   * repository and are tagged {@code md2pdf-v<version>}. {@code releases/latest} is repo-wide, so a
   * lib-only release published after a gui release would shadow it and ship no platform zips at all
   * — the releases list must be filtered to this prefix instead.
   */
  private static final String TAG_PREFIX = "MarkdownToPdf-v";

  private static final String DEFAULT_API_URL =
      "https://api.github.com/repos/Alipsa/MarkdownToPdf/releases?per_page=100";

  private static final Logger LOGGER = LogManager.getLogger(UpdateChecker.class);

  /**
   * Fetches recent GitHub releases and returns update info if the latest release tagged {@code
   * MarkdownToPdf-v*} is newer than {@code currentVersion} and ships an asset for this platform.
   *
   * @param currentVersion the version currently running
   * @return update information when a newer platform release is available
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
   * Pure evaluation of a {@code GET /releases} JSON array response against the currently running
   * version and platform. No network access — used directly by tests.
   *
   * <p>The response is a top-level array of releases, not a single release: {@code releases/latest}
   * is repo-wide and would let an unrelated {@code lib} release (tagged {@code md2pdf-v*}) shadow
   * the actual latest {@code gui} release, or return no platform zips at all. This scans every
   * entry, keeps only tags starting with {@link #TAG_PREFIX}, and picks the highest version among
   * matches via {@link VersionComparator} — not "the first match" — because GitHub sorts {@code
   * /releases} by the tagged commit's date, not publish time, so a gui release cut from an older
   * commit is not guaranteed to sort above a newer lib release.
   *
   * @param currentVersion the version currently running
   * @param platform the platform whose release asset should be selected
   * @param responseJson the {@code GET /releases} JSON array response
   * @return update information when a newer matching release is available
   */
  public static Optional<UpdateInfo> parseAndEvaluate(
      String currentVersion, UpdatePlatform platform, String responseJson) {
    if (platform == UpdatePlatform.UNSUPPORTED) {
      LOGGER.info("Skipping update check: no release archive for this platform.");
      return Optional.empty();
    }
    String releaseJson = selectLatestGuiRelease(responseJson);
    if (releaseJson == null) {
      LOGGER.warn("Skipping update check: no {}* release found in the fetched page.", TAG_PREFIX);
      return Optional.empty();
    }
    String tagName = GitHubReleaseJson.extractTagName(releaseJson);
    String latestVersion = tagName.substring(TAG_PREFIX.length());
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
    String htmlUrl = GitHubReleaseJson.extractHtmlUrl(releaseJson);
    if (htmlUrl == null || !htmlUrl.contains("/releases/")) {
      LOGGER.info(
          "Skipping update check: release {} had no usable html_url ({}).", tagName, htmlUrl);
      return Optional.empty();
    }
    List<GitHubReleaseJson.Asset> assets = GitHubReleaseJson.extractAssets(releaseJson);
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

  /**
   * Scans a {@code GET /releases} JSON array and returns the JSON object of the release with the
   * highest version among those tagged {@link #TAG_PREFIX}, or {@code null} if none match.
   */
  private static String selectLatestGuiRelease(String responseJson) {
    int openBracket = responseJson.indexOf('[');
    if (openBracket < 0) {
      return null;
    }
    String arrayBody = GitHubReleaseJson.extractBracketedRegion(responseJson, openBracket);
    String bestJson = null;
    String bestVersion = null;
    for (String candidate : GitHubReleaseJson.splitTopLevelObjects(arrayBody)) {
      String tagName = GitHubReleaseJson.extractTagName(candidate);
      if (tagName == null || !tagName.startsWith(TAG_PREFIX)) {
        continue;
      }
      String candidateVersion = tagName.substring(TAG_PREFIX.length());
      if (bestVersion == null || VersionComparator.isNewer(candidateVersion, bestVersion)) {
        bestJson = candidate;
        bestVersion = candidateVersion;
      }
    }
    return bestJson;
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
