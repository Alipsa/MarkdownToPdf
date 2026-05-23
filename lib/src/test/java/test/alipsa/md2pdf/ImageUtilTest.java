package test.alipsa.md2pdf;

import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.ImageUtil;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageUtilTest {

  @Test
  void testAsDataUrlFromBytes() {
    String dataUrl = ImageUtil.asDataUrl("hello".getBytes(StandardCharsets.UTF_8), "text/plain");

    assertEquals("data:text/plain;base64,aGVsbG8=", dataUrl);
  }

  @Test
  void testAsDataUrlFromResource() throws Exception {
    String dataUrl = ImageUtil.asDataUrl("/images/alice2.png");

    assertTrue(dataUrl.startsWith("data:image/png;base64,"));
    assertTrue(Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1)).length > 0);
  }

  @Test
  void testAsDataUrlFromUrl() throws Exception {
    URL resource = getClass().getResource("/images/alice2.png");

    String dataUrl = ImageUtil.asDataUrl(resource, "image/png");

    assertTrue(dataUrl.startsWith("data:image/png;base64,"));
    assertTrue(Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1)).length > 0);
  }
}
