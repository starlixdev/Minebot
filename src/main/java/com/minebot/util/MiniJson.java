package com.minebot.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public final class MiniJson {
   private static final int MAX_DEPTH = 128;

   private MiniJson() {
   }

   public static Object parse(String var0) {
      if (var0 == null) {
         return null;
      } else {
         MiniJson.Parser var1 = new MiniJson.Parser(var0);
         Object var2 = var1.parseValue(0);
         var1.skipWs();
         if (!var1.end()) {
            throw new IllegalArgumentException("Unexpected trailing JSON at position " + var1.pos);
         } else {
            return var2;
         }
      }
   }

   public static Map<String, Object> object(String var0) {
      Object var1 = parse(var0);
      if (!(var1 instanceof Map)) {
         throw new IllegalArgumentException("Expected JSON object");
      } else {
         return (Map<String, Object>)var1;
      }
   }

   public static String stringify(Object var0) {
      StringBuilder var1 = new StringBuilder();
      write(var1, var0, 0, new IdentityHashMap<>());
      return var1.toString();
   }

   private static void write(StringBuilder var0, Object var1, int var2, IdentityHashMap<Object, Boolean> var3) {
      if (var2 > 128) {
         throw new IllegalArgumentException("JSON structure exceeds maximum nesting depth");
      } else if (var1 == null) {
         var0.append("null");
      } else if (var1 instanceof String var15) {
         quote(var0, var15);
      } else if (var1 instanceof Character var14) {
         quote(var0, String.valueOf(var14));
      } else if (var1 instanceof Boolean) {
         var0.append(var1);
      } else if (var1 instanceof Number var13) {
         if (!(var13 instanceof Double var18 && !Double.isFinite(var18)) && !(var13 instanceof Float var20 && !Float.isFinite(var20))) {
            var0.append(var13);
         } else {
            throw new IllegalArgumentException("JSON cannot encode NaN or Infinity");
         }
      } else if (var3.put(var1, Boolean.TRUE) != null) {
         throw new IllegalArgumentException("JSON structure contains a cycle");
      } else {
         try {
            if (var1 instanceof Map<?, ?> var12) {
               var0.append('{');
               boolean var17 = true;

               for (Entry var21 : var12.entrySet()) {
                  if (!var17) {
                     var0.append(',');
                  }

                  var17 = false;
                  quote(var0, String.valueOf(var21.getKey()));
                  var0.append(':');
                  write(var0, var21.getValue(), var2 + 1, var3);
               }

               var0.append('}');
               return;
            }

            if (!(var1 instanceof Iterable var4)) {
               if (var1.getClass().isArray()) {
                  var0.append('[');
                  int var11 = Array.getLength(var1);

                  for (int var16 = 0; var16 < var11; var16++) {
                     if (var16 > 0) {
                        var0.append(',');
                     }

                     write(var0, Array.get(var1, var16), var2 + 1, var3);
                  }

                  var0.append(']');
                  return;
               }

               quote(var0, String.valueOf(var1));
               return;
            }

            var0.append('[');
            boolean var5 = true;

            for (Object var7 : var4) {
               if (!var5) {
                  var0.append(',');
               }

               var5 = false;
               write(var0, var7, var2 + 1, var3);
            }

            var0.append(']');
         } finally {
            var3.remove(var1);
         }
      }
   }

   private static void quote(StringBuilder var0, String var1) {
      var0.append('"');

      for (int var2 = 0; var2 < var1.length(); var2++) {
         char var3 = var1.charAt(var2);
         switch (var3) {
            case '\b':
               var0.append("\\b");
               break;
            case '\t':
               var0.append("\\t");
               break;
            case '\n':
               var0.append("\\n");
               break;
            case '\f':
               var0.append("\\f");
               break;
            case '\r':
               var0.append("\\r");
               break;
            case '"':
               var0.append("\\\"");
               break;
            case '\\':
               var0.append("\\\\");
               break;
            default:
               if (var3 < ' ') {
                  var0.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(var3)));
               } else {
                  var0.append(var3);
               }
         }
      }

      var0.append('"');
   }

   private static final class Parser {
      private final String s;
      private int pos;

      private Parser(String var1) {
         this.s = var1;
      }

      private boolean end() {
         return this.pos >= this.s.length();
      }

      private void skipWs() {
         while (!this.end() && Character.isWhitespace(this.s.charAt(this.pos))) {
            this.pos++;
         }
      }

      private Object parseValue(int var1) {
         if (var1 > 128) {
            throw this.error("JSON exceeds maximum nesting depth");
         } else {
            this.skipWs();
            if (this.end()) {
               throw this.error("Unexpected end of JSON");
            } else {
               char var2 = this.s.charAt(this.pos);

               return switch (var2) {
                  case '"' -> this.parseString();
                  case '[' -> this.parseArray(var1 + 1);
                  case 'f' -> {
                     this.expect("false");
                     yield Boolean.FALSE;
                  }
                  case 'n' -> {
                     this.expect("null");
                     yield null;
                  }
                  case 't' -> {
                     this.expect("true");
                     yield Boolean.TRUE;
                  }
                  case '{' -> this.parseObject(var1 + 1);
                  default -> {
                     if (var2 != '-' && !Character.isDigit(var2)) {
                        throw this.error("Unexpected character '" + var2 + "'");
                     }

                     yield this.parseNumber();
                  }
               };
            }
         }
      }

      private Map<String, Object> parseObject(int var1) {
         LinkedHashMap var2 = new LinkedHashMap();
         this.pos++;
         this.skipWs();
         if (!this.end() && this.s.charAt(this.pos) == '}') {
            this.pos++;
            return var2;
         } else {
            while (true) {
               this.skipWs();
               if (!this.end() && this.s.charAt(this.pos) == '"') {
                  String var3 = this.parseString();
                  this.skipWs();
                  if (!this.end() && this.s.charAt(this.pos++) == ':') {
                     var2.put(var3, this.parseValue(var1));
                     this.skipWs();
                     if (this.end()) {
                        throw this.error("Unterminated object");
                     }

                     char var4 = this.s.charAt(this.pos++);
                     if (var4 == '}') {
                        return var2;
                     }

                     if (var4 != ',') {
                        throw this.error("Expected ',' or '}'");
                     }
                     continue;
                  }

                  throw this.error("Expected ':'");
               }

               throw this.error("Expected object key");
            }
         }
      }

      private List<Object> parseArray(int var1) {
         ArrayList var2 = new ArrayList();
         this.pos++;
         this.skipWs();
         if (!this.end() && this.s.charAt(this.pos) == ']') {
            this.pos++;
            return var2;
         } else {
            char var3;
            do {
               var2.add(this.parseValue(var1));
               this.skipWs();
               if (this.end()) {
                  throw this.error("Unterminated array");
               }

               var3 = this.s.charAt(this.pos++);
               if (var3 == ']') {
                  return var2;
               }
            } while (var3 == ',');

            throw this.error("Expected ',' or ']'");
         }
      }

      private String parseString() {
         if (this.s.charAt(this.pos++) != '"') {
            throw this.error("Expected string");
         } else {
            StringBuilder var1 = new StringBuilder();

            while (!this.end()) {
               char var2 = this.s.charAt(this.pos++);
               if (var2 == '"') {
                  return var1.toString();
               }

               if (var2 < ' ') {
                  throw this.error("Unescaped control character in string");
               }

               if (var2 != '\\') {
                  var1.append(var2);
               } else {
                  if (this.end()) {
                     throw this.error("Bad escape");
                  }

                  char var3 = this.s.charAt(this.pos++);
                  switch (var3) {
                     case '"':
                     case '/':
                     case '\\':
                        var1.append(var3);
                        break;
                     case 'b':
                        var1.append('\b');
                        break;
                     case 'f':
                        var1.append('\f');
                        break;
                     case 'n':
                        var1.append('\n');
                        break;
                     case 'r':
                        var1.append('\r');
                        break;
                     case 't':
                        var1.append('\t');
                        break;
                     case 'u':
                        if (this.pos + 4 > this.s.length()) {
                           throw this.error("Bad unicode escape");
                        }

                        String var4 = this.s.substring(this.pos, this.pos + 4);
                        this.pos += 4;

                        try {
                           var1.append((char)Integer.parseInt(var4, 16));
                           break;
                        } catch (NumberFormatException var6) {
                           throw this.error("Bad unicode escape");
                        }
                     default:
                        throw this.error("Bad escape '" + var3 + "'");
                  }
               }
            }

            throw this.error("Unterminated string");
         }
      }

      private Number parseNumber() {
         int var1 = this.pos;
         if (this.s.charAt(this.pos) == '-') {
            this.pos++;
            if (this.end()) {
               throw this.error("Bad number");
            }
         }

         if (this.s.charAt(this.pos) == '0') {
            this.pos++;
            if (!this.end() && Character.isDigit(this.s.charAt(this.pos))) {
               throw this.error("Leading zero in number");
            }
         } else {
            if (this.end() || !Character.isDigit(this.s.charAt(this.pos))) {
               throw this.error("Bad number");
            }

            while (!this.end() && Character.isDigit(this.s.charAt(this.pos))) {
               this.pos++;
            }
         }

         boolean var2 = false;
         if (!this.end() && this.s.charAt(this.pos) == '.') {
            var2 = true;
            this.pos++;
            int var3 = this.pos;

            while (!this.end() && Character.isDigit(this.s.charAt(this.pos))) {
               this.pos++;
            }

            if (var3 == this.pos) {
               throw this.error("Bad number fraction");
            }
         }

         if (!this.end() && (this.s.charAt(this.pos) == 'e' || this.s.charAt(this.pos) == 'E')) {
            var2 = true;
            this.pos++;
            if (!this.end() && (this.s.charAt(this.pos) == '+' || this.s.charAt(this.pos) == '-')) {
               this.pos++;
            }

            int var6 = this.pos;

            while (!this.end() && Character.isDigit(this.s.charAt(this.pos))) {
               this.pos++;
            }

            if (var6 == this.pos) {
               throw this.error("Bad number exponent");
            }
         }

         String var7 = this.s.substring(var1, this.pos);

         try {
            if (var2) {
               return Double.valueOf(Double.parseDouble(var7));
            }
            return Long.valueOf(Long.parseLong(var7));
         } catch (NumberFormatException var5) {
            throw this.error("Bad number");
         }
      }

      private void expect(String var1) {
         if (!this.s.startsWith(var1, this.pos)) {
            throw this.error("Expected " + var1);
         } else {
            this.pos = this.pos + var1.length();
         }
      }

      private IllegalArgumentException error(String var1) {
         return new IllegalArgumentException(var1 + " at position " + this.pos);
      }
   }
}
