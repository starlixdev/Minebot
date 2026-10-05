package com.minebot.security;

import com.minebot.util.MiniJson;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;

public final class SecretStore {
   private final Path file;
   private volatile Map<String, String> values = Map.of();
   private volatile List<String> redactionValues = List.of();

   public SecretStore(Path var1) {
      this.file = Objects.requireNonNull(var1, "file");
   }

   public synchronized void ensureFile() throws IOException {
      Files.createDirectories(this.file.getParent());
      if (!Files.exists(this.file)) {
         Files.writeString(this.file, "secrets:\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);

         try {
            Files.setPosixFilePermissions(this.file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
         } catch (IOException | UnsupportedOperationException var2) {
         }
      }
   }

   public synchronized void reload() throws IOException {
      this.ensureFile();
      LinkedHashMap<String, String> var1 = new LinkedHashMap<>();
      String var2 = "";
      List var3 = Files.readAllLines(this.file, StandardCharsets.UTF_8);

      for (int var4 = 0; var4 < var3.size(); var4++) {
         String var5 = stripComment((String)var3.get(var4));
         if (!var5.isBlank()) {
            if (var5.indexOf(9) >= 0) {
               throw new IOException("secrets.yml line " + (var4 + 1) + ": tabs are not allowed");
            }

            int var6 = countIndent(var5);
            String var7 = var5.trim();
            int var8 = findColon(var7);
            if (var8 < 0) {
               throw new IOException("secrets.yml line " + (var4 + 1) + ": expected KEY: VALUE");
            }

            String var9 = var7.substring(0, var8).trim();
            String var10 = var7.substring(var8 + 1).trim();
            if (var6 == 0 && var10.isEmpty()) {
               var2 = var9;
            } else {
               if (var6 == 0) {
                  var2 = "";
               }

               if (var2.isEmpty() || var2.equals("secrets")) {
                  validateName(var9, var4 + 1);
                  if (var1.containsKey(var9)) {
                     throw new IOException("secrets.yml line " + (var4 + 1) + ": duplicate secret '" + var9 + "'");
                  }

                  String var11 = resolveValue(unquote(var10, var4 + 1));
                  if (!var11.isEmpty()) {
                     var1.put(var9, var11);
                  }
               }
            }
         }
      }

      this.values = Collections.unmodifiableMap(var1);
      LinkedHashSet<String> var12 = new LinkedHashSet<>();

      for (String var15 : var1.values()) {
         var12.addAll(redactionVariants(var15));
      }

      ArrayList<String> var14 = new ArrayList<>(var12);
      var14.removeIf(String::isBlank);
      var14.sort(Comparator.comparingInt(String::length).reversed());
      this.redactionValues = List.copyOf(var14);
   }

   public boolean contains(String var1) {
      return this.values.containsKey(var1);
   }

   public String getRequired(String var1) {
      String var2 = this.values.get(var1);
      if (var2 != null && !var2.isEmpty()) {
         return var2;
      } else {
         throw new IllegalArgumentException("Secret '" + var1 + "' is not configured");
      }
   }

   public Set<String> names() {
      return this.values.keySet();
   }

   public String redact(String var1, String... var2) {
      if (var1 != null && !var1.isEmpty()) {
         String var3 = var1;

         for (String var5 : this.redactionValues) {
            var3 = replaceSecret(var3, var5);
         }

         if (var2 != null) {
            LinkedHashSet<String> var9 = new LinkedHashSet<>();

            for (String var8 : var2) {
               var9.addAll(redactionVariants(var8));
            }

            ArrayList<String> var11 = new ArrayList<>(var9);
            var11.sort(Comparator.comparingInt(String::length).reversed());

            for (String var13 : var11) {
               var3 = replaceSecret(var3, var13);
            }
         }

         return var3;
      } else {
         return var1 == null ? "" : var1;
      }
   }

   public boolean containsSecret(Object var1, String... var2) {
      return this.containsSecret(var1, Collections.newSetFromMap(new IdentityHashMap<>()), var2);
   }

   public Object redactedCopy(Object var1, String... var2) {
      return this.redactedCopy(var1, new IdentityHashMap<>(), var2);
   }

   private boolean containsSecret(Object var1, Set<Object> var2, String... var3) {
      if (var1 == null) {
         return false;
      } else if (var1 instanceof CharSequence var13) {
         return this.isSensitiveText(var13.toString(), var3);
      } else if (var1 instanceof Number || var1 instanceof Boolean || var1 instanceof Character || var1 instanceof Enum) {
         return false;
      } else if (!var2.add(var1)) {
         return false;
      } else {
         try {
            if (var1 instanceof Map<?, ?> var4) {
               for (Entry var18 : var4.entrySet()) {
                  if (this.containsSecret(String.valueOf(var18.getKey()), var2, var3) || this.containsSecret(var18.getValue(), var2, var3)) {
                     return true;
                  }
               }
            } else if (var1 instanceof Iterable) {
               for (Object var17 : (Iterable)var1) {
                  if (this.containsSecret(var17, var2, var3)) {
                     return true;
                  }
               }
            } else {
               if (!var1.getClass().isArray()) {
                  return this.isSensitiveText(String.valueOf(var1), var3);
               }

               int var6 = Array.getLength(var1);

               for (int var7 = 0; var7 < var6; var7++) {
                  if (this.containsSecret(Array.get(var1, var7), var2, var3)) {
                     return true;
                  }
               }
            }

            return false;
         } finally {
            var2.remove(var1);
         }
      }
   }

   private Object redactedCopy(Object var1, IdentityHashMap<Object, Object> var2, String... var3) {
      if (var1 == null) {
         return null;
      } else if (var1 instanceof String var10) {
         return this.redact(var10, var3);
      } else if (var1 instanceof CharSequence var9) {
         return this.redact(var9.toString(), var3);
      } else if (!(var1 instanceof Number) && !(var1 instanceof Boolean)) {
         Object var4 = var2.get(var1);
         if (var4 != null) {
            return "[CYCLE]";
         } else if (var1 instanceof Map<?, ?> var12) {
            LinkedHashMap var14 = new LinkedHashMap();
            var2.put(var1, var14);

            for (Entry var17 : var12.entrySet()) {
               var14.put(this.redact(String.valueOf(var17.getKey()), var3), this.redactedCopy(var17.getValue(), var2, var3));
            }

            var2.remove(var1);
            return var14;
         } else if (var1 instanceof Iterable var11) {
            ArrayList var13 = new ArrayList();
            var2.put(var1, var13);

            for (Object var8 : var11) {
               var13.add(this.redactedCopy(var8, var2, var3));
            }

            var2.remove(var1);
            return var13;
         } else if (!var1.getClass().isArray()) {
            return this.redact(String.valueOf(var1), var3);
         } else {
            ArrayList var5 = new ArrayList();
            var2.put(var1, var5);
            int var6 = Array.getLength(var1);

            for (int var7 = 0; var7 < var6; var7++) {
               var5.add(this.redactedCopy(Array.get(var1, var7), var2, var3));
            }

            var2.remove(var1);
            return var5;
         }
      } else {
         return var1;
      }
   }

   private boolean isSensitiveText(String var1, String... var2) {
      if (var1 != null && !var1.isEmpty()) {
         for (String var4 : this.redactionValues) {
            if (!var4.isEmpty() && var1.contains(var4)) {
               return true;
            }
         }

         if (var2 != null) {
            for (String var6 : var2) {
               for (String var8 : redactionVariants(var6)) {
                  if (var1.contains(var8)) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static List<String> redactionVariants(String var0) {
      if (var0 != null && !var0.isBlank()) {
         LinkedHashSet<String> var1 = new LinkedHashSet<>();
         var1.add(var0);
         String var2 = MiniJson.stringify(var0);
         if (var2.length() >= 2) {
            var1.add(var2.substring(1, var2.length() - 1));
         }

         String var3 = URLEncoder.encode(var0, StandardCharsets.UTF_8);
         var1.add(var3);
         var1.add(var3.replace("+", "%20"));
         var1.removeIf(String::isBlank);
         return List.copyOf(var1);
      } else {
         return List.of();
      }
   }

   private static String replaceSecret(String var0, String var1) {
      return var1 != null && !var1.isEmpty() ? var0.replace(var1, "[REDACTED]") : var0;
   }

   private static String resolveValue(String var0) {
      String var1 = var0.trim();
      if (var1.startsWith("${ENV:") && var1.endsWith("}")) {
         String var2 = var1.substring(6, var1.length() - 1);
         return Objects.requireNonNullElse(System.getenv(var2), "");
      } else {
         return var0;
      }
   }

   private static void validateName(String var0, int var1) throws IOException {
      if (!var0.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) {
         throw new IOException("secrets.yml line " + var1 + ": invalid secret name '" + var0 + "'");
      }
   }

   private static int countIndent(String var0) {
      int var1 = 0;

      while (var1 < var0.length() && var0.charAt(var1) == ' ') {
         var1++;
      }

      return var1;
   }

   private static int findColon(String var0) {
      boolean var1 = false;
      char var2 = 0;
      boolean var3 = false;

      for (int var4 = 0; var4 < var0.length(); var4++) {
         char var5 = var0.charAt(var4);
         if (var3) {
            var3 = false;
         } else if (var5 == '\\' && var1 && var2 == '"') {
            var3 = true;
         } else if (var1) {
            if (var5 == var2) {
               var1 = false;
            }
         } else if (var5 != '"' && var5 != '\'') {
            if (var5 == ':') {
               return var4;
            }
         } else {
            var1 = true;
            var2 = var5;
         }
      }

      return -1;
   }

   private static String stripComment(String var0) {
      boolean var1 = false;
      char var2 = 0;
      boolean var3 = false;

      for (int var4 = 0; var4 < var0.length(); var4++) {
         char var5 = var0.charAt(var4);
         if (var3) {
            var3 = false;
         } else if (var5 == '\\' && var1 && var2 == '"') {
            var3 = true;
         } else if (var1) {
            if (var5 == var2) {
               var1 = false;
            }
         } else if (var5 != '"' && var5 != '\'') {
            if (var5 == '#') {
               return var0.substring(0, var4);
            }
         } else {
            var1 = true;
            var2 = var5;
         }
      }

      return var0;
   }

   private static String unquote(String var0, int var1) throws IOException {
      if (var0.isEmpty()) {
         return "";
      } else if (var0.charAt(0) == '\'') {
         if (var0.length() >= 2 && var0.charAt(var0.length() - 1) == '\'') {
            return var0.substring(1, var0.length() - 1).replace("''", "'");
         } else {
            throw new IOException("secrets.yml line " + var1 + ": unterminated quoted value");
         }
      } else if (var0.charAt(0) != '"') {
         return var0;
      } else if (var0.length() >= 2 && var0.charAt(var0.length() - 1) == '"') {
         String var2 = var0.substring(1, var0.length() - 1);
         StringBuilder var3 = new StringBuilder();
         boolean var4 = false;

         for (int var5 = 0; var5 < var2.length(); var5++) {
            char var6 = var2.charAt(var5);
            if (!var4 && var6 == '\\') {
               var4 = true;
            } else if (!var4) {
               var3.append(var6);
            } else {
               switch (var6) {
                  case '"':
                     var3.append('"');
                     break;
                  case '\\':
                     var3.append('\\');
                     break;
                  case 'n':
                     var3.append('\n');
                     break;
                  case 'r':
                     var3.append('\r');
                     break;
                  case 't':
                     var3.append('\t');
                     break;
                  default:
                     var3.append(var6);
               }

               var4 = false;
            }
         }

         if (var4) {
            var3.append('\\');
         }

         return var3.toString();
      } else {
         throw new IOException("secrets.yml line " + var1 + ": unterminated quoted value");
      }
   }
}
