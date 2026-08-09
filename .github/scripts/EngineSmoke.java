// Converts Markdown to PDF on whatever runtime invokes it. Exercises Batik, PDFBox and
// OpenHTMLtoPDF, so a java.* module missing from the jlink set fails here rather than in
// the field. Run with MarkdownToPdf.jar on the classpath: dependencies resolve through
// that jar's manifest Class-Path, which is also what proves lib/ is complete.
import se.alipsa.md2pdf.Md2PdfEngine;

public class EngineSmoke {
  public static void main(String[] args) throws Exception {
    String md = "# Smoke\n\nHello *world*.\n\n- one\n- two\n\n`code`\n";
    byte[] pdf = new Md2PdfEngine().markdown(md).toPdf();

    // Observed output for this input is ~1.6 KB; 500 catches a truncated or empty render
    // without being so tight that a harmless rendering change trips it.
    if (pdf.length < 500) {
      System.err.println("EngineSmoke: PDF is only " + pdf.length + " bytes");
      System.exit(1);
    }
    String magic = new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
    if (!"%PDF".equals(magic)) {
      System.err.println("EngineSmoke: expected %PDF, got " + magic);
      System.exit(1);
    }
    System.out.println("EngineSmoke: OK (" + pdf.length + " bytes)");
  }
}
