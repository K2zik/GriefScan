package dev.k1zik.service;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BypassService {
   private final JavaPlugin plugin;

   public BypassService(JavaPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean isExempt(Player player) {
      if (player.hasPermission("griefscan.bypass")) {
         return true;
      }
      for (String name : this.plugin.getConfig().getStringList("exempt.players")) {
         if (player.getName().equalsIgnoreCase(name)) {
            return true;
         }
      }
      for (String uuidText : this.plugin.getConfig().getStringList("exempt.uuids")) {
         try {
            if (player.getUniqueId().equals(UUID.fromString(uuidText.trim()))) {
               return true;
            }
         } catch (IllegalArgumentException ignored) {
            // skip invalid UUID entries
         }
      }
      return false;
   }
}
