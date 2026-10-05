package com.minebot.api;

import com.minebot.runtime.JavaBotRuntime;
import java.util.Map;

public final class StorageApi {
   private final JavaBotRuntime runtime;

   StorageApi(JavaBotRuntime var1) {
      this.runtime = var1;
   }

   public Object get(String var1) {
      return this.runtime.storage().get(var1);
   }

   public Object getOrDefault(String var1, Object var2) {
      return this.runtime.storage().getOrDefault(var1, var2);
   }

   public boolean contains(String var1) {
      return this.runtime.storage().contains(var1);
   }

   public Map<String, Object> snapshot() {
      return this.runtime.storage().snapshot();
   }

   public void set(String var1, Object var2) {
      if (this.runtime.containsSecret(var2)) {
         throw new IllegalArgumentException("Persistent storage refuses configured secrets or the Discord token");
      } else {
         this.runtime.storage().set(var1, var2);
      }
   }

   public void delete(String var1) {
      this.runtime.storage().remove(var1);
   }
}
