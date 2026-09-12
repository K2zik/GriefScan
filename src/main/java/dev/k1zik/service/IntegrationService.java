package dev.k1zik.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class IntegrationService {
   private final JavaPlugin plugin;

   public IntegrationService(JavaPlugin plugin) {
      this.plugin = plugin;
   }

   public String coreProtectLookup(Player player) {
      String template = this.plugin.getConfig().getString(
            "integrations.coreprotect_lookup",
            "/co lookup %player% time:1d");
      return apply(template, player, null);
   }

   public String liteBansHistory(Player player) {
      String template = this.plugin.getConfig().getString(
            "integrations.litebans_history",
            "/litebans:history %player%");
      return apply(template, player, null);
   }

   public String teleportCommand(Player player, Location location) {
      String template = this.plugin.getConfig().getString(
            "integrations.teleport_command",
            "/tp @s %x% %y% %z%");
      return apply(template, player, location);
   }

   public String kickCommand(Player player) {
      return apply(this.plugin.getConfig().getString("integrations.kick_command", "/kick %player%"), player, null);
   }

   public String banCommand(Player player) {
      return apply(this.plugin.getConfig().getString("integrations.ban_command", "/ban %player%"), player, null);
   }

   private String apply(String template, Player player, Location location) {
      String result = template
            .replace("%player%", player.getName())
            .replace("%uuid%", player.getUniqueId().toString());
      if (location != null) {
         String world = location.getWorld() != null ? location.getWorld().getName() : "world";
         result = result
               .replace("%world%", world)
               .replace("%x%", String.valueOf(location.getBlockX()))
               .replace("%y%", String.valueOf(location.getBlockY()))
               .replace("%z%", String.valueOf(location.getBlockZ()));
      }
      return result;
   }
}
