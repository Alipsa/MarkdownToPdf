package test.alipsa.md2pdf.gui.update;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import se.alipsa.md2pdf.gui.update.UpdateCheckException;
import se.alipsa.md2pdf.gui.update.UpdateChecker;
import se.alipsa.md2pdf.gui.update.UpdateInfo;
import se.alipsa.md2pdf.gui.update.UpdatePlatform;

/**
 * Exercises {@link UpdateChecker#checkForUpdate(String)} end-to-end against a real local HTTP
 * server (JDK-bundled {@code com.sun.net.httpserver}, so this adds no dependency) via the {@link
 * UpdateChecker#API_URL_PROPERTY} override seam — the same seam intended for manual QA against a
 * fixture server.
 *
 * <p>{@code @Isolated}: this test mutates the {@code md2pdf.update.apiUrl} system property, which
 * is global JVM state. Harmless with this project's default sequential test execution, but
 * isolating it keeps that true even if parallel execution is ever enabled.
 */
@Isolated
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
    if (server != null) {
      server.stop(0);
    }
    System.clearProperty(UpdateChecker.API_URL_PROPERTY);
  }

  private void respond(int status, String body) {
    server.createContext(
        "/releases/latest",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          // sendResponseHeaders' responseLength contract: 0 means chunked with unspecified
          // length, -1 means no response body at all. A genuinely empty body must send -1, not
          // 0, or the client is left waiting on a chunked stream that never starts.
          exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            if (bytes.length > 0) {
              os.write(bytes);
            }
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
    UpdatePlatform platform = UpdatePlatform.detectCurrent();
    String assetName = "md2pdf-99.0.0" + platform.assetSuffix();
    respond(
        200,
        """
        {
          "tag_name": "v99.0.0",
          "html_url": "https://github.com/Alipsa/MarkdownToPdf/releases/tag/v99.0.0",
          "assets": [
            {"name": "%s", "browser_download_url": "https://example.com/%s"}
          ]
        }
        """
            .formatted(assetName, assetName));

    Optional<UpdateInfo> result = new UpdateChecker().checkForUpdate("0.1.0");

    // Deterministic on every platform CI runs on: UNSUPPORTED never matches (no asset suffix to
    // build a real file name from), every supported platform matches the asset built above.
    assertEquals(platform != UpdatePlatform.UNSUPPORTED, result.isPresent());
  }
}
