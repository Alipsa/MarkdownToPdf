package test.alipsa.md2pdf;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.openhtmltopdf.util.JDKXRLogger;
import com.openhtmltopdf.util.XRLog;
import com.openhtmltopdf.util.XRLogger;
import org.junit.jupiter.api.Test;
import se.alipsa.md2pdf.Md2PdfEngine;
import se.alipsa.md2pdf.Slf4jXRLogger;

class LoggingConfigurationTest {

  @Test
  void engineConstructionInstallsSlf4jBridgeWhenNoLoggerIsConfigured() {
    XRLogger original = XRLog.getLoggerImpl();
    try {
      XRLog.setLoggerImpl(null);
      new Md2PdfEngine();

      assertInstanceOf(Slf4jXRLogger.class, XRLog.getLoggerImpl());
    } finally {
      restoreLogger(original);
    }
  }

  @Test
  void engineConstructionPreservesConfiguredLogger() {
    XRLogger original = XRLog.getLoggerImpl();
    XRLogger configured = new Slf4jXRLogger();
    XRLog.setLoggerImpl(configured);
    try {
      new Md2PdfEngine();

      assertSame(configured, XRLog.getLoggerImpl());
    } finally {
      restoreLogger(original);
    }
  }

  @Test
  void explicitLoggingConfigurationReplacesExistingLogger() {
    XRLogger original = XRLog.getLoggerImpl();
    XRLogger configured = new JDKXRLogger();
    XRLog.setLoggerImpl(configured);
    try {
      Md2PdfEngine.configureOpenHtmlToPdfLogging();

      assertInstanceOf(Slf4jXRLogger.class, XRLog.getLoggerImpl());
    } finally {
      restoreLogger(original);
    }
  }

  private static void restoreLogger(XRLogger original) {
    XRLog.setLoggerImpl(original);
  }
}
