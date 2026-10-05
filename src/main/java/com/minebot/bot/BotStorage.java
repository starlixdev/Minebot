package com.minebot.bot;

import com.minebot.util.MiniJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class BotStorage {
   private final Path file;
   private final Map<String, Object> values = new LinkedHashMap<>();

   public BotStorage(Path var1) throws IOException {
      this.file = var1.resolve("data.json");
      this.load();
   }

   public synchronized Object get(String var1) {
      return this.values.get(var1);
   }

   public synchronized Object getOrDefault(String var1, Object var2) {
      return this.values.getOrDefault(var1, var2);
   }

   public synchronized boolean contains(String var1) {
      return this.values.containsKey(var1);
   }

   public synchronized void set(String var1, Object var2) {
      LinkedHashMap var3 = new LinkedHashMap<>(this.values);
      if (var2 == null) {
         var3.remove(var1);
      } else {
         var3.put(var1, var2);
      }

      this.saveUnchecked(var3);
      this.replaceState(var3);
   }

   public synchronized void remove(String var1) {
      LinkedHashMap var2 = new LinkedHashMap<>(this.values);
      var2.remove(var1);
      this.saveUnchecked(var2);
      this.replaceState(var2);
   }

   public synchronized Map<String, Object> snapshot() {
      return new LinkedHashMap<>(this.values);
   }

   private void load() throws IOException {
      this.values.clear();
      if (Files.exists(this.file)) {
         String var1 = Files.readString(this.file, StandardCharsets.UTF_8);
         if (!var1.isBlank()) {
            Object var2;
            try {
               var2 = MiniJson.parse(var1);
            } catch (RuntimeException var6) {
               throw new IOException("Invalid JSON in " + this.file + ": " + var6.getMessage(), var6);
            }

            if (!(var2 instanceof Map<?, ?> var3)) {
               throw new IOException("Invalid storage root in " + this.file + ": expected a JSON object");
            } else {
               for (Entry var5 : var3.entrySet()) {
                  this.values.put(String.valueOf(var5.getKey()), var5.getValue());
               }
            }
         }
      }
   }

   private void replaceState(Map<String, Object> var1) {
      this.values.clear();
      this.values.putAll(var1);
   }

   private void saveUnchecked(Map<String, Object> var1) {
      try {
         Files.createDirectories(this.file.getParent());
         Path var2 = this.file.resolveSibling(this.file.getFileName() + ".tmp");
         Files.writeString(var2, MiniJson.stringify(var1), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

         try {
            Files.move(var2, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var4) {
            Files.move(var2, this.file, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException var5) {
         throw new IllegalStateException("Could not save bot storage " + this.file, var5);
      }
   }
}
