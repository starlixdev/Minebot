package com.minebot.runtime;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;

public final class BoundedExecutors {
   private static volatile int networkQueueCapacity = 256;

   private BoundedExecutors() {
   }

   public static void configure(int var0) {
      networkQueueCapacity = Math.max(16, Math.min(65536, var0));
   }

   public static ExecutorService newFixedThreadPool(int var0, ThreadFactory var1) {
      return new ThreadPoolExecutor(var0, var0, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(networkQueueCapacity), var1, new AbortPolicy());
   }

   public static ExecutorService newSingleThreadExecutor(ThreadFactory var0) {
      return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(networkQueueCapacity), var0, new AbortPolicy());
   }

   public static ScheduledExecutorService newSingleThreadScheduledExecutor(ThreadFactory var0) {
      ScheduledThreadPoolExecutor var1 = new ScheduledThreadPoolExecutor(1, var0);
      var1.setRemoveOnCancelPolicy(true);
      return var1;
   }
}
