package se.alipsa.md2pdf.gui.update;

import java.util.Locale;

/**
 * The self-contained release archives {@code gui/createApp.sh} builds, one per platform. Each
 * carries the release-asset file-name suffix and the installer script it ships next to the app
 * payload (see {@code gui/src/main/assembly/install.sh}, {@code mac/md2pdf-install.zsh}, {@code
 * win/md2pdf-install.cmd}).
 */
public enum UpdatePlatform {
  LINUX_X64("-linux-x64.zip", "md2pdf-install.sh"),
  MACOS_AARCH64("-macos-aarch64.zip", "md2pdf-install.zsh"),
  WINDOWS_X64("-windows-x64.zip", "md2pdf-install.cmd"),
  UNSUPPORTED("", "");

  private final String assetSuffix;
  private final String installerScriptName;

  UpdatePlatform(String assetSuffix, String installerScriptName) {
    this.assetSuffix = assetSuffix;
    this.installerScriptName = installerScriptName;
  }

  /** The release-asset file-name suffix for this platform, e.g. {@code "-linux-x64.zip"}. */
  public String assetSuffix() {
    return assetSuffix;
  }

  /** The installer script file name shipped inside this platform's release archive. */
  public String installerScriptName() {
    return installerScriptName;
  }

  /**
   * Maps raw {@code os.name}/{@code os.arch} values to the release archive that matches this
   * machine. There is no ARM-Linux, Intel-Mac, or non-x64-Windows build ({@code createApp.sh} pins
   * {@code EXPECTED_ARCH} per platform), so those combinations resolve to {@link #UNSUPPORTED}
   * rather than guessing — a mismatched archive would bundle a JRE that can't run.
   */
  public static UpdatePlatform detect(String osName, String osArch) {
    if (osName == null) {
      return UNSUPPORTED;
    }
    String name = osName.toLowerCase(Locale.ROOT);
    String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
    if (name.contains("linux")) {
      return isX64(arch) ? LINUX_X64 : UNSUPPORTED;
    }
    if (name.contains("mac") || name.contains("darwin")) {
      return isArm(arch) ? MACOS_AARCH64 : UNSUPPORTED;
    }
    if (name.contains("windows")) {
      return isX64(arch) ? WINDOWS_X64 : UNSUPPORTED;
    }
    return UNSUPPORTED;
  }

  /** Convenience overload that reads the live {@code os.name}/{@code os.arch} properties. */
  public static UpdatePlatform detectCurrent() {
    return detect(System.getProperty("os.name"), System.getProperty("os.arch"));
  }

  private static boolean isArm(String arch) {
    return arch.equals("aarch64") || arch.equals("arm64");
  }

  private static boolean isX64(String arch) {
    return arch.equals("amd64") || arch.equals("x86_64");
  }
}
