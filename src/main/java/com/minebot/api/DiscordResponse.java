package com.minebot.api;

import java.util.List;
import java.util.Map;

public record DiscordResponse(boolean ok, int status, String body, String error, Map<String, List<String>> headers) {
   public DiscordResponse(boolean ok, int status, String body, String error, Map<String, List<String>> headers) {
      headers = headers == null ? Map.of() : Map.copyOf(headers);
      this.ok = ok;
      this.status = status;
      this.body = body;
      this.error = error;
      this.headers = headers;
   }
}
