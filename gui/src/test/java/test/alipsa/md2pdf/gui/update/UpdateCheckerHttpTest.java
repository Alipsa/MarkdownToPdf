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
import org.junit.jupiter.api.parallel.Isolated;
import se.alipsa.md2pdf.gui.update.UpdateCheckException;
import se.alipsa.md2pdf.gui.update.UpdateCheckOutcome;
import se.alipsa.md2pdf.gui.update.UpdateCheckResult;
import se.alipsa.md2pdf.gui.update.UpdateChecker;
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
    // HttpServer context matching is path-only; the query string does not need to be part
    // of the registered context path below.
    System.setProperty(
        UpdateChecker.API_URL_PROPERTY,
        "http://127.0.0.1:" + server.getAddress().getPort() + "/releases?per_page=100");
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
    System.clearProperty(UpdateChecker.API_URL_PROPERTY);
  }

  private void respond(int status, String body) {
    respond(status, body, false);
  }

  private void respond(int status, String body, boolean hasNextPage) {
    server.createContext(
        "/releases",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          if (hasNextPage) {
            exchange
                .getResponseHeaders()
                .add(
                    "Link",
                    "<https://api.github.com/repos/Alipsa/MarkdownToPdf/releases?page=2>; rel=\"next\"");
          }
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
  void emptyBodyIsIndeterminateWithoutThrowing() throws UpdateCheckException {
    respond(200, "");
    UpdateChecker checker = new UpdateChecker();
    UpdateCheckResult result = checker.checkForUpdate("0.1.0");
    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  @Test
  void malformedJsonIsIndeterminateWithoutThrowing() throws UpdateCheckException {
    respond(200, "{not json at all");
    UpdateChecker checker = new UpdateChecker();
    UpdateCheckResult result = checker.checkForUpdate("0.1.0");
    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }

  @Test
  void wellFormedNewerReleaseIsReturned() throws UpdateCheckException {
    UpdatePlatform platform = UpdatePlatform.detectCurrent();
    String assetName = "md2pdf-99.0.0" + platform.assetSuffix();
    respond(
        200,
        """
        [
          {
            "tag_name": "MarkdownToPdf-v99.0.0",
            "html_url": "https://github.com/Alipsa/MarkdownToPdf/releases/tag/MarkdownToPdf-v99.0.0",
            "assets": [
              {"name": "%s", "browser_download_url": "https://example.com/%s"}
            ]
          }
        ]
        """
            .formatted(assetName, assetName));

    UpdateCheckResult result = new UpdateChecker().checkForUpdate("0.1.0");

    // Deterministic on every platform CI runs on: UNSUPPORTED is INDETERMINATE (no asset suffix
    // to build a real file name from), every supported platform matches the asset built above.
    if (platform == UpdatePlatform.UNSUPPORTED) {
      assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    } else {
      assertEquals(UpdateCheckOutcome.UPDATE_AVAILABLE, result.outcome());
      assertTrue(result.updateInfo().isPresent());
    }
  }

  @Test
  void currentVersionOnAnIncompletePageIsIndeterminate() throws UpdateCheckException {
    UpdatePlatform platform = UpdatePlatform.detectCurrent();
    String assetName = "md2pdf-0.1.0" + platform.assetSuffix();
    respond(
        200,
        """
        [
          {
            "tag_name": "MarkdownToPdf-v0.1.0",
            "html_url": "https://github.com/Alipsa/MarkdownToPdf/releases/tag/MarkdownToPdf-v0.1.0",
            "assets": [
              {"name": "%s", "browser_download_url": "https://example.com/%s"}
            ]
          }
        ]
        """
            .formatted(assetName, assetName),
        true);

    UpdateCheckResult result = new UpdateChecker().checkForUpdate("0.1.0");

    assertEquals(UpdateCheckOutcome.INDETERMINATE, result.outcome());
    assertTrue(result.updateInfo().isEmpty());
  }
}
