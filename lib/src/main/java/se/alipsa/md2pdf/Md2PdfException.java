package se.alipsa.md2pdf;

/** Base checked exception class for md2pdf operations */
public class Md2PdfException extends Exception {

  /**
   * Constructs a new exception with the specified detail message.
   *
   * @param message the description of the issue
   */
  public Md2PdfException(String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the specified detail message and cause.
   *
   * @param message the description of the issue
   * @param cause the cause (which is saved for later retrieval by the Throwable.getCause() method).
   */
  public Md2PdfException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new exception with the specified cause and a detail message of (cause==null ? null
   * : cause.toString()) (which typically contains the class and detail message of cause).
   *
   * @param cause the cause (which is saved for later retrieval by the Throwable.getCause() method).
   */
  public Md2PdfException(Throwable cause) {
    super(cause);
  }
}
