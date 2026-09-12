package dev.k1zik.service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;

public class AlertCooldownService {
   private final JavaPlugin plugin;
   private final Map<String, Long> lastAlertAt = new ConcurrentHashMap<>();

   public AlertCooldownService(JavaPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean tryAlert(UUID playerId, String channel) {
      int cooldownSeconds = this.plugin.getConfig().getInt("alerts.cooldown_seconds", 30);
      if (cooldownSeconds <= 0) {
         return true;
      }
      String key = playerId + ":" + channel.toLowerCase(Locale.ROOT);
      long now = System.currentTimeMillis();
      Long previous = this.lastAlertAt.get(key);
      if (previous != null && now - previous < cooldownSeconds * 1000L) {
         return false;
      }
      this.lastAlertAt.put(key, now);
      return true;
   }

   public void clear() {
      this.lastAlertAt.clear();
   }
}
