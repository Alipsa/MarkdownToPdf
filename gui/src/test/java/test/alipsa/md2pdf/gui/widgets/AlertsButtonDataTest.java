package test.alipsa.md2pdf.gui.widgets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.widgets.Alerts;

/**
 * Pins the two JavaFX button-data constraints that {@code Alerts.explicitConfirmButtons()} (used by
 * {@code confirmAsync(Runnable, Runnable)}) has to satisfy simultaneously — the two pulls against
 * each other, so they're asserted together against the real button list rather than as separate
 * facts about the {@code ButtonData} enum:
 *
 * <ul>
 *   <li><b>Closable</b>: {@code FXDialog.requestPermissionToClose} refuses to let the window's X
 *       button or ESC close a multi-button dialog unless at least one button is {@code
 *       CANCEL_CLOSE} or otherwise a cancel button — without one, the dialog becomes an unclosable
 *       modal trap.
 *   <li><b>Unambiguous</b>: the button labeled "No" must itself <i>not</i> be a cancel button, or
 *       {@code Dialog.close()} substitutes it as the result on that same X/ESC close, making
 *       "closed without answering" indistinguishable from "clicked No".
 * </ul>
 *
 * <p>These assertions don't need a live JavaFX toolkit — {@link ButtonType} and {@link
 * ButtonBar.ButtonData} are plain value types, not {@code Node}s — unlike constructing an {@code
 * Alert} itself, which requires {@code Platform.startup()} and isn't attempted here.
 */
public class AlertsButtonDataTest {

  @Test
  void buttonsAreBothClosableAndUnambiguous() {
    List<ButtonType> buttons = Alerts.explicitConfirmButtons();

    assertTrue(
        buttons.stream()
            .map(ButtonType::getButtonData)
            .anyMatch(d -> d == ButtonBar.ButtonData.CANCEL_CLOSE || d.isCancelButton()),
        "must contain a CANCEL_CLOSE or cancel-button, or the dialog cannot be closed via X/ESC");

    assertFalse(
        buttons.stream()
            .filter(b -> "No".equals(b.getText()))
            .anyMatch(b -> b.getButtonData().isCancelButton()),
        "the No button must not itself be a cancel button");
  }
}
