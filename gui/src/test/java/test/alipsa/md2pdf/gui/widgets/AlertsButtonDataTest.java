package test.alipsa.md2pdf.gui.widgets;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.junit.jupiter.api.Test;

/**
 * Pins the JavaFX button-data semantics that {@code Alerts.confirmAsync(Runnable, Runnable)} relies
 * on to distinguish an explicit "No" click from the user closing the dialog without answering.
 *
 * <p>{@code Dialog.close()} substitutes any cancel-button as the result when the window is closed
 * (X button or ESC) with nothing clicked, so building the "No" button on the built-in {@link
 * ButtonType#NO} — whose data is {@link ButtonBar.ButtonData#NO}, a cancel button — makes a window
 * close indistinguishable from clicking No. {@link ButtonBar.ButtonData#OTHER} is not a cancel
 * button, which is why {@code Alerts} builds its explicit-No button on that instead.
 *
 * <p>These assertions don't need a live JavaFX toolkit — {@link ButtonType} and {@link
 * ButtonBar.ButtonData} are plain value types, not {@code Node}s — unlike constructing an {@code
 * Alert} itself, which requires {@code Platform.startup()} and isn't attempted here.
 */
public class AlertsButtonDataTest {

  @Test
  void builtinNoButtonDataIsACancelButton() {
    assertTrue(ButtonType.NO.getButtonData().isCancelButton());
  }

  @Test
  void otherButtonDataIsNotACancelButton() {
    assertFalse(ButtonBar.ButtonData.OTHER.isCancelButton());
  }
}
