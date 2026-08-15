package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.md2pdf.Md2PdfEngine;
import se.alipsa.md2pdf.Md2PdfException;

public class OutputTest {

  static Md2PdfEngine engine = new Md2PdfEngine();

  @TempDir Path tempDir;

  @Test
  public void TestRenderToHtml() throws Md2PdfException {
    String md =
        """
      # TestRenderToHtml
      - Users
        - Per
        - Sixten

      [Fancy stuff](http://some.url.se/)
      """;
    String html = engine.markdown(md).toHtml();
    // System.out.println(html);
    assertTrue(html.contains("<li>Per</li>"));
    assertTrue(html.contains("<a href=\"http://some.url.se/\">Fancy stuff</a>"));
  }

  @Test
  public void testRenderToFile() throws Md2PdfException {
    String md =
        """
      # TestRenderToFile
      - Users
        - Per

      [Fancy stuff](http://some.url.se/)
      """;
    Path path = Paths.get("target/testRenderToFile.pdf");
    engine.markdown(md).toPdf(path);

    File file = path.toFile();
    assertTrue(file.exists(), "File not found");
    assertTrue(file.length() > 0, "File length should be greater than 0");
  }

  @Test
  void testFailedRenderPreservesExistingPdfFile() throws IOException {
    Path output = tempDir.resolve("existing.pdf");
    Files.writeString(output, "existing document");

    assertThrows(
        NullPointerException.class, () -> engine.markdown((String) null).toPdf(output.toFile()));

    assertEquals("existing document", Files.readString(output));
  }

  @Test
  void testFilesystemRootReportsMd2PdfException() {
    File root = File.listRoots()[0];

    assertThrows(Md2PdfException.class, () -> engine.markdown("# Report").toPdf(root));
  }

  @Test
  void testPdfDestinationDirectoryFailsBeforeRendering() throws IOException {
    Path output = tempDir.resolve("output-directory");
    Files.createDirectory(output);

    assertThrows(Md2PdfException.class, () -> engine.markdown("# Report").toPdf(output));
  }

  @Test
  void testPdfFilePreservesExistingPosixPermissions() throws Exception {
    assumeTrue(
        Files.getFileAttributeView(tempDir, java.nio.file.attribute.PosixFileAttributeView.class)
            != null);
    Path output = tempDir.resolve("existing.pdf");
    Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-r-----");
    Files.writeString(output, "existing document");
    Files.setPosixFilePermissions(output, permissions);

    engine.markdown("# Report").toPdf(output);

    assertEquals(permissions, Files.getPosixFilePermissions(output));
  }

  @Test
  void testPdfFileUsesPlatformDefaultPermissionsForNewFile() throws Exception {
    assumeTrue(
        Files.getFileAttributeView(tempDir, java.nio.file.attribute.PosixFileAttributeView.class)
            != null);
    Path reference = tempDir.resolve("reference.txt");
    Path output = tempDir.resolve("new.pdf");
    Files.writeString(reference, "reference");

    engine.markdown("# Report").toPdf(output);

    assertEquals(Files.getPosixFilePermissions(reference), Files.getPosixFilePermissions(output));
  }

  @Test
  void testPdfFileWritesThroughSymbolicLink() throws Exception {
    assumeTrue(
        Files.getFileAttributeView(tempDir, java.nio.file.attribute.PosixFileAttributeView.class)
            != null);
    Path target = tempDir.resolve("target.pdf");
    Path link = tempDir.resolve("report.pdf");
    Files.writeString(target, "existing document");
    Files.createSymbolicLink(link, target.getFileName());

    engine.markdown("# Report").toPdf(link);

    assertTrue(Files.isSymbolicLink(link));
    assertTrue(Files.size(target) > "existing document".length());
  }

  @Test
  void testReadOnlyPdfFileIsNotReplaced() throws Exception {
    assumeTrue(
        Files.getFileAttributeView(tempDir, java.nio.file.attribute.PosixFileAttributeView.class)
            != null);
    Path output = tempDir.resolve("readonly.pdf");
    Set<PosixFilePermission> originalPermissions = PosixFilePermissions.fromString("rw-r--r--");
    Files.writeString(output, "existing document");
    Files.setPosixFilePermissions(output, PosixFilePermissions.fromString("r--r--r--"));
    try {
      assumeFalse(Files.isWritable(output));

      assertThrows(Md2PdfException.class, () -> engine.markdown("# Report").toPdf(output));
    } finally {
      Files.setPosixFilePermissions(output, originalPermissions);
    }

    assertEquals("existing document", Files.readString(output));
  }

  @Test
  public void testRenderToByteArray() throws Md2PdfException {
    String md =
        """
      # TestRenderToByteArray
      - Users
        - Per
      """;
    byte[] bytes = engine.markdown(md).toPdf();
    assertTrue(bytes.length > 0, "Byte array length should be greater than 0");
  }

  @Test
  public void testRenderToStream() throws Md2PdfException, IOException {
    String md =
        """
      # TestRenderToStream
      - Users
        - Per
      """;
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      engine.markdown(md).toPdf(out);
      assertTrue(out.toByteArray().length > 0, "Byte array length should be greater than 0");
    }
  }

  @Test
  public void testRenderToStreamDoesNotCloseCallerStream() throws Md2PdfException {
    String md =
        """
      # TestRenderToStreamDoesNotCloseCallerStream
      - Users
        - Per
      """;
    CloseTrackingOutputStream out = new CloseTrackingOutputStream();
    engine.markdown(md).toPdf(out);

    assertTrue(out.toByteArray().length > 0, "Byte array length should be greater than 0");
    assertFalse(out.isClosed(), "Caller-owned output stream should not be closed");
  }

  @Test
  void testMathMlToPdf() throws Exception {
    // MathML must be in a CommonMark HTML block (blank line before the <math> tag).
    // Without the blank line it becomes inline HTML and the softbreak renderer
    // inserts <br /> between every line, breaking the MathML structure.
    String md =
        """
      # MathML Test
      The quadratic formula:

      <math mode="display" xmlns="http://www.w3.org/1998/Math/MathML">
          <semantics>
              <mrow>
                  <mi>x</mi>
                  <mo>=</mo>
                  <mfrac>
                      <mrow>
                          <mo form="prefix">−</mo>
                          <mi>b</mi>
                          <mo>±</mo>
                          <msqrt>
                              <msup>
                                  <mi>b</mi>
                                  <mn>2</mn>
                              </msup>
                              <mo>−</mo>
                              <mn>4</mn>
                              <mo>⁢</mo>
                              <mi>a</mi>
                              <mo>⁢</mo>
                              <mi>c</mi>
                          </msqrt>
                      </mrow>
                      <mrow>
                          <mn>2</mn>
                          <mo>⁢</mo>
                          <mi>a</mi>
                      </mrow>
                  </mfrac>
              </mrow>
          </semantics>
      </math>
      """;

    var job = engine.markdown(md);

    Path pdfPath = Paths.get("target/mathml.pdf");
    job.toPdf(pdfPath);
    assertTrue(pdfPath.toFile().exists());

    // Verify the MathML was actually rendered as a vector graphic (Form XObject)
    boolean hasMathMlGraphic = false;
    try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
      for (PDPage page : doc.getPages()) {
        PDResources res = page.getResources();
        for (var name : res.getXObjectNames()) {
          if (res.getXObject(name) instanceof PDFormXObject) {
            hasMathMlGraphic = true;
          }
        }
      }
    }
    assertTrue(hasMathMlGraphic, "PDF should contain a FormXObject from MathML rendering");

    Path htmlPath = Paths.get("target/mathml.html");
    job.toHtml(htmlPath);
    assertTrue(htmlPath.toFile().exists());

    String html = java.nio.file.Files.readString(htmlPath);
    assertTrue(html.contains("<math"), "HTML should contain the MathML element");
    assertTrue(html.contains("mfrac"), "HTML should contain the fraction element");
  }

  private static class CloseTrackingOutputStream extends OutputStream {

    private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
    private boolean closed;

    @Override
    public void write(int b) {
      delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      delegate.write(b, off, len);
    }

    @Override
    public void close() {
      closed = true;
    }

    byte[] toByteArray() {
      return delegate.toByteArray();
    }

    boolean isClosed() {
      return closed;
    }
  }
}
