package com.minebot.command;

import com.minebot.bot.BotManager;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

public final class MineBotCommand implements TabExecutor {
   private final BotManager manager;

   public MineBotCommand(BotManager var1) {
      this.manager = var1;
   }

   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      try {
         if (var4.length == 0) {
            help(var1, var3);
            return true;
         } else {
            String var5 = var4[0].toLowerCase(Locale.ROOT);
            if (var5.equals("list")) {
               var1.sendMessage("Bots: " + String.join(", ", this.manager.names()));
               return true;
            } else if (var4.length < 2) {
               var1.sendMessage("Usage: /" + var3 + " <create|start|stop|reload|validate|status> <bot>");
               return true;
            } else {
               String var6 = var4[1];
               switch (var5) {
                  case "create":
                     var1.sendMessage("Created bot at " + this.manager.create(var6));
                     break;
                  case "start":
                     this.manager.start(var6);
                     var1.sendMessage("Started " + var6);
                     break;
                  case "stop":
                     this.manager.stop(var6);
                     var1.sendMessage("Stopped " + var6);
                     break;
                  case "reload":
                     this.manager.reload(var6);
                     var1.sendMessage("Reloaded " + var6);
                     break;
                  case "status":
                     var1.sendMessage(this.manager.status(var6));
                     break;
                  case "validate":
                     BotManager.Validation var9 = this.manager.validate(var6);
                     var1.sendMessage("Validation valid=" + var9.valid() + ", checks=" + var9.checks() + ", errors=" + var9.errors());
                     break;
                  default:
                     help(var1, var3);
               }

               return true;
            }
         }
      } catch (Exception var10) {
         var1.sendMessage("MineBOT error: " + var10.getMessage());
         return true;
      }
   }

   private static void help(CommandSender var0, String var1) {
      var0.sendMessage("/" + var1 + " <list|create|start|stop|reload|validate|status>");
   }

   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if (var4.length == 1) {
         return filter(List.of("list", "create", "start", "stop", "reload", "validate", "status"), var4[0]);
      } else {
         return var4.length == 2 && !var4[0].equalsIgnoreCase("create") ? filter(this.manager.names(), var4[1]) : List.of();
      }
   }

   private static List<String> filter(Collection<String> var0, String var1) {
      String var2 = var1.toLowerCase(Locale.ROOT);
      return var0.stream().filter(var1x -> var1x.toLowerCase(Locale.ROOT).startsWith(var2)).sorted().toList();
   }
}
