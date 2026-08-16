package se.alipsa.md2pdf.gui.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
   * @return the outcome of the check, and update information when a newer platform release is
   *     available
   * @throws UpdateCheckException on any network, HTTP-status or interrupt failure
   */
  public UpdateCheckResult checkForUpdate(String currentVersion) throws UpdateCheckException {
    String apiUrl = System.getProperty(API_URL_PROPERTY, DEFAULT_API_URL);
    try (HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      return parseAndEvaluate(
          currentVersion, UpdatePlatform.detectCurrent(), fetchAllReleasePages(httpClient, apiUrl));
    } catch (IOException e) {
      throw new UpdateCheckException("Failed to reach " + apiUrl, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpdateCheckException("Interrupted while checking for updates", e);
    }
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
   * @return the outcome of the check, and update information when a newer matching release is
   *     available
   */
  public static UpdateCheckResult parseAndEvaluate(
      String currentVersion, UpdatePlatform platform, String responseJson) {
    if (platform == UpdatePlatform.UNSUPPORTED) {
      LOGGER.info("Skipping update check: no release archive for this platform.");
      return UpdateCheckResult.indeterminate();
    }
    String releaseJson = selectLatestGuiRelease(responseJson);
    if (releaseJson == null) {
      LOGGER.warn("Skipping update check: no {}* release found in the fetched page.", TAG_PREFIX);
      return UpdateCheckResult.indeterminate();
    }
    String tagName = GitHubReleaseJson.extractTagName(releaseJson);
    String latestVersion = tagName.substring(TAG_PREFIX.length());
    if (!VersionComparator.isNewer(latestVersion, currentVersion)) {
      LOGGER.info(
          "No update available: latest release {} is not newer than the running {}.",
          latestVersion,
          currentVersion);
      return UpdateCheckResult.upToDate();
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
      return UpdateCheckResult.indeterminate();
    }
    List<GitHubReleaseJson.Asset> assets = GitHubReleaseJson.extractAssets(releaseJson);
    String expectedAssetName = "md2pdf-" + latestVersion + platform.assetSuffix();
    String downloadUrl = findAssetUrl(assets, expectedAssetName);
    if (downloadUrl == null) {
      LOGGER.info(
          "Release {} is newer but ships no '{}' asset for this platform.",
          tagName,
          expectedAssetName);
      return UpdateCheckResult.indeterminate();
    }
    String checksumsUrl = findAssetUrl(assets, "SHA256SUMS");
    return UpdateCheckResult.updateAvailable(
        new UpdateInfo(
            latestVersion, tagName, expectedAssetName, downloadUrl, checksumsUrl, htmlUrl));
  }

  /** Fetches and combines every page that GitHub links from a releases response. */
  private static String fetchAllReleasePages(HttpClient httpClient, String apiUrl)
      throws IOException, InterruptedException, UpdateCheckException {
    URI nextPage = URI.create(apiUrl);
    Set<URI> fetchedPages = new HashSet<>();
    StringBuilder releases = new StringBuilder("[");
    boolean firstPage = true;
    while (nextPage != null) {
      if (!fetchedPages.add(nextPage)) {
        throw new UpdateCheckException(
            "GitHub Releases pagination linked to a page already fetched");
      }
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(nextPage)
              .header("Accept", "application/vnd.github+json")
              .timeout(Duration.ofSeconds(10))
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new UpdateCheckException(
            "GitHub returned HTTP " + response.statusCode() + " for " + nextPage);
      }
      String page = response.body().strip();
      if (!page.startsWith("[")) {
        return response.body();
      }
      String pageItems = GitHubReleaseJson.extractBracketedRegion(page, page.indexOf('[')).strip();
      if (!pageItems.isEmpty()) {
        if (!firstPage) {
          releases.append(',');
        }
        releases.append(pageItems);
        firstPage = false;
      }
      nextPage = findNextPage(response.headers());
    }
    return releases.append(']').toString();
  }

  private static URI findNextPage(HttpHeaders headers) throws UpdateCheckException {
    for (String link : headers.allValues("Link")) {
      for (String entry : link.split(",")) {
        if (entry.contains("rel=\"next\"")) {
          int open = entry.indexOf('<');
          int close = entry.indexOf('>', open + 1);
          if (open < 0 || close < 0) {
            throw new UpdateCheckException("GitHub returned a malformed next-page Link header");
          }
          try {
            return URI.create(entry.substring(open + 1, close));
          } catch (IllegalArgumentException e) {
            throw new UpdateCheckException("GitHub returned an invalid next-page Link URL", e);
          }
        }
      }
    }
    return null;
  }

  /**
   * Scans a {@code GET /releases} JSON array and returns the JSON object of the release with the
   * highest version among those tagged {@link #TAG_PREFIX}, skipping drafts, prereleases, and
   * candidates whose version cannot be parsed, or {@code null} if none match.
   *
   * <p>Drafts and prereleases are skipped outright: GitHub's {@code draft}/{@code prerelease} flags
   * mark a release as not a real, generally-available release, and offering one as an update would
   * nag every user still on a genuinely released version. Candidates with an unparseable version
   * (e.g. a malformed tag like {@code MarkdownToPdf-v0.4.0.RC1}) are skipped rather than allowed to
   * seed {@code bestVersion}: {@link VersionComparator#isNewer} fails safe to {@code false}
   * whenever either side fails to parse, so once an unparseable version became {@code bestVersion}
   * every later, genuinely newer, well-formed candidate would also compare as "not newer" against
   * it (because parsing {@code bestVersion} itself fails) and could never replace it — the
   * malformed tag would win forever.
   */
  private static String selectLatestGuiRelease(String responseJson) {
    String trimmed = responseJson.strip();
    if (!trimmed.startsWith("[")) {
      LOGGER.warn(
          "Response is not a JSON array (expected the GET /releases contract) — check the {} "
              + "override if set.",
          API_URL_PROPERTY);
      return null;
    }
    int openBracket = responseJson.indexOf('[');
    String arrayBody = GitHubReleaseJson.extractBracketedRegion(responseJson, openBracket);
    String bestJson = null;
    String bestVersion = null;
    for (String candidate : GitHubReleaseJson.splitTopLevelObjects(arrayBody)) {
      String tagName = GitHubReleaseJson.extractTagName(candidate);
      if (tagName == null || !tagName.startsWith(TAG_PREFIX)) {
        continue;
      }
      if (GitHubReleaseJson.extractBooleanBeforeAssets(candidate, "draft")
          || GitHubReleaseJson.extractBooleanBeforeAssets(candidate, "prerelease")) {
        continue;
      }
      String candidateVersion = tagName.substring(TAG_PREFIX.length());
      if (!VersionComparator.isParseable(candidateVersion)) {
        continue;
      }
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
