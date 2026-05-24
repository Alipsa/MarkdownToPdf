package se.alipsa.md2pdf.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Pagination;
import javafx.scene.image.ImageView;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/** JavaFX {@link Pagination} component that renders PDF pages as images using PDFBox. */
public class PDFViewer extends Pagination {

  private byte[] content;
  private final MarkdownToPdf gui;

  /**
   * Creates a viewer registered with the main application window.
   *
   * @param gui the main application window
   */
  public PDFViewer(MarkdownToPdf gui) {
    this.gui = gui;
  }

  /**
   * Loads a PDF file and renders it page-by-page into the pagination view.
   *
   * @param pdfFile the PDF file to load
   * @throws IOException if the file cannot be read
   */
  public void load(File pdfFile) throws IOException {
    load(Files.readAllBytes(pdfFile.toPath()));
  }

  /**
   * Loads PDF bytes and renders them page-by-page into the pagination view.
   *
   * @param content the raw PDF bytes
   * @throws IOException if the PDF cannot be decoded
   */
  public void load(byte[] content) throws IOException {
    PDDocument document = Loader.loadPDF(content);
    PDFRenderer renderer = new PDFRenderer(document);
    load(document, renderer);
    this.content = content;
  }

  private void load(PDDocument document, PDFRenderer renderer) {
    this.setPageCount(document.getNumberOfPages());
    this.setMaxPageIndicatorCount(3);

    this.setPageFactory(
        (pageIndex) -> {
          BufferedImage image;
          ImageView imageView;
          try {
            image = renderer.renderImage(pageIndex);
            imageView = new ImageView(SwingFXUtils.toFXImage(image, null));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          return imageView;
        });
  }

  /**
   * Returns the raw PDF bytes of the most recently loaded document.
   *
   * @return the PDF bytes, or {@code null} if no document has been loaded
   */
  public byte[] getContent() {
    return content;
  }
}
