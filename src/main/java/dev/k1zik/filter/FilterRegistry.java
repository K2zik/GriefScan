package dev.k1zik.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.k1zik.GriefScan;
import dev.k1zik.util.PlaytimeUtil;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class FilterRegistry {
   private final GriefScan plugin;
   private final Map<UUID, Map<String, List<Long>>> playerActions = new ConcurrentHashMap<>();
   private final double playtimeHoursLimit;
   private final Map<String, ScanFilter> filters = new HashMap<>();
   private final Set<String> trackedBlocks = new HashSet<>();
   private final Set<String> trackedBreakBlocks = new HashSet<>();
   private final Set<String> trackedEntities = new HashSet<>();
   private final Set<String> trackedKillEntities = new HashSet<>();

   public FilterRegistry(GriefScan plugin) {
      this.plugin = plugin;
      this.playtimeHoursLimit = PlaytimeUtil.parseHours(
            plugin.getConfig().getString("playtime", "30h"),
            30.0,
            plugin.getLogger());
      this.loadFilters();
      plugin.getLogger().info("FilterRegistry initialized. Filters loaded: " + this.filters.size());
   }

   private String normalizeFilterName(String name) {
      return name.toLowerCase(Locale.ROOT).trim();
   }

   public boolean shouldTrackBlock(String blockType) {
      return this.trackedBlocks.contains(blockType.toUpperCase(Locale.ROOT));
   }

   public boolean shouldTrackBreakBlock(String blockType) {
      return this.trackedBreakBlocks.contains(blockType.toUpperCase(Locale.ROOT));
   }

   public boolean shouldTrackEntity(String entityType) {
      return this.trackedEntities.contains(entityType.toUpperCase(Locale.ROOT));
   }

   public boolean shouldTrackKillEntity(String entityType) {
      return this.trackedKillEntities.contains(entityType.toUpperCase(Locale.ROOT));
   }

   public void checkAndRecord(Player player, String eventType, String target, Location location) {
      for (ScanFilter filter : this.filters.values()) {
         String worldName = location != null && location.getWorld() != null
               ? location.getWorld().getName()
               : null;

         if (!filter.matches(eventType, target, worldName)) {
            continue;
         }

         if (location != null && filter.hasHeightLimit() && !filter.matchesHeight(location.getBlockY())) {
            if (isFilterDebugEnabled()) {
               this.plugin.getLogger().info("[FILTER] " + filter.getName()
                     + " skipped: Y=" + location.getBlockY()
                     + " out of range [" + filter.getMinHeight() + ".." + filter.getMaxHeight() + "]");
            }
            continue;
         }

         this.recordAction(player, filter.getName(), location);
         break;
      }
   }

   public void checkAndRecord(Player player, String eventType, String target) {
      this.checkAndRecord(player, eventType, target, null);
   }

   public void recordAction(Player player, String filterName, Location location) {
      if (this.plugin.getBypassService().isExempt(player)) {
         return;
      }
      String normalizedName = this.normalizeFilterName(filterName);
      ScanFilter filter = this.filters.get(normalizedName);
      if (filter == null || !filter.isEnabled()) {
         return;
      }

      int playTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
      double playHours = playTicks / 20.0 / 60.0 / 60.0;
      if (playHours >= this.playtimeHoursLimit) {
         return;
      }

      long now = System.currentTimeMillis();
      UUID playerId = player.getUniqueId();
      Map<String, List<Long>> actionsByFilter = this.playerActions.computeIfAbsent(
            playerId, ignored -> new ConcurrentHashMap<>());
      List<Long> timestamps = actionsByFilter.computeIfAbsent(
            normalizedName, ignored -> Collections.synchronizedList(new ArrayList<>()));

      timestamps.add(now);
      timestamps.removeIf(timestamp -> now - timestamp > filter.getTimeWindow() * 1000L);

      if (isFilterDebugEnabled()) {
         this.plugin.getLogger().info("[FILTER] " + filter.getName() + ": "
               + timestamps.size() + "/" + filter.getLimit()
               + " in " + filter.getTimeWindow() + " sec");
      }

      if (timestamps.size() >= filter.getLimit()) {
         this.plugin.getLogger().warning("TRIGGER: " + player.getName()
               + " | filter: " + filter.getName()
               + " | actions: " + timestamps.size());
         if (this.plugin.getAlertDispatcher() != null) {
            this.plugin.getAlertDispatcher().triggerAlert(
                  player, filter.getName(), timestamps.size(), filter, location);
         }
         timestamps.clear();
      }
   }

   public void recordAction(Player player, String filterName) {
      this.recordAction(player, filterName, null);
   }

   public void loadFilters() {
      this.filters.clear();
      this.trackedBlocks.clear();
      this.trackedBreakBlocks.clear();
      this.trackedEntities.clear();
      this.trackedKillEntities.clear();

      ConfigurationSection filtersSection = this.plugin.getConfig().getConfigurationSection("filters");
      if (filtersSection == null) {
         this.plugin.getLogger().warning("No filters section in config!");
         return;
      }

      this.plugin.getLogger().info("=== LOADING FILTERS ===");
      for (String filterName : filtersSection.getKeys(false)) {
         try {
            List<String> commands = readCommands(filterName);
            List<String> blocks = toUpperList(this.plugin.getConfig().getStringList("filters." + filterName + ".blocks"));
            List<String> breakBlocks = toUpperList(this.plugin.getConfig().getStringList("filters." + filterName + ".break_blocks"));
            List<String> entities = toUpperList(this.plugin.getConfig().getStringList("filters." + filterName + ".entities"));
            List<String> killEntities = toUpperList(this.plugin.getConfig().getStringList("filters." + filterName + ".kill_entities"));
            List<String> events = toUpperList(this.plugin.getConfig().getStringList("filters." + filterName + ".events"));
            List<String> worlds = readWorlds(filterName);
            int minHeight = this.plugin.getConfig().getInt("filters." + filterName + ".min_height", Integer.MIN_VALUE);
            int maxHeight = this.plugin.getConfig().getInt("filters." + filterName + ".max_height", Integer.MAX_VALUE);

            ScanFilter filter = new ScanFilter(
                  filterName,
                  this.plugin.getConfig().getBoolean("filters." + filterName + ".enabled", true),
                  this.plugin.getConfig().getInt("filters." + filterName + ".limit", 5),
                  this.plugin.getConfig().getInt("filters." + filterName + ".time_window", 60),
                  this.plugin.getConfig().getString("filters." + filterName + ".action", "notify"),
                  commands,
                  blocks,
                  breakBlocks,
                  entities,
                  killEntities,
                  events,
                  worlds,
                  minHeight,
                  maxHeight);

            this.filters.put(this.normalizeFilterName(filterName), filter);
            this.trackedBlocks.addAll(filter.getBlocks());
            this.trackedBreakBlocks.addAll(filter.getBreakBlocks());
            this.trackedEntities.addAll(filter.getEntities());
            this.trackedKillEntities.addAll(filter.getKillEntities());
            this.plugin.getLogger().info("Filter loaded: " + filterName);
         } catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to load filter " + filterName + ": " + exception.getMessage());
            exception.printStackTrace();
         }
      }
      this.plugin.getLogger().info("=== FILTERS LOADED: " + this.filters.size() + " ===");
   }

   private List<String> readCommands(String filterName) {
      List<String> commands = new ArrayList<>();
      Object commandValue = this.plugin.getConfig().get("filters." + filterName + ".command");
      if (commandValue instanceof String command && !command.trim().isEmpty()) {
         commands.add(command);
      } else if (commandValue instanceof List<?> commandList) {
         for (Object entry : commandList) {
            if (entry instanceof String command && !command.trim().isEmpty()) {
               commands.add(command);
            }
         }
      }
      return commands;
   }

   private List<String> readWorlds(String filterName) {
      List<String> worlds = new ArrayList<>();
      Object worldsValue = this.plugin.getConfig().get("filters." + filterName + ".worlds");
      if (worldsValue instanceof String world && !world.trim().isEmpty()) {
         worlds.add(world.toUpperCase(Locale.ROOT));
      } else if (worldsValue instanceof List<?> worldList) {
         for (Object entry : worldList) {
            if (entry instanceof String world && !world.trim().isEmpty()) {
               worlds.add(world.toUpperCase(Locale.ROOT));
            }
         }
      }

      if (worlds.isEmpty()) {
         Object singleWorld = this.plugin.getConfig().get("filters." + filterName + ".world");
         if (singleWorld instanceof String world && !world.trim().isEmpty()) {
            worlds.add(world.toUpperCase(Locale.ROOT));
         }
      }
      return worlds;
   }

   private List<String> toUpperList(List<String> input) {
      List<String> result = new ArrayList<>(input.size());
      for (String value : input) {
         result.add(value.toUpperCase(Locale.ROOT));
      }
      return result;
   }

   private boolean isFilterDebugEnabled() {
      return this.plugin.getConfig().getBoolean("debug.enabled", false)
            && this.plugin.getConfig().getBoolean("debug.filters", false);
   }

   public int getLoadedFiltersCount() {
      return this.filters.size();
   }

   public Map<String, ScanFilter> getFilters() {
      return new HashMap<>(this.filters);
   }

   public boolean hasFilter(String name) {
      return this.filters.containsKey(this.normalizeFilterName(name));
   }

   public ScanFilter getFilter(String name) {
      return this.filters.get(this.normalizeFilterName(name));
   }

   public void reloadFilters() {
      this.loadFilters();
      this.plugin.getLogger().info("Filters reloaded. Active: " + this.filters.size());
   }

   public boolean addFilter(String name, ScanFilter filter) {
      String normalizedName = this.normalizeFilterName(name);
      if (this.filters.containsKey(normalizedName)) {
         return false;
      }
      this.filters.put(normalizedName, filter);
      this.trackedBlocks.addAll(filter.getBlocks());
      this.trackedBreakBlocks.addAll(filter.getBreakBlocks());
      this.trackedEntities.addAll(filter.getEntities());
      this.trackedKillEntities.addAll(filter.getKillEntities());
      return true;
   }

   public boolean removeFilter(String name) {
      String normalizedName = this.normalizeFilterName(name);
      ScanFilter removed = this.filters.remove(normalizedName);
      if (removed == null) {
         return false;
      }
      this.rebuildCache();
      return true;
   }

   private void rebuildCache() {
      this.trackedBlocks.clear();
      this.trackedBreakBlocks.clear();
      this.trackedEntities.clear();
      this.trackedKillEntities.clear();
      for (ScanFilter filter : this.filters.values()) {
         this.trackedBlocks.addAll(filter.getBlocks());
         this.trackedBreakBlocks.addAll(filter.getBreakBlocks());
         this.trackedEntities.addAll(filter.getEntities());
         this.trackedKillEntities.addAll(filter.getKillEntities());
      }
   }

   public boolean renameFilter(String oldName, String newName) {
      String normalizedOld = this.normalizeFilterName(oldName);
      String normalizedNew = this.normalizeFilterName(newName);
      if (!this.filters.containsKey(normalizedOld) || this.filters.containsKey(normalizedNew)) {
         return false;
      }

      ScanFilter oldFilter = this.filters.remove(normalizedOld);
      ScanFilter renamed = new ScanFilter(
            newName,
            oldFilter.isEnabled(),
            oldFilter.getLimit(),
            oldFilter.getTimeWindow(),
            oldFilter.getAction(),
            oldFilter.getCommands(),
            oldFilter.getBlocks(),
            oldFilter.getBreakBlocks(),
            oldFilter.getEntities(),
            oldFilter.getKillEntities(),
            oldFilter.getEvents(),
            oldFilter.getWorlds(),
            oldFilter.getMinHeight(),
            oldFilter.getMaxHeight());
      this.filters.put(normalizedNew, renamed);
      return true;
   }

   public List<Long> getPlayerActions(UUID playerId, String filterName) {
      Map<String, List<Long>> actionsByFilter = this.playerActions.get(playerId);
      if (actionsByFilter == null) {
         return new ArrayList<>();
      }
      return new ArrayList<>(actionsByFilter.getOrDefault(this.normalizeFilterName(filterName), List.of()));
   }

   public void clearPlayerActions(UUID playerId, String filterName) {
      Map<String, List<Long>> actionsByFilter = this.playerActions.get(playerId);
      if (actionsByFilter != null) {
         actionsByFilter.remove(this.normalizeFilterName(filterName));
      }
   }

   public void clearAllPlayerActions(UUID playerId) {
      this.playerActions.remove(playerId);
   }

   public Set<String> getTrackedBlocks() {
      return new HashSet<>(this.trackedBlocks);
   }

   public Set<String> getTrackedBreakBlocks() {
      return new HashSet<>(this.trackedBreakBlocks);
   }

   public Set<String> getTrackedEntities() {
      return new HashSet<>(this.trackedEntities);
   }

   public Set<String> getTrackedKillEntities() {
      return new HashSet<>(this.trackedKillEntities);
   }
}
