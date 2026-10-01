package dev.k1zik.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.k1zik.GriefScan;
import dev.k1zik.notify.DiscordWebhook;
import dev.k1zik.storage.ViolationDatabase;
import dev.k1zik.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ViolationLog {
   private final GriefScan plugin;
   private final String logFileName = "logs.log";
   private final Map<UUID, PlayerViolationStats> playerStats = new ConcurrentHashMap<>();
   private final Map<UUID, SessionSummary> sessionSummaries = new ConcurrentHashMap<>();
   private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
   private ViolationDatabase database;

   public ViolationLog(GriefScan plugin) {
      this.plugin = plugin;
      initializeDatabase();
      initializeLogFile();
   }

   private void initializeDatabase() {
      this.database = new ViolationDatabase(this.plugin.getLogger());
      try {
         this.database.open(this.plugin.getDataFolder());
         Map<UUID, ViolationDatabase.StoredPlayerStats> loaded = this.database.loadAll();
         for (Map.Entry<UUID, ViolationDatabase.StoredPlayerStats> entry : loaded.entrySet()) {
            ViolationDatabase.StoredPlayerStats stored = entry.getValue();
            PlayerViolationStats stats = new PlayerViolationStats(
                  stored.playerName(),
                  stored.totalViolations(),
                  stored.firstViolation(),
                  stored.lastViolation(),
                  stored.filterCounts());
            this.playerStats.put(entry.getKey(), stats);
         }
         this.plugin.getLogger().info("Loaded " + this.playerStats.size() + " players from violations.db");
      } catch (SQLException exception) {
         this.plugin.getLogger().severe("Failed to open violations DB: " + exception.getMessage());
         this.database = null;
      }
   }

   private void initializeLogFile() {
      File logFile = new File(this.plugin.getDataFolder(), this.logFileName);
      if (logFile.exists()) {
         return;
      }
      try {
         File parent = logFile.getParentFile();
         if (parent != null) {
            parent.mkdirs();
         }
         if (!logFile.createNewFile()) {
            return;
         }
         try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("==========================================");
            writer.println(this.plugin.getMessageService().raw("violations_log_title"));
            writer.println("Created: " + this.dateFormat.format(new Date()));
            writer.println("==========================================");
            writer.println();
         }
      } catch (IOException exception) {
         this.plugin.getLogger().severe("Failed to create log file: " + exception.getMessage());
      }
   }

   public void logViolation(Player player, String filterName, Location location) {
      if (!this.plugin.getConfig().getBoolean("actions.log_to_file", true)) {
         return;
      }
      if (this.plugin.getBypassService().isExempt(player)) {
         return;
      }

      UUID playerId = player.getUniqueId();
      String playerName = player.getName();
      String timestamp = this.dateFormat.format(new Date());
      PlayerViolationStats stats = this.playerStats.computeIfAbsent(
            playerId, ignored -> new PlayerViolationStats(playerName));
      stats.recordViolation(filterName);
      recordSession(playerId, playerName, filterName, location);

      String logEntry = String.format(
            "[%s] Player: %s (UUID: %s) | Filter: %s | Total violations: %d | Coordinates: %s",
            timestamp, playerName, playerId, filterName, stats.getTotalViolations(), formatCoordinates(location));
      writeToLogFileAsync(logEntry);
      persistAsync(playerId, stats, filterName, location);
      checkAutoBan(player, stats);
   }

   private void recordSession(UUID playerId, String playerName, String filterName, Location location) {
      SessionSummary summary = this.sessionSummaries.computeIfAbsent(
            playerId, ignored -> new SessionSummary(playerName));
      summary.record(filterName, location);
   }

   public SessionSummary getSessionSummary(UUID playerId) {
      return this.sessionSummaries.get(playerId);
   }

   public SessionSummary findSessionByName(String playerName) {
      for (SessionSummary summary : this.sessionSummaries.values()) {
         if (summary.playerName().equalsIgnoreCase(playerName)) {
            return summary;
         }
      }
      return null;
   }

   private void persistAsync(UUID playerId, PlayerViolationStats stats, String filterName, Location location) {
      if (this.database == null) {
         return;
      }
      SchedulerUtil.runAsync(this.plugin, () -> {
         try {
            this.database.upsertPlayerStats(
                  playerId,
                  stats.getPlayerName(),
                  stats.getTotalViolations(),
                  stats.getFirstViolation(),
                  stats.getLastViolation(),
                  stats.getFilterViolations());
            String world = location != null && location.getWorld() != null ? location.getWorld().getName() : null;
            Integer x = location != null ? location.getBlockX() : null;
            Integer y = location != null ? location.getBlockY() : null;
            Integer z = location != null ? location.getBlockZ() : null;
            this.database.insertEvent(playerId, stats.getPlayerName(), filterName, world, x, y, z, System.currentTimeMillis());
         } catch (SQLException exception) {
            this.plugin.getLogger().warning("Failed to persist violation: " + exception.getMessage());
         }
      });
   }

   private void checkAutoBan(Player player, PlayerViolationStats stats) {
      if (!this.plugin.getConfig().getBoolean("auto_ban.enabled", false)) {
         return;
      }
      int threshold = this.plugin.getConfig().getInt("auto_ban.violations_threshold", 3);
      if (stats.getTotalViolations() >= threshold) {
         executeAutoBan(player, stats);
      }
   }

   private void executeAutoBan(Player player, PlayerViolationStats stats) {
      String playerName = player.getName();
      UUID playerId = player.getUniqueId();
      String timestamp = this.dateFormat.format(new Date());
      String banReason = this.plugin.getConfig().getString(
            "auto_ban.ban_reason", "Automatic ban: repeated rule violations");
      String banCommand = this.plugin.getConfig()
            .getString("auto_ban.ban_command", "ban %player% %reason%")
            .replace("%player%", playerName)
            .replace("%reason%", banReason)
            .replace("%violations%", String.valueOf(stats.getTotalViolations()));

      writeToLogFileAsync(String.format(
            "[%s] BAN: %s (UUID: %s) | Violations: %d | Reason: %s",
            timestamp, playerName, playerId, stats.getTotalViolations(), banReason));

      SchedulerUtil.runGlobal(this.plugin, () -> {
         try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), banCommand);
            this.plugin.getLogger().warning(
                  "Automatic ban: " + playerName + " (" + stats.getTotalViolations() + " violations)");
         } catch (Exception exception) {
            this.plugin.getLogger().severe("Failed to execute ban command: " + exception.getMessage());
         }
      });

      if (this.plugin.getConfig().getBoolean("auto_ban.discord_alert", true)) {
         String discordMessage = this.plugin.getConfig()
               .getString("auto_ban.discord_message",
                     "🔨 **Automatic ban** Player %player% was banned for %violations% violations. Reason: %reason%")
               .replace("%player%", playerName)
               .replace("%violations%", String.valueOf(stats.getTotalViolations()))
               .replace("%reason%", banReason);
         DiscordWebhook.send(this.plugin, discordMessage);
      }

      clearPlayerStats(playerId);
   }

   private String formatCoordinates(Location location) {
      if (location == null) {
         return "unknown";
      }
      String worldName = location.getWorld() != null ? location.getWorld().getName() : "?";
      return String.format("%d %d %d (world: %s)",
            location.getBlockX(), location.getBlockY(), location.getBlockZ(), worldName);
   }

   private void writeToLogFileAsync(String message) {
      SchedulerUtil.runAsync(this.plugin, () -> writeToLogFile(message));
   }

   private void writeToLogFile(String message) {
      File logFile = new File(this.plugin.getDataFolder(), this.logFileName);
      try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
         writer.println(message);
      } catch (IOException exception) {
         this.plugin.getLogger().severe("Failed to write log file: " + exception.getMessage());
      }
   }

   public PlayerViolationStats getPlayerStats(UUID playerId) {
      return this.playerStats.get(playerId);
   }

   public Map<UUID, PlayerViolationStats> getAllStats() {
      return new HashMap<>(this.playerStats);
   }

   public void clearPlayerStats(UUID playerId) {
      this.playerStats.remove(playerId);
      this.sessionSummaries.remove(playerId);
      writeToLogFileAsync(String.format("[%s] Player stats %s cleared",
            this.dateFormat.format(new Date()), playerId));
      if (this.database != null) {
         SchedulerUtil.runAsync(this.plugin, () -> {
            try {
               this.database.deletePlayer(playerId);
            } catch (SQLException exception) {
               this.plugin.getLogger().warning("Failed to delete player from DB: " + exception.getMessage());
            }
         });
      }
   }

   public void clearAllStats() {
      this.playerStats.clear();
      this.sessionSummaries.clear();
      writeToLogFileAsync(String.format("[%s] All violation stats cleared",
            this.dateFormat.format(new Date())));
      if (this.database != null) {
         SchedulerUtil.runAsync(this.plugin, () -> {
            try {
               this.database.deleteAll();
            } catch (SQLException exception) {
               this.plugin.getLogger().warning("Failed to clear DB: " + exception.getMessage());
            }
         });
      }
   }

   public void close() {
      if (this.database != null) {
         this.database.close();
      }
   }

   public void generateReport() {
      String timestamp = this.dateFormat.format(new Date());
      StringBuilder report = new StringBuilder();
      report.append("\n").append("=".repeat(50)).append("\n");
      report.append("VIOLATIONS REPORT - ").append(timestamp).append("\n\n");

      if (this.playerStats.isEmpty()) {
         report.append("No violations recorded\n");
      } else {
         int totalViolations = this.playerStats.values().stream()
               .mapToInt(PlayerViolationStats::getTotalViolations).sum();
         report.append("Players with violations: ").append(this.playerStats.size()).append("\n");
         report.append("Total violations: ").append(totalViolations).append("\n\n");

         List<Map.Entry<UUID, PlayerViolationStats>> sortedStats = new ArrayList<>(this.playerStats.entrySet());
         sortedStats.sort((left, right) -> Integer.compare(
               right.getValue().getTotalViolations(), left.getValue().getTotalViolations()));

         report.append("Top offenders:\n");
         int limit = Math.min(10, sortedStats.size());
         for (int index = 0; index < limit; index++) {
            PlayerViolationStats stats = sortedStats.get(index).getValue();
            report.append(String.format("  %d. %s: %d violations\n",
                  index + 1, stats.getPlayerName(), stats.getTotalViolations()));
         }

         report.append("\nDetailed stats:\n");
         for (Map.Entry<UUID, PlayerViolationStats> entry : this.playerStats.entrySet()) {
            PlayerViolationStats stats = entry.getValue();
            report.append(String.format("%s (UUID: %s)\n", stats.getPlayerName(), entry.getKey()));
            report.append(String.format("  Total: %d | Filters: %s\n",
                  stats.getTotalViolations(), stats.getFilterViolations()));
         }
      }

      report.append("=".repeat(50)).append("\n");
      writeToLogFile(report.toString());
      this.plugin.getLogger().info("Violations report written to " + this.logFileName);
   }

   public static class PlayerViolationStats {
      private final String playerName;
      private final Map<String, Integer> filterViolations = new ConcurrentHashMap<>();
      private int totalViolations;
      private final long firstViolation;
      private long lastViolation;

      public PlayerViolationStats(String playerName) {
         this(playerName, 0, System.currentTimeMillis(), System.currentTimeMillis(), Map.of());
      }

      public PlayerViolationStats(
            String playerName,
            int totalViolations,
            long firstViolation,
            long lastViolation,
            Map<String, Integer> filterCounts) {
         this.playerName = playerName;
         this.totalViolations = totalViolations;
         this.firstViolation = firstViolation;
         this.lastViolation = lastViolation;
         if (filterCounts != null) {
            this.filterViolations.putAll(filterCounts);
         }
      }

      public synchronized void recordViolation(String filterName) {
         this.filterViolations.merge(filterName, 1, Integer::sum);
         this.totalViolations++;
         this.lastViolation = System.currentTimeMillis();
      }

      public String getPlayerName() {
         return this.playerName;
      }

      public synchronized int getTotalViolations() {
         return this.totalViolations;
      }

      public long getFirstViolation() {
         return this.firstViolation;
      }

      public long getLastViolation() {
         return this.lastViolation;
      }

      public Map<String, Integer> getFilterViolations() {
         return new HashMap<>(this.filterViolations);
      }
   }

   public static final class SessionSummary {
      private final String playerName;
      private final long sessionStart = System.currentTimeMillis();
      private final Map<String, Integer> filters = new ConcurrentHashMap<>();
      private Location lastLocation;
      private int total;

      private SessionSummary(String playerName) {
         this.playerName = playerName;
      }

      private void record(String filterName, Location location) {
         this.filters.merge(filterName, 1, Integer::sum);
         this.total++;
         if (location != null) {
            this.lastLocation = location.clone();
         }
      }

      public String playerName() {
         return playerName;
      }

      public long sessionStart() {
         return sessionStart;
      }

      public Map<String, Integer> filters() {
         return new HashMap<>(filters);
      }

      public Location lastLocation() {
         return lastLocation;
      }

      public int total() {
         return total;
      }
   }
}
