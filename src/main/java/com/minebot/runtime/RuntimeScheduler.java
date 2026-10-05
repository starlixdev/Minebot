package com.minebot.runtime;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeScheduler implements AutoCloseable {
   private final ScheduledThreadPoolExecutor executor;

   public RuntimeScheduler() {
      AtomicInteger var1 = new AtomicInteger();
      this.executor = new ScheduledThreadPoolExecutor(1, var1x -> {
         Thread var2 = new Thread(var1x, "MineBOT-runtime-scheduler-" + var1.incrementAndGet());
         var2.setDaemon(true);
         return var2;
      });
      this.executor.setRemoveOnCancelPolicy(true);
      this.executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
      this.executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
   }

   public ScheduledFuture<?> schedule(Runnable var1, long var2, TimeUnit var4) {
      return this.executor.schedule(var1, var2, var4);
   }

   public ScheduledFuture<?> scheduleAtFixedRate(Runnable var1, long var2, long var4, TimeUnit var6) {
      return this.executor.scheduleAtFixedRate(var1, var2, var4, var6);
   }

   @Override
   public void close() {
      this.executor.shutdownNow();
   }
}
