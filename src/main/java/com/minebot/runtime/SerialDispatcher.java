package com.minebot.runtime;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

final class SerialDispatcher {
   private static final int BATCH_SIZE = 64;
   private final ThreadPoolExecutor workers;
   private final int capacity;
   private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
   private boolean scheduled;
   private boolean closed;

   SerialDispatcher(RuntimeExecutors var1, int var2) {
      this.workers = var1.workers();
      this.capacity = Math.max(16, var2);
   }

   boolean execute(Runnable var1) {
      synchronized (this.queue) {
         if (this.closed || this.queue.size() >= this.capacity) {
            return false;
         }

         this.queue.addLast(var1);
         if (this.scheduled) {
            return true;
         }

         this.scheduled = true;
      }

      try {
         this.workers.execute(this::drain);
         return true;
      } catch (RejectedExecutionException var6) {
         synchronized (this.queue) {
            this.scheduled = false;
            this.queue.removeLastOccurrence(var1);
            return false;
         }
      }
   }

   private void drain() {
      int var1 = 0;

      while (true) {
         Runnable var2;
         synchronized (this.queue) {
            var2 = this.queue.pollFirst();
            if (var2 == null) {
               this.scheduled = false;
               return;
            }
         }

         try {
            var2.run();
         } catch (Throwable var9) {
            Thread var4 = Thread.currentThread();
            UncaughtExceptionHandler var5 = var4.getUncaughtExceptionHandler();
            if (var5 != null) {
               var5.uncaughtException(var4, var9);
            }
         }

         if (++var1 >= 64) {
            synchronized (this.queue) {
               if (this.queue.isEmpty()) {
                  this.scheduled = false;
                  return;
               }
            }

            try {
               this.workers.execute(this::drain);
               return;
            } catch (RejectedExecutionException var7) {
               var1 = 0;
            }
         }
      }
   }

   int pending() {
      synchronized (this.queue) {
         return this.queue.size();
      }
   }

   void clear() {
      synchronized (this.queue) {
         this.queue.clear();
      }
   }

   void close() {
      synchronized (this.queue) {
         this.closed = true;
         this.queue.clear();
      }
   }
}
