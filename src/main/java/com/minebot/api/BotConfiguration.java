package com.minebot.api;

import java.util.Objects;

public record BotConfiguration(String name, boolean enabled, long intents, String activityType, String activityText, boolean autoRegisterSlash) {
   public BotConfiguration(String name, boolean enabled, long intents, String activityType, String activityText, boolean autoRegisterSlash) {
      Objects.requireNonNull(name);
      activityType = activityType == null ? "playing" : activityType;
      activityText = activityText == null ? "" : activityText;
      this.name = name;
      this.enabled = enabled;
      this.intents = intents;
      this.activityType = activityType;
      this.activityText = activityText;
      this.autoRegisterSlash = autoRegisterSlash;
   }
}
