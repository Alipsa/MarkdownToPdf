package se.alipsa.md2pdf.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Manages named style profiles. Built-in profiles are returned from code;
 * user profiles are stored as .properties files under {@code profilesDir}.
 */
public class StyleProfileManager {

  private static final Logger log = LogManager.getLogger(StyleProfileManager.class);
  private static final List<String> BUILTIN_NAMES = List.of("Default", "Minimal", "Print");

  private final Path profilesDir;

  /** Creates a manager using {@code ~/.config/md2pdf/styles/}. */
  public StyleProfileManager() {
    this(Paths.get(System.getProperty("user.home"), ".config", "md2pdf", "styles"));
  }

  /** Creates a manager using the given directory (used in tests). */
  public StyleProfileManager(Path profilesDir) {
    this.profilesDir = profilesDir;
  }

  /**
   * Returns all available profile names: built-ins first, then user profiles sorted alphabetically.
   * Built-ins are always present even if the profiles directory does not exist.
   */
  public List<String> listNames() {
    List<String> names = new ArrayList<>(BUILTIN_NAMES);
    for (String userProfile : listUserProfileNames()) {
      if (!BUILTIN_NAMES.contains(userProfile)) {
        names.add(userProfile);
      }
    }
    return names;
  }

  /** Returns only the user-defined profile names (not built-ins). */
  public List<String> listUserProfileNames() {
    if (!Files.isDirectory(profilesDir)) {
      return List.of();
    }
    try {
      return Files.list(profilesDir)
          .filter(p -> p.getFileName().toString().endsWith(".properties"))
          .map(p -> p.getFileName().toString().replace(".properties", ""))
          .sorted()
          .toList();
    } catch (IOException e) {
      log.warn("Failed to list user profiles from {}", profilesDir, e);
      return List.of();
    }
  }

  /**
   * Loads a profile by name. Checks built-ins first, then disk.
   * Returns {@code null} if not found.
   */
  public StyleProfile load(String name) throws IOException {
    StyleProfile builtin = getBuiltin(name);
    if (builtin != null) {
      return builtin;
    }
    Path file = profilesDir.resolve(name + ".properties");
    if (!Files.exists(file)) {
      return null;
    }
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      props.load(in);
    }
    return StyleProfile.fromProperties(props);
  }

  /** Saves a user profile to disk. Silently refuses to overwrite built-in names. */
  public void save(StyleProfile profile) throws IOException {
    if (BUILTIN_NAMES.contains(profile.getName())) {
      log.warn("Refusing to overwrite built-in profile '{}'", profile.getName());
      return;
    }
    if (!Files.exists(profilesDir)) {
      Files.createDirectories(profilesDir);
    }
    Properties props = new Properties();
    profile.saveToProperties(props);
    Path file = profilesDir.resolve(profile.getName() + ".properties");
    try (OutputStream out = Files.newOutputStream(file)) {
      props.store(out, "MarkdownToPdf style profile");
    }
  }

  /** Deletes a user profile. No-op for built-in profiles. */
  public void delete(String name) throws IOException {
    if (BUILTIN_NAMES.contains(name)) {
      return;
    }
    Path file = profilesDir.resolve(name + ".properties");
    Files.deleteIfExists(file);
  }

  /**
   * Returns a built-in profile by name, or {@code null} if the name is not a built-in.
   * Built-in profiles are synthesised in code and match the lib's DEFAULT_CSS defaults.
   */
  public static StyleProfile getBuiltin(String name) {
    return switch (name) {
      case "Default" -> buildDefault();
      case "Minimal" -> buildMinimal();
      case "Print" -> buildPrint();
      default -> null;
    };
  }

  private static StyleProfile buildDefault() {
    // Mirrors the lib's DEFAULT_CSS so the preview looks identical to a raw conversion
    StyleProfile p = new StyleProfile("Default");
    p.setBodyFont("-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif");
    p.setBodyFontSizePt(11);
    p.setLineHeight(1.6);
    p.setBodyColor("#333333");
    p.setH1SizeEm(2.0);
    p.setH2SizeEm(1.5);
    p.setH3SizeEm(1.25);
    p.setHeadingColor("#000000");
    p.setLinkColor("#0366d6");
    p.setCodeFont("monospace");
    p.setCodeFontSizePct(85);
    p.setCodeBackground("#f6f8fa");
    p.setBlockquoteBorderColor("#dfe2e5");
    p.setBlockquoteTextColor("#6a737d");
    p.setPageSize("A4");
    p.setPageOrientation("portrait");
    p.setMarginTop("1in");
    p.setMarginRight("1in");
    p.setMarginBottom("1in");
    p.setMarginLeft("1in");
    return p;
  }

  private static StyleProfile buildMinimal() {
    StyleProfile p = new StyleProfile("Minimal");
    p.setBodyFont("Arial, Helvetica, sans-serif");
    p.setBodyFontSizePt(11);
    p.setLineHeight(1.7);
    p.setBodyColor("#222222");
    p.setH1SizeEm(1.8);
    p.setH2SizeEm(1.4);
    p.setH3SizeEm(1.15);
    p.setHeadingColor("#222222");
    p.setLinkColor("#0055cc");
    p.setCodeFont("monospace");
    p.setCodeFontSizePct(88);
    p.setCodeBackground("#f4f4f4");
    p.setBlockquoteBorderColor("#cccccc");
    p.setBlockquoteTextColor("#555555");
    p.setPageSize("A4");
    p.setPageOrientation("portrait");
    p.setMarginTop("1.2in");
    p.setMarginRight("1.2in");
    p.setMarginBottom("1.2in");
    p.setMarginLeft("1.2in");
    return p;
  }

  private static StyleProfile buildPrint() {
    StyleProfile p = new StyleProfile("Print");
    p.setBodyFont("Georgia, \"Times New Roman\", Times, serif");
    p.setBodyFontSizePt(12);
    p.setLineHeight(1.5);
    p.setBodyColor("#000000");
    p.setH1SizeEm(1.6);
    p.setH2SizeEm(1.3);
    p.setH3SizeEm(1.1);
    p.setHeadingColor("#000000");
    p.setLinkColor("#000000");
    p.setCodeFont("\"Courier New\", Courier, monospace");
    p.setCodeFontSizePct(90);
    p.setCodeBackground("#eeeeee");
    p.setBlockquoteBorderColor("#888888");
    p.setBlockquoteTextColor("#333333");
    p.setPageSize("A4");
    p.setPageOrientation("portrait");
    p.setMarginTop("0.9in");
    p.setMarginRight("1in");
    p.setMarginBottom("0.9in");
    p.setMarginLeft("1in");
    return p;
  }
}
