package test.alipsa.md2pdf.gui.fs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.fs.FileAccess;

public class FileAccessTest {

  @Test
  public void grantedAccessIsGranted() {
    assertTrue(FileAccess.granted().isGranted());
  }

  @Test
  public void deniedAccessIsNotGranted() {
    assertFalse(FileAccess.denied().isGranted());
  }

  @Test
  public void closingDoesNotThrowAndIsRepeatable() {
    FileAccess access = FileAccess.granted();
    assertDoesNotThrow(access::close);
    assertDoesNotThrow(access::close);
  }

  @Test
  public void closingDeniedAccessIsSafe() {
    // Callers close whatever restore() handed back, without first checking whether it was granted.
    assertDoesNotThrow(FileAccess.denied()::close);
  }

  @Test
  public void worksInTryWithResourcesWithoutACheckedException() {
    // close() narrows AutoCloseable's throws clause; if that ever regresses, every call site has to
    // start catching Exception. This test fails to compile rather than fails at runtime.
    try (FileAccess access = FileAccess.granted()) {
      assertTrue(access.isGranted());
    }
  }
}
