package com.minebot.api;

import java.time.Instant;
import java.util.Objects;

public record ConsoleLogEvent(Instant timestamp, String level, String logger, String message, ConsoleThrowable throwable) {
   public ConsoleLogEvent(Instant timestamp, String level, String logger, String message, ConsoleThrowable throwable) {
      timestamp = Objects.requireNonNull(timestamp);
      level = Objects.requireNonNullElse(level, "UNKNOWN");
      logger = Objects.requireNonNullElse(logger, "");
      message = Objects.requireNonNullElse(message, "");
      this.timestamp = timestamp;
      this.level = level;
      this.logger = logger;
      this.message = message;
      this.throwable = throwable;
   }

   public String stackTrace() {
      return this.throwable == null ? "" : this.throwable.stackTrace();
   }
}
