package com.minebot.bot;

import com.minebot.api.JavaBot;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalBotLoader {
   public static LocalBotLoader.LocalBotDefinition inspect(Path var0) throws IOException {
      String var1 = entrypoint(var0.resolve("bot.yml"));
      Path var2 = var0.resolve("bot.jar");
      if (var1.isBlank()) {
         if (Files.exists(var2)) {
            throw new IOException("bot.yml must define entrypoint when bot.jar is present");
         } else {
            return null;
         }
      } else if (!Files.isRegularFile(var2)) {
         throw new IOException("Local bot entrypoint is configured but bot.jar is missing");
      } else {
         return new LocalBotLoader.LocalBotDefinition(var1, var2);
      }
   }

   public static LocalBotLoader.LoadedBot load(LocalBotLoader.LocalBotDefinition var0, ClassLoader var1) throws Exception {
      URLClassLoader var2 = new URLClassLoader(new URL[]{var0.jar().toUri().toURL()}, var1);

      try {
         Class var3 = Class.forName(var0.entrypoint(), true, var2);
         if (!JavaBot.class.isAssignableFrom(var3)) {
            throw new IllegalArgumentException("Local bot entrypoint must extend com.minebot.api.JavaBot");
         } else if (LocalBotLoader.ModifierSupport.invalid(var3)) {
            throw new IllegalArgumentException("Local bot entrypoint must be a public, concrete class");
         } else {
            JavaBot var8 = (JavaBot)var3.getDeclaredConstructor().newInstance();
            return new LocalBotLoader.LoadedBot(var8, var2);
         }
      } catch (Throwable var6) {
         try {
            var2.close();
         } catch (IOException var5) {
         }

         if (var6 instanceof Exception var7) {
            throw var7;
         } else if (var6 instanceof Error var4) {
            throw var4;
         } else {
            throw new RuntimeException(var6);
         }
      }
   }

   private static String entrypoint(Path var0) throws IOException {
      if (!Files.isRegularFile(var0)) {
         throw new IOException("Missing bot.yml");
      } else {
         for (String var2 : Files.readAllLines(var0, StandardCharsets.UTF_8)) {
            String var3 = strip(var2).trim();
            if (var3.startsWith("entrypoint:")) {
               return unquote(var3.substring("entrypoint:".length()).trim());
            }
         }

         return "";
      }
   }

   private static String strip(String var0) {
      boolean var1 = false;
      boolean var2 = false;

      for (int var3 = 0; var3 < var0.length(); var3++) {
         char var4 = var0.charAt(var3);
         if (var4 == '\'' && !var2) {
            var1 = !var1;
         } else if (var4 == '"' && !var1) {
            var2 = !var2;
         } else if (var4 == '#' && !var1 && !var2) {
            return var0.substring(0, var3);
         }
      }

      return var0;
   }

   private static String unquote(String var0) {
      return var0.length() < 2 || (!var0.startsWith("\"") || !var0.endsWith("\"")) && (!var0.startsWith("'") || !var0.endsWith("'"))
         ? var0
         : var0.substring(1, var0.length() - 1);
   }

   public record LoadedBot(JavaBot bot, URLClassLoader loader) implements AutoCloseable {
      @Override
      public void close() throws IOException {
         this.loader.close();
      }
   }

   public record LocalBotDefinition(String entrypoint, Path jar) {
   }

   private static final class ModifierSupport {
      static boolean invalid(Class<?> var0) {
         int var1 = var0.getModifiers();
         return !Modifier.isPublic(var1) || Modifier.isAbstract(var1) || var0.isInterface();
      }
   }
}
