package se.alipsa.md2pdf;

import com.openhtmltopdf.util.Diagnostic;
import com.openhtmltopdf.util.XRLog;
import com.openhtmltopdf.util.XRLogger;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.slf4j.LoggerFactory;

/**
 * TODO: contribute this class to the openhtmltopdf project
 * https://github.com/danfickle/openhtmltopdf
 */
/** Bridges openhtmltopdf's XRLog to SLF4J. */
public class Slf4jXRLogger implements XRLogger {

  /** Creates a new logger bridge. */
  public Slf4jXRLogger() {}

  private static final String DEFAULT_LOGGER_NAME = "org.openhtmltopdf.other";

  private static final Map<String, String> LOGGER_NAME_MAP;

  static {
    LOGGER_NAME_MAP = new HashMap<>();

    LOGGER_NAME_MAP.put(XRLog.CONFIG, "com.openhtmltopdf.config");
    LOGGER_NAME_MAP.put(XRLog.EXCEPTION, "com.openhtmltopdf.exception");
    LOGGER_NAME_MAP.put(XRLog.GENERAL, "com.openhtmltopdf.general");
    LOGGER_NAME_MAP.put(XRLog.INIT, "com.openhtmltopdf.init");
    LOGGER_NAME_MAP.put(XRLog.JUNIT, "com.openhtmltopdf.junit");
    LOGGER_NAME_MAP.put(XRLog.LOAD, "com.openhtmltopdf.load");
    LOGGER_NAME_MAP.put(XRLog.MATCH, "com.openhtmltopdf.match");
    LOGGER_NAME_MAP.put(XRLog.CASCADE, "com.openhtmltopdf.cascade");
    LOGGER_NAME_MAP.put(XRLog.XML_ENTITIES, "com.openhtmltopdf.load.xmlentities");
    LOGGER_NAME_MAP.put(XRLog.CSS_PARSE, "com.openhtmltopdf.cssparse");
    LOGGER_NAME_MAP.put(XRLog.LAYOUT, "com.openhtmltopdf.layout");
    LOGGER_NAME_MAP.put(XRLog.RENDER, "com.openhtmltopdf.render");
  }

  @Override
  public void log(String where, Level level, String msg) {
    LoggerFactory.getLogger(getLoggerName(where)).atLevel(toSlf4JLevel(level)).log(msg);
  }

  @Override
  public void log(String where, Level level, String msg, Throwable th) {
    LoggerFactory.getLogger(getLoggerName(where)).atLevel(toSlf4JLevel(level)).log(msg, th);
  }

  @Override
  public void setLevel(String logger, Level level) {
    String loggerName = getLoggerName(logger);
    String backendLevel = toBackendLevel(level);
    if (setLogbackLevel(loggerName, backendLevel)) {
      return;
    }
    setLog4jLevel(loggerName, backendLevel);
  }

  @Override
  public boolean isLogLevelEnabled(Diagnostic diagnostic) {
    var lvl = toSlf4JLevel(diagnostic.getLevel());
    var logger = LoggerFactory.getLogger(getLoggerName(diagnostic.getLogMessageId().getWhere()));
    return logger.isEnabledForLevel(lvl);
  }

  @Override
  public void log(Diagnostic diagnostic) {
    var logger = LoggerFactory.getLogger(getLoggerName(diagnostic.getLogMessageId().getWhere()));
    var level = toSlf4JLevel(diagnostic.getLevel());
    String msg = diagnostic.getLogMessageId().getMessageFormat();
    logger.atLevel(level).log(msg, diagnostic.getArgs());
  }

  private org.slf4j.event.Level toSlf4JLevel(Level level) {
    if (level == Level.SEVERE) {
      return org.slf4j.event.Level.ERROR;
    } else if (level == Level.WARNING) {
      return org.slf4j.event.Level.WARN;
    } else if (level == Level.INFO) {
      return org.slf4j.event.Level.INFO;
    } else if (level == Level.CONFIG) {
      return org.slf4j.event.Level.INFO;
    } else if (level == Level.FINE) {
      return org.slf4j.event.Level.DEBUG;
    } else if (level == Level.FINER || level == Level.FINEST) {
      return org.slf4j.event.Level.TRACE;
    } else {
      return org.slf4j.event.Level.INFO;
    }
  }

  private String toBackendLevel(Level level) {
    if (level == Level.SEVERE) {
      return "ERROR";
    } else if (level == Level.WARNING) {
      return "WARN";
    } else if (level == Level.INFO || level == Level.CONFIG) {
      return "INFO";
    } else if (level == Level.FINE) {
      return "DEBUG";
    } else if (level == Level.FINER || level == Level.FINEST) {
      return "TRACE";
    } else if (level == Level.OFF) {
      return "OFF";
    } else if (level == Level.ALL) {
      return "TRACE";
    } else {
      return "INFO";
    }
  }

  private boolean setLogbackLevel(String loggerName, String levelName) {
    org.slf4j.Logger logger = LoggerFactory.getLogger(loggerName);
    if (!"ch.qos.logback.classic.Logger".equals(logger.getClass().getName())) {
      return false;
    }
    try {
      Class<?> levelClass = Class.forName("ch.qos.logback.classic.Level");
      Object logbackLevel = levelClass.getField(levelName).get(null);
      logger.getClass().getMethod("setLevel", levelClass).invoke(logger, logbackLevel);
      return true;
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }

  private boolean setLog4jLevel(String loggerName, String levelName) {
    try {
      Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
      Object log4jLevel = levelClass.getMethod("valueOf", String.class).invoke(null, levelName);
      Class<?> configuratorClass =
          Class.forName("org.apache.logging.log4j.core.config.Configurator");
      configuratorClass
          .getMethod("setLevel", String.class, levelClass)
          .invoke(null, loggerName, log4jLevel);
      return true;
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }

  private String getLoggerName(String xrLoggerName) {
    return LOGGER_NAME_MAP.getOrDefault(xrLoggerName, DEFAULT_LOGGER_NAME);
  }
}
