package com.minebot.api;

import java.util.List;
import java.util.Objects;

public record ConsoleThrowable(String type, String message, String stackTrace, List<ConsoleThrowable> suppressed, ConsoleThrowable cause) {
   public ConsoleThrowable(String type, String message, String stackTrace, List<ConsoleThrowable> suppressed, ConsoleThrowable cause) {
      type = Objects.requireNonNullElse(type, "");
      message = Objects.requireNonNullElse(message, "");
      stackTrace = Objects.requireNonNullElse(stackTrace, "");
      suppressed = List.copyOf(Objects.requireNonNullElseGet(suppressed, List::of));
      this.type = type;
      this.message = message;
      this.stackTrace = stackTrace;
      this.suppressed = suppressed;
      this.cause = cause;
   }
}
