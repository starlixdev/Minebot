package com.minebot.api;

import com.minebot.bot.BotManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MineBotJavaApi {
   private static volatile BotManager manager;

   private MineBotJavaApi() {
   }

   private static void bootstrap(BotManager var0) {
      manager = Objects.requireNonNull(var0);
   }

   private static void clear() {
      manager = null;
   }

   public static boolean available() {
      return manager != null;
   }

   public static Path create(String var0) throws IOException {
      return manager().create(var0);
   }

   public static void registerProvider(String var0, BotProvider var1) throws Exception {
      manager().registerProvider(var0, var1);
   }

   public static void unregisterProvider(String var0) {
      manager().unregisterProvider(var0);
   }

   public static void start(String var0) throws Exception {
      manager().start(var0);
   }

   public static void stop(String var0) {
      manager().stop(var0);
   }

   public static void reload(String var0) throws Exception {
      manager().reload(var0);
   }

   public static Optional<RuntimeHandle> runtime(String var0) {
      return manager().runtime(var0);
   }

   public static List<String> names() {
      return manager().names();
   }

   public static ValidationResult validate(String var0) {
      BotManager.Validation var1 = manager().validate(var0);
      return new ValidationResult(var1.valid(), var1.checks(), var1.errors());
   }

   private static BotManager manager() {
      BotManager var0 = manager;
      if (var0 == null) {
         throw new IllegalStateException("MineBOT Java API is not initialized");
      } else {
         return var0;
      }
   }
}
