package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.update.UpdatePlatform;

public class UpdatePlatformTest {

  @Test
  void linuxAnyArchDetectsLinuxX64() {
    assertEquals(UpdatePlatform.LINUX_X64, UpdatePlatform.detect("Linux", "amd64"));
    assertEquals(UpdatePlatform.LINUX_X64, UpdatePlatform.detect("Linux", "aarch64"));
  }

  @Test
  void macAppleSiliconDetectsMacosAarch64() {
    assertEquals(UpdatePlatform.MACOS_AARCH64, UpdatePlatform.detect("Mac OS X", "aarch64"));
    assertEquals(UpdatePlatform.MACOS_AARCH64, UpdatePlatform.detect("Mac OS X", "arm64"));
  }

  @Test
  void macIntelIsUnsupported() {
    assertEquals(UpdatePlatform.UNSUPPORTED, UpdatePlatform.detect("Mac OS X", "x86_64"));
  }

  @Test
  void windowsX64DetectsWindowsX64() {
    assertEquals(UpdatePlatform.WINDOWS_X64, UpdatePlatform.detect("Windows 11", "amd64"));
  }

  @Test
  void windowsNonX64IsUnsupported() {
    assertEquals(UpdatePlatform.UNSUPPORTED, UpdatePlatform.detect("Windows 11", "aarch64"));
  }

  @Test
  void unknownOsIsUnsupported() {
    assertEquals(UpdatePlatform.UNSUPPORTED, UpdatePlatform.detect("SolarisOS", "sparc"));
    assertEquals(UpdatePlatform.UNSUPPORTED, UpdatePlatform.detect(null, "amd64"));
  }

  @Test
  void assetSuffixesMatchReleaseNamingConvention() {
    assertEquals("-linux-x64.zip", UpdatePlatform.LINUX_X64.assetSuffix());
    assertEquals("-macos-aarch64.zip", UpdatePlatform.MACOS_AARCH64.assetSuffix());
    assertEquals("-windows-x64.zip", UpdatePlatform.WINDOWS_X64.assetSuffix());
  }

  @Test
  void installerScriptNamesMatchAssemblyFiles() {
    assertEquals("md2pdf-install.sh", UpdatePlatform.LINUX_X64.installerScriptName());
    assertEquals("md2pdf-install.zsh", UpdatePlatform.MACOS_AARCH64.installerScriptName());
    assertEquals("md2pdf-install.cmd", UpdatePlatform.WINDOWS_X64.installerScriptName());
  }
}
