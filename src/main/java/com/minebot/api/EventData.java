package com.minebot.api;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class EventData {
   private final Map<String, Object> values;

   public EventData(Map<String, Object> var1) {
      this.values = freezeMap(var1 == null ? Map.of() : var1);
   }

   public Map<String, Object> asMap() {
      return this.values;
   }

   public Object get(String var1) {
      if (var1 != null && !var1.isEmpty()) {
         Object var2 = this.values;
         int var3 = 0;
         int var4 = var1.length();

         while (var3 < var4) {
            int var5 = var1.indexOf(46, var3);
            int var6 = var5 < 0 ? var4 : var5;
            if (!(var2 instanceof Map<?, ?> var7)) {
               return null;
            }

            var2 = var7.get(var1.substring(var3, var6));
            if (var5 < 0) {
               return var2;
            }

            var3 = var5 + 1;
         }

         return var2;
      } else {
         return null;
      }
   }

   public String string(String var1) {
      Object var2 = this.get(var1);
      return var2 == null ? null : String.valueOf(var2);
   }

   public boolean bool(String var1, boolean var2) {
      Object var3 = this.get(var1);
      return var3 instanceof Boolean var4 ? var4 : (var3 == null ? var2 : Boolean.parseBoolean(String.valueOf(var3)));
   }

   public long longValue(String var1, long var2) {
      Object var4 = this.get(var1);
      if (var4 instanceof Number var5) {
         return var5.longValue();
      } else {
         try {
            return var4 == null ? var2 : Long.parseLong(String.valueOf(var4));
         } catch (Exception var6) {
            return var2;
         }
      }
   }

   private static Map<String, Object> freezeMap(Map<?, ?> var0) {
      if (var0.isEmpty()) {
         return Map.of();
      } else {
         LinkedHashMap var1 = new LinkedHashMap(Math.max(16, var0.size() * 2));

         for (Entry var3 : var0.entrySet()) {
            var1.put(String.valueOf(var3.getKey()), freeze(var3.getValue()));
         }

         return Collections.unmodifiableMap(var1);
      }
   }

   private static Object freeze(Object var0) {
      if (var0 == null || var0 instanceof String || var0 instanceof Number || var0 instanceof Boolean || var0 instanceof Enum) {
         return var0;
      } else if (var0 instanceof Map<?, ?> var7) {
         return freezeMap(var7);
      } else if (var0 instanceof List var6) {
         if (var6.isEmpty()) {
            return List.of();
         } else {
            ArrayList var9 = new ArrayList(var6.size());

            for (Object var12 : var6) {
               var9.add(freeze(var12));
            }

            return Collections.unmodifiableList(var9);
         }
      } else if (var0 instanceof Set var5) {
         if (var5.isEmpty()) {
            return Set.of();
         } else {
            LinkedHashSet var8 = new LinkedHashSet(Math.max(16, var5.size() * 2));

            for (Object var4 : var5) {
               var8.add(freeze(var4));
            }

            return Collections.unmodifiableSet(var8);
         }
      } else if (!var0.getClass().isArray()) {
         return String.valueOf(var0);
      } else {
         int var1 = Array.getLength(var0);
         if (var1 == 0) {
            return List.of();
         } else {
            ArrayList var2 = new ArrayList(var1);

            for (int var3 = 0; var3 < var1; var3++) {
               var2.add(freeze(Array.get(var0, var3)));
            }

            return Collections.unmodifiableList(var2);
         }
      }
   }
}
