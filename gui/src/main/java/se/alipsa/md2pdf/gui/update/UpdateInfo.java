package se.alipsa.md2pdf.gui.update;

/**
 * Describes an available MarkdownToPdf release, as resolved from the GitHub Releases API for the
 * current platform.
 *
 * @param latestVersion the release version with any leading {@code v} stripped, e.g. {@code
 *     "0.1.2"}
 * @param tagName the raw git tag, e.g. {@code "v0.1.2"}
 * @param assetName the platform-specific release asset file name, e.g. {@code
 *     "md2pdf-0.1.2-linux-x64.zip"}
 * @param downloadUrl the direct download URL for {@code assetName}
 * @param checksumsUrl the direct download URL for the release's {@code SHA256SUMS} asset, or {@code
 *     null} if the release doesn't publish one — not required for this check-only feature, but
 *     needed by the self-apply follow-up before it downloads anything
 * @param releaseHtmlUrl the URL of the release page on GitHub
 */
public record UpdateInfo(
    String latestVersion,
    String tagName,
    String assetName,
    String downloadUrl,
    String checksumsUrl,
    String releaseHtmlUrl) {}
