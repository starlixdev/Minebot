package com.minebot.api;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class JavaBot {
   private BotContext context;

   final void attach(BotContext var1) {
      if (this.context != null) {
         throw new IllegalStateException("Bot is already attached");
      } else {
         this.context = Objects.requireNonNull(var1);
      }
   }

   protected final BotContext bot() {
      if (this.context == null) {
         throw new IllegalStateException("Bot is not attached");
      } else {
         return this.context;
      }
   }

   public void onLoad() throws Exception {
   }

   public void onReady() throws Exception {
   }

   public void onDiscordEvent(String var1, EventData var2) throws Exception {
   }

   public void onSlashCommand(String var1, Interaction var2) throws Exception {
   }

   public void onAutocomplete(String var1, EventData var2) throws Exception {
   }

   public void onMinecraftEvent(String var1, EventData var2) throws Exception {
   }

   public void onStop() throws Exception {
   }

   public Collection<String> minecraftEvents() {
      return List.of();
   }

   public Collection<SlashCommand> slashCommands() {
      return List.of();
   }
}
