package com.minebot.bot;

import com.minebot.MineBotPlugin;
import com.minebot.api.BotProvider;
import com.minebot.api.JavaBot;
import com.minebot.api.RuntimeHandle;
import com.minebot.console.ConsoleLogHub;
import com.minebot.http.HttpService;
import com.minebot.minecraft.MinecraftBridge;
import com.minebot.runtime.JavaBotRuntime;
import com.minebot.runtime.RuntimeScheduler;
import com.minebot.security.SecretStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class BotManager implements AutoCloseable {
   private final MineBotPlugin plugin;
   private final MinecraftBridge minecraft;
   private final ConsoleLogHub console;
   private final HttpService http;
   private final SecretStore secrets;
   private final RuntimeScheduler scheduler;
   private final Path botsFolder;
   private final Map<String, BotProvider> providers = new ConcurrentHashMap<>();
   private final Map<String, JavaBotRuntime> runtimes = new ConcurrentHashMap<>();
   private final Map<String, LocalBotLoader.LoadedBot> localBots = new ConcurrentHashMap<>();
   private final Map<String, BotConfig> configs = new ConcurrentHashMap<>();

   public BotManager(MineBotPlugin var1, MinecraftBridge var2, ConsoleLogHub var3, HttpService var4, SecretStore var5, RuntimeScheduler var6) throws IOException {
      this.plugin = var1;
      this.minecraft = var2;
      this.console = var3;
      this.http = var4;
      this.secrets = var5;
      this.scheduler = Objects.requireNonNull(var6);
      this.botsFolder = var1.getDataFolder().toPath().resolve("Bots");
      Files.createDirectories(this.botsFolder);
   }

   public synchronized void loadAll() {
      this.configs.clear();

      try (Stream<Path> var1 = Files.list(this.botsFolder)) {
         var1.filter(var0 -> Files.isDirectory(var0)).forEach(var1x -> {
            try {
               BotConfig var2 = BotConfig.load(var1x);
               BotName.validate(var2.name());
               if (!var1x.getFileName().toString().equals(var2.name())) {
                  throw new IOException("Bot folder name must match bot.yml name: " + var1x.getFileName());
               }

               this.configs.put(key(var2.name()), var2);
               if (var2.enabled()) {
                  this.startIfAvailable(var2.name());
               }
            } catch (Exception var3) {
               this.plugin.getLogger().severe("Failed to load bot " + var1x.getFileName() + ": " + this.plugin.redact(var3.getMessage()));
            }
         });
      } catch (IOException var6) {
         this.plugin.getLogger().severe("Failed to scan Bots directory: " + this.plugin.redact(var6.getMessage()));
      }
   }

   public synchronized Path create(String var1) throws IOException {
      BotName.validate(var1);
      Path var2 = BotName.resolve(this.botsFolder, var1);
      if (Files.exists(var2)) {
         throw new FileAlreadyExistsException(var2.toString());
      } else {
         Files.createDirectories(var2);
         Files.writeString(var2.resolve("bot.yml"), defaultBotYml(var1), StandardCharsets.UTF_8);
         Files.writeString(var2.resolve("data.json"), "{}\n", StandardCharsets.UTF_8);
         this.configs.put(key(var1), BotConfig.load(var2));
         return var2;
      }
   }

   public synchronized void registerProvider(String var1, BotProvider var2) throws Exception {
      BotName.validate(var1);
      Objects.requireNonNull(var2);
      String var3 = key(var1);
      if (this.providers.putIfAbsent(var3, var2) != null) {
         throw new IllegalStateException("A provider is already registered for bot " + var1);
      } else {
         try {
            BotConfig var4 = this.config(var1);
            if (var4.enabled()) {
               this.start(var1);
            }
         } catch (Throwable var5) {
            this.providers.remove(var3, var2);
            throw var5;
         }
      }
   }

   public synchronized void unregisterProvider(String var1) {
      this.stop(var1);
      this.providers.remove(key(var1));
   }

   public synchronized void start(String var1) throws Exception {
      BotName.validate(var1);
      String var2 = key(var1);
      if (!this.runtimes.containsKey(var2)) {
         BotConfig var3 = this.config(var1);
         Path var4 = BotName.resolve(this.botsFolder, var1);
         BotProvider var5 = this.providers.get(var2);
         LocalBotLoader.LoadedBot var6 = null;
         JavaBot var7;
         if (var5 != null) {
            var7 = var5.create();
         } else {
            LocalBotLoader.LocalBotDefinition var8 = LocalBotLoader.inspect(var4);
            if (var8 == null) {
               throw new IllegalStateException("No Java provider is registered and no local bot entrypoint is configured for bot " + var1);
            }

            var6 = LocalBotLoader.load(var8, this.getClass().getClassLoader());
            var7 = var6.bot();
         }

         JavaBotRuntime var13 = null;

         try {
            var13 = new JavaBotRuntime(this.plugin, this.minecraft, this.console, this.http, this.secrets, this.scheduler, var4, var7);
            if (!var13.name().equals(var1)) {
               throw new IllegalStateException("Provider identifier and bot.yml name must match");
            } else {
               var13.attach();
               var13.start();
               this.runtimes.put(var2, var13);
               if (var6 != null) {
                  this.localBots.put(var2, var6);
               }
            }
         } catch (Throwable var12) {
            if (var13 != null) {
               var13.close();
            }

            if (var6 != null) {
               try {
                  var6.close();
               } catch (IOException var11) {
               }
            }

            throw var12;
         }
      }
   }

   private void startIfAvailable(String var1) {
      try {
         if (this.providers.containsKey(key(var1)) || LocalBotLoader.inspect(BotName.resolve(this.botsFolder, var1)) != null) {
            this.start(var1);
         }
      } catch (Exception var3) {
         this.plugin.getLogger().severe("Failed to start bot " + var1 + ": " + this.plugin.redact(var3.getMessage()));
      }
   }

   public synchronized void stop(String var1) {
      String var2 = key(var1);
      JavaBotRuntime var3 = this.runtimes.remove(var2);
      if (var3 != null) {
         var3.close();
      }

      LocalBotLoader.LoadedBot var4 = this.localBots.remove(var2);
      if (var4 != null) {
         try {
            var4.close();
         } catch (IOException var6) {
            this.plugin.getLogger().warning("Failed to close local bot classloader for " + var1 + ": " + this.plugin.redact(var6.getMessage()));
         }
      }
   }

   public synchronized void reload(String var1) throws Exception {
      this.stop(var1);
      Path var2 = BotName.resolve(this.botsFolder, var1);
      BotConfig var3 = BotConfig.load(var2);
      this.configs.put(key(var1), var3);
      if (var3.enabled() && (this.providers.containsKey(key(var1)) || LocalBotLoader.inspect(var2) != null)) {
         this.start(var1);
      }
   }

   public synchronized void reloadAll() {
      for (String var2 : new ArrayList<>(this.names())) {
         this.stop(var2);
      }

      this.loadAll();
   }

   public synchronized BotManager.Validation validate(String var1) {
      ArrayList var2 = new ArrayList();
      ArrayList var3 = new ArrayList();
      LocalBotLoader.LoadedBot var4 = null;

      try {
         BotName.validate(var1);
         var3.add("identifier/path");
         Path var5 = BotName.resolve(this.botsFolder, var1);
         BotConfig var6 = BotConfig.load(var5);
         var3.add("config");
         if (!var1.equals(var6.name())) {
            var2.add("bot.yml name must match the folder identifier");
         }

         BotProvider var8 = this.providers.get(key(var1));
         JavaBot var7;
         if (var8 != null) {
            var3.add("registered provider");
            var7 = var8.create();
         } else {
            LocalBotLoader.LocalBotDefinition var9 = LocalBotLoader.inspect(var5);
            if (var9 == null) {
               throw new IllegalStateException("No Java provider is registered and no local bot entrypoint is configured");
            }

            var3.add("local entrypoint");
            var4 = LocalBotLoader.load(var9, this.getClass().getClassLoader());
            var7 = var4.bot();
            var3.add("local classloading");
         }

         new JavaBotRuntime(this.plugin, this.minecraft, this.console, this.http, this.secrets, this.scheduler, var5, var7).close();
         var3.add("events/slash commands");
      } catch (Throwable var18) {
         var2.add(this.plugin.redact(var18.getMessage()));
      } finally {
         if (var4 != null) {
            try {
               var4.close();
            } catch (IOException var17) {
            }
         }
      }

      return new BotManager.Validation(var2.isEmpty(), List.copyOf(var3), List.copyOf(var2));
   }

   public List<String> names() {
      TreeSet var1 = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

      for (BotConfig var3 : this.configs.values()) {
         var1.add(var3.name());
      }

      return List.copyOf(var1);
   }

   public String status(String var1) {
      BotConfig var2 = this.config(var1);
      JavaBotRuntime var3 = this.runtimes.get(key(var1));
      boolean var4 = this.providers.containsKey(key(var1));
      boolean var5 = false;

      try {
         var5 = LocalBotLoader.inspect(BotName.resolve(this.botsFolder, var1)) != null;
      } catch (IOException var7) {
      }

      return var2.name()
         + ": enabled="
         + var2.enabled()
         + ", provider="
         + var4
         + ", local="
         + var5
         + ", lifecycle="
         + (var3 == null ? "STOPPED" : var3.lifecycle())
         + (var3 == null ? "" : ", pending=" + var3.pendingTasks() + ", dropped=" + var3.droppedTasks() + ", stale=" + var3.staleTasks());
   }

   public Optional<RuntimeHandle> runtime(String var1) {
      return Optional.ofNullable(this.runtimes.get(key(var1))).map(var0 -> (RuntimeHandle)var0);
   }

   private BotConfig config(String var1) {
      BotConfig var2 = this.configs.get(key(var1));
      if (var2 != null) {
         return var2;
      } else {
         try {
            var2 = BotConfig.load(BotName.resolve(this.botsFolder, var1));
            this.configs.put(key(var1), var2);
            return var2;
         } catch (IOException var4) {
            throw new IllegalArgumentException("Bot configuration not found: " + var1, var4);
         }
      }
   }

   private static String key(String var0) {
      return var0.toLowerCase(Locale.ROOT);
   }

   private static String defaultBotYml(String var0) {
      return "name: \""
         + var0
         + "\"\nenabled: false\ntoken: \"PASTE_TOKEN_HERE\"\nintents:\n  - GUILDS\nauto-register-slash: true\nactivity:\n  type: playing\n  text: \"Minecraft\"\n";
   }

   @Override
   public synchronized void close() {
      for (String var2 : new ArrayList<>(this.names())) {
         this.stop(var2);
      }

      this.runtimes.clear();
      this.localBots.clear();
      this.providers.clear();
   }

   public record Validation(boolean valid, List<String> checks, List<String> errors) {
   }
}
