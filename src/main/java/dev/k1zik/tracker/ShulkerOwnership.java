package dev.k1zik.tracker;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.k1zik.GriefScan;
import dev.k1zik.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

public class ShulkerOwnership {
   private final GriefScan plugin;
   private final Map<String, UUID> ownership = new ConcurrentHashMap<>();
   private final File dataFile;

   public ShulkerOwnership(GriefScan plugin) {
      this.plugin = plugin;
      this.dataFile = new File(plugin.getDataFolder(), "shulker_owners.yml");
      load();
   }

   public void registerOwner(Location location, UUID ownerId) {
      if (location == null || ownerId == null) {
         return;
      }
      this.ownership.put(locationKey(location), ownerId);
      saveAsync();
   }

   public void removeOwner(Location location) {
      if (location == null) {
         return;
      }
      if (this.ownership.remove(locationKey(location)) != null) {
         saveAsync();
      }
   }

   public boolean isOwner(Location location, UUID playerId) {
      if (location == null || playerId == null) {
         return false;
      }
      UUID ownerId = this.ownership.get(locationKey(location));
      return ownerId != null && ownerId.equals(playerId);
   }

   public boolean isTracked(Location location) {
      return location != null && this.ownership.containsKey(locationKey(location));
   }

   public int size() {
      return this.ownership.size();
   }

   public void clearAll() {
      this.ownership.clear();
      saveAsync();
   }

   private String locationKey(Location location) {
      String worldName = location.getWorld() != null ? location.getWorld().getName() : "?";
      return worldName + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
   }

   private synchronized void load() {
      if (!this.dataFile.exists()) {
         return;
      }

      try {
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.dataFile);
         for (String key : yaml.getKeys(false)) {
            String uuidText = yaml.getString(key);
            if (uuidText == null || uuidText.isEmpty()) {
               continue;
            }
            try {
               this.ownership.put(key, UUID.fromString(uuidText));
            } catch (IllegalArgumentException ignored) {
               this.plugin.getLogger().warning("[ShulkerOwnership] Invalid UUID entry: " + key);
            }
         }
         this.plugin.getLogger().info("[ShulkerOwnership] Loaded entries: " + this.ownership.size());
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[ShulkerOwnership] Load error: " + exception.getMessage());
      }
   }

   private void saveAsync() {
      try {
         SchedulerUtil.runAsync(this.plugin, this::saveSync);
      } catch (Throwable ignored) {
         saveSync();
      }
   }

   public synchronized void saveSync() {
      try {
         File parent = this.dataFile.getParentFile();
         if (parent != null && !parent.exists()) {
            parent.mkdirs();
         }

         YamlConfiguration yaml = new YamlConfiguration();
         for (Map.Entry<String, UUID> entry : this.ownership.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue().toString());
         }
         yaml.save(this.dataFile);
      } catch (IOException exception) {
         this.plugin.getLogger().warning("[ShulkerOwnership] Save error: " + exception.getMessage());
      }
   }
}
