package dev.k1zik.tracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.k1zik.GriefScan;
import org.bukkit.entity.Player;

public class PlayerActionTracker {
   private final GriefScan plugin;
   private final Map<UUID, Map<String, List<Long>>> actionsByPlayer = new HashMap<>();

   public PlayerActionTracker(GriefScan plugin) {
      this.plugin = plugin;
   }

   public void recordAction(Player player, String filterName) {
      UUID playerId = player.getUniqueId();
      Map<String, List<Long>> actionsByFilter = this.actionsByPlayer.computeIfAbsent(
            playerId, ignored -> new HashMap<>());
      List<Long> timestamps = actionsByFilter.computeIfAbsent(filterName, ignored -> new ArrayList<>());
      timestamps.add(System.currentTimeMillis());
   }

   public int getActions(Player player, String filterName, int seconds) {
      Map<String, List<Long>> actionsByFilter = this.actionsByPlayer.get(player.getUniqueId());
      if (actionsByFilter == null || !actionsByFilter.containsKey(filterName)) {
         return 0;
      }

      long now = System.currentTimeMillis();
      long windowMillis = seconds * 1000L;
      return (int) actionsByFilter.get(filterName).stream()
            .filter(timestamp -> now - timestamp <= windowMillis)
            .count();
   }

   public void clearPlayer(UUID playerId) {
      this.actionsByPlayer.remove(playerId);
      this.plugin.getLogger().info("Player stats " + playerId + " cleared");
   }

   public List<Long> getActionTimestamps(UUID playerId, String filterName) {
      Map<String, List<Long>> actionsByFilter = this.actionsByPlayer.get(playerId);
      if (actionsByFilter == null || !actionsByFilter.containsKey(filterName)) {
         return new ArrayList<>();
      }
      return new ArrayList<>(actionsByFilter.get(filterName));
   }

   public Set<String> getPlayerFilters(UUID playerId) {
      Map<String, List<Long>> actionsByFilter = this.actionsByPlayer.get(playerId);
      return actionsByFilter == null ? new HashSet<>() : new HashSet<>(actionsByFilter.keySet());
   }
}
