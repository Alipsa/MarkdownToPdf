// Starts the JavaFX toolkit and constructs a WebView. This is the only assertion that
// javafx.web's WebKit native library actually loads — a class-load check passes without it.
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.web.WebView;

public class ToolkitSmoke {
  public static void main(String[] args) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    Platform.startup(
        () -> {
          try {
            WebView view = new WebView();
            view.getEngine().loadContent("<html><body><h1>ok</h1></body></html>");
          } catch (Throwable t) {
            failure.set(t);
          } finally {
            done.countDown();
          }
        });

    if (!done.await(60, TimeUnit.SECONDS)) {
      System.err.println("ToolkitSmoke: JavaFX toolkit did not start within 60s");
      Runtime.getRuntime().halt(1);
    }
    Platform.exit();

    Throwable t = failure.get();
    if (t != null) {
      System.err.println("ToolkitSmoke: failed on the FX thread");
      t.printStackTrace();
      Runtime.getRuntime().halt(1);
    }
    System.out.println("ToolkitSmoke: OK");
    Runtime.getRuntime().halt(0);
  }
}
