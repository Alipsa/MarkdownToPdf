package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfEngine;

public class PdfMetadataTest {

  @Test
  void testFluentPdfMetadata() throws Exception {
    byte[] pdf =
        new Md2PdfEngine()
            .markdown("# Metadata")
            .title("Quarterly Report")
            .author("Alipsa")
            .subject("Sales")
            .producer("Md2Pdf")
            .toPdf();

    try (PDDocument document = Loader.loadPDF(pdf)) {
      PDDocumentInformation information = document.getDocumentInformation();
      assertEquals("Quarterly Report", information.getTitle());
      assertEquals("Alipsa", information.getAuthor());
      assertEquals("Sales", information.getSubject());
      assertEquals("Md2Pdf", information.getProducer());
    }
  }
}
