package com.minebot.api;

import com.minebot.discord.DiscordClient;
import com.minebot.runtime.JavaBotRuntime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class DiscordApi {
   private final JavaBotRuntime runtime;

   DiscordApi(JavaBotRuntime var1) {
      this.runtime = var1;
   }

   private DiscordClient client() {
      DiscordClient var1 = this.runtime.discordClient();
      if (var1 == null) {
         throw new IllegalStateException("Discord client is not running");
      } else {
         return var1;
      }
   }

   private CompletableFuture<DiscordResponse> adapt(Consumer<Consumer<DiscordClient.RestResponse>> var1) {
      CompletableFuture var2 = new CompletableFuture();
      var1.accept(var2x -> var2.complete(this.runtime.discordResponse(var2x)));
      return var2;
   }

   public boolean connected() {
      return this.client().isRunning();
   }

   public String applicationId() {
      return this.client().applicationId();
   }

   public String botUserId() {
      return this.client().botUserId();
   }

   public String botUsername() {
      return this.client().botUsername();
   }

   public CompletableFuture<DiscordResponse> send(String var1, String var2) {
      String var3 = this.runtime.redact(var2);
      return this.adapt(var3x -> this.client().sendMessage(var1, var3, var3x));
   }

   public CompletableFuture<DiscordResponse> request(String var1, String var2, String var3) {
      return this.adapt(var4 -> this.client().request(var1, var2, this.runtime.redact(var3), var4));
   }

   public CompletableFuture<DiscordResponse> interactionReply(String var1, String var2, String var3, boolean var4) {
      return this.adapt(var5 -> this.client().interactionReply(var1, var2, this.runtime.redact(var3), var4, var5));
   }

   public CompletableFuture<DiscordResponse> interactionDefer(String var1, String var2, boolean var3) {
      return this.adapt(var4 -> this.client().interactionDefer(var1, var2, var3, var4));
   }

   public CompletableFuture<DiscordResponse> editOriginal(String var1, String var2) {
      return this.adapt(var3 -> this.client().editOriginalInteraction(var1, this.runtime.redact(var2), var3));
   }

   public CompletableFuture<DiscordResponse> followup(String var1, String var2, boolean var3) {
      return this.adapt(var4 -> this.client().followup(var1, this.runtime.redact(var2), var3, var4));
   }

   public CompletableFuture<DiscordResponse> registerGlobalCommands(Collection<SlashCommand> var1) {
      List var2 = var1.stream().map(SlashCommand::toDiscord).toList();
      return this.adapt(var2x -> this.client().registerGlobalCommands(var2, var2x));
   }

   public void presence(String var1, String var2, String var3) {
      this.client().updatePresence(var1, var2, this.runtime.redact(var3));
   }
}
