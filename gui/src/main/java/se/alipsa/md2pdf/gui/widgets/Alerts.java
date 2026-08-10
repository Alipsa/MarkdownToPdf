package se.alipsa.md2pdf.gui.widgets;

import java.net.URL;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import se.alipsa.md2pdf.gui.MarkdownToPdf;

/** Utility class providing styled JavaFX alert dialogs with the application icon and theme. */
public class Alerts {

  /**
   * A "No" button whose {@link ButtonBar.ButtonData} is {@code OTHER} rather than the built-in
   * {@link ButtonType#NO} (whose data is {@code ButtonData.NO}, which {@link
   * ButtonBar.ButtonData#isCancelButton()} reports as a cancel button). {@code Dialog.close()}
   * substitutes any cancel-button as the result when the window is closed via the X button or ESC
   * with no button clicked — so a dialog built with the built-in {@code ButtonType.NO} cannot
   * distinguish "the user clicked No" from "the user closed the window without answering". {@code
   * OTHER} is not a cancel button, so a close leaves {@code getResult()} {@code null} instead.
   */
  private static final ButtonType NO_EXPLICIT = new ButtonType("No", ButtonBar.ButtonData.OTHER);

  private Alerts() {}

  /**
   * Shows a confirmation dialog and returns whether the user clicked Yes.
   *
   * @param title the dialog window title
   * @param headerText the dialog header
   * @param contentText the dialog body text
   * @return {@code true} if the user chose Yes
   */
  public static boolean confirm(String title, String headerText, String contentText) {
    Alert alert = createConfirmation(title, headerText, contentText, ButtonType.YES, ButtonType.NO);
    Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && result.get() == ButtonType.YES;
  }

  /**
   * Shows a confirmation dialog without blocking the current event handler.
   *
   * @param title the dialog window title
   * @param headerText the dialog header
   * @param contentText the dialog body
   * @param resultHandler called with {@code true} when Yes is selected
   */
  public static void confirmAsync(
      String title, String headerText, String contentText, Consumer<Boolean> resultHandler) {
    Alert alert = createConfirmation(title, headerText, contentText, ButtonType.YES, ButtonType.NO);
    alert.setOnHidden(event -> resultHandler.accept(alert.getResult() == ButtonType.YES));
    alert.show();
  }

  /**
   * Shows a confirmation dialog without blocking, distinguishing an explicit Yes/No answer from the
   * user closing the dialog without choosing (e.g. via the window's close button or ESC), for which
   * neither handler runs. Use this instead of {@link #confirmAsync(String, String, String,
   * Consumer)} whenever a No answer has a side effect (such as remembering a dismissal) that a
   * plain "didn't say Yes" must not trigger.
   *
   * @param title the dialog window title
   * @param headerText the dialog header
   * @param contentText the dialog body
   * @param onYes run when the user clicks Yes
   * @param onNo run when the user clicks No
   */
  public static void confirmAsync(
      String title, String headerText, String contentText, Runnable onYes, Runnable onNo) {
    Alert alert = createConfirmation(title, headerText, contentText, ButtonType.YES, NO_EXPLICIT);
    alert.setOnHidden(
        event -> {
          ButtonType result = alert.getResult();
          if (result == ButtonType.YES) {
            onYes.run();
          } else if (result == NO_EXPLICIT) {
            onNo.run();
          }
        });
    alert.show();
  }

  private static Alert createConfirmation(
      String title, String headerText, String contentText, ButtonType... buttons) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION, contentText, buttons);
    alert.setTitle(title);
    alert.setHeaderText(headerText);
    URL styleSheetUrl = Alerts.class.getResource("/default-theme.css");
    if (styleSheetUrl != null) {
      alert.getDialogPane().getStylesheets().add(styleSheetUrl.toExternalForm());
    }
    alert.getDialogPane().getStylesheets().add(MarkdownToPdf.getStyleSheet().toExternalForm());
    Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
    stage.getIcons().add(MarkdownToPdf.getLogo());
    return alert;
  }

  /**
   * Shows an informational alert.
   *
   * @param title the dialog title
   * @param content the message to display
   * @return the button the user clicked
   */
  public static Optional<ButtonType> info(String title, String content) {
    return showAlert(title, content, Alert.AlertType.INFORMATION);
  }

  /**
   * Shows a warning alert.
   *
   * @param title the dialog title
   * @param content the message to display
   * @return the button the user clicked
   */
  public static Optional<ButtonType> warn(String title, String content) {
    return showAlert(title, content, Alert.AlertType.WARNING);
  }

  /**
   * Shows an alert with the given type, title, and scrollable text content.
   *
   * @param title the dialog title
   * @param content the message to display in the scrollable text area
   * @param information the JavaFX alert type
   * @return the button the user clicked
   */
  public static Optional<ButtonType> showAlert(
      String title, String content, Alert.AlertType information) {

    TextArea textArea = new TextArea(content);
    textArea.setEditable(false);
    textArea.setWrapText(true);

    BorderPane pane = new BorderPane();
    pane.setCenter(textArea);

    Alert alert = new Alert(information);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.getDialogPane().setContent(pane);
    alert.setResizable(true);

    alert.getDialogPane().getStylesheets().add(MarkdownToPdf.getStyleSheet().toExternalForm());
    Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
    stage.getIcons().add(MarkdownToPdf.getLogo());

    return alert.showAndWait();
  }
}
