package test.alipsa.md2pdf.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.md2pdf.model.StyleProfile;
import se.alipsa.md2pdf.model.StyleProfileManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StyleProfileManagerTest {

  @Test
  void builtinProfilesAlwaysPresent() {
    StyleProfileManager manager = new StyleProfileManager(Path.of(System.getProperty("java.io.tmpdir"), "md2pdf-test-empty-" + System.nanoTime()));
    List<String> names = manager.listNames();
    assertTrue(names.contains("Default"), "Default builtin must always be listed");
    assertTrue(names.contains("Minimal"), "Minimal builtin must always be listed");
    assertTrue(names.contains("Print"), "Print builtin must always be listed");
  }

  @Test
  void builtinDefaultMatchesLibDefaults() {
    StyleProfile defaultProfile = StyleProfileManager.getBuiltin("Default");
    assertNotNull(defaultProfile, "Default builtin profile must not be null");
    assertTrue(defaultProfile.getBodyFont().contains("sans-serif"), "Default font must be sans-serif");
    assertEquals(1.6, defaultProfile.getLineHeight(), 0.001);
    assertEquals("A4", defaultProfile.getPageSize());
  }

  @Test
  void builtinMinimalExists() {
    StyleProfile minimal = StyleProfileManager.getBuiltin("Minimal");
    assertNotNull(minimal);
    assertEquals("Minimal", minimal.getName());
  }

  @Test
  void builtinPrintExists() {
    StyleProfile print = StyleProfileManager.getBuiltin("Print");
    assertNotNull(print);
    assertEquals("Print", print.getName());
  }

  @Test
  void saveAndLoadUserProfile(@TempDir Path tempDir) throws IOException {
    StyleProfileManager manager = new StyleProfileManager(tempDir);
    StyleProfile profile = new StyleProfile("MySave");
    profile.setBodyFontSizePt(16);
    profile.setBodyColor("#AABBCC");
    manager.save(profile);

    StyleProfile loaded = manager.load("MySave");
    assertNotNull(loaded);
    assertEquals("MySave", loaded.getName());
    assertEquals(16, loaded.getBodyFontSizePt());
    assertEquals("#AABBCC", loaded.getBodyColor());
  }

  @Test
  void userProfileAppearsInList(@TempDir Path tempDir) throws IOException {
    StyleProfileManager manager = new StyleProfileManager(tempDir);
    manager.save(new StyleProfile("ListTest"));

    List<String> names = manager.listNames();
    assertTrue(names.contains("ListTest"), "Saved user profile must appear in listNames()");
    assertTrue(names.contains("Default"), "Builtins must still be listed alongside user profiles");
  }

  @Test
  void deleteRemovesUserProfile(@TempDir Path tempDir) throws IOException {
    StyleProfileManager manager = new StyleProfileManager(tempDir);
    manager.save(new StyleProfile("ToDelete"));
    assertTrue(manager.listUserProfileNames().contains("ToDelete"));

    manager.delete("ToDelete");
    assertFalse(manager.listUserProfileNames().contains("ToDelete"), "Deleted profile must not appear in user list");
  }

  @Test
  void loadBuiltinByName() throws IOException {
    StyleProfileManager manager = new StyleProfileManager(Path.of(System.getProperty("java.io.tmpdir"), "md2pdf-test-bi-" + System.nanoTime()));
    StyleProfile p = manager.load("Default");
    assertNotNull(p);
    assertEquals("Default", p.getName());
  }

  @Test
  void cannotDeleteBuiltin(@TempDir Path tempDir) {
    StyleProfileManager manager = new StyleProfileManager(tempDir);
    // Deleting a builtin should be a no-op (no exception, but it stays listed)
    assertDoesNotThrow(() -> manager.delete("Default"));
    assertTrue(manager.listNames().contains("Default"), "Builtin must remain after delete attempt");
  }
}
