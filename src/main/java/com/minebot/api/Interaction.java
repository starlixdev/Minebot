package com.minebot.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

public final class Interaction {
   private final BotContext context;
   private final EventData event;

   public Interaction(BotContext var1, EventData var2) {
      this.context = var1;
      this.event = var2;
   }

   public EventData event() {
      return this.event;
   }

   public Map<String, Object> options() {
      return this.event.get("options") instanceof Map<?, ?> var2 ? copy(var2) : Map.of();
   }

   public CompletableFuture<DiscordResponse> reply(String var1) {
      return this.reply(var1, false);
   }

   public CompletableFuture<DiscordResponse> reply(String var1, boolean var2) {
      return this.context.discord().interactionReply(this.event.string("id"), this.event.string("token"), var1, var2);
   }

   public CompletableFuture<DiscordResponse> defer(boolean var1) {
      return this.context.discord().interactionDefer(this.event.string("id"), this.event.string("token"), var1);
   }

   public CompletableFuture<DiscordResponse> edit(String var1) {
      return this.context.discord().editOriginal(this.event.string("token"), var1);
   }

   public CompletableFuture<DiscordResponse> followup(String var1, boolean var2) {
      return this.context.discord().followup(this.event.string("token"), var1, var2);
   }

   private static Map<String, Object> copy(Map<?, ?> var0) {
      LinkedHashMap var1 = new LinkedHashMap();

      for (Entry var3 : var0.entrySet()) {
         var1.put(String.valueOf(var3.getKey()), var3.getValue());
      }

      return Collections.unmodifiableMap(var1);
   }
}
