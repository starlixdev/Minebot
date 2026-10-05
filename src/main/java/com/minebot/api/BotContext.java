package com.minebot.api;

import com.minebot.runtime.JavaBotRuntime;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class BotContext {
   private final JavaBotRuntime runtime;
   private final DiscordApi discord;
   private final MinecraftApi minecraft;
   private final StorageApi storage;
   private final HttpApi http;
   private final ConsoleApi console;

   BotContext(JavaBotRuntime var1) {
      this.runtime = var1;
      this.discord = new DiscordApi(var1);
      this.minecraft = new MinecraftApi(var1);
      this.storage = new StorageApi(var1);
      this.http = new HttpApi(var1);
      this.console = new ConsoleApi(var1);
   }

   public String name() {
      return this.runtime.name();
   }

   public BotConfiguration configuration() {
      return this.runtime.configuration();
   }

   public DiscordApi discord() {
      return this.discord;
   }

   public MinecraftApi minecraft() {
      return this.minecraft;
   }

   public StorageApi storage() {
      return this.storage;
   }

   public HttpApi http() {
      return this.http;
   }

   public ConsoleApi console() {
      return this.console;
   }

   public String secret(String var1) {
      return this.runtime.secret(var1);
   }

   public void log(String var1) {
      this.runtime.log(var1);
   }

   public void warn(String var1) {
      this.runtime.warn(var1);
   }

   public CompletableFuture<Void> waitFor(Duration var1) {
      return this.runtime.delay(var1);
   }

   public AutoCloseable every(Duration var1, Runnable var2) {
      return this.runtime.scheduleEvery(var1, var2);
   }

   public void executeSerial(Runnable var1) {
      this.runtime.executeSerial(var1);
   }

   public boolean running() {
      return this.runtime.running();
   }
}
