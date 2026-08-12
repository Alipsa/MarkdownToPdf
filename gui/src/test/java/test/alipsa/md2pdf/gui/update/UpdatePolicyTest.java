package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.update.UpdatePolicy;

public class UpdatePolicyTest {

  @AfterEach
  public void clearProperty() {
    System.clearProperty(UpdatePolicy.ENABLED_PROPERTY);
  }

  @Test
  public void updatesAreEnabledWhenThePropertyIsAbsent() {
    System.clearProperty(UpdatePolicy.ENABLED_PROPERTY);
    assertTrue(UpdatePolicy.isEnabled(), "the open source build must keep checking for updates");
  }

  @Test
  public void updatesAreDisabledWhenThePropertyIsFalse() {
    System.setProperty(UpdatePolicy.ENABLED_PROPERTY, "false");
    assertFalse(UpdatePolicy.isEnabled());
  }

  @Test
  public void updatesAreEnabledWhenThePropertyIsTrue() {
    System.setProperty(UpdatePolicy.ENABLED_PROPERTY, "true");
    assertTrue(UpdatePolicy.isEnabled());
  }

  @Test
  public void thePropertyNameIsTheOneTheStoreBuildSets() {
    assertEquals("md2pdf.update.enabled", UpdatePolicy.ENABLED_PROPERTY);
  }
}
