package com.minebot.api;

import com.minebot.http.HttpService;
import com.minebot.runtime.JavaBotRuntime;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class HttpApi {
   private final JavaBotRuntime runtime;

   HttpApi(JavaBotRuntime var1) {
      this.runtime = var1;
   }

   public CompletableFuture<HttpResponse> request(String var1, String var2, Map<String, List<String>> var3, String var4, Duration var5) {
      return this.runtime
         .httpService()
         .execute(new HttpService.Request(var1, URI.create(var2), var3 == null ? Map.of() : var3, var4, var5))
         .thenApply(this.runtime::httpResponse);
   }

   public CompletableFuture<HttpResponse> get(String var1) {
      return this.request("GET", var1, Map.of(), null, null);
   }
}
