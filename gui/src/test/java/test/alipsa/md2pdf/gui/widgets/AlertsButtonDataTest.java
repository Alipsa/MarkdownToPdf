package test.alipsa.md2pdf.gui.widgets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.widgets.Alerts;

/**
 * Pins the two JavaFX button-data constraints that {@code Alerts.explicitConfirmButtons(String,
 * String)} (used by {@code confirmAsync(String, String, String, String, String, Runnable,
 * Runnable)}) has to satisfy simultaneously — the two pull against each other, so they're asserted
 * together against the real button list rather than as separate facts about the {@code ButtonData}
 * enum:
 *
 * <ul>
 *   <li><b>Closable</b>: {@code FXDialog.requestPermissionToClose} refuses to let the window's X
 *       button or ESC close a multi-button dialog unless at least one button is {@code
 *       CANCEL_CLOSE} or otherwise a cancel button — without one, the dialog becomes an unclosable
 *       modal trap.
 *   <li><b>Unambiguous</b>: exactly one button may be substitutable as the result of that X/ESC
 *       close, and it must be the no-op "Later" button — not the yes- or no-labelled one — or
 *       {@code Dialog.close()} could substitute a real answer as the result of the user not
 *       answering at all.
 * </ul>
 *
 * <p>Asserted on {@code ButtonData}, not button text: a label-based check (e.g. matching on the
 * text "No") passes vacuously the moment a label changes, since the filter then matches nothing.
 *
 * <p>These assertions don't need a live JavaFX toolkit — {@link ButtonType} and {@link
 * ButtonBar.ButtonData} are plain value types, not {@code Node}s — unlike constructing an {@code
 * Alert} itself, which requires {@code Platform.startup()} and isn't attempted here.
 */
public class AlertsButtonDataTest {

  @Test
  void buttonsAreBothClosableAndUnambiguous() {
    List<ButtonType> buttons = Alerts.explicitConfirmButtons("Yes", "No");

    List<ButtonType> substitutableOnClose =
        buttons.stream()
            .filter(
                b ->
                    b.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE
                        || b.getButtonData().isCancelButton())
            .toList();

    assertEquals(
        1,
        substitutableOnClose.size(),
        "exactly one button may be substituted as the result of an X/ESC close");
    assertEquals(
        ButtonBar.ButtonData.CANCEL_CLOSE,
        substitutableOnClose.get(0).getButtonData(),
        "and it must be the no-op Later button, not the yes- or no-labelled one");
  }
}
