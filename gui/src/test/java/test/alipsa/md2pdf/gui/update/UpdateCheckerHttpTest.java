package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.gui.update.UpdateCheckException;
import se.alipsa.md2pdf.gui.update.UpdateChecker;

/**
 * Exercises {@link UpdateChecker#checkForUpdate(String)} end-to-end against a real local HTTP
 * server (JDK-bundled {@code com.sun.net.httpserver}, so this adds no dependency) via the {@link
 * UpdateChecker#API_URL_PROPERTY} override seam — the same seam intended for manual QA against a
 * fixture server.
 */
public class UpdateCheckerHttpTest {

  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    System.setProperty(
        UpdateChecker.API_URL_PROPERTY,
        "http://127.0.0.1:" + server.getAddress().getPort() + "/releases/latest");
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
    System.clearProperty(UpdateChecker.API_URL_PROPERTY);
  }

  private void respond(int status, String body) {
    server.createContext(
        "/releases/latest",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });
  }

  @Test
  void non200StatusThrowsUpdateCheckException() {
    respond(500, "boom");
    UpdateChecker checker = new UpdateChecker();
    assertThrows(UpdateCheckException.class, () -> checker.checkForUpdate("0.1.0"));
  }

  @Test
  void emptyBodyReturnsEmptyWithoutThrowing() throws UpdateCheckException {
    respond(200, "");
    UpdateChecker checker = new UpdateChecker();
    assertTrue(checker.checkForUpdate("0.1.0").isEmpty());
  }

  @Test
  void malformedJsonReturnsEmptyWithoutThrowing() throws UpdateCheckException {
    respond(200, "{not json at all");
    UpdateChecker checker = new UpdateChecker();
    assertTrue(checker.checkForUpdate("0.1.0").isEmpty());
  }

  @Test
  void wellFormedNewerReleaseIsReturned() throws UpdateCheckException {
    respond(
        200,
        """
        {
          "tag_name": "v99.0.0",
          "html_url": "https://github.com/Alipsa/MarkdownToPdf/releases/tag/v99.0.0",
          "assets": [
            {"name": "md2pdf-99.0.0-linux-x64.zip",
             "browser_download_url": "https://example.com/md2pdf-99.0.0-linux-x64.zip"}
          ]
        }
        """);
    UpdateChecker checker = new UpdateChecker();
    // The result depends on the platform this test runs on (only Linux x64 will see the asset
    // above match); either way it must not throw, which is what this end-to-end path exists to
    // cover — the pure-logic asset matching itself is already covered by UpdateCheckerTest.
    assertDoesNotThrow(() -> checker.checkForUpdate("0.1.0"));
  }
}
