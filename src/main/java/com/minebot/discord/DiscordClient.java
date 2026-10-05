package com.minebot.discord;

import com.minebot.bot.BotConfig;
import com.minebot.util.ExecutorFactory;
import com.minebot.util.MiniJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.WebSocket;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket.Listener;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class DiscordClient implements AutoCloseable {
   private static final String API = "https://discord.com/api/v10";
   private final BotConfig config;
   private final Logger logger;
   private final HttpClient http;
   private final ScheduledExecutorService scheduler;
   private final ExecutorService callbacks;
   private final BiConsumer<String, Map<String, Object>> eventConsumer;
   private final AtomicBoolean running = new AtomicBoolean(false);
   private final AtomicLong generation = new AtomicLong();
   private final AtomicInteger reconnectAttempt = new AtomicInteger();
   private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
   private final Random random = new Random();
   private volatile WebSocket socket;
   private volatile ScheduledFuture<?> heartbeatTask;
   private volatile Long sequence;
   private volatile String sessionId;
   private volatile String resumeGatewayUrl;
   private volatile boolean heartbeatAcked = true;
   private volatile String applicationId;
   private volatile String botUserId;
   private volatile String botUsername;

   public DiscordClient(BotConfig var1, Logger var2, BiConsumer<String, Map<String, Object>> var3) {
      this.config = var1;
      this.logger = var2;
      this.eventConsumer = var3;
      this.scheduler = Executors.newSingleThreadScheduledExecutor(named("MineBOT-Discord-Scheduler-" + safe(var1.name())));
      this.callbacks = ExecutorFactory.newBoundedSingleThreadExecutor(named("MineBOT-Discord-Callbacks-" + safe(var1.name())));
      this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20L)).executor(this.callbacks).followRedirects(Redirect.NORMAL).build();
   }

   public void start() {
      if (this.running.compareAndSet(false, true)) {
         long var1 = this.generation.incrementAndGet();
         this.fetchGatewayAndConnect(var1, false);
      }
   }

   public boolean isRunning() {
      return this.running.get();
   }

   public String applicationId() {
      return this.applicationId;
   }

   public String botUserId() {
      return this.botUserId;
   }

   public String botUsername() {
      return this.botUsername;
   }

   private void fetchGatewayAndConnect(long var1, boolean var3) {
      if (this.isCurrent(var1)) {
         if (var3 && this.resumeGatewayUrl != null && this.sessionId != null && this.sequence != null) {
            this.connectWebSocket(this.resumeGatewayUrl, var1, true);
         } else {
            this.request(
               "GET",
               "/gateway/bot",
               null,
               0,
               var3x -> {
                  if (this.isCurrent(var1)) {
                     if (!var3x.ok()) {
                        this.logger
                           .warning(
                              "[MineBOT:"
                                 + this.config.name()
                                 + "] Could not obtain Discord gateway: HTTP "
                                 + var3x.status()
                                 + " "
                                 + this.redact(sanitize(var3x.body()))
                           );
                        this.scheduleReconnect(var1, false);
                     } else {
                        try {
                           Map var4 = asMap(MiniJson.parse(var3x.body()));
                           String var5 = string(var4.get("url"));
                           if (var5.isBlank()) {
                              throw new IllegalArgumentException("Gateway response had no URL");
                           }

                           this.connectWebSocket(var5, var1, false);
                        } catch (Exception var6) {
                           this.logger
                              .warning("[MineBOT:" + this.config.name() + "] Bad Discord gateway response: " + this.redact(String.valueOf(var6.getMessage())));
                           this.scheduleReconnect(var1, false);
                        }
                     }
                  }
               }
            );
         }
      }
   }

   private void connectWebSocket(String var1, long var2, boolean var4) {
      if (this.isCurrent(var2)) {
         String var5 = var1 + (var1.contains("?") ? "&" : "?") + "v=10&encoding=json";
         this.http
            .newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(20L))
            .buildAsync(URI.create(var5), new DiscordClient.GatewayListener(var2, var4))
            .whenComplete((var4x, var5x) -> {
               if (!this.isCurrent(var2)) {
                  if (var4x != null) {
                     var4x.abort();
                  }
               } else {
                  if (var5x != null) {
                     this.logger.warning("[MineBOT:" + this.config.name() + "] Discord gateway connection failed: " + this.redact(rootMessage(var5x)));
                     this.scheduleReconnect(var2, var4);
                  } else {
                     this.socket = var4x;
                  }
               }
            });
      }
   }

   private void handleGatewayPayload(String var1, long var2, boolean var4) {
      if (this.isCurrent(var2)) {
         try {
            Map var5 = asMap(MiniJson.parse(var1));
            int var6 = intValue(var5.get("op"), -1);
            if (var5.get("s") instanceof Number var8) {
               this.sequence = var8.longValue();
            }

            Object var13 = var5.get("d");
            switch (var6) {
               case 0:
                  this.handleDispatch(string(var5.get("t")), asMapOrEmpty(var13));
                  break;
               case 1:
                  this.sendHeartbeat();
               case 2:
               case 3:
               case 4:
               case 5:
               case 6:
               case 8:
               default:
                  break;
               case 7:
                  this.reconnectNow(var2, true);
                  break;
               case 9:
                  boolean var14 = Boolean.TRUE.equals(var13);
                  if (!var14) {
                     this.clearSession();
                  }

                  long var15 = 1000L + this.random.nextInt(4001);
                  this.scheduler.schedule(() -> this.reconnectNow(var2, var14), var15, TimeUnit.MILLISECONDS);
                  break;
               case 10:
                  Map var9 = asMap(var13);
                  long var10 = longValue(var9.get("heartbeat_interval"), 45000L);
                  this.startHeartbeat(var10);
                  if (var4 && this.sessionId != null && this.sequence != null) {
                     this.sendResume();
                  } else {
                     this.sendIdentify();
                  }
                  break;
               case 11:
                  this.heartbeatAcked = true;
            }
         } catch (Exception var12) {
            this.logger
               .warning("[MineBOT:" + this.config.name() + "] Could not parse Discord gateway payload: " + this.redact(String.valueOf(var12.getMessage())));
         }
      }
   }

   private void handleDispatch(String var1, Map<String, Object> var2) {
      this.reconnectAttempt.set(0);
      if ("READY".equals(var1)) {
         this.sessionId = string(var2.get("session_id"));
         this.resumeGatewayUrl = string(var2.get("resume_gateway_url"));
         Map var3 = asMapOrEmpty(var2.get("user"));
         this.botUserId = string(var3.get("id"));
         this.botUsername = string(var3.get("username"));
         Map var4 = asMapOrEmpty(var2.get("application"));
         this.applicationId = string(var4.get("id"));
         if (this.applicationId.isBlank()) {
            this.applicationId = this.botUserId;
         }

         this.logger.info("[MineBOT:" + this.config.name() + "] Connected to Discord as " + this.botUsername + " (" + this.botUserId + ")");
      }

      try {
         this.eventConsumer.accept(var1, var2);
      } catch (Throwable var5) {
         this.logger.warning("[MineBOT:" + this.config.name() + "] Event consumer failed: " + this.redact(rootMessage(var5)));
      }
   }

   private void sendIdentify() {
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("os", System.getProperty("os.name", "unknown"));
      var1.put("browser", "MineBOT");
      var1.put("device", "MineBOT");
      LinkedHashMap var2 = new LinkedHashMap();
      var2.put("token", this.config.token());
      var2.put("intents", this.config.intents());
      var2.put("properties", var1);
      Map var3 = this.presencePayload();
      if (!var3.isEmpty()) {
         var2.put("presence", var3);
      }

      this.sendGateway(Map.of("op", 2, "d", var2));
   }

   private void sendResume() {
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("token", this.config.token());
      var1.put("session_id", this.sessionId);
      var1.put("seq", this.sequence);
      this.sendGateway(Map.of("op", 6, "d", var1));
   }

   private Map<String, Object> presencePayload() {
      if (this.config.activityText() != null && !this.config.activityText().isBlank()) {
         String var2 = this.config.activityType().toLowerCase(Locale.ROOT);

         byte var1 = switch (var2) {
            case "streaming" -> 1;
            case "listening" -> 2;
            case "watching" -> 3;
            case "custom" -> 4;
            case "competing" -> 5;
            default -> 0;
         };
         LinkedHashMap var4 = new LinkedHashMap();
         var4.put("name", this.config.activityText());
         var4.put("type", Integer.valueOf(var1));
         LinkedHashMap var5 = new LinkedHashMap();
         var5.put("since", null);
         var5.put("activities", List.of(var4));
         var5.put("status", "online");
         var5.put("afk", false);
         return var5;
      } else {
         return Map.of();
      }
   }

   public void updatePresence(String var1, String var2, String var3) {
      String var5 = String.valueOf(var1).toLowerCase(Locale.ROOT);

      byte var4 = switch (var5) {
         case "streaming" -> 1;
         case "listening" -> 2;
         case "watching" -> 3;
         case "custom" -> 4;
         case "competing" -> 5;
         default -> 0;
      };
      LinkedHashMap var7 = new LinkedHashMap();
      var7.put("since", null);
      var7.put("activities", var2 != null && !var2.isBlank() ? List.of(Map.of("name", var2, "type", Integer.valueOf(var4))) : List.of());
      var7.put("status", var3 != null && !var3.isBlank() ? var3 : "online");
      var7.put("afk", false);
      this.sendGateway(Map.of("op", 3, "d", var7));
   }

   private void startHeartbeat(long var1) {
      this.cancelHeartbeat();
      this.heartbeatAcked = true;
      long var3 = var1 <= 1L ? 0L : (long)(this.random.nextDouble() * var1);
      this.heartbeatTask = this.scheduler.scheduleAtFixedRate(() -> {
         if (this.running.get()) {
            if (!this.heartbeatAcked) {
               this.logger.warning("[MineBOT:" + this.config.name() + "] Discord heartbeat was not acknowledged; reconnecting.");
               this.reconnectNow(this.generation.get(), true);
            } else {
               this.sendHeartbeat();
            }
         }
      }, var3, Math.max(1000L, var1), TimeUnit.MILLISECONDS);
   }

   private void sendHeartbeat() {
      this.heartbeatAcked = false;
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("op", 1);
      var1.put("d", this.sequence);
      this.sendGateway(var1);
   }

   private void sendGateway(Map<String, Object> var1) {
      WebSocket var2 = this.socket;
      if (var2 != null && this.running.get()) {
         try {
            var2.sendText(MiniJson.stringify(var1), true);
         } catch (Exception var4) {
            this.logger.warning("[MineBOT:" + this.config.name() + "] Could not send gateway payload: " + this.redact(String.valueOf(var4.getMessage())));
         }
      }
   }

   public void request(String var1, String var2, String var3, Consumer<DiscordClient.RestResponse> var4) {
      this.request(var1, var2, var3, 0, var4);
   }

   private void request(String var1, String var2, String var3, int var4, Consumer<DiscordClient.RestResponse> var5) {
      if (var2 == null || var2.isBlank() || var2.startsWith("http://") || var2.startsWith("https://")) {
         var5.accept(new DiscordClient.RestResponse(false, 0, "", "Discord REST paths must be relative to the Discord API"));
      } else if (!this.running.get() && !var2.equals("/gateway/bot")) {
         var5.accept(new DiscordClient.RestResponse(false, 0, "", "Bot is stopped"));
      } else {
         String var6 = "https://discord.com/api/v10" + (var2.startsWith("/") ? var2 : "/" + var2);
         Builder var7 = HttpRequest.newBuilder(URI.create(var6))
            .timeout(Duration.ofSeconds(30L))
            .header("Authorization", "Bot " + this.config.token())
            .header("User-Agent", "MineBOT/2.1 (Minecraft Paper Plugin)")
            .header("Accept", "application/json");
         String var8 = var1 == null ? "GET" : var1.toUpperCase(Locale.ROOT);
         if (var3 != null && !var3.isEmpty()) {
            var7.header("Content-Type", "application/json");
            var7.method(var8, BodyPublishers.ofString(var3, StandardCharsets.UTF_8));
         } else {
            var7.method(var8, BodyPublishers.noBody());
         }

         this.http.sendAsync(var7.build(), BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((var6x, var7x) -> {
            if (var7x != null) {
               var5.accept(new DiscordClient.RestResponse(false, 0, "", this.redact(rootMessage(var7x))));
            } else {
               int var8x = var6x.statusCode();
               String var9 = var6x.body() == null ? "" : var6x.body();
               if (var8x == 429 && var4 < 4) {
                  long var10 = retryAfterMillis(var9, var6x.headers());
                  this.scheduler.schedule(() -> this.request(var1, var2, var3, var4 + 1, var5), var10, TimeUnit.MILLISECONDS);
               } else {
                  var5.accept(new DiscordClient.RestResponse(var8x >= 200 && var8x < 300, var8x, var9, var8x >= 200 && var8x < 300 ? "" : "HTTP " + var8x));
               }
            }
         });
      }
   }

   public void sendMessage(String var1, String var2, Consumer<DiscordClient.RestResponse> var3) {
      this.request("POST", "/channels/" + var1 + "/messages", MiniJson.stringify(Map.of("content", var2)), var3);
   }

   public void interactionReply(String var1, String var2, String var3, boolean var4, Consumer<DiscordClient.RestResponse> var5) {
      LinkedHashMap var6 = new LinkedHashMap();
      var6.put("content", var3);
      if (var4) {
         var6.put("flags", 64);
      }

      Map var7 = Map.of("type", 4, "data", var6);
      this.request("POST", "/interactions/" + var1 + "/" + var2 + "/callback", MiniJson.stringify(var7), var5);
   }

   public void interactionDefer(String var1, String var2, boolean var3, Consumer<DiscordClient.RestResponse> var4) {
      Map var5 = var3 ? Map.of("flags", 64) : Map.of();
      this.request("POST", "/interactions/" + var1 + "/" + var2 + "/callback", MiniJson.stringify(Map.of("type", 5, "data", var5)), var4);
   }

   public void editOriginalInteraction(String var1, String var2, Consumer<DiscordClient.RestResponse> var3) {
      if (this.applicationId != null && !this.applicationId.isBlank()) {
         this.request("PATCH", "/webhooks/" + this.applicationId + "/" + var1 + "/messages/@original", MiniJson.stringify(Map.of("content", var2)), var3);
      } else {
         var3.accept(new DiscordClient.RestResponse(false, 0, "", "Application ID is not available yet"));
      }
   }

   public void followup(String var1, String var2, boolean var3, Consumer<DiscordClient.RestResponse> var4) {
      if (this.applicationId != null && !this.applicationId.isBlank()) {
         LinkedHashMap var5 = new LinkedHashMap();
         var5.put("content", var2);
         if (var3) {
            var5.put("flags", 64);
         }

         this.request("POST", "/webhooks/" + this.applicationId + "/" + var1, MiniJson.stringify(var5), var4);
      } else {
         var4.accept(new DiscordClient.RestResponse(false, 0, "", "Application ID is not available yet"));
      }
   }

   public void registerGlobalCommands(List<Map<String, Object>> var1, Consumer<DiscordClient.RestResponse> var2) {
      if (this.applicationId != null && !this.applicationId.isBlank()) {
         this.request("PUT", "/applications/" + this.applicationId + "/commands", MiniJson.stringify(var1), var2);
      } else {
         var2.accept(new DiscordClient.RestResponse(false, 0, "", "Application ID is not available yet"));
      }
   }

   private void reconnectNow(long var1, boolean var3) {
      if (this.isCurrent(var1) && this.reconnectScheduled.compareAndSet(false, true)) {
         this.cancelHeartbeat();
         WebSocket var4 = this.socket;
         this.socket = null;
         if (var4 != null) {
            var4.abort();
         }

         this.scheduler.execute(() -> {
            this.reconnectScheduled.set(false);
            if (this.isCurrent(var1)) {
               this.fetchGatewayAndConnect(var1, var3);
            }
         });
      }
   }

   private void scheduleReconnect(long var1, boolean var3) {
      if (this.isCurrent(var1) && this.reconnectScheduled.compareAndSet(false, true)) {
         int var4 = this.reconnectAttempt.getAndIncrement();
         long var5 = Math.min(60000L, 1000L * (1L << Math.min(5, var4)));
         var5 += this.random.nextInt(500);
         this.scheduler.schedule(() -> {
            this.reconnectScheduled.set(false);
            if (this.isCurrent(var1)) {
               this.fetchGatewayAndConnect(var1, var3);
            }
         }, var5, TimeUnit.MILLISECONDS);
      }
   }

   private boolean isCurrent(long var1) {
      return this.running.get() && this.generation.get() == var1;
   }

   private void clearSession() {
      this.sequence = null;
      this.sessionId = null;
      this.resumeGatewayUrl = null;
   }

   private void cancelHeartbeat() {
      ScheduledFuture var1 = this.heartbeatTask;
      this.heartbeatTask = null;
      if (var1 != null) {
         var1.cancel(false);
      }
   }

   private static boolean isFatalClose(int var0) {
      return var0 == 4004 || var0 == 4010 || var0 == 4011 || var0 == 4012 || var0 == 4013 || var0 == 4014;
   }

   private static boolean canResume(int var0) {
      return var0 != 4007 && var0 != 4009;
   }

   private static long retryAfterMillis(String var0, HttpHeaders var1) {
      try {
         Object var2 = MiniJson.parse(var0);
         Map var3 = asMap(var2);
         if (var3.get("retry_after") instanceof Number var5) {
            return Math.max(250L, (long)(var5.doubleValue() * 1000.0) + 100L);
         }
      } catch (Exception var6) {
      }

      Optional var8 = var1.firstValue("Retry-After");
      if (var8.isPresent()) {
         try {
            return Math.max(250L, (long)(Double.parseDouble((String)var8.get()) * 1000.0) + 100L);
         } catch (NumberFormatException var7) {
         }
      }

      return 1000L;
   }

   public static Map<String, Object> asMap(Object var0) {
      if (var0 instanceof Map<?, ?> var1) {
         return (Map<String, Object>)var1;
      } else {
         throw new IllegalArgumentException("Expected JSON object");
      }
   }

   private static Map<String, Object> asMapOrEmpty(Object var0) {
      return var0 instanceof Map ? (Map)var0 : Collections.emptyMap();
   }

   private static String string(Object var0) {
      return var0 == null ? "" : String.valueOf(var0);
   }

   private static int intValue(Object var0, int var1) {
      return var0 instanceof Number var2 ? var2.intValue() : var1;
   }

   private static long longValue(Object var0, long var1) {
      return var0 instanceof Number var3 ? var3.longValue() : var1;
   }

   private static String safe(String var0) {
      return var0 == null ? "bot" : var0.replaceAll("[^A-Za-z0-9_-]", "_");
   }

   private static ThreadFactory named(String var0) {
      return var1 -> {
         Thread var2 = new Thread(var1, var0);
         var2.setDaemon(true);
         return var2;
      };
   }

   private static String rootMessage(Throwable var0) {
      while (var0.getCause() != null) {
         var0 = var0.getCause();
      }

      return String.valueOf(var0.getMessage());
   }

   private static String sanitize(String var0) {
      if (var0 == null) {
         return "";
      } else {
         return var0.length() > 500 ? var0.substring(0, 500) + "..." : var0;
      }
   }

   private String redact(String var1) {
      if (var1 != null && !var1.isEmpty()) {
         String var2 = this.config.token();
         return var2 != null && !var2.isBlank() ? var1.replace(var2, "[REDACTED]") : var1;
      } else {
         return var1 == null ? "" : var1;
      }
   }

   @Override
   public void close() {
      if (!this.scheduler.isShutdown() || !this.callbacks.isShutdown()) {
         this.running.set(false);
         this.generation.incrementAndGet();
         this.reconnectScheduled.set(false);
         this.cancelHeartbeat();
         WebSocket var1 = this.socket;
         this.socket = null;
         if (var1 != null) {
            try {
               var1.sendClose(1000, "MineBOT stopping").orTimeout(2L, TimeUnit.SECONDS).exceptionally(var0 -> null);
            } catch (Exception var3) {
               var1.abort();
            }
         }

         this.scheduler.shutdownNow();
         this.callbacks.shutdownNow();
      }
   }

   private final class GatewayListener implements Listener {
      private final long gen;
      private final boolean shouldResume;
      private final StringBuilder buffer = new StringBuilder();

      GatewayListener(long nullx, boolean nullxx) {
         this.gen = nullx;
         this.shouldResume = nullxx;
      }

      @Override
      public void onOpen(WebSocket var1) {
         if (!DiscordClient.this.isCurrent(this.gen)) {
            var1.abort();
         } else {
            DiscordClient.this.socket = var1;
            var1.request(1L);
         }
      }

      @Override
      public CompletionStage<?> onText(WebSocket var1, CharSequence var2, boolean var3) {
         if (!DiscordClient.this.isCurrent(this.gen)) {
            return CompletableFuture.completedFuture(null);
         } else {
            synchronized (this.buffer) {
               this.buffer.append(var2);
               if (var3) {
                  String var5 = this.buffer.toString();
                  this.buffer.setLength(0);
                  DiscordClient.this.callbacks.execute(() -> DiscordClient.this.handleGatewayPayload(var5, this.gen, this.shouldResume));
               }
            }

            var1.request(1L);
            return CompletableFuture.completedFuture(null);
         }
      }

      @Override
      public CompletionStage<?> onClose(WebSocket var1, int var2, String var3) {
         if (!DiscordClient.this.isCurrent(this.gen)) {
            return CompletableFuture.completedFuture(null);
         } else {
            DiscordClient.this.cancelHeartbeat();
            DiscordClient.this.socket = null;
            if (DiscordClient.isFatalClose(var2)) {
               DiscordClient.this.logger
                  .severe(
                     "[MineBOT:"
                        + DiscordClient.this.config.name()
                        + "] Discord closed the gateway with fatal code "
                        + var2
                        + ": "
                        + DiscordClient.this.redact(var3)
                        + ". Check token/intents/configuration."
                  );
               DiscordClient.this.running.set(false);
            } else {
               DiscordClient.this.logger
                  .warning("[MineBOT:" + DiscordClient.this.config.name() + "] Discord gateway closed (" + var2 + "): " + DiscordClient.this.redact(var3));
               DiscordClient.this.scheduleReconnect(this.gen, DiscordClient.canResume(var2));
            }

            return CompletableFuture.completedFuture(null);
         }
      }

      @Override
      public void onError(WebSocket var1, Throwable var2) {
         if (DiscordClient.this.isCurrent(this.gen)) {
            DiscordClient.this.cancelHeartbeat();
            DiscordClient.this.socket = null;
            DiscordClient.this.logger
               .warning(
                  "[MineBOT:" + DiscordClient.this.config.name() + "] Discord gateway error: " + DiscordClient.this.redact(DiscordClient.rootMessage(var2))
               );
            DiscordClient.this.scheduleReconnect(this.gen, true);
         }
      }
   }

   public record RestResponse(boolean ok, int status, String body, String error) {
   }
}
