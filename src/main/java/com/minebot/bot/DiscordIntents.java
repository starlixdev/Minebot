package com.minebot.bot;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public final class DiscordIntents {
   private static final Map<String, Long> VALUES = Map.ofEntries(
      Map.entry("GUILDS", 1L),
      Map.entry("GUILD_MEMBERS", 2L),
      Map.entry("GUILD_MODERATION", 4L),
      Map.entry("GUILD_EXPRESSIONS", 8L),
      Map.entry("GUILD_INTEGRATIONS", 16L),
      Map.entry("GUILD_WEBHOOKS", 32L),
      Map.entry("GUILD_INVITES", 64L),
      Map.entry("GUILD_VOICE_STATES", 128L),
      Map.entry("GUILD_PRESENCES", 256L),
      Map.entry("GUILD_MESSAGES", 512L),
      Map.entry("GUILD_MESSAGE_REACTIONS", 1024L),
      Map.entry("GUILD_MESSAGE_TYPING", 2048L),
      Map.entry("DIRECT_MESSAGES", 4096L),
      Map.entry("DIRECT_MESSAGE_REACTIONS", 8192L),
      Map.entry("DIRECT_MESSAGE_TYPING", 16384L),
      Map.entry("MESSAGE_CONTENT", 32768L),
      Map.entry("GUILD_SCHEDULED_EVENTS", 65536L),
      Map.entry("AUTO_MODERATION_CONFIGURATION", 1048576L),
      Map.entry("AUTO_MODERATION_EXECUTION", 2097152L),
      Map.entry("GUILD_MESSAGE_POLLS", 16777216L),
      Map.entry("DIRECT_MESSAGE_POLLS", 33554432L)
   );

   private DiscordIntents() {
   }

   public static long fromNames(Collection<String> var0) {
      long var1 = 0L;

      for (String var4 : var0) {
         String var5 = var4.trim().toUpperCase(Locale.ROOT);
         Long var6 = VALUES.get(var5);
         if (var6 == null) {
            throw new IllegalArgumentException("Unknown Discord intent: " + var4 + ". Use intents-value for future/unknown intents.");
         }

         var1 |= var6;
      }

      return var1;
   }
}
