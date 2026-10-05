package com.minebot.console;

import com.minebot.api.ConsoleLogEvent;
import com.minebot.api.ConsoleThrowable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class ConsoleLogHub implements AutoCloseable {
   private static final String APPENDER_NAME = "MineBOTConsoleObserver";
   private static final ThreadLocal<Boolean> PUBLISHING = ThreadLocal.withInitial(() -> false);
   private final Logger logger;
   private final CopyOnWriteArrayList<Consumer<ConsoleLogEvent>> subscribers = new CopyOnWriteArrayList<>();
   private AutoCloseable backend = () -> {};
   private final AtomicBoolean closed = new AtomicBoolean();

   public ConsoleLogHub(Logger var1) {
      this.logger = Objects.requireNonNull(var1);
      this.backend = this.install();
   }

   public AutoCloseable subscribe(Consumer<ConsoleLogEvent> var1) {
      if (this.closed.get()) {
         throw new IllegalStateException("Console log hub is closed");
      } else {
         this.subscribers.add(var1);
         return () -> this.subscribers.remove(var1);
      }
   }

   private void publish(ConsoleLogEvent var1) {
      if (!PUBLISHING.get()) {
         PUBLISHING.set(true);

         try {
            for (Consumer var3 : this.subscribers) {
               try {
                  var3.accept(var1);
               } catch (Throwable var8) {
                  this.logger.log(Level.WARNING, "Console log subscriber failed", var8);
               }
            }
         } finally {
            PUBLISHING.set(false);
         }
      }
   }

   private AutoCloseable install() {
      try {
         return this.installLog4j();
      } catch (Throwable var2) {
         this.logger.log(Level.INFO, "Log4j console observation is unavailable; using JUL fallback");
         return this.installJul();
      }
   }

   private AutoCloseable installLog4j() throws Exception {
      ClassLoader var1 = this.getClass().getClassLoader();
      Class var2 = Class.forName("org.apache.logging.log4j.LogManager", false, var1);
      Class var3 = Class.forName("org.apache.logging.log4j.core.Appender", false, var1);
      Object var4 = var2.getMethod("getContext", boolean.class).invoke(null, false);
      Object var5 = var4.getClass().getMethod("getConfiguration").invoke(var4);
      Object var6 = var5.getClass().getMethod("getRootLogger").invoke(var5);
      AtomicBoolean var7 = new AtomicBoolean(true);
      InvocationHandler var8 = (var2x, var3x, var4x) -> {
         String var5x = var3x.getName();
         if (var5x.equals("append") && var4x != null && var4x.length == 1) {
            ConsoleLogEvent var7x = fromLog4j(var4x[0]);
            if (var7x != null) {
               this.publish(var7x);
            }

            return null;
         } else if (var5x.equals("getName")) {
            return "MineBOTConsoleObserver";
         } else if (var5x.equals("ignoreExceptions")) {
            return true;
         } else if (var5x.equals("isStarted")) {
            return var7.get();
         } else if (var5x.equals("isStopped")) {
            return !var7.get();
         } else if (var5x.equals("start")) {
            var7.set(true);
            return null;
         } else if (var5x.equals("stop")) {
            var7.set(false);
            return null;
         } else if (var5x.equals("getHandler")) {
            return null;
         } else if (var5x.equals("getState")) {
            Class var6x = var3x.getReturnType();
            return Enum.valueOf(var6x, var7.get() ? "STARTED" : "STOPPED");
         } else if (var5x.equals("toString")) {
            return "MineBOTConsoleObserver";
         } else if (var3x.getReturnType() == boolean.class) {
            return false;
         } else if (var3x.getReturnType() == int.class) {
            return 0;
         } else {
            return var3x.getReturnType() == long.class ? 0L : null;
         }
      };
      Object var9 = Proxy.newProxyInstance(var1, new Class[]{var3}, var8);
      var6.getClass()
         .getMethod(
            "addAppender",
            var3,
            Class.forName("org.apache.logging.log4j.Level", false, var1),
            Class.forName("org.apache.logging.log4j.core.Filter", false, var1)
         )
         .invoke(var6, var9, null, null);
      var4.getClass().getMethod("updateLoggers").invoke(var4);
      return () -> {
         try {
            var6.getClass().getMethod("removeAppender", String.class).invoke(var6, "MineBOTConsoleObserver");
            var4.getClass().getMethod("updateLoggers").invoke(var4);
            var7.set(false);
         } catch (Exception var5x) {
            this.logger.log(Level.WARNING, "Failed to detach Log4j console observer", (Throwable)var5x);
         }
      };
   }

   private AutoCloseable installJul() {
      Logger var1 = Logger.getLogger("");
      Handler var2 = new Handler() {
         @Override
         public void publish(LogRecord var1) {
            if (var1 != null && this.isLoggable(var1)) {
               ConsoleLogHub.this.publishJul(var1);
            }
         }

         @Override
         public void flush() {
         }

         @Override
         public void close() {
         }
      };
      var2.setLevel(Level.ALL);
      var1.addHandler(var2);
      return () -> var1.removeHandler(var2);
   }

   private void publishJul(LogRecord var1) {
      this.publish(
         new ConsoleLogEvent(
            Instant.ofEpochMilli(var1.getMillis()),
            var1.getLevel() == null ? "UNKNOWN" : var1.getLevel().getName(),
            var1.getLoggerName(),
            formatJul(var1),
            throwable(var1.getThrown())
         )
      );
   }

   private static String formatJul(LogRecord var0) {
      String var1 = Objects.requireNonNullElse(var0.getMessage(), "");
      Object[] var2 = var0.getParameters();
      if (var2 != null && var2.length != 0) {
         try {
            return MessageFormat.format(var1, var2);
         } catch (Exception var4) {
            return var1;
         }
      } else {
         return var1;
      }
   }

   private static ConsoleLogEvent fromLog4j(Object var0) {
      try {
         Class var1 = var0.getClass();
         long var2 = ((Number)var1.getMethod("getTimeMillis").invoke(var0)).longValue();
         Object var4 = var1.getMethod("getLevel").invoke(var0);
         String var5 = String.valueOf(var1.getMethod("getLoggerName").invoke(var0));
         Object var6 = var1.getMethod("getMessage").invoke(var0);
         String var7 = var6 == null ? "" : String.valueOf(var6.getClass().getMethod("getFormattedMessage").invoke(var6));
         Throwable var8 = (Throwable)var1.getMethod("getThrown").invoke(var0);
         return new ConsoleLogEvent(Instant.ofEpochMilli(var2), String.valueOf(var4), var5, var7, throwable(var8));
      } catch (Throwable var9) {
         return null;
      }
   }

   private static ConsoleThrowable throwable(Throwable var0) {
      if (var0 == null) {
         return null;
      } else {
         StringWriter var1 = new StringWriter();
         var0.printStackTrace(new PrintWriter(var1));
         ArrayList var2 = new ArrayList();

         for (Throwable var6 : var0.getSuppressed()) {
            var2.add(throwable(var6));
         }

         return new ConsoleThrowable(
            var0.getClass().getName(), Objects.requireNonNullElse(var0.getMessage(), ""), var1.toString(), var2, throwable(var0.getCause())
         );
      }
   }

   @Override
   public void close() {
      if (this.closed.compareAndSet(false, true)) {
         this.subscribers.clear();

         try {
            this.backend.close();
         } catch (Exception var2) {
            this.logger.log(Level.WARNING, "Failed to close console log observer", (Throwable)var2);
         }
      }
   }
}
