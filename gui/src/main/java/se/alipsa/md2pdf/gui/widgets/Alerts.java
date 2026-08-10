package se.alipsa.md2pdf.gui.widgets;

import java.net.URL;
import java.util.List;
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
   * {@link ButtonType#NO} (whose data is {@code ButtonData.NO}, a cancel button). {@code
   * Dialog.close()} substitutes any cancel-button as the result when the window is closed (X or
   * ESC) with nothing clicked, so a "No" built on a cancel-type button is indistinguishable from
   * the user not answering at all. {@code OTHER} is not a cancel button, so it can't be substituted
   * that way.
   */
  private static final ButtonType NO_EXPLICIT = new ButtonType("No", ButtonBar.ButtonData.OTHER);

  /**
   * A third button, present purely so the dialog remains closable. {@code
   * FXDialog.requestPermissionToClose} refuses to let the X button or ESC close a multi-button
   * dialog unless at least one button is {@code CANCEL_CLOSE} or otherwise a cancel button — with
   * only {@link ButtonType#YES} and {@link #NO_EXPLICIT} (neither a cancel button), the dialog
   * became an unclosable modal trap. {@code CANCEL_CLOSE} both satisfies that gate and is what
   * {@code Dialog.close()} prefers when substituting a result on an abnormal close, so a window/ESC
   * close resolves to this button — not {@code YES}, not {@code NO_EXPLICIT} — and {@link
   * #confirmAsync(String, String, String, Runnable, Runnable)} correctly runs neither handler.
   */
  private static final ButtonType LATER =
      new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);

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
   * neither handler runs. Adds a third "Later" button so the window remains closable via X / ESC
   * (see {@link #LATER}'s javadoc) — closing it that way behaves the same as clicking "Later":
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
    List<ButtonType> buttons = explicitConfirmButtons();
    Alert alert =
        createConfirmation(title, headerText, contentText, buttons.toArray(new ButtonType[0]));
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

  /**
   * The Yes/No/Later button set used by {@link #confirmAsync(String, String, String, Runnable,
   * Runnable)}. Exposed only so tests can assert on the real button list — closable via X/ESC, and
   * unambiguous between "closed" and "clicked No" — without constructing a live {@code Alert}
   * (which requires an initialized JavaFX toolkit).
   */
  public static List<ButtonType> explicitConfirmButtons() {
    return List.of(ButtonType.YES, NO_EXPLICIT, LATER);
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
