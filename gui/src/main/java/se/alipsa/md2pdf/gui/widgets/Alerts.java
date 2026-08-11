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
   * A third button, present purely so the dialog remains closable. {@code
   * FXDialog.requestPermissionToClose} refuses to let the X button or ESC close a multi-button
   * dialog unless at least one button is {@code CANCEL_CLOSE} or otherwise a cancel button —
   * without one, a two-button Yes/No dialog becomes an unclosable modal trap. {@code CANCEL_CLOSE}
   * both satisfies that gate and is what {@code Dialog.close()} prefers when substituting a result
   * on an abnormal close, so a window/ESC close resolves to this button — not the yes- or
   * no-labelled one — and {@link #confirmAsync(String, String, String, String, String, Runnable,
   * Runnable)} correctly runs neither handler.
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
   * Shows a confirmation dialog without blocking, distinguishing an explicit yes/no answer from the
   * user closing the dialog without choosing (e.g. via the window's close button or ESC), for which
   * neither handler runs. Adds a third "Later" button so the window remains closable via X / ESC
   * (see {@link #LATER}'s javadoc) — closing it that way behaves the same as clicking "Later":
   * neither handler runs. Use this instead of {@link #confirmAsync(String, String, String,
   * Consumer)} whenever a "no" answer has a side effect (such as remembering a dismissal) that a
   * plain "didn't say yes" must not trigger.
   *
   * <p>{@code yesLabel}/{@code noLabel} should state each button's effect (e.g. "Open release page"
   * / "Skip this version") rather than a bare "Yes"/"No" — a plain "No" reads as "not right now",
   * which invites the user into a side effect the dialog text doesn't otherwise hint at.
   *
   * @param title the dialog window title
   * @param headerText the dialog header
   * @param contentText the dialog body
   * @param yesLabel the label of the button that runs {@code onYes}
   * @param noLabel the label of the button that runs {@code onNo}
   * @param onYes run when the user clicks the yes-labelled button
   * @param onNo run when the user clicks the no-labelled button
   */
  public static void confirmAsync(
      String title,
      String headerText,
      String contentText,
      String yesLabel,
      String noLabel,
      Runnable onYes,
      Runnable onNo) {
    ConfirmButtons buttons = explicitConfirmButtons(yesLabel, noLabel);
    Alert alert =
        createConfirmation(
            title, headerText, contentText, buttons.asList().toArray(new ButtonType[0]));
    alert.setOnHidden(
        event -> {
          ButtonType result = alert.getResult();
          if (result == buttons.yes()) {
            onYes.run();
          } else if (result == buttons.no()) {
            onNo.run();
          }
        });
    alert.show();
  }

  /**
   * The yes/no/Later buttons built by {@link #explicitConfirmButtons(String, String)}, named so
   * callers never have to index a list positionally to recover which button means what — a
   * reordering of {@link #asList()} would otherwise silently swap which button runs {@code onYes}
   * vs. {@code onNo}, and {@code ButtonData}-based tests like {@code AlertsButtonDataTest} wouldn't
   * catch it, since the {@code ButtonData} values would still be exactly right.
   *
   * @param yes the affirmative button
   * @param no the negative button
   * @param later the button used to dismiss the dialog without an answer
   */
  public record ConfirmButtons(ButtonType yes, ButtonType no, ButtonType later) {
    /**
     * Returns the three buttons in the order the dialog should display them.
     *
     * @return the yes, no, and later buttons
     */
    public List<ButtonType> asList() {
      return List.of(yes, no, later);
    }
  }

  /**
   * The yes/no/Later button set used by {@link #confirmAsync(String, String, String, String,
   * String, Runnable, Runnable)}, with caller-supplied labels for the first two. The {@code
   * ButtonBar.ButtonData} assigned to each — {@code YES}, {@code OTHER}, {@code CANCEL_CLOSE} — is
   * fixed regardless of label, which is what keeps the dialog closable via X/ESC and keeps that
   * close unambiguous from clicking the no-labelled button (see {@link #LATER}'s javadoc). Exposed
   * so tests can assert on the real button list without constructing a live {@code Alert} (which
   * requires an initialized JavaFX toolkit).
   *
   * @param yesLabel the label for the affirmative button
   * @param noLabel the label for the negative button
   * @return the labeled yes, no, and later buttons
   */
  public static ConfirmButtons explicitConfirmButtons(String yesLabel, String noLabel) {
    return new ConfirmButtons(
        new ButtonType(yesLabel, ButtonBar.ButtonData.YES),
        new ButtonType(noLabel, ButtonBar.ButtonData.OTHER),
        LATER);
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
