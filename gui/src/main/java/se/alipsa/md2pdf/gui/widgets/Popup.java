package se.alipsa.md2pdf.gui.widgets;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import se.alipsa.md2pdf.gui.MarkdownToPdf;

/** Utility class for displaying a JavaFX node in a standalone popup window. */
public class Popup {

  private Popup() {}

  /**
   * Opens a new window showing the given node, styled with the application theme.
   *
   * @param img the scene-graph node to display
   * @param gui the main application window (provides icon and stylesheet)
   * @param title optional window title; first element is used if present
   */
  public static void display(Node img, MarkdownToPdf gui, String... title) {
    Stage stage = new Stage();
    if (title.length > 0) {
      stage.setTitle(title[0]);
    }
    BorderPane pane = new BorderPane(img);
    pane.setPadding(new Insets(10));
    Scene scene = new Scene(pane);
    stage.getIcons().add(MarkdownToPdf.getLogo());
    scene.getStylesheets().add(MarkdownToPdf.getStyleSheet().toExternalForm());
    stage.setScene(scene);
    stage.show();
  }
}
