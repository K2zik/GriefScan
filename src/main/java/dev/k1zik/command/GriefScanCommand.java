package dev.k1zik.command;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import dev.k1zik.GriefScan;
import dev.k1zik.filter.ScanFilter;
import dev.k1zik.logging.ViolationLog;
import dev.k1zik.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

public class GriefScanCommand implements CommandExecutor, TabCompleter {
   private final GriefScan plugin;

   public GriefScanCommand(GriefScan plugin) {
      this.plugin = plugin;
   }

   private MessageService msg() {
      return this.plugin.getMessageService();
   }

   @Override
   public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
      if (!sender.hasPermission("griefscan.admin")) {
         sender.sendMessage(this.msg().msg("no_permission"));
         return true;
      }
      if (args.length == 0) {
         this.help(sender);
         return true;
      }

      switch (args[0].toLowerCase()) {
         case "reload" -> {
            this.plugin.reloadSystem();
            sender.sendMessage(this.msg().msg("reloaded"));
         }
         case "status" -> this.handleStatus(sender);
         case "toggle" -> this.handleToggle(sender, args);
         case "logs" -> this.handleLogs(sender, args);
         case "antitheft" -> this.handleAntiTheft(sender, args);
         case "language", "lang" -> this.handleLanguage(sender, args);
         case "inspect" -> this.handleInspect(sender, args);
         default -> this.help(sender);
      }
      return true;
   }

   private void handleStatus(CommandSender sender) {
      sender.sendMessage(this.msg().msg("status_filters_header"));
      for (ScanFilter filter : this.plugin.getFilterRegistry().getFilters().values()) {
         String worldsInfo = "";
         if (!filter.getWorlds().isEmpty()) {
            worldsInfo = this.msg().format("status_worlds", "worlds", String.join(", ", filter.getWorlds()));
         }
         sender.sendMessage(this.msg().format("status_filter_line",
               "filter", filter.getName(),
               "state", filter.isEnabled() ? this.msg().msg("state_on") : this.msg().msg("state_off"),
               "worlds", worldsInfo));
      }

      sender.sendMessage(this.msg().msg("status_logs_header"));
      boolean logging = this.plugin.getConfig().getBoolean("actions.log_to_file", true);
      sender.sendMessage(this.msg().format("status_logging",
            "state", logging ? this.msg().msg("state_on") : this.msg().msg("state_off")));
      if (this.plugin.getViolationLog() != null) {
         int totalPlayers = this.plugin.getViolationLog().getAllStats().size();
         int totalViolations = this.plugin.getViolationLog().getAllStats().values().stream()
               .mapToInt(ViolationLog.PlayerViolationStats::getTotalViolations).sum();
         sender.sendMessage(this.msg().format("status_players", "count", String.valueOf(totalPlayers)));
         sender.sendMessage(this.msg().format("status_violations", "count", String.valueOf(totalViolations)));
      }
   }

   private void handleToggle(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(this.msg().msg("toggle_usage"));
         return;
      }

      String filterArg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
      if (!this.plugin.getFilterRegistry().hasFilter(filterArg)) {
         sender.sendMessage(this.msg().format("toggle_not_found", "filter", filterArg));
         sender.sendMessage(this.msg().format("toggle_available", "filters",
               String.join("§7, §f", this.plugin.getFilterRegistry().getFilters().keySet())));
         return;
      }

      ScanFilter filterObj = this.plugin.getFilterRegistry().getFilter(filterArg);
      if (filterObj == null) {
         sender.sendMessage(this.msg().format("toggle_not_found", "filter", filterArg));
         return;
      }

      String originalFilterName = filterObj.getName();
      boolean currentlyEnabled = this.plugin.getConfig().getBoolean("filters." + originalFilterName + ".enabled", true);
      boolean newState = !currentlyEnabled;

      try {
         File configFile = new File(this.plugin.getDataFolder(), "config.yml");
         YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(configFile);
         diskConfig.set("filters." + originalFilterName + ".enabled", newState);
         diskConfig.save(configFile);
         this.plugin.reloadConfig();
         this.plugin.applyConfigDefaults();
         this.plugin.getFilterRegistry().reloadFilters();
         sender.sendMessage(this.msg().format("toggle_ok",
               "filter", originalFilterName,
               "state", newState ? this.msg().msg("state_on") : this.msg().msg("state_off")));
      } catch (Exception e) {
         sender.sendMessage(this.msg().format("toggle_error", "error", e.getMessage()));
         this.plugin.getLogger().severe("Filter toggle error: " + e.getMessage());
      }
   }

   private void handleLogs(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(this.msg().msg("logs_usage"));
         return;
      }

      switch (args[1].toLowerCase()) {
         case "report" -> {
            if (this.plugin.getViolationLog() != null) {
               this.plugin.getViolationLog().generateReport();
               sender.sendMessage(this.msg().msg("logs_report_ok"));
            } else {
               sender.sendMessage(this.msg().msg("logs_not_init"));
            }
         }
         case "clear" -> {
            if (args.length >= 3) {
               String playerName = args[2];
               ViolationLog.PlayerViolationStats stats = this.findPlayerStatsByName(playerName);
               if (stats == null) {
                  sender.sendMessage(this.msg().msg("logs_player_not_found"));
                  return;
               }
               UUID playerUUID = null;
               for (Map.Entry<UUID, ViolationLog.PlayerViolationStats> entry : this.plugin.getViolationLog().getAllStats().entrySet()) {
                  if (entry.getValue().getPlayerName().equalsIgnoreCase(playerName)) {
                     playerUUID = entry.getKey();
                     break;
                  }
               }
               if (playerUUID != null) {
                  this.plugin.getViolationLog().clearPlayerStats(playerUUID);
                  this.plugin.clearPlayerRuntimeState(playerUUID);
                  sender.sendMessage(this.msg().format("logs_player_cleared", "player", stats.getPlayerName()));
               } else {
                  sender.sendMessage(this.msg().msg("logs_player_uuid_missing"));
               }
            } else if (this.plugin.getViolationLog() != null) {
               this.plugin.getViolationLog().clearAllStats();
               sender.sendMessage(this.msg().msg("logs_all_cleared"));
            } else {
               sender.sendMessage(this.msg().msg("logs_not_init"));
            }
         }
         case "stats" -> this.handleLogsStats(sender, args);
         default -> sender.sendMessage(this.msg().msg("logs_usage"));
      }
   }

   private void handleLogsStats(CommandSender sender, String[] args) {
      if (this.plugin.getViolationLog() == null) {
         sender.sendMessage(this.msg().msg("logs_not_init"));
         return;
      }

      if (args.length >= 3) {
         String playerName = args[2];
         ViolationLog.PlayerViolationStats stats = this.findPlayerStatsByName(playerName);
         if (stats == null) {
            Player onlinePlayer = Bukkit.getPlayer(playerName);
            if (onlinePlayer != null) {
               stats = this.plugin.getViolationLog().getPlayerStats(onlinePlayer.getUniqueId());
            }
            if (stats == null) {
               sender.sendMessage(this.msg().format("logs_no_violations_player", "player", playerName));
               return;
            }
         }

         String displayName = stats.getPlayerName();
         Player onlinePlayer = Bukkit.getPlayer(displayName);
         String onlineStatus = onlinePlayer != null ? this.msg().msg("online") : this.msg().msg("offline");
         sender.sendMessage(this.msg().format("logs_player_header", "player", displayName, "online", onlineStatus));
         sender.sendMessage(this.msg().format("logs_total", "count", String.valueOf(stats.getTotalViolations())));
         SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
         sender.sendMessage(this.msg().format("logs_first", "time", sdf.format(new Date(stats.getFirstViolation()))));
         sender.sendMessage(this.msg().format("logs_last", "time", sdf.format(new Date(stats.getLastViolation()))));

         if (this.plugin.getConfig().getBoolean("auto_ban.enabled", false)) {
            int threshold = this.plugin.getConfig().getInt("auto_ban.violations_threshold", 3);
            int violations = stats.getTotalViolations();
            if (violations >= threshold) {
               sender.sendMessage(this.msg().msg("auto_ban_reached"));
            } else if (violations == threshold - 1) {
               sender.sendMessage(this.msg().msg("auto_ban_close_one"));
            } else if (violations >= threshold - 2) {
               sender.sendMessage(this.msg().format("auto_ban_approaching", "remaining", String.valueOf(threshold - violations)));
            }
         }

         if (!stats.getFilterViolations().isEmpty()) {
            sender.sendMessage(this.msg().msg("logs_by_filter"));
            stats.getFilterViolations().forEach((filter, count) ->
                  sender.sendMessage(this.msg().format("logs_filter_entry", "filter", filter, "count", String.valueOf(count))));
         }
         return;
      }

      Map<UUID, ViolationLog.PlayerViolationStats> allStats = this.plugin.getViolationLog().getAllStats();
      if (allStats.isEmpty()) {
         sender.sendMessage(this.msg().msg("logs_none"));
         return;
      }

      sender.sendMessage(this.msg().msg("logs_overall_header"));
      sender.sendMessage(this.msg().format("logs_players_total", "count", String.valueOf(allStats.size())));
      int totalViolations = allStats.values().stream().mapToInt(ViolationLog.PlayerViolationStats::getTotalViolations).sum();
      sender.sendMessage(this.msg().format("status_violations", "count", String.valueOf(totalViolations)));
      sender.sendMessage(this.msg().msg("logs_top"));
      allStats.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().getTotalViolations(), a.getValue().getTotalViolations()))
            .limit(5L)
            .forEach(entry -> {
               String name = entry.getValue().getPlayerName();
               int violations = entry.getValue().getTotalViolations();
               Player onlinePlayer = Bukkit.getPlayer(name);
               String onlineStatus = onlinePlayer != null ? this.msg().msg("online") : this.msg().msg("offline");
               sender.sendMessage(this.msg().format("logs_top_entry",
                     "player", name, "online", onlineStatus, "count", String.valueOf(violations)));
            });
   }

   private void handleAntiTheft(CommandSender sender, String[] args) {
      if (this.plugin.getTheftMonitor() == null) {
         sender.sendMessage(this.msg().msg("antitheft_not_init"));
         return;
      }
      if (args.length < 2) {
         sender.sendMessage(this.msg().msg("antitheft_usage"));
         return;
      }

      switch (args[1].toLowerCase()) {
         case "status" -> {
            boolean enabled = this.plugin.getConfig().getBoolean("anti_theft.enabled", true);
            FileConfiguration cfg = this.plugin.getConfig();
            sender.sendMessage(this.msg().msg("antitheft_header"));
            sender.sendMessage(this.msg().format("antitheft_enabled",
                  "state", enabled ? this.msg().msg("state_on") : this.msg().msg("state_off")));
            sender.sendMessage(this.msg().format("antitheft_min_containers",
                  "count", String.valueOf(cfg.getInt("anti_theft.min_containers", 3))));
            sender.sendMessage(this.msg().format("antitheft_window",
                  "seconds", String.valueOf(cfg.getInt("anti_theft.time_window", 300))));
            sender.sendMessage(this.msg().format("antitheft_min_distance",
                  "blocks", String.valueOf(cfg.getInt("anti_theft.min_distance", 20))));
         }
         case "clear" -> {
            if (args.length < 3) {
               sender.sendMessage(this.msg().msg("antitheft_clear_usage"));
               return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
               sender.sendMessage(this.msg().msg("antitheft_player_not_found"));
               return;
            }
            this.plugin.getTheftMonitor().clearPlayerHistory(target.getUniqueId());
            sender.sendMessage(this.msg().format("antitheft_cleared", "player", target.getName()));
         }
         case "clearall" -> {
            this.plugin.getTheftMonitor().clearAllHistory();
            sender.sendMessage(this.msg().msg("antitheft_cleared_all"));
         }
         default -> sender.sendMessage(this.msg().msg("antitheft_usage"));
      }
   }

   private void handleLanguage(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(this.msg().msg("language_usage"));
         return;
      }
      if (!this.plugin.setLanguage(args[1])) {
         sender.sendMessage(this.msg().msg("language_invalid"));
         return;
      }
      sender.sendMessage(this.msg().format("language_changed", "lang", this.msg().getLanguage()));
   }

   private void handleInspect(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(this.msg().msg("inspect_usage"));
         return;
      }
      String playerName = args[1];
      ViolationLog.SessionSummary session = this.plugin.getViolationLog().findSessionByName(playerName);
      Player online = Bukkit.getPlayer(playerName);
      if (session == null && online != null) {
         session = this.plugin.getViolationLog().getSessionSummary(online.getUniqueId());
      }
      if (session == null) {
         sender.sendMessage(this.msg().format("inspect_none", "player", playerName));
         return;
      }

      SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
      sender.sendMessage(this.msg().format("inspect_header", "player", session.playerName()));
      sender.sendMessage(this.msg().format("inspect_total", "count", String.valueOf(session.total())));
      sender.sendMessage(this.msg().format("inspect_started", "time", sdf.format(new Date(session.sessionStart()))));
      if (session.lastLocation() != null) {
         var loc = session.lastLocation();
         String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
         sender.sendMessage(this.msg().format("inspect_last_location",
               "world", world,
               "x", String.valueOf(loc.getBlockX()),
               "y", String.valueOf(loc.getBlockY()),
               "z", String.valueOf(loc.getBlockZ())));
      }
      if (!session.filters().isEmpty()) {
         sender.sendMessage(this.msg().msg("inspect_filters"));
         session.filters().forEach((filter, count) ->
               sender.sendMessage(this.msg().format("logs_filter_entry", "filter", filter, "count", String.valueOf(count))));
      }

      ViolationLog.PlayerViolationStats lifetime = null;
      if (online != null) {
         lifetime = this.plugin.getViolationLog().getPlayerStats(online.getUniqueId());
      } else {
         lifetime = this.findPlayerStatsByName(playerName);
      }
      if (lifetime != null) {
         sender.sendMessage(this.msg().format("inspect_lifetime", "count", String.valueOf(lifetime.getTotalViolations())));
      }
   }

   private ViolationLog.PlayerViolationStats findPlayerStatsByName(String playerName) {
      if (this.plugin.getViolationLog() == null) {
         return null;
      }
      Map<UUID, ViolationLog.PlayerViolationStats> allStats = this.plugin.getViolationLog().getAllStats();
      for (ViolationLog.PlayerViolationStats stats : allStats.values()) {
         if (stats.getPlayerName().equals(playerName)) {
            return stats;
         }
      }
      for (ViolationLog.PlayerViolationStats stats : allStats.values()) {
         if (stats.getPlayerName().equalsIgnoreCase(playerName)) {
            return stats;
         }
      }
      return null;
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      List<String> completions = new ArrayList<>();
      if (!sender.hasPermission("griefscan.admin")) {
         return completions;
      }

      if (args.length == 1) {
         StringUtil.copyPartialMatches(args[0],
               Arrays.asList("reload", "status", "toggle", "logs", "antitheft", "language", "inspect"), completions);
      } else if (args.length == 2) {
         switch (args[0].toLowerCase()) {
            case "toggle" -> {
               if (this.plugin.getConfig().getConfigurationSection("filters") != null) {
                  StringUtil.copyPartialMatches(args[1],
                        this.plugin.getConfig().getConfigurationSection("filters").getKeys(false), completions);
               }
            }
            case "logs" -> StringUtil.copyPartialMatches(args[1], Arrays.asList("report", "clear", "stats"), completions);
            case "antitheft" -> StringUtil.copyPartialMatches(args[1], Arrays.asList("status", "clear", "clearall"), completions);
            case "language", "lang" -> StringUtil.copyPartialMatches(args[1], MessageService.SUPPORTED_LANGUAGES, completions);
            case "inspect" -> {
               List<String> playerNames = new ArrayList<>();
               for (Player player : Bukkit.getOnlinePlayers()) {
                  playerNames.add(player.getName());
               }
               StringUtil.copyPartialMatches(args[1], playerNames, completions);
            }
            default -> {
            }
         }
      } else if (args.length == 3) {
         if (args[0].equalsIgnoreCase("logs") && (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("stats"))) {
            List<String> playerNames = new ArrayList<>();
            if (this.plugin.getViolationLog() != null) {
               for (ViolationLog.PlayerViolationStats stats : this.plugin.getViolationLog().getAllStats().values()) {
                  playerNames.add(stats.getPlayerName());
               }
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
               if (!playerNames.contains(player.getName())) {
                  playerNames.add(player.getName());
               }
            }
            StringUtil.copyPartialMatches(args[2], playerNames, completions);
         } else if (args[0].equalsIgnoreCase("antitheft") && args[1].equalsIgnoreCase("clear")) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
               playerNames.add(player.getName());
            }
            StringUtil.copyPartialMatches(args[2], playerNames, completions);
         }
      }

      Collections.sort(completions);
      return completions;
   }

   private void help(CommandSender s) {
      s.sendMessage(this.msg().msg("help_header"));
      s.sendMessage(this.msg().msg("help_reload"));
      s.sendMessage(this.msg().msg("help_status"));
      s.sendMessage(this.msg().msg("help_toggle"));
      s.sendMessage(this.msg().msg("help_logs_report"));
      s.sendMessage(this.msg().msg("help_logs_clear"));
      s.sendMessage(this.msg().msg("help_logs_clear_player"));
      s.sendMessage(this.msg().msg("help_logs_stats"));
      s.sendMessage(this.msg().msg("help_logs_stats_player"));
      s.sendMessage(this.msg().msg("help_antitheft_status"));
      s.sendMessage(this.msg().msg("help_antitheft_clear"));
      s.sendMessage(this.msg().msg("help_antitheft_clearall"));
      s.sendMessage(this.msg().msg("help_language"));
      s.sendMessage(this.msg().msg("help_inspect"));
      if (this.plugin.getConfig().getBoolean("auto_ban.enabled", false)) {
         int threshold = this.plugin.getConfig().getInt("auto_ban.violations_threshold", 3);
         s.sendMessage("");
         s.sendMessage(this.msg().format("auto_ban_help", "threshold", String.valueOf(threshold)));
      }
   }
}
