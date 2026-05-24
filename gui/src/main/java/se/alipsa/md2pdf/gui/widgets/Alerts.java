package se.alipsa.md2pdf.gui.widgets;

import java.net.URL;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import se.alipsa.md2pdf.gui.MarkdownToPdf;

/** Utility class providing styled JavaFX alert dialogs with the application icon and theme. */
public class Alerts {

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
    Alert alert =
        new Alert(Alert.AlertType.CONFIRMATION, contentText, ButtonType.YES, ButtonType.NO);
    alert.setTitle(title);
    alert.setHeaderText(headerText);
    URL styleSheetUrl = Alerts.class.getResource("/default-theme.css");
    if (styleSheetUrl != null) {
      alert.getDialogPane().getStylesheets().add(styleSheetUrl.toExternalForm());
    }
    alert.getDialogPane().getStylesheets().add(MarkdownToPdf.getStyleSheet().toExternalForm());
    Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
    stage.getIcons().add(MarkdownToPdf.getLogo());
    Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && result.get() == ButtonType.YES;
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
