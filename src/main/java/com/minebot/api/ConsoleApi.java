package com.minebot.api;

import com.minebot.runtime.JavaBotRuntime;
import java.util.Objects;
import java.util.function.Consumer;

public final class ConsoleApi {
   private final JavaBotRuntime runtime;

   ConsoleApi(JavaBotRuntime var1) {
      this.runtime = var1;
   }

   public AutoCloseable subscribe(Consumer<ConsoleLogEvent> var1) {
      return this.runtime.subscribeConsole(Objects.requireNonNull(var1));
   }
}
