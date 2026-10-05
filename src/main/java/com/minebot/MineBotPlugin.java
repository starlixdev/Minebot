package com.minebot;

import com.minebot.api.MineBotJavaApi;
import com.minebot.bot.BotManager;
import com.minebot.command.MineBotCommand;
import com.minebot.console.ConsoleLogHub;
import com.minebot.http.HttpService;
import com.minebot.minecraft.MinecraftBridge;
import com.minebot.runtime.RuntimeScheduler;
import com.minebot.security.SecretStore;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.Duration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MineBotPlugin extends JavaPlugin {
   private BotManager botManager;
   private HttpService httpService;
   private SecretStore secretStore;
   private MinecraftBridge minecraftBridge;
   private RuntimeScheduler runtimeScheduler;
   private ConsoleLogHub consoleLogHub;

   public void onEnable() {
      try {
         this.saveDefaultConfig();
         Files.createDirectories(this.getDataFolder().toPath());
         this.secretStore = new SecretStore(this.getDataFolder().toPath().resolve("secrets.yml"));
         this.secretStore.reload();
         this.httpService = new HttpService(
            Duration.ofSeconds(this.positive("http-connect-timeout-seconds", 10)),
            Duration.ofSeconds(this.positive("http-timeout-seconds", 20)),
            this.bounded("http-worker-threads", 4, 1, 64),
            this.bounded("http-max-response-bytes", 2097152, 1024, 67108864)
         );
         this.minecraftBridge = new MinecraftBridge(this);
         this.consoleLogHub = new ConsoleLogHub(this.getLogger());
         this.runtimeScheduler = new RuntimeScheduler();
         this.botManager = new BotManager(this, this.minecraftBridge, this.consoleLogHub, this.httpService, this.secretStore, this.runtimeScheduler);
         MineBotCommand var1 = new MineBotCommand(this.botManager);
         PluginCommand var2 = this.getCommand("minebot");
         if (var2 == null) {
            throw new IllegalStateException("minebot command is missing from plugin.yml");
         }

         var2.setExecutor(var1);
         var2.setTabCompleter(var1);
         this.botManager.loadAll();
         this.bootstrapApi("bootstrap", this.botManager);
         this.getLogger().info("MineBOT Java runtime enabled. Bots directory: " + this.getDataFolder().toPath().resolve("Bots"));
      } catch (Exception var3) {
         this.getLogger().severe("MineBOT failed to enable: " + this.redact(var3.getMessage()));
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   public void onDisable() {
      this.bootstrapApi("clear", null);
      if (this.botManager != null) {
         this.botManager.close();
      }

      if (this.runtimeScheduler != null) {
         this.runtimeScheduler.close();
      }

      if (this.minecraftBridge != null) {
         this.minecraftBridge.close();
      }

      if (this.consoleLogHub != null) {
         this.consoleLogHub.close();
      }

      if (this.httpService != null) {
         this.httpService.close();
      }
   }

   private void bootstrapApi(String var1, Object var2) {
      try {
         for (Method var6 : MineBotJavaApi.class.getDeclaredMethods()) {
            if (var6.getName().equals(var1)) {
               var6.setAccessible(true);
               if (var6.getParameterCount() == 0) {
                  var6.invoke(null);
               } else {
                  var6.invoke(null, var2);
               }

               return;
            }
         }

         throw new IllegalStateException("Java API bootstrap method is unavailable");
      } catch (Exception var7) {
         throw new IllegalStateException("Failed to update Java API bootstrap state", var7);
      }
   }

   public String redact(String var1) {
      return this.secretStore == null ? var1 : this.secretStore.redact(var1);
   }

   private int positive(String var1, int var2) {
      int var3 = this.getConfig().getInt(var1, var2);
      return var3 > 0 ? var3 : var2;
   }

   private int bounded(String var1, int var2, int var3, int var4) {
      int var5 = this.getConfig().getInt(var1, var2);
      return Math.max(var3, Math.min(var4, var5));
   }
}
