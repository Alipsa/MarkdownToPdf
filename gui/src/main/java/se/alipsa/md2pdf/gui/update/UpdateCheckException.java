package se.alipsa.md2pdf.gui.update;

/** Thrown when checking GitHub for a new MarkdownToPdf release fails. */
public class UpdateCheckException extends Exception {

  /**
   * Creates an exception with a detail message.
   *
   * @param message the detail message
   */
  public UpdateCheckException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a detail message and cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public UpdateCheckException(String message, Throwable cause) {
    super(message, cause);
  }
}
