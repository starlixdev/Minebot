package com.minebot.runtime;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeExecutors implements AutoCloseable {
   private final ThreadPoolExecutor workers;
   private final ScheduledThreadPoolExecutor scheduler;

   public RuntimeExecutors(int var1, int var2, int var3) {
      this.workers = new ThreadPoolExecutor(
         var1, var1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(var3), named("MineBOT-runtime-worker-"), new AbortPolicy()
      );
      this.workers.allowCoreThreadTimeOut(true);
      this.scheduler = new ScheduledThreadPoolExecutor(var2, named("MineBOT-runtime-scheduler-"));
      this.scheduler.setRemoveOnCancelPolicy(true);
   }

   ThreadPoolExecutor workers() {
      return this.workers;
   }

   ScheduledExecutorService scheduler() {
      return this.scheduler;
   }

   @Override
   public void close() {
      this.scheduler.shutdownNow();
      this.workers.shutdownNow();
   }

   private static ThreadFactory named(String var0) {
      AtomicInteger var1 = new AtomicInteger();
      return var2 -> {
         Thread var3 = new Thread(var2, var0 + var1.incrementAndGet());
         var3.setDaemon(true);
         return var3;
      };
   }
}
