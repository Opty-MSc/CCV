package pt.ulisboa.tecnico.cnv.util;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LoggerFormatter extends Formatter {

  public static final String ANSI_RESET = "\u001B[0m";
  public static final String ANSI_CYAN = "\u001B[36m";
  public static final String ANSI_YELLOW = "\u001B[33m";
  public static final String ANSI_RED = "\u001B[31m";

  @Override
  public String format(LogRecord logRecord) {
    StringBuilder logBuilder = new StringBuilder();
    logBuilder.append(logRecord.getLevel().toString()).append(": ");
    if (logRecord.getSourceClassName() != null) {
      logBuilder
          .append(
              logRecord
                  .getSourceClassName()
                  .substring(logRecord.getSourceClassName().lastIndexOf('.') + 1))
          .append(": ");
      if (logRecord.getSourceMethodName() != null) {
        logBuilder.append(logRecord.getSourceMethodName()).append(": ");
      }
    } else {
      logBuilder.append(logRecord.getLoggerName()).append(": ");
    }
    String message = formatMessage(logRecord);

    switch (logRecord.getLevel().toString()) {
      case "INFO":
        return String.format("%s%s\n>>> %s%s\n", ANSI_CYAN, logBuilder, ANSI_RESET, message);
      case "WARNING":
        return String.format("%s%s\n>>> %s%s\n", ANSI_YELLOW, logBuilder, ANSI_RESET, message);
      case "SEVERE":
        return String.format("%s%s\n>>> %s%s\n", ANSI_RED, logBuilder, ANSI_RESET, message);
      default:
        return String.format("%s\n>>> %s\n", logBuilder, message);
    }
  }
}
