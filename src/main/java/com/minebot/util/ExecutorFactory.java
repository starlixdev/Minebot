package com.minebot.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;

public final class ExecutorFactory {
   private ExecutorFactory() {
      throw new AssertionError();
   }

   public static ExecutorService newBoundedSingleThreadExecutor(ThreadFactory var0) {
      return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256), var0, new CallerRunsPolicy());
   }

   public static ExecutorService newBoundedFixedThreadPool(int var0, ThreadFactory var1) {
      int var2 = Math.max(128, Math.min(1024, var0 * 128));
      return new ThreadPoolExecutor(var0, var0, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(var2), var1, new CallerRunsPolicy());
   }
}
