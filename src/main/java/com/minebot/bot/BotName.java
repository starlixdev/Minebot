package com.minebot.bot;

import java.nio.file.Path;
import java.util.regex.Pattern;

public final class BotName {
   private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_-]{1,48}$");

   private BotName() {
   }

   public static String validate(String var0) {
      if (var0 != null && VALID.matcher(var0).matches()) {
         return var0;
      } else {
         throw new IllegalArgumentException("Bot identifier must be 1-48 characters using letters, digits, '_' or '-'");
      }
   }

   public static Path resolve(Path var0, String var1) {
      validate(var1);
      Path var2 = var0.toAbsolutePath().normalize();
      Path var3 = var2.resolve(var1).normalize();
      if (var3.isAbsolute() && !var3.startsWith(var2)) {
         throw new IllegalArgumentException("Bot path escapes the Bots directory");
      } else if (!var3.startsWith(var2)) {
         throw new IllegalArgumentException("Bot path escapes the Bots directory");
      } else {
         return var3;
      }
   }
}
