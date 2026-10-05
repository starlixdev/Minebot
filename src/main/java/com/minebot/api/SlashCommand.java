package com.minebot.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public record SlashCommand(String name, String description, List<Map<String, Object>> options) {
   private static final Pattern NAME = Pattern.compile("^[a-z0-9_-]{1,32}$");

   public SlashCommand(String name, String description, List<Map<String, Object>> options) {
      if (name != null && NAME.matcher(name).matches()) {
         if (description != null && !description.isBlank() && description.length() <= 100) {
            options = validateOptions(options == null ? List.of() : options, 0);
            this.name = name;
            this.description = description;
            this.options = options;
         } else {
            throw new IllegalArgumentException("Slash command description must be 1-100 characters");
         }
      } else {
         throw new IllegalArgumentException("Slash command name must be 1-32 lowercase characters using letters, digits, '_' or '-'");
      }
   }

   public static SlashCommand simple(String var0, String var1) {
      return new SlashCommand(var0, var1, List.of());
   }

   public Map<String, Object> toDiscord() {
      LinkedHashMap var1 = new LinkedHashMap();
      var1.put("name", this.name);
      var1.put("description", this.description);
      var1.put("type", 1);
      if (!this.options.isEmpty()) {
         var1.put("options", this.options);
      }

      return Collections.unmodifiableMap(var1);
   }

   private static List<Map<String, Object>> validateOptions(List<Map<String, Object>> var0, int var1) {
      if (var0.size() > 25) {
         throw new IllegalArgumentException("Discord allows at most 25 options at each level");
      } else if (var1 > 2) {
         throw new IllegalArgumentException("Slash command option nesting is too deep");
      } else {
         ArrayList var2 = new ArrayList();

         for (Map var4 : var0) {
            if (var4 == null) {
               throw new IllegalArgumentException("Slash command option cannot be null");
            }

            Object var5 = var4.get("type");
            Object var6 = var4.get("name");
            Object var7 = var4.get("description");
            if (var5 instanceof Number var8 && var8.intValue() >= 1 && var8.intValue() <= 11) {
               if (var6 instanceof String var9 && NAME.matcher(var9).matches()) {
                  if (var7 instanceof String var10 && !var10.isBlank() && var10.length() <= 100) {
                     LinkedHashMap var11 = new LinkedHashMap(var4);
                     if (var11.get("options") instanceof List var13) {
                        ArrayList var14 = new ArrayList();

                        for (Object var16 : var13) {
                           if (!(var16 instanceof Map<?, ?> var17)) {
                              throw new IllegalArgumentException("Nested slash option must be an object");
                           }

                           LinkedHashMap var18 = new LinkedHashMap();

                           for (Entry var20 : var17.entrySet()) {
                              var18.put(String.valueOf(var20.getKey()), var20.getValue());
                           }

                           var14.add(var18);
                        }

                        var11.put("options", validateOptions(var14, var1 + 1));
                     }

                     var2.add(Collections.unmodifiableMap(var11));
                     continue;
                  }

                  throw new IllegalArgumentException("Slash command option description must be 1-100 characters");
               }

               throw new IllegalArgumentException("Slash command option name is invalid");
            }

            throw new IllegalArgumentException("Slash command option type must be between 1 and 11");
         }

         return Collections.unmodifiableList(var2);
      }
   }
}
