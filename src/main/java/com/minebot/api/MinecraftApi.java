package com.minebot.api;

import com.minebot.runtime.JavaBotRuntime;
import java.util.Map;

public final class MinecraftApi {
   private final JavaBotRuntime runtime;

   MinecraftApi(JavaBotRuntime var1) {
      this.runtime = var1;
   }

   public Map<String, Object> snapshot() {
      return this.runtime.serverSnapshot();
   }

   public void command(String var1) {
      this.runtime.minecraftCommand(var1);
   }

   public void broadcast(String var1) {
      this.runtime.minecraftBroadcast(var1);
   }
}
