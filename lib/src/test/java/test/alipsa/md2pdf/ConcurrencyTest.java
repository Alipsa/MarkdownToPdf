package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openhtmltopdf.util.XRLog;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfEngine;
import se.alipsa.md2pdf.Md2PdfException;

public class ConcurrencyTest {

  static {
    // silence openhtmltopdf noice
    XRLog.setLevel(XRLog.LOAD, Level.WARNING);
    XRLog.setLevel(XRLog.MATCH, Level.WARNING);
    XRLog.setLevel(XRLog.GENERAL, Level.WARNING);
  }

  @Test
  public void testSameInstanceConcurrently() throws InterruptedException, IOException {
    Md2PdfEngine engine = new Md2PdfEngine();

    Path path1 = Paths.get("target/blue.pdf");
    Path path2 = Paths.get("target/yellow.pdf");

    String md1 = """
      # Blue Circle
      A big blue circle
      """;
    String md2 = """
      # Yellow Circle
      A big yellow circle
      """;

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    Runnable task1 = () -> {
      try {
        engine.markdown(md1).toPdf(path1);
      } catch (Md2PdfException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    };
    Runnable task2 = () -> {
      try {
        engine.markdown(md2).toPdf(path2);
      } catch (Md2PdfException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    };
    executorService.submit(task1);
    executorService.submit(task2);
    executorService.shutdown();
    assertTrue(executorService.awaitTermination(3, TimeUnit.SECONDS));
    assertTrue(path1.toFile().exists());
    assertTrue(path2.toFile().exists());
    assertTrue(extractContent(path1).contains("Blue Circle"));
    assertTrue(extractContent(path2).contains("Yellow Circle"));
  }

  @Test
  public void testDifferentInstancesConcurrently() throws InterruptedException, IOException {
    Path path1 = Paths.get("target/blue2.pdf");
    Path path2 = Paths.get("target/yellow2.pdf");

    String md1 = """
      # Blue Circle
      A big blue circle
      """;
    String md2 = """
      # Yellow Circle
      A big yellow circle
      """;

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    Runnable task1 = () -> {
      Md2PdfEngine engine = new Md2PdfEngine();
      try {
        engine.markdown(md1).toPdf(path1);
      } catch (Md2PdfException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    };
    Runnable task2 = () -> {
      Md2PdfEngine engine = new Md2PdfEngine();
      try {
        engine.markdown(md2).toPdf(path2);
      } catch (Md2PdfException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    };
    executorService.submit(task1);
    executorService.submit(task2);
    executorService.shutdown();
    assertTrue(executorService.awaitTermination(3, TimeUnit.SECONDS));
    assertTrue(path1.toFile().exists());
    assertTrue(path2.toFile().exists());
    assertTrue(extractContent(path1).contains("Blue Circle"));
    assertTrue(extractContent(path2).contains("Yellow Circle"));
  }

  String extractContent(Path path) throws IOException {
    try(PDDocument pdf = Loader.loadPDF(path.toFile())) {
      return new PDFTextStripper().getText(pdf);
    }
  }
}
