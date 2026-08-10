package se.alipsa.md2pdf.gui.update;

/** Thrown when checking GitHub for a new MarkdownToPdf release fails. */
public class UpdateCheckException extends Exception {

  public UpdateCheckException(String message) {
    super(message);
  }

  public UpdateCheckException(String message, Throwable cause) {
    super(message, cause);
  }
}
