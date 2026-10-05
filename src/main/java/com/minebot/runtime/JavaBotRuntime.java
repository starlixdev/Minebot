package com.minebot.runtime;

import com.minebot.MineBotPlugin;
import com.minebot.api.BotConfiguration;
import com.minebot.api.BotContext;
import com.minebot.api.BotLifecycle;
import com.minebot.api.ConsoleLogEvent;
import com.minebot.api.DiscordResponse;
import com.minebot.api.EventData;
import com.minebot.api.HttpResponse;
import com.minebot.api.Interaction;
import com.minebot.api.JavaBot;
import com.minebot.api.RuntimeHandle;
import com.minebot.api.SlashCommand;
import com.minebot.bot.BotConfig;
import com.minebot.bot.BotStorage;
import com.minebot.console.ConsoleLogHub;
import com.minebot.discord.DiscordClient;
import com.minebot.http.HttpService;
import com.minebot.minecraft.MinecraftBridge;
import com.minebot.security.SecretStore;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class JavaBotRuntime implements RuntimeHandle, AutoCloseable {
   private final MineBotPlugin plugin;
   private final MinecraftBridge minecraft;
   private final ConsoleLogHub console;
   private final HttpService http;
   private final SecretStore secrets;
   private final Path folder;
   private final BotConfig rawConfig;
   private final BotConfiguration config;
   private final BotStorage storage;
   private final JavaBot bot;
   private final List<String> minecraftEvents;
   private final List<SlashCommand> slashCommands;
   private final ThreadPoolExecutor dispatcher;
   private final RuntimeScheduler scheduler;
   private final Set<CompletableFuture<?>> pendingFutures = ConcurrentHashMap.newKeySet();
   private final Set<ScheduledFuture<?>> scheduledTasks = ConcurrentHashMap.newKeySet();
   private final Set<AutoCloseable> subscriptions = ConcurrentHashMap.newKeySet();
   private final ConcurrentMap<Long, AtomicBoolean> timerPending = new ConcurrentHashMap<>();
   private final AtomicLong timerIds = new AtomicLong();
   private final AtomicLong dropped = new AtomicLong();
   private final AtomicLong stale = new AtomicLong();
   private final AtomicLong completed = new AtomicLong();
   private final int queueCapacity;
   private final long maxAgeNanos;
   private final boolean coalesce;
   private volatile BotLifecycle lifecycle = BotLifecycle.CREATED;
   private volatile DiscordClient discord;
   private volatile BotContext context;
   private volatile boolean loadSucceeded;
   private final AtomicBoolean stopInvoked = new AtomicBoolean();

   public JavaBotRuntime(
      MineBotPlugin var1, MinecraftBridge var2, ConsoleLogHub var3, HttpService var4, SecretStore var5, RuntimeScheduler var6, Path var7, JavaBot var8
   ) throws Exception {
      this.plugin = var1;
      this.minecraft = var2;
      this.console = var3;
      this.http = var4;
      this.secrets = var5;
      this.folder = var7;
      this.rawConfig = BotConfig.load(var7);
      this.config = new BotConfiguration(
         this.rawConfig.name(),
         this.rawConfig.enabled(),
         this.rawConfig.intents(),
         this.rawConfig.activityType(),
         this.rawConfig.activityText(),
         this.rawConfig.autoRegisterSlash()
      );
      this.storage = new BotStorage(var7.resolve("data.json"));
      this.bot = Objects.requireNonNull(var8);
      this.scheduler = Objects.requireNonNull(var6);
      this.minecraftEvents = List.copyOf(Objects.requireNonNullElseGet(var8.minecraftEvents(), List::of));
      this.slashCommands = List.copyOf(Objects.requireNonNullElseGet(var8.slashCommands(), List::of));
      this.queueCapacity = var1.getConfig().getInt("runtime-max-pending-events", 1024);
      this.maxAgeNanos = TimeUnit.SECONDS.toNanos(var1.getConfig().getInt("runtime-max-event-age-seconds", 30));
      this.coalesce = var1.getConfig().getBoolean("runtime-coalesce-timers", true);
      this.dispatcher = new ThreadPoolExecutor(
         1,
         1,
         0L,
         TimeUnit.MILLISECONDS,
         new ArrayBlockingQueue<>(Math.max(16, this.queueCapacity)),
         named("MineBOT-" + this.name() + "-dispatcher"),
         new AbortPolicy()
      );
      this.validateDeclarations();
   }

   public void attach() {
      if (this.context != null) {
         throw new IllegalStateException("Runtime is already attached");
      } else {
         this.context = this.createContext();
         this.attachBot(this.context);
      }
   }

   private BotContext createContext() {
      try {
         Constructor var1 = BotContext.class.getDeclaredConstructor(JavaBotRuntime.class);
         var1.setAccessible(true);
         return (BotContext)var1.newInstance(this);
      } catch (Exception var2) {
         throw new IllegalStateException("Failed to create bot context", var2);
      }
   }

   private void attachBot(BotContext var1) {
      try {
         Method var2 = JavaBot.class.getDeclaredMethod("attach", BotContext.class);
         var2.setAccessible(true);
         var2.invoke(this.bot, var1);
      } catch (InvocationTargetException var5) {
         Throwable var3 = var5.getCause();
         if (var3 instanceof RuntimeException var7) {
            throw var7;
         } else if (var3 instanceof Error var4) {
            throw var4;
         } else {
            throw new IllegalStateException("Failed to attach bot", var3);
         }
      } catch (Exception var6) {
         throw new IllegalStateException("Failed to attach bot", var6);
      }
   }

   public synchronized void start() throws Exception {
      if (this.lifecycle != BotLifecycle.RUNNING && this.lifecycle != BotLifecycle.STARTING) {
         if (this.context == null) {
            throw new IllegalStateException("Runtime must be attached before start");
         } else {
            this.secrets.reload();
            this.ensureToken();
            this.lifecycle = BotLifecycle.STARTING;

            try {
               for (String var2 : this.minecraftEvents) {
                  this.subscriptions.add(this.minecraft.subscribe(var2, this::acceptMinecraftEvent));
               }

               this.runControl("onLoad", this.bot::onLoad);
               this.loadSucceeded = true;
               this.discord = new DiscordClient(this.rawConfig, this.plugin.getLogger(), this::acceptDiscordEvent);
               this.discord.start();
               this.lifecycle = BotLifecycle.RUNNING;
            } catch (Throwable var3) {
               this.lifecycle = BotLifecycle.FAILED;
               this.cleanupFailedStart();
               throw propagate(var3);
            }
         }
      }
   }

   private void validateDeclarations() {
      for (String var2 : this.minecraftEvents) {
         this.minecraft.validate(var2);
      }

      for (SlashCommand var4 : this.slashCommands) {
         Objects.requireNonNull(var4);
      }
   }

   private void ensureToken() {
      String var1 = this.rawConfig.token();
      if (var1 == null || var1.isBlank() || "PASTE_TOKEN_HERE".equals(var1)) {
         throw new IllegalStateException("Discord token is not configured for bot " + this.name());
      }
   }

   private void acceptDiscordEvent(String var1, Map<String, Object> var2) {
      if (this.lifecycle == BotLifecycle.RUNNING) {
         LinkedHashMap var3 = var2 == null ? new LinkedHashMap() : new LinkedHashMap<>(var2);
         if ("INTERACTION_CREATE".equals(var1)) {
            enrichInteraction(var3);
         }

         EventData var4 = new EventData(var3);
         this.submit("discord:" + var1, () -> {
            this.bot.onDiscordEvent(var1, var4);
            if ("READY".equals(var1)) {
               this.bot.onReady();
               if (this.config.autoRegisterSlash() && !this.slashCommands.isEmpty()) {
                  this.context.discord().registerGlobalCommands(this.slashCommands).thenAccept(var1xx -> {
                     if (!var1xx.ok()) {
                        this.warn("Slash command registration failed with HTTP " + var1xx.status() + ": " + var1xx.error());
                     }
                  });
               }
            }

            if ("INTERACTION_CREATE".equals(var1)) {
               int var4x = intValue(var3.get("type"));
               if (var3.get("data") instanceof Map<?, ?> var6 && var6.get("name") != null) {
                  String var7 = String.valueOf(var6.get("name"));
                  if (var4x == 2) {
                     this.bot.onSlashCommand(var7, new Interaction(this.context, var4));
                  } else if (var4x == 4) {
                     this.bot.onAutocomplete(var7, var4);
                  }
               }
            }
         });
      }
   }

   private void acceptMinecraftEvent(String var1, EventData var2) {
      this.submit("minecraft:" + var1, () -> this.bot.onMinecraftEvent(var1, var2));
   }

   public void executeSerial(Runnable var1) {
      this.submit("continuation", () -> var1.run());
   }

   public AutoCloseable subscribeConsole(Consumer<ConsoleLogEvent> var1) {
      AtomicReference var2 = new AtomicReference();
      AutoCloseable var3 = this.console.subscribe(var2x -> this.submit("console:" + var2x.level(), () -> var1.accept(var2x)));
      AutoCloseable var4 = () -> {
         AutoCloseable var2x = (AutoCloseable)var2.getAndSet(null);
         if (var2x != null) {
            this.subscriptions.remove(var2x);
            var2x.close();
         }
      };
      var2.set(var3);
      this.subscriptions.add(var3);
      return var4;
   }

   private void submit(String var1, JavaBotRuntime.ThrowingRunnable var2) {
      BotLifecycle var3 = this.lifecycle;
      if (var3 == BotLifecycle.RUNNING || var3 == BotLifecycle.STARTING) {
         long var4 = System.nanoTime();
         Runnable var6 = () -> {
            if (this.maxAgeNanos > 0L && System.nanoTime() - var4 > this.maxAgeNanos) {
               this.stale.incrementAndGet();
            } else {
               try {
                  var2.run();
                  this.completed.incrementAndGet();
               } catch (Throwable var6x) {
                  this.logThrowable(var1, var6x);
               }
            }
         };

         try {
            this.dispatcher.execute(var6);
         } catch (RejectedExecutionException var8) {
            this.dropped.incrementAndGet();
            if (this.lifecycle == BotLifecycle.RUNNING) {
               this.plugin
                  .getLogger()
                  .log(Level.WARNING, "[MineBOT/" + this.name() + "] Dispatcher rejected " + var1 + " while runtime is RUNNING", (Throwable)var8);
            }
         }
      }
   }

   private void runControl(String var1, JavaBotRuntime.ThrowingRunnable var2) throws Exception {
      CompletableFuture var3 = new CompletableFuture();

      try {
         this.dispatcher.execute(() -> {
            try {
               var2.run();
               var3.complete(null);
            } catch (Throwable var5x) {
               this.logThrowable(var1, var5x);
               var3.completeExceptionally(var5x);
            }
         });
      } catch (RejectedExecutionException var7) {
         throw var7;
      }

      try {
         var3.get();
      } catch (ExecutionException var5) {
         throw propagate(var5.getCause());
      } catch (InterruptedException var6) {
         Thread.currentThread().interrupt();
         throw new CancellationException("Interrupted while waiting for " + var1);
      }
   }

   public CompletableFuture<Void> delay(Duration var1) {
      if (var1 != null && !var1.isNegative()) {
         CompletableFuture var2 = new CompletableFuture();
         if (this.scheduledTasks.size() >= Math.max(16, this.queueCapacity)) {
            this.dropped.incrementAndGet();
            var2.completeExceptionally(new RejectedExecutionException("Runtime scheduled task capacity exceeded"));
            return var2;
         } else {
            this.pendingFutures.add(var2);

            try {
               AtomicReference var3 = new AtomicReference();
               ScheduledFuture var4 = this.scheduler.schedule(() -> {
                  if (!var2.isDone()) {
                     var2.complete(null);
                  }
               }, var1.toMillis(), TimeUnit.MILLISECONDS);
               var3.set(var4);
               this.scheduledTasks.add(var4);
               var2.whenComplete((var3x, var4x) -> {
                  this.pendingFutures.remove(var2);
                  ScheduledFuture var5x = (ScheduledFuture)var3.get();
                  if (var5x != null) {
                     this.scheduledTasks.remove(var5x);
                     if (var2.isCancelled()) {
                        var5x.cancel(false);
                     }
                  }
               });
            } catch (RejectedExecutionException var5) {
               var2.completeExceptionally(new CancellationException("Runtime scheduler is stopped"));
            }

            return var2;
         }
      } else {
         throw new IllegalArgumentException("Duration must be non-negative");
      }
   }

   public AutoCloseable scheduleEvery(Duration var1, Runnable var2) {
      if (var1 != null && !var1.isZero() && !var1.isNegative()) {
         if (this.scheduledTasks.size() >= Math.max(16, this.queueCapacity)) {
            this.dropped.incrementAndGet();
            throw new RejectedExecutionException("Runtime scheduled task capacity exceeded");
         } else {
            long var3 = this.timerIds.incrementAndGet();
            AtomicBoolean var5 = new AtomicBoolean();
            this.timerPending.put(var3, var5);
            ScheduledFuture var6 = this.scheduler.scheduleAtFixedRate(() -> {
               if (!this.coalesce || var5.compareAndSet(false, true)) {
                  if (!this.coalesce) {
                     var5.set(true);
                  }

                  this.submit("timer:" + var3, () -> {
                     try {
                        var2.run();
                     } finally {
                        var5.set(false);
                     }
                  });
               }
            }, var1.toMillis(), var1.toMillis(), TimeUnit.MILLISECONDS);
            this.scheduledTasks.add(var6);
            AutoCloseable var7 = () -> {
               var6.cancel(false);
               this.scheduledTasks.remove(var6);
               this.timerPending.remove(var3);
            };
            this.subscriptions.add(var7);
            return var7;
         }
      } else {
         throw new IllegalArgumentException("Timer interval must be positive");
      }
   }

   @Override
   public synchronized void close() {
      if (this.lifecycle != BotLifecycle.STOPPED && this.lifecycle != BotLifecycle.STOPPING) {
         BotLifecycle var1 = this.lifecycle;
         this.lifecycle = BotLifecycle.STOPPING;
         DiscordClient var2 = this.discord;
         this.discord = null;
         if (var2 != null) {
            var2.close();
         }

         for (AutoCloseable var4 : this.subscriptions) {
            try {
               var4.close();
            } catch (Exception var7) {
               this.warn("Failed to close runtime subscription: " + var7.getMessage());
            }
         }

         this.subscriptions.clear();
         if (this.loadSucceeded) {
            try {
               this.runStopSerialized();
            } catch (Exception var6) {
               this.logThrowable("onStop", var6);
            }
         }

         for (ScheduledFuture var10 : this.scheduledTasks) {
            var10.cancel(false);
         }

         this.scheduledTasks.clear();

         for (CompletableFuture var11 : this.pendingFutures) {
            var11.completeExceptionally(new CancellationException("Bot runtime is stopping"));
         }

         this.pendingFutures.clear();
         this.dispatcher.getQueue().clear();
         this.dispatcher.shutdownNow();
         this.lifecycle = BotLifecycle.STOPPED;
      }
   }

   private void runStopSerialized() throws Exception {
      if (this.stopInvoked.compareAndSet(false, true)) {
         CompletableFuture var1 = new CompletableFuture();

         try {
            this.dispatcher.execute(() -> {
               try {
                  this.bot.onStop();
                  var1.complete(null);
               } catch (Throwable var3x) {
                  var1.completeExceptionally(var3x);
               }
            });
         } catch (RejectedExecutionException var6) {
            return;
         }

         try {
            var1.get(10L, TimeUnit.SECONDS);
         } catch (TimeoutException var3) {
            var1.cancel(true);
            throw new IllegalStateException("onStop timed out", var3);
         } catch (ExecutionException var4) {
            throw propagate(var4.getCause());
         } catch (InterruptedException var5) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted during shutdown");
         }
      }
   }

   private void cleanupFailedStart() {
      DiscordClient var1 = this.discord;
      this.discord = null;
      if (var1 != null) {
         var1.close();
      }

      for (AutoCloseable var3 : this.subscriptions) {
         try {
            var3.close();
         } catch (Exception var5) {
         }
      }

      this.subscriptions.clear();

      for (ScheduledFuture var8 : this.scheduledTasks) {
         var8.cancel(false);
      }

      this.scheduledTasks.clear();

      for (CompletableFuture var9 : this.pendingFutures) {
         var9.completeExceptionally(new CancellationException("Bot startup failed"));
      }

      this.pendingFutures.clear();
      this.dispatcher.getQueue().clear();
      this.dispatcher.shutdownNow();
   }

   @Override
   public String name() {
      return this.config.name();
   }

   public BotConfiguration configuration() {
      return this.config;
   }

   @Override
   public BotLifecycle lifecycle() {
      return this.lifecycle;
   }

   @Override
   public boolean running() {
      return this.lifecycle == BotLifecycle.RUNNING;
   }

   @Override
   public long pendingTasks() {
      return this.dispatcher.getQueue().size();
   }

   @Override
   public long droppedTasks() {
      return this.dropped.get();
   }

   @Override
   public long staleTasks() {
      return this.stale.get();
   }

   @Override
   public long completedTasks() {
      return this.completed.get();
   }

   public BotStorage storage() {
      return this.storage;
   }

   public DiscordClient discordClient() {
      return this.discord;
   }

   public HttpService httpService() {
      return this.http;
   }

   public String secret(String var1) {
      return this.secrets.getRequired(var1);
   }

   public String redact(String var1) {
      return var1 == null ? null : this.secrets.redact(var1, this.rawConfig.token());
   }

   public boolean containsSecret(Object var1) {
      return this.secrets.containsSecret(var1, this.rawConfig.token());
   }

   public void log(String var1) {
      this.plugin.getLogger().info("[MineBOT/" + this.name() + "] " + this.redact(var1));
   }

   public void warn(String var1) {
      this.plugin.getLogger().warning("[MineBOT/" + this.name() + "] " + this.redact(var1));
   }

   public Map<String, Object> serverSnapshot() {
      return this.minecraft.serverSnapshot();
   }

   public void minecraftCommand(String var1) {
      this.minecraft.runConsoleCommand(this.redact(var1));
   }

   public void minecraftBroadcast(String var1) {
      this.minecraft.broadcast(this.redact(var1));
   }

   public DiscordResponse discordResponse(DiscordClient.RestResponse var1) {
      return var1 == null
         ? new DiscordResponse(false, 0, "", "No response", Map.of())
         : new DiscordResponse(var1.ok(), var1.status(), this.redact(var1.body()), this.redact(var1.error()), Map.of());
   }

   public HttpResponse httpResponse(HttpService.Result var1) {
      return var1 == null
         ? new HttpResponse(false, 0, "", "No response", Map.of(), 0L)
         : new HttpResponse(var1.ok(), var1.status(), this.redact(var1.body()), this.redact(var1.error()), var1.headers(), var1.durationMillis());
   }

   private void logThrowable(String var1, Throwable var2) {
      if (!this.containsSecret(var2.getMessage())) {
         this.plugin.getLogger().log(Level.SEVERE, "[MineBOT/" + this.name() + "] " + var1 + " failed", var2);
      } else {
         RuntimeException var3 = new RuntimeException(var2.getClass().getName() + ": " + this.redact(var2.getMessage()));
         var3.setStackTrace(var2.getStackTrace());
         this.plugin.getLogger().log(Level.SEVERE, "[MineBOT/" + this.name() + "] " + var1 + " failed", (Throwable)var3);
      }
   }

   private static Exception propagate(Throwable var0) {
      if (var0 instanceof Exception var2) {
         return var2;
      } else if (var0 instanceof Error var1) {
         throw var1;
      } else {
         return new RuntimeException(var0);
      }
   }

   private static ThreadFactory named(String var0) {
      return var1 -> {
         Thread var2 = new Thread(var1, var0);
         var2.setDaemon(true);
         return var2;
      };
   }

   private static int intValue(Object var0) {
      return var0 instanceof Number var1 ? var1.intValue() : -1;
   }

   private static void enrichInteraction(Map<String, Object> var0) {
      if (var0.get("data") instanceof Map<?, ?> var2) {
         LinkedHashMap var3 = new LinkedHashMap();
         flatten(var2.get("options"), var3);
         var0.put("options", var3);
      }
   }

   private static void flatten(Object var0, Map<String, Object> var1) {
      if (var0 instanceof List) {
         for (Object var4 : (List)var0) {
            if (var4 instanceof Map<?, ?> var5) {
               Object var6 = var5.get("name");
               if (var6 != null) {
                  if (var5.containsKey("value")) {
                     var1.put(String.valueOf(var6), var5.get("value"));
                  } else {
                     flatten(var5.get("options"), var1);
                  }
               }
            }
         }
      }
   }

   private static Map<String, List<String>> copyHeaders(Map<?, ?> var0) {
      LinkedHashMap var1 = new LinkedHashMap();

      for (Entry var3 : var0.entrySet()) {
         ArrayList var4 = new ArrayList();
         Object var6 = var3.getValue();
         if (var6 instanceof List) {
            for (Object var7 : (List)var6) {
               var4.add(String.valueOf(var7));
            }
         }

         var1.put(String.valueOf(var3.getKey()), List.copyOf(var4));
      }

      return Map.copyOf(var1);
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }
}
