package se.alipsa.md2pdf.gui.update;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A narrow, hand-written extractor for the handful of fields this app needs from a GitHub Releases
 * API response ({@code tag_name}, {@code html_url}, {@code assets[].name}, {@code
 * assets[].browser_download_url}). This project has no JSON library — see CLAUDE.md's
 * zero-new-dependency constraint for the {@code gui} module — so this follows the same
 * hand-written-parser precedent as {@code StyleProfile.fromCss(String)}.
 *
 * <p>This is not a general JSON parser: it does not resolve unicode escape sequences, and it relies
 * on GitHub's own field ordering (top-level scalars such as {@code html_url} and {@code tag_name}
 * appear before the {@code assets} array) so the release's own {@code html_url} is never confused
 * with the field of the same name nested inside each asset's {@code uploader} object.
 */
public final class GitHubReleaseJson {

  private static final Pattern ASSETS_KEY = Pattern.compile("\"assets\"\\s*:");

  private GitHubReleaseJson() {}

  /** A single release asset: its file name and direct download URL. */
  public record Asset(String name, String browserDownloadUrl) {}

  public static String extractTagName(String json) {
    return extractScalarBeforeAssets(json, "tag_name");
  }

  public static String extractHtmlUrl(String json) {
    return extractScalarBeforeAssets(json, "html_url");
  }

  public static List<Asset> extractAssets(String json) {
    List<Asset> assets = new ArrayList<>();
    Matcher keyMatcher = ASSETS_KEY.matcher(json);
    if (!keyMatcher.find()) {
      return assets;
    }
    int openBracket = json.indexOf('[', keyMatcher.end());
    if (openBracket < 0) {
      return assets;
    }
    String arrayBody = extractBracketedRegion(json, openBracket);
    for (String objectJson : splitTopLevelObjects(arrayBody)) {
      String name = extractField(objectJson, "name");
      String url = extractField(objectJson, "browser_download_url");
      if (name != null && url != null) {
        assets.add(new Asset(name, url));
      }
    }
    return assets;
  }

  // ── internals, public static so they can be exercised directly by unit tests ─────────────

  /**
   * Scans a scalar top-level field, restricted to the JSON text preceding the {@code assets} array
   * so a release's own {@code html_url}/{@code tag_name} is never confused with the
   * identically-named field GitHub nests inside each asset's {@code uploader} object.
   */
  public static String extractScalarBeforeAssets(String json, String key) {
    int assetsIndex = json.indexOf("\"assets\"");
    String scope = assetsIndex < 0 ? json : json.substring(0, assetsIndex);
    return extractField(scope, key);
  }

  /** First {@code "key": "value"} match in {@code json}, unescaped, or {@code null} if absent. */
  public static String extractField(String json, String key) {
    Pattern pattern =
        Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    Matcher matcher = pattern.matcher(json);
    return matcher.find() ? unescape(matcher.group(1)) : null;
  }

  /**
   * Given the index of an opening bracket/brace, returns the text strictly between it and its
   * matching close, tracking nesting depth and skipping over string literals so a bracket inside a
   * quoted value (e.g. a URL) never desynchronizes the count.
   */
  public static String extractBracketedRegion(String json, int openIndex) {
    char open = json.charAt(openIndex);
    char close = open == '[' ? ']' : '}';
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = openIndex; i < json.length(); i++) {
      char c = json.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth == 0) {
          return json.substring(openIndex + 1, i);
        }
      }
    }
    return "";
  }

  /**
   * Splits the body of a JSON array of objects (as returned by {@link #extractBracketedRegion})
   * into one string per top-level {@code {...}} object, ignoring braces nested inside string
   * literals or inside a nested object (such as each asset's {@code uploader} object).
   */
  public static List<String> splitTopLevelObjects(String arrayBody) {
    List<String> objects = new ArrayList<>();
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    int start = -1;
    for (int i = 0; i < arrayBody.length(); i++) {
      char c = arrayBody.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '{') {
        if (depth == 0) {
          start = i;
        }
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && start >= 0) {
          objects.add(arrayBody.substring(start, i + 1));
          start = -1;
        }
      }
    }
    return objects;
  }

  /** Resolves the small set of escapes needed for tags, file names and URLs. */
  public static String unescape(String raw) {
    if (raw == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < raw.length()) {
        char next = raw.charAt(i + 1);
        switch (next) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'n' -> sb.append('\n');
          case 't' -> sb.append('\t');
          case 'r' -> sb.append('\r');
          default -> sb.append(next);
        }
        i++;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
