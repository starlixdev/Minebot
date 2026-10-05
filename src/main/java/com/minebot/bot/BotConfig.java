package com.minebot.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class BotConfig {
   private final String name;
   private final boolean enabled;
   private final String token;
   private final long intents;
   private final String activityType;
   private final String activityText;
   private final boolean autoRegisterSlash;

   public BotConfig(String var1, boolean var2, String var3, long var4, String var6, String var7, boolean var8) {
      this.name = var1;
      this.enabled = var2;
      this.token = var3;
      this.intents = var4;
      this.activityType = var6;
      this.activityText = var7;
      this.autoRegisterSlash = var8;
   }

   public String name() {
      return this.name;
   }

   public boolean enabled() {
      return this.enabled;
   }

   public String token() {
      return this.token;
   }

   public long intents() {
      return this.intents;
   }

   public String activityType() {
      return this.activityType;
   }

   public String activityText() {
      return this.activityText;
   }

   public boolean autoRegisterSlash() {
      return this.autoRegisterSlash;
   }

   public static BotConfig load(Path var0) throws IOException {
      Path var1 = var0.resolve("bot.yml");
      if (!Files.exists(var1)) {
         throw new IOException("Missing bot.yml");
      } else {
         List<String> var2 = Files.readAllLines(var1, StandardCharsets.UTF_8);
         LinkedHashMap<String, String> var3 = new LinkedHashMap<>();
         ArrayList<String> var4 = new ArrayList<>();
         String var5 = "";
         int var6 = 0;

         for (String var8 : var2) {
            var6++;
            String var9 = stripComment(var8);
            if (!var9.isBlank()) {
               int var10 = countIndent(var9);
               String var11 = var9.trim();
               if (var10 == 0) {
                  var5 = "";
                  int var21 = var11.indexOf(58);
                  if (var21 >= 0) {
                     String var13 = var11.substring(0, var21).trim();
                     String var14 = var11.substring(var21 + 1).trim();
                     if (var14.isEmpty()) {
                        var5 = var13;
                     } else {
                        var3.put(var13, unquote(var14));
                     }
                  }
               } else if (var11.startsWith("-") && var5.equals("intents")) {
                  var4.add(unquote(var11.substring(1).trim()));
               } else {
                  int var12 = var11.indexOf(58);
                  if (var12 > 0 && !var5.isEmpty()) {
                     var3.put(var5 + "." + var11.substring(0, var12).trim(), unquote(var11.substring(var12 + 1).trim()));
                  }
               }
            }
         }

         String var17 = var3.getOrDefault("name", var0.getFileName().toString()).trim();
         if (var17.isBlank()) {
            throw new IOException("bot.yml: name must not be blank");
         } else if (var17.codePoints().anyMatch(Character::isISOControl)) {
            throw new IOException("bot.yml: name contains control characters");
         } else {
            boolean var18 = parseBoolean((String)var3.get("enabled"), false, "enabled");
            String var19 = resolveToken(var3.getOrDefault("token", ""));
            String var22 = (String)var3.get("intents-value");

            long var20;
            try {
               var20 = var22 != null && !var22.isBlank() ? Long.parseLong(var22.trim()) : DiscordIntents.fromNames(var4);
            } catch (RuntimeException var16) {
               throw new IOException("bot.yml: invalid intents configuration: " + var16.getMessage(), var16);
            }

            String var23 = var3.getOrDefault("activity.type", "playing").trim().toLowerCase(Locale.ROOT);
            if (!Set.of("playing", "streaming", "listening", "watching", "custom", "competing").contains(var23)) {
               throw new IOException("bot.yml: activity.type must be playing, streaming, listening, watching, custom or competing");
            } else {
               String var24 = var3.getOrDefault("activity.text", "");
               boolean var15 = parseBoolean((String)var3.get("auto-register-slash"), true, "auto-register-slash");
               return new BotConfig(var17, var18, var19, var20, var23, var24, var15);
            }
         }
      }
   }

   private static boolean parseBoolean(String var0, boolean var1, String var2) throws IOException {
      if (var0 == null || var0.isBlank()) {
         return var1;
      } else if (var0.equalsIgnoreCase("true")) {
         return true;
      } else if (var0.equalsIgnoreCase("false")) {
         return false;
      } else {
         throw new IOException("bot.yml: " + var2 + " must be true or false (got '" + var0 + "')");
      }
   }

   private static String resolveToken(String var0) {
      String var1 = unquote(var0).trim();
      if (var1.startsWith("${ENV:") && var1.endsWith("}")) {
         String var2 = var1.substring(6, var1.length() - 1);
         return Objects.requireNonNullElse(System.getenv(var2), "");
      } else {
         return var1;
      }
   }

   private static int countIndent(String var0) {
      int var1 = 0;

      while (var1 < var0.length() && var0.charAt(var1) == ' ') {
         var1++;
      }

      return var1;
   }

   private static String stripComment(String var0) {
      boolean var1 = false;
      char var2 = 0;

      for (int var3 = 0; var3 < var0.length(); var3++) {
         char var4 = var0.charAt(var3);
         if ((var4 == '\'' || var4 == '"') && (var3 == 0 || var0.charAt(var3 - 1) != '\\')) {
            if (!var1) {
               var1 = true;
               var2 = var4;
            } else if (var2 == var4) {
               var1 = false;
            }
         }

         if (var4 == '#' && !var1) {
            return var0.substring(0, var3);
         }
      }

      return var0;
   }

   private static String unquote(String var0) {
      if (var0 == null) {
         return "";
      } else {
         String var1 = var0.trim();
         return var1.length() < 2 || (!var1.startsWith("\"") || !var1.endsWith("\"")) && (!var1.startsWith("'") || !var1.endsWith("'"))
            ? var1
            : var1.substring(1, var1.length() - 1);
      }
   }
}
