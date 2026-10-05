package com.minebot.minecraft;

import com.minebot.MineBotPlugin;
import com.minebot.api.EventData;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import org.bukkit.plugin.PluginManager;

public final class MinecraftBridge implements AutoCloseable {
   private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("player_join", "org.bukkit.event.player.PlayerJoinEvent"),
      Map.entry("player_quit", "org.bukkit.event.player.PlayerQuitEvent"),
      Map.entry("player_chat", "org.bukkit.event.player.AsyncPlayerChatEvent"),
      Map.entry("player_command", "org.bukkit.event.player.PlayerCommandPreprocessEvent"),
      Map.entry("player_death", "org.bukkit.event.entity.PlayerDeathEvent"),
      Map.entry("block_break", "org.bukkit.event.block.BlockBreakEvent"),
      Map.entry("block_place", "org.bukkit.event.block.BlockPlaceEvent"),
      Map.entry("server_command", "org.bukkit.event.server.ServerCommandEvent")
   );
   private static final ClassValue<MinecraftBridge.Accessor[]> EVENT_ACCESSORS = new ClassValue<MinecraftBridge.Accessor[]>() {
      protected MinecraftBridge.Accessor[] computeValue(Class<?> var1) {
         ArrayList<MinecraftBridge.Accessor> var2 = new ArrayList<>();

         for (Method var6 : var1.getMethods()) {
            if (var6.getParameterCount() == 0 && var6.getReturnType() != void.class && var6.getDeclaringClass() != Object.class) {
               String var7 = var6.getName();
               if (!var7.equals("getHandlers") && !var7.equals("isAsynchronous") && (var7.startsWith("get") || var7.startsWith("is"))) {
                  var2.add(new MinecraftBridge.Accessor(var6, MinecraftBridge.property(var7)));
               }
            }
         }

         return var2.toArray(MinecraftBridge.Accessor[]::new);
      }
   };
   private static final ClassValue<MinecraftBridge.Accessor[]> VALUE_ACCESSORS = new ClassValue<MinecraftBridge.Accessor[]>() {
      protected MinecraftBridge.Accessor[] computeValue(Class<?> var1) {
         ArrayList<MinecraftBridge.Accessor> var2 = new ArrayList<>();

         for (Method var6 : var1.getMethods()) {
            if (var6.getParameterCount() == 0 && var6.getReturnType() != void.class && var6.getDeclaringClass() != Object.class) {
               String var7 = var6.getName();
               if (var7.equals("getName")
                  || var7.equals("getUniqueId")
                  || var7.equals("getX")
                  || var7.equals("getY")
                  || var7.equals("getZ")
                  || var7.equals("getWorld")) {
                  var2.add(new MinecraftBridge.Accessor(var6, MinecraftBridge.property(var7)));
               }
            }
         }

         return var2.toArray(MinecraftBridge.Accessor[]::new);
      }
   };
   private final MineBotPlugin plugin;
   private final ClassLoader classLoader;
   private final Class<?> listenerClass;
   private final Class<?> executorClass;
   private final Class<?> priorityClass;
   private final Method registerEvent;
   private final Method unregisterAll;
   private final Method asynchronous;
   private final Map<Class<?>, MinecraftBridge.Hub> hubs = new HashMap<>();
   private volatile Map<String, Object> serverSnapshot = Map.of();

   public MinecraftBridge(MineBotPlugin var1) {
      try {
         this.plugin = var1;
         this.classLoader = var1.getClass().getClassLoader();
         this.listenerClass = Class.forName("org.bukkit.event.Listener", true, this.classLoader);
         this.executorClass = Class.forName("org.bukkit.plugin.EventExecutor", true, this.classLoader);
         this.priorityClass = Class.forName("org.bukkit.event.EventPriority", true, this.classLoader);
         Class var2 = Class.forName("org.bukkit.event.Event", true, this.classLoader);
         this.asynchronous = var2.getMethod("isAsynchronous");
         Class var3 = Class.forName("org.bukkit.event.HandlerList", true, this.classLoader);
         this.unregisterAll = var3.getMethod("unregisterAll", this.listenerClass);
         Method var4 = null;
         PluginManager var5 = var1.getServer().getPluginManager();

         for (Method var9 : var5.getClass().getMethods()) {
            if (var9.getName().equals("registerEvent") && var9.getParameterCount() == 6) {
               var4 = var9;
               break;
            }
         }

         if (var4 == null) {
            throw new IllegalStateException("Bukkit registerEvent API is unavailable");
         } else {
            this.registerEvent = var4;
            this.updateServerSnapshot();
         }
      } catch (ReflectiveOperationException var10) {
         throw new IllegalStateException("Bukkit event API is unavailable", var10);
      }
   }

   public void validate(String var1) {
      try {
         this.resolve(var1);
      } catch (Exception var3) {
         throw new IllegalArgumentException("Unknown Bukkit event: " + var1, var3);
      }
   }

   public AutoCloseable subscribe(String var1, BiConsumer<String, EventData> var2) throws Exception {
      Objects.requireNonNull(var2);
      String var3 = var1.trim();
      Class var4 = this.resolve(var3);
      MinecraftBridge.Subscriber var5 = new MinecraftBridge.Subscriber(var3, var2);
      MinecraftBridge.Hub var6;
      synchronized (this.hubs) {
         var6 = this.hubs.get(var4);
         if (var6 == null) {
            var6 = this.createHub(var4);
            this.hubs.put(var4, var6);
         }

         var6.subscribers.add(var5);
      }

      MinecraftBridge.Hub var10 = var6;
      return () -> {
         synchronized (this.hubs) {
            var10.subscribers.remove(var5);
            if (var10.subscribers.isEmpty() && this.hubs.remove(var4, var10)) {
               this.unregisterAll.invoke(null, var10.listener);
            }
         }
      };
   }

   public Map<String, Object> serverSnapshot() {
      return this.serverSnapshot;
   }

   public void runConsoleCommand(String var1) {
      this.plugin
         .getServer()
         .getScheduler()
         .runTask(this.plugin, () -> this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(), var1));
   }

   public void broadcast(String var1) {
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.plugin.getServer().broadcastMessage(var1));
   }

   @Override
   public void close() {
      ArrayList var1 = new ArrayList();
      synchronized (this.hubs) {
         for (MinecraftBridge.Hub var4 : this.hubs.values()) {
            var1.add(var4.listener);
         }

         this.hubs.clear();
      }

      for (Object var9 : var1) {
         try {
            this.unregisterAll.invoke(null, var9);
         } catch (Exception var6) {
         }
      }
   }

   private MinecraftBridge.Hub createHub(Class<?> var1) throws Exception {
      CopyOnWriteArrayList<MinecraftBridge.Subscriber> var2 = new CopyOnWriteArrayList<>();
      Object var3 = Proxy.newProxyInstance(this.classLoader, new Class[]{this.listenerClass}, (var0, var1x, var2x) -> null);
      Object var4 = Proxy.newProxyInstance(this.classLoader, new Class[]{this.executorClass}, (var2x, var3x, var4x) -> {
         if ("execute".equals(var3x.getName()) && var4x != null && var4x.length == 2) {
            Object var5x = var4x[1];
            if (this.isAsync(var5x)) {
               MinecraftBridge.Subscriber[] var6 = var2.toArray(MinecraftBridge.Subscriber[]::new);
               this.plugin.getServer().getScheduler().runTask(this.plugin, () -> dispatch(var5x, var6));
            } else {
               dispatch(var5x, var2);
            }
         }

         return null;
      });
      Enum var5 = Enum.valueOf((Class<Enum>)this.priorityClass, "MONITOR");
      this.registerEvent.invoke(this.plugin.getServer().getPluginManager(), var1, var3, var5, var4, this.plugin, true);
      return new MinecraftBridge.Hub(var3, var2);
   }

   private static void dispatch(Object var0, Iterable<MinecraftBridge.Subscriber> var1) {
      EventData var2 = snapshotEvent(var0);

      for (MinecraftBridge.Subscriber var4 : var1) {
         var4.consumer.accept(var4.key, var2);
      }
   }

   private static void dispatch(Object var0, MinecraftBridge.Subscriber[] var1) {
      EventData var2 = snapshotEvent(var0);

      for (MinecraftBridge.Subscriber var6 : var1) {
         var6.consumer.accept(var6.key, var2);
      }
   }

   private Class<?> resolve(String var1) throws Exception {
      String var2 = ALIASES.getOrDefault(var1.toLowerCase(Locale.ROOT), var1);
      return Class.forName(var2, true, this.classLoader);
   }

   private boolean isAsync(Object var1) {
      try {
         return (Boolean)this.asynchronous.invoke(var1);
      } catch (Exception var3) {
         return false;
      }
   }

   private static EventData snapshotEvent(Object var0) {
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("event.class", var0.getClass().getName());
      IdentityHashMap var2 = new IdentityHashMap();

      for (MinecraftBridge.Accessor var6 : EVENT_ACCESSORS.get(var0.getClass())) {
         try {
            var1.put(var6.name, safe(var6.method.invoke(var0), 0, var2));
         } catch (Exception var8) {
         }
      }

      return new EventData(var1);
   }

   private static Object safe(Object var0, int var1, IdentityHashMap<Object, Boolean> var2) {
      if (var0 == null || var0 instanceof String || var0 instanceof Number || var0 instanceof Boolean || var0 instanceof Enum) {
         return var0;
      } else if (var1 > 2) {
         return String.valueOf(var0);
      } else if (var2.put(var0, Boolean.TRUE) != null) {
         return "<cycle>";
      } else {
         LinkedHashMap var20;
         try {
            if (var0 instanceof UUID) {
               return var0.toString();
            }

            if (var0 instanceof Collection var15) {
               ArrayList var19 = new ArrayList(var15.size());

               for (Object var25 : var15) {
                  var19.add(safe(var25, var1 + 1, var2));
               }

               return var19;
            }

            if (!(var0 instanceof Map<?, ?> var3)) {
               LinkedHashMap var14 = new LinkedHashMap();

               for (MinecraftBridge.Accessor var7 : VALUE_ACCESSORS.get(var0.getClass())) {
                  try {
                     var14.put(var7.name, safe(var7.method.invoke(var0), var1 + 1, var2));
                  } catch (Exception var12) {
                  }
               }

               return var14.isEmpty() ? String.valueOf(var0) : var14;
            }

            LinkedHashMap var4 = new LinkedHashMap(Math.max(16, var3.size() * 2));

            for (Entry var6 : var3.entrySet()) {
               var4.put(String.valueOf(var6.getKey()), safe(var6.getValue(), var1 + 1, var2));
            }

            var20 = var4;
         } finally {
            var2.remove(var0);
         }

         return var20;
      }
   }

   private void updateServerSnapshot() {
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("name", this.plugin.getServer().getName());
      var1.put("version", this.plugin.getServer().getVersion());
      var1.put("onlinePlayers", this.plugin.getServer().getOnlinePlayers().size());
      this.serverSnapshot = Collections.unmodifiableMap(var1);
   }

   private static String property(String var0) {
      String var1 = var0.startsWith("get") ? var0.substring(3) : (var0.startsWith("is") ? var0.substring(2) : var0);
      return var1.isEmpty() ? var0 : Character.toLowerCase(var1.charAt(0)) + var1.substring(1);
   }

   private record Accessor(Method method, String name) {
   }

   private record Hub(Object listener, CopyOnWriteArrayList<MinecraftBridge.Subscriber> subscribers) {
   }

   private record Subscriber(String key, BiConsumer<String, EventData> consumer) {
   }
}
