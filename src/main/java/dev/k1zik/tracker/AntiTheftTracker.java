package dev.k1zik.tracker;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import dev.k1zik.GriefScan;
import dev.k1zik.notify.DiscordNotifier;
import dev.k1zik.service.MessageService;
import dev.k1zik.util.LocationClusterUtil;
import dev.k1zik.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class AntiTheftTracker {
   private final GriefScan plugin;
   private final Map<UUID, Map<String, ItemStack[]>> inventorySnapshots = new ConcurrentHashMap<>();
   private final Map<UUID, List<TheftEvent>> theftHistory = new ConcurrentHashMap<>();

   private boolean enabled;
   private int minDistance;
   private int timeWindowMs;
   private int minSuspiciousEvents;
   private int minLocations;
   private double playtimeHoursLimit;
   private Set<Material> suspiciousItems = new HashSet<>();
   private boolean notifyAdmins;
   private boolean discordNotify;
   private String discordMessage;
   private boolean logActions;

   public AntiTheftTracker(GriefScan plugin) {
      this.plugin = plugin;
      loadConfig();
      plugin.getLogger().info("AntiTheftTracker initialized");
   }

   public void loadConfig() {
      this.enabled = this.plugin.getConfig().getBoolean("anti_theft.enabled", true);
      this.minDistance = this.plugin.getConfig().getInt("anti_theft.min_distance", 20);
      this.timeWindowMs = this.plugin.getConfig().getInt("anti_theft.time_window", 300) * 1000;
      this.minSuspiciousEvents = this.plugin.getConfig().getInt("anti_theft.min_containers", 3);
      this.minLocations = this.plugin.getConfig().getInt("anti_theft.min_locations", this.minSuspiciousEvents);
      if (this.minLocations < 2) {
         this.minLocations = 2;
      }

      this.notifyAdmins = this.plugin.getConfig().getBoolean("anti_theft.notify_admins", true);
      this.discordNotify = this.plugin.getConfig().getBoolean("anti_theft.discord_notify", true);
      this.discordMessage = this.plugin.getConfig().getString(
            "anti_theft.discord_message",
            "🚨 **Theft suspicion** Player %player% took %items% from %containers% containers across %locations% locations \\n📍 Last location: %world% %x% %y% %z%");

      String rawPlaytime = this.plugin.getConfig().getString("playtime", "30h").replace("h", "").trim();
      try {
         this.playtimeHoursLimit = Double.parseDouble(rawPlaytime);
      } catch (NumberFormatException ignored) {
         this.playtimeHoursLimit = 30.0;
         this.plugin.getLogger().warning("[AntiTheft] Invalid playtime format, using 30h");
      }

      this.logActions = this.plugin.getConfig().getBoolean("anti_theft.log_actions", false);
      this.suspiciousItems = new HashSet<>();
      for (String materialName : this.plugin.getConfig().getStringList("anti_theft.suspicious_items")) {
         Material material = Material.getMaterial(materialName.toUpperCase(Locale.ROOT));
         if (material != null) {
            this.suspiciousItems.add(material);
         } else {
            this.plugin.getLogger().warning("[AntiTheft] Unknown material in suspicious_items: " + materialName);
         }
      }

      if (this.enabled) {
         this.plugin.getLogger().info(String.format(
               "[AntiTheft] materials: %d | min distance: %d | window: %ds | min events: %d | min locations: %d | playtime: %.0fh | log: %s",
               this.suspiciousItems.size(),
               this.minDistance,
               this.timeWindowMs / 1000,
               this.minSuspiciousEvents,
               this.minLocations,
               this.playtimeHoursLimit,
               this.logActions ? "ON" : "OFF"));
      }
   }

   public void onContainerOpen(Player player, Location containerLocation) {
      if (!this.enabled || this.plugin.getBypassService().isExempt(player) || !checkPlaytime(player)) {
         return;
      }
      String locationKey = locationKey(containerLocation);
      ItemStack[] snapshot = copyInventory(player.getInventory());
      this.inventorySnapshots
            .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
            .put(locationKey, snapshot);
   }

   public void onContainerClose(Player player, Location containerLocation) {
      if (!this.enabled || this.plugin.getBypassService().isExempt(player) || !checkPlaytime(player)) {
         return;
      }

      UUID playerId = player.getUniqueId();
      Map<String, ItemStack[]> snapshots = this.inventorySnapshots.get(playerId);
      if (snapshots == null) {
         return;
      }

      String locationKey = locationKey(containerLocation);
      ItemStack[] before = snapshots.remove(locationKey);
      if (before == null) {
         return;
      }
      if (snapshots.isEmpty()) {
         this.inventorySnapshots.remove(playerId);
      }

      Map<Material, Integer> gainedItems = diffInventory(before, player.getInventory());
      Map<Material, Integer> suspiciousGained = new HashMap<>();
      for (Map.Entry<Material, Integer> entry : gainedItems.entrySet()) {
         if (this.suspiciousItems.contains(entry.getKey())) {
            suspiciousGained.put(entry.getKey(), entry.getValue());
         }
      }

      if (suspiciousGained.isEmpty()) {
         return;
      }

      if (this.logActions) {
         this.plugin.getLogger().info(String.format(
               "[AntiTheft] %s took: %s | %d,%d,%d",
               player.getName(),
               formatItems(suspiciousGained),
               containerLocation.getBlockX(),
               containerLocation.getBlockY(),
               containerLocation.getBlockZ()));
      }

      TheftEvent theftEvent = new TheftEvent(containerLocation, suspiciousGained);
      List<TheftEvent> history = this.theftHistory.computeIfAbsent(
            playerId, ignored -> Collections.synchronizedList(new ArrayList<>()));
      long now = System.currentTimeMillis();
      history.removeIf(event -> now - event.timestamp > this.timeWindowMs);
      history.add(theftEvent);
      checkForTheft(player, history);
   }

   private boolean checkPlaytime(Player player) {
      if (this.playtimeHoursLimit <= 0.0) {
         return true;
      }
      try {
         int playTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
         double playHours = playTicks / 20.0 / 60.0 / 60.0;
         return playHours < this.playtimeHoursLimit;
      } catch (Exception ignored) {
         return true;
      }
   }

   private void checkForTheft(Player player, List<TheftEvent> history) {
      if (history.size() < this.minSuspiciousEvents) {
         return;
      }

      List<org.bukkit.Location> locations = new ArrayList<>();
      for (TheftEvent event : history) {
         locations.add(event.location);
      }
      List<dev.k1zik.util.LocationClusterUtil.Cluster> clusters =
            dev.k1zik.util.LocationClusterUtil.buildClusters(locations, this.minDistance);
      if (clusters.size() < this.minLocations) {
         return;
      }
      if (!dev.k1zik.util.LocationClusterUtil.hasDistantPair(clusters, this.minDistance)) {
         return;
      }

      Map<Material, Integer> totalTaken = new HashMap<>();
      for (TheftEvent event : history) {
         event.takenItems.forEach((material, count) -> totalTaken.merge(material, count, Integer::sum));
      }

      Location lastLocation = history.get(history.size() - 1).location;
      triggerAlert(player, history, clusters.size(), totalTaken, lastLocation);
      this.theftHistory.remove(player.getUniqueId());
   }

   public void onStashDeposit(Player player, Location location, Map<Material, Integer> deposited) {
      if (!this.enabled || this.plugin.getBypassService().isExempt(player) || deposited.isEmpty()) {
         return;
      }
      if (!checkPlaytime(player)) {
         return;
      }
      Map<Material, Integer> suspicious = new HashMap<>();
      for (Map.Entry<Material, Integer> entry : deposited.entrySet()) {
         if (this.suspiciousItems.contains(entry.getKey())) {
            suspicious.put(entry.getKey(), entry.getValue());
         }
      }
      if (suspicious.isEmpty()) {
         return;
      }
      if (this.logActions) {
         this.plugin.getLogger().info("[AntiTheft] " + player.getName() + " stashed: " + formatItems(suspicious));
      }
      TheftEvent event = new TheftEvent(location, suspicious);
      List<TheftEvent> history = this.theftHistory.computeIfAbsent(
            player.getUniqueId(), ignored -> Collections.synchronizedList(new ArrayList<>()));
      long now = System.currentTimeMillis();
      history.removeIf(existing -> now - existing.timestamp > this.timeWindowMs);
      history.add(event);
      checkForTheft(player, history);
   }

   private void triggerAlert(
         Player player,
         List<TheftEvent> events,
         int locationCount,
         Map<Material, Integer> totalTaken,
         Location lastLocation) {
      String playerName = player.getName();
      String itemsText = formatItems(totalTaken);
      String x = lastLocation != null ? String.valueOf(lastLocation.getBlockX()) : "?";
      String y = lastLocation != null ? String.valueOf(lastLocation.getBlockY()) : "?";
      String z = lastLocation != null ? String.valueOf(lastLocation.getBlockZ()) : "?";
      String world = lastLocation != null && lastLocation.getWorld() != null
            ? lastLocation.getWorld().getName() : "?";

      MessageService messages = this.plugin.getMessageService();
      String adminMessage = messages.format(
            "antitheft_admin_alert",
            "player", playerName,
            "items", itemsText,
            "events", String.valueOf(events.size()),
            "locations", String.valueOf(locationCount),
            "world", world,
            "x", x,
            "y", y,
            "z", z);

      if (this.notifyAdmins) {
         for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("griefscan.admin")) {
               admin.sendMessage(adminMessage);
            }
         }
         this.plugin.getLogger().warning(messages.format(
               "antitheft_console_alert",
               "player", playerName,
               "events", String.valueOf(events.size()),
               "locations", String.valueOf(locationCount),
               "items", itemsText,
               "world", world,
               "x", x,
               "y", y,
               "z", z));
      }

      if (this.discordNotify && this.plugin.getAlertCooldownService().tryAlert(player.getUniqueId(), "discord-theft")) {
         String discordText = this.discordMessage
               .replace("%player%", playerName)
               .replace("%uuid%", player.getUniqueId().toString())
               .replace("%items%", itemsText)
               .replace("%containers%", String.valueOf(events.size()))
               .replace("%locations%", String.valueOf(locationCount))
               .replace("%world%", world)
               .replace("%x%", x)
               .replace("%y%", y)
               .replace("%z%", z);
         var integrations = this.plugin.getIntegrationService();
         DiscordNotifier.sendAlertEmbed(
               this.plugin,
               "GriefScan Theft Alert",
               discordText,
               DiscordNotifier.defaultActions(
                     integrations.kickCommand(player),
                     integrations.banCommand(player),
                     integrations.teleportCommand(player, lastLocation),
                     integrations.coreProtectLookup(player),
                     integrations.liteBansHistory(player)),
               true);
      }

      String reportText = buildReport(player, events, locationCount, totalTaken);
      dev.k1zik.util.SchedulerUtil.runAsync(this.plugin, () -> {
         try {
            File file = new File(this.plugin.getDataFolder(), "theft_reports.log");
            File parent = file.getParentFile();
            if (parent != null) {
               parent.mkdirs();
            }
            if (!file.exists() && !file.createNewFile()) {
               return;
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
               writer.println(reportText);
            }
         } catch (Exception exception) {
            this.plugin.getLogger().warning("[AntiTheft] Failed to write report: " + exception.getMessage());
         }
      });
   }

   private String buildReport(
         Player player,
         List<TheftEvent> events,
         int locationCount,
         Map<Material, Integer> totalTaken) {
      String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
      StringBuilder report = new StringBuilder();
      report.append("=".repeat(50)).append("\n");
      report.append("THEFT REPORT - ").append(timestamp).append("\n");
      report.append("Player: ").append(player.getName())
            .append(" (UUID: ").append(player.getUniqueId()).append(")\n");
      report.append("Events: ").append(events.size())
            .append(" | Locations: ").append(locationCount).append("\n");
      report.append("Taken: ").append(formatItems(totalTaken)).append("\n");
      report.append("Event locations:\n");
      for (TheftEvent event : events) {
         report.append("  - ")
               .append(event.location.getBlockX()).append(",")
               .append(event.location.getBlockY()).append(",")
               .append(event.location.getBlockZ())
               .append(" | ").append(formatItems(event.takenItems)).append("\n");
      }
      report.append("=".repeat(50));
      return report.toString();
   }

   private ItemStack[] copyInventory(PlayerInventory inventory) {
      ItemStack[] contents = inventory.getStorageContents();
      ItemStack[] copy = new ItemStack[contents.length];
      for (int index = 0; index < contents.length; index++) {
         copy[index] = contents[index] != null ? contents[index].clone() : null;
      }
      return copy;
   }

   private Map<Material, Integer> diffInventory(ItemStack[] before, PlayerInventory after) {
      Map<Material, Integer> countBefore = countItems(before);
      Map<Material, Integer> countAfter = countItems(after.getStorageContents());
      Map<Material, Integer> gained = new HashMap<>();
      for (Map.Entry<Material, Integer> entry : countAfter.entrySet()) {
         int difference = entry.getValue() - countBefore.getOrDefault(entry.getKey(), 0);
         if (difference > 0) {
            gained.put(entry.getKey(), difference);
         }
      }
      return gained;
   }

   private Map<Material, Integer> countItems(ItemStack[] items) {
      Map<Material, Integer> counts = new HashMap<>();
      for (ItemStack item : items) {
         if (item != null && item.getType() != Material.AIR) {
            counts.merge(item.getType(), item.getAmount(), Integer::sum);
         }
      }
      return counts;
   }

   private String formatItems(Map<Material, Integer> items) {
      return items.entrySet().stream()
            .map(entry -> entry.getKey().name() + " x" + entry.getValue())
            .collect(Collectors.joining(", "));
   }

   private String locationKey(Location location) {
      String worldName = location.getWorld() != null ? location.getWorld().getName() : "?";
      return worldName + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
   }

   public void reloadConfig() {
      loadConfig();
   }

   public void clearPlayerHistory(UUID playerId) {
      this.theftHistory.remove(playerId);
      this.inventorySnapshots.remove(playerId);
   }

   public void clearAllHistory() {
      this.theftHistory.clear();
      this.inventorySnapshots.clear();
   }

   private static class TheftEvent {
      private final Location location;
      private final Map<Material, Integer> takenItems;
      private final long timestamp;

      private TheftEvent(Location location, Map<Material, Integer> takenItems) {
         this.location = location;
         this.takenItems = new HashMap<>(takenItems);
         this.timestamp = System.currentTimeMillis();
      }
   }
}
