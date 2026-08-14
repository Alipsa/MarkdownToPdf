package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.openhtmltopdf.util.XRLog;
import com.openhtmltopdf.util.XRLogger;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfEngine;
import se.alipsa.md2pdf.Slf4jXRLogger;

class LoggingConfigurationTest {

  @Test
  void engineConstructionDoesNotReplaceConfiguredLogger() {
    XRLogger original = XRLog.getLoggerImpl();
    XRLogger configured = new Slf4jXRLogger();
    XRLog.setLoggerImpl(configured);
    try {
      new Md2PdfEngine();

      assertSame(configured, XRLog.getLoggerImpl());
    } finally {
      if (original != null) {
        XRLog.setLoggerImpl(original);
      }
    }
  }

  @Test
  void loggingBridgeIsEnabledExplicitly() {
    XRLogger original = XRLog.getLoggerImpl();
    try {
      Md2PdfEngine.configureOpenHtmlToPdfLogging();

      assertInstanceOf(Slf4jXRLogger.class, XRLog.getLoggerImpl());
    } finally {
      if (original != null) {
        XRLog.setLoggerImpl(original);
      }
    }
  }
}
