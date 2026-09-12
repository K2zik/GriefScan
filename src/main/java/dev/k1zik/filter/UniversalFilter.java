package dev.k1zik.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UniversalFilter {
   private final String name;
   private final boolean enabled;
   private final int limit;
   private final int timeWindow;
   private final String action;
   private final List<String> commands;
   private final List<String> blocks;
   private final List<String> breakBlocks;
   private final List<String> entities;
   private final List<String> killEntities;
   private final List<String> events;
   private final List<String> worlds;
   private final int minHeight;
   private final int maxHeight;

   public UniversalFilter(
         String name,
         boolean enabled,
         int limit,
         int timeWindow,
         String action,
         List<String> commands,
         List<String> blocks,
         List<String> breakBlocks,
         List<String> entities,
         List<String> killEntities,
         List<String> events,
         List<String> worlds,
         int minHeight,
         int maxHeight) {
      this.name = name;
      this.enabled = enabled;
      this.limit = limit;
      this.timeWindow = timeWindow;
      this.action = action;
      this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
      this.blocks = blocks != null ? new ArrayList<>(blocks) : new ArrayList<>();
      this.breakBlocks = breakBlocks != null ? new ArrayList<>(breakBlocks) : new ArrayList<>();
      this.entities = entities != null ? new ArrayList<>(entities) : new ArrayList<>();
      this.killEntities = killEntities != null ? new ArrayList<>(killEntities) : new ArrayList<>();
      this.events = events != null ? new ArrayList<>(events) : new ArrayList<>();
      this.worlds = worlds != null ? new ArrayList<>(worlds) : new ArrayList<>();
      this.minHeight = minHeight;
      this.maxHeight = maxHeight;
   }

   public UniversalFilter(
         String name,
         boolean enabled,
         int limit,
         int timeWindow,
         String action,
         List<String> commands,
         List<String> blocks,
         List<String> breakBlocks,
         List<String> entities,
         List<String> killEntities,
         List<String> events,
         List<String> worlds) {
      this(name, enabled, limit, timeWindow, action, commands, blocks, breakBlocks,
            entities, killEntities, events, worlds, Integer.MIN_VALUE, Integer.MAX_VALUE);
   }

   public boolean matches(String eventType, String target, String worldName) {
      if (!this.enabled) {
         return false;
      }
      if (!this.worlds.isEmpty()
            && (worldName == null || !this.worlds.contains(worldName.toUpperCase(Locale.ROOT)))) {
         return false;
      }

      String normalizedEvent = eventType.toUpperCase(Locale.ROOT);
      String normalizedTarget = target.toUpperCase(Locale.ROOT);

      if (!this.events.isEmpty() && !this.events.contains(normalizedEvent)) {
         return false;
      }

      if (normalizedEvent.equals("BLOCK_PLACE") && !this.blocks.isEmpty()) {
         return this.blocks.contains(normalizedTarget) || isFluidMatch(normalizedTarget);
      }
      if (normalizedEvent.equals("BLOCK_BREAK") && !this.breakBlocks.isEmpty()) {
         return this.breakBlocks.contains(normalizedTarget);
      }
      if (normalizedEvent.equals("ENTITY_PLACE") && !this.entities.isEmpty()) {
         return this.entities.contains(normalizedTarget);
      }
      if (normalizedEvent.equals("ENTITY_KILL") && !this.killEntities.isEmpty()) {
         return this.killEntities.contains(normalizedTarget) || isHangingMatch(normalizedTarget);
      }

      return !this.events.isEmpty()
            && this.blocks.isEmpty()
            && this.breakBlocks.isEmpty()
            && this.entities.isEmpty()
            && this.killEntities.isEmpty();
   }

   private boolean isFluidMatch(String target) {
      for (String block : this.blocks) {
         if (block.equals("LAVA") && target.contains("LAVA")) {
            return true;
         }
         if (block.equals("WATER") && target.contains("WATER")) {
            return true;
         }
      }
      return false;
   }

   private boolean isHangingMatch(String target) {
      for (String entity : this.killEntities) {
         if (entity.equals("ITEM_FRAME") && target.contains("ITEM_FRAME")) {
            return true;
         }
         if (entity.equals("PAINTING") && target.contains("PAINTING")) {
            return true;
         }
      }
      return false;
   }

   public boolean matchesHeight(int y) {
      return y >= this.minHeight && y <= this.maxHeight;
   }

   public boolean hasHeightLimit() {
      return this.minHeight != Integer.MIN_VALUE || this.maxHeight != Integer.MAX_VALUE;
   }

   public boolean matches(String eventType, String target) {
      return this.matches(eventType, target, null);
   }

   public String getName() {
      return this.name;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public int getLimit() {
      return this.limit;
   }

   public int getTimeWindow() {
      return this.timeWindow;
   }

   public String getAction() {
      return this.action;
   }

   public List<String> getCommands() {
      return new ArrayList<>(this.commands);
   }

   public List<String> getBlocks() {
      return new ArrayList<>(this.blocks);
   }

   public List<String> getBreakBlocks() {
      return new ArrayList<>(this.breakBlocks);
   }

   public List<String> getEntities() {
      return new ArrayList<>(this.entities);
   }

   public List<String> getKillEntities() {
      return new ArrayList<>(this.killEntities);
   }

   public List<String> getEvents() {
      return new ArrayList<>(this.events);
   }

   public List<String> getWorlds() {
      return new ArrayList<>(this.worlds);
   }

   public int getMinHeight() {
      return this.minHeight;
   }

   public int getMaxHeight() {
      return this.maxHeight;
   }

   @Override
   public String toString() {
      return "UniversalFilter{name='" + this.name
            + "', enabled=" + this.enabled
            + ", limit=" + this.limit
            + ", timeWindow=" + this.timeWindow
            + ", action='" + this.action
            + "', commands=" + this.commands
            + ", blocks=" + this.blocks
            + ", breakBlocks=" + this.breakBlocks
            + ", entities=" + this.entities
            + ", killEntities=" + this.killEntities
            + ", events=" + this.events
            + ", worlds=" + this.worlds
            + ", minHeight=" + (this.minHeight == Integer.MIN_VALUE ? "none" : this.minHeight)
            + ", maxHeight=" + (this.maxHeight == Integer.MAX_VALUE ? "none" : this.maxHeight)
            + "}";
   }
}
