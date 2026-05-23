package test.alipsa.md2pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.alipsa.md2pdf.Md2PdfEngine;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceResolutionTest {

  @TempDir
  Path tempDir;

  @Test
  void testMarkdownPathUsesParentDirectoryAsBasePath() throws Exception {
    copyTestImage(tempDir.resolve("alice2.png"));
    Path markdown = tempDir.resolve("report.md");
    Files.writeString(markdown, """
        # Relative Image

        ![Alice](alice2.png)
        """, StandardCharsets.UTF_8);

    File pdf = tempDir.resolve("report.pdf").toFile();
    new Md2PdfEngine()
        .markdown(markdown)
        .toPdf(pdf);

    assertTrue(pdf.exists(), "PDF file was not created");
    assertTrue(containsImage(pdf), "PDF should contain the relative image");
  }

  @Test
  void testBasePathResolvesRelativeResourcesForMarkdownString() throws Exception {
    copyTestImage(tempDir.resolve("alice2.png"));

    File pdf = tempDir.resolve("report-from-string.pdf").toFile();
    new Md2PdfEngine()
        .markdown("""
            # Relative Image

            ![Alice](alice2.png)
            """)
        .basePath(tempDir)
        .toPdf(pdf);

    assertTrue(pdf.exists(), "PDF file was not created");
    assertTrue(containsImage(pdf), "PDF should contain the relative image");
  }

  private void copyTestImage(Path target) throws Exception {
    try (InputStream inputStream = getClass().getResourceAsStream("/images/alice2.png")) {
      assertNotNull(inputStream, "Test image is missing");
      Files.copy(inputStream, target);
    }
  }

  private boolean containsImage(File pdf) throws Exception {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      for (PDPage page : document.getPages()) {
        PDResources resources = page.getResources();
        for (var name : resources.getXObjectNames()) {
          if (resources.getXObject(name) instanceof PDImageXObject) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
