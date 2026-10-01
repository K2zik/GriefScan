package dev.k1zik.listener;

import java.util.HashMap;
import java.util.Map;
import dev.k1zik.GriefScan;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public class PlayerEventListener implements Listener {
   private static final BlockFace[] HORIZONTAL_FACES = {
         BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
   };

   private final GriefScan plugin;

   public PlayerEventListener(GriefScan plugin) {
      this.plugin = plugin;
   }

   private void debugLog(String category, String message) {
      if (!this.plugin.getConfig().getBoolean("debug.enabled", false)) {
         return;
      }
      if (this.plugin.getConfig().getBoolean("debug.all", false)
            || this.plugin.getConfig().getBoolean("debug." + category, false)) {
         this.plugin.getLogger().info("[DEBUG] " + message);
      }
   }

   private void filterLog(String message) {
      if (this.plugin.getConfig().getBoolean("debug.enabled", false)
            && this.plugin.getConfig().getBoolean("debug.filters", false)) {
         this.plugin.getLogger().info("[FILTER] " + message);
      }
   }

   private void antiTheftLog(String message) {
      if (this.plugin.getConfig().getBoolean("debug.enabled", false)
            && this.plugin.getConfig().getBoolean("debug.anti_theft", false)) {
         this.plugin.getLogger().info("[ANTI-THEFT] " + message);
      }
   }

   @EventHandler
   public void onBlockPlace(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      String blockType = event.getBlock().getType().name();
      Location location = event.getBlock().getLocation();
      String debugCategory = blockType.contains("LAVA") || blockType.contains("WATER") ? "liquids" : "blocks";
      this.debugLog(debugCategory, "Placed: " + blockType + " by " + player.getName());

      if (blockType.contains("SHULKER_BOX") && this.plugin.getShulkerOwnerIndex() != null) {
         this.plugin.getShulkerOwnerIndex().registerOwner(location, player.getUniqueId());
      }

      if (this.plugin.getFilterRegistry().shouldTrackBlock(blockType)) {
         this.filterLog("BLOCK_PLACE " + blockType + " | " + player.getName());
         this.plugin.getFilterRegistry().checkAndRecord(player, "BLOCK_PLACE", blockType, location);
      }
   }

   @EventHandler
   public void onBucketEmpty(PlayerBucketEmptyEvent event) {
      Player player = event.getPlayer();
      String bucketType = event.getBucket().name();
      Location location = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
      this.debugLog("liquids", "Bucket emptied: " + bucketType + " by " + player.getName());

      String blockType = bucketType.replace("_BUCKET", "");
      if (this.plugin.getFilterRegistry().shouldTrackBlock(blockType)) {
         this.filterLog("BUCKET_EMPTY " + bucketType + " -> " + blockType + " | " + player.getName());
         this.plugin.getFilterRegistry().checkAndRecord(player, "BLOCK_PLACE", blockType, location);
      }
   }

   @EventHandler
   public void onBlockBreak(BlockBreakEvent event) {
      Player player = event.getPlayer();
      String blockType = event.getBlock().getType().name();
      Location location = event.getBlock().getLocation();
      String debugCategory = blockType.contains("CHEST") || blockType.contains("BARREL") || blockType.contains("SHULKER")
            ? "containers" : "blocks";
      this.debugLog(debugCategory, "Broken: " + blockType + " by " + player.getName());

      if (blockType.contains("SHULKER_BOX") && this.plugin.getShulkerOwnerIndex() != null) {
         this.plugin.getShulkerOwnerIndex().removeOwner(location);
      }

      if (this.plugin.getFilterRegistry().shouldTrackBreakBlock(blockType)) {
         this.filterLog("BLOCK_BREAK " + blockType + " | " + player.getName());
         this.plugin.getFilterRegistry().checkAndRecord(player, "BLOCK_BREAK", blockType, location);
      }
   }

   @EventHandler
   public void onEntityPlace(EntityPlaceEvent event) {
      Player player = event.getPlayer();
      if (player == null) {
         return;
      }

      String entityType = event.getEntityType().name();
      Location location = event.getEntity().getLocation();
      this.debugLog("entities", "Entity placed: " + entityType + " by " + player.getName());

      if (this.plugin.getFilterRegistry().shouldTrackEntity(entityType)) {
         this.filterLog("ENTITY_PLACE " + entityType + " | " + player.getName());
         this.plugin.getFilterRegistry().checkAndRecord(player, "ENTITY_PLACE", entityType, location);
      }
   }

   @EventHandler
   public void onHangingPlace(HangingPlaceEvent event) {
      Player player = event.getPlayer();
      if (player == null) {
         return;
      }

      String entityType = event.getEntity().getType().name();
      Location location = event.getEntity().getLocation();
      this.debugLog("entities", "Hanging placed: " + entityType + " by " + player.getName());

      if (this.plugin.getFilterRegistry().shouldTrackEntity(entityType)) {
         this.filterLog("HANGING_PLACE " + entityType + " | " + player.getName());
         this.plugin.getFilterRegistry().checkAndRecord(player, "ENTITY_PLACE", entityType, location);
      }
   }

   @EventHandler
   public void onEntityDeath(EntityDeathEvent event) {
      Player killer = resolveKiller(event);
      if (killer == null) {
         return;
      }

      String entityType = event.getEntityType().name();
      Location location = event.getEntity().getLocation();
      this.debugLog("entities", "Entity killed: " + entityType + " by " + killer.getName());

      if (this.plugin.getFilterRegistry().shouldTrackKillEntity(entityType)) {
         this.filterLog("ENTITY_KILL " + entityType + " | " + killer.getName());
         this.plugin.getFilterRegistry().checkAndRecord(killer, "ENTITY_KILL", entityType, location);
      }
   }

   private Player resolveKiller(EntityDeathEvent event) {
      Player killer = event.getEntity().getKiller();
      if (killer != null) {
         return killer;
      }

      EntityDamageEvent lastDamage = event.getEntity().getLastDamageCause();
      if (!(lastDamage instanceof EntityDamageByEntityEvent damageByEntity)) {
         return null;
      }

      Entity damager = damageByEntity.getDamager();
      if (damager instanceof Player playerDamager) {
         return playerDamager;
      }
      if (damager instanceof Projectile projectile) {
         ProjectileSource shooter = projectile.getShooter();
         if (shooter instanceof Player playerShooter) {
            return playerShooter;
         }
      }
      return null;
   }

   @EventHandler
   public void onHangingBreak(HangingBreakByEntityEvent event) {
      Entity remover = event.getRemover();
      if (!(remover instanceof Player player)) {
         return;
      }

      String entityType = event.getEntity().getType().name();
      Location location = event.getEntity().getLocation();
      this.debugLog("entities", "Hanging broken: " + entityType + " by " + player.getName());

      if (this.plugin.getFilterRegistry().shouldTrackKillEntity(entityType)) {
         this.filterLog("HANGING_BREAK " + entityType + " | " + player.getName());
         this.plugin.getFilterRegistry().checkAndRecord(player, "ENTITY_KILL", entityType, location);
      }
   }

   @EventHandler
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
         return;
      }

      Block block = event.getClickedBlock();
      if (block == null) {
         return;
      }

      Player player = event.getPlayer();
      Material material = block.getType();
      if (!isContainer(material)) {
         return;
      }

      Location location = block.getLocation();
      this.antiTheftLog("Opened: " + material.name() + " | " + player.getName());

      boolean ownShulker = material.name().contains("SHULKER_BOX")
            && this.plugin.getShulkerOwnerIndex() != null
            && this.plugin.getShulkerOwnerIndex().isOwner(location, player.getUniqueId());

      if (this.plugin.getTheftMonitor() != null && !ownShulker) {
         this.plugin.getTheftMonitor().onContainerOpen(player, location);
         if (material == Material.CHEST || material == Material.TRAPPED_CHEST) {
            for (BlockFace face : HORIZONTAL_FACES) {
               Block neighbor = block.getRelative(face);
               if (neighbor.getType() == material) {
                  this.plugin.getTheftMonitor().onContainerOpen(player, neighbor.getLocation());
                  break;
               }
            }
         }
      } else if (ownShulker) {
         this.antiTheftLog("Own shulker skipped: " + player.getName()
               + " | " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
      }

      String blockTypeName = material.name();
      if (this.plugin.getFilterRegistry().shouldTrackBlock(blockTypeName)) {
         this.plugin.getFilterRegistry().checkAndRecord(player, "CONTAINER_OPEN", blockTypeName, location);
      }
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (!(event.getPlayer() instanceof Player player)) {
         return;
      }
      if (this.plugin.getTheftMonitor() == null) {
         return;
      }

      Location location = resolveContainerLocation(event.getInventory());
      if (location == null) {
         // Still handle ender chest close via type
         if (event.getInventory().getType() == InventoryType.ENDER_CHEST
               && this.plugin.getConfig().getBoolean("anti_theft.track_ender_chest", true)) {
            location = player.getLocation();
         } else {
            return;
         }
      }

      this.antiTheftLog("Closed container | " + player.getName()
            + " | " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
      this.plugin.getTheftMonitor().onContainerClose(player, location);
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      if (this.plugin.getTheftMonitor() == null || event.getClickedInventory() == null) {
         return;
      }

      Inventory top = event.getView().getTopInventory();
      InventoryType topType = top.getType();
      boolean trackEnder = topType == InventoryType.ENDER_CHEST
            && this.plugin.getConfig().getBoolean("anti_theft.track_ender_chest", true);
      boolean trackHopper = topType == InventoryType.HOPPER
            && this.plugin.getConfig().getBoolean("anti_theft.track_hopper", true);
      boolean trackShulker = this.plugin.getConfig().getBoolean("anti_theft.track_shulker_deposit", true)
            && top.getHolder() instanceof Container container
            && container.getType().name().contains("SHULKER");

      if (!trackEnder && !trackHopper && !trackShulker) {
         return;
      }

      // Player shifts/clicks items into top inventory (stash)
      ItemStack current = event.getCurrentItem();
      boolean depositingIntoTop = event.getClickedInventory() == top
            || event.isShiftClick();
      if (!depositingIntoTop || current == null || current.getType() == Material.AIR) {
         return;
      }

      Map<Material, Integer> deposited = new HashMap<>();
      deposited.put(current.getType(), Math.max(1, current.getAmount()));
      Location location = resolveContainerLocation(top);
      if (location == null) {
         location = player.getLocation();
      }
      this.plugin.getTheftMonitor().onStashDeposit(player, location, deposited);
   }

   private Location resolveContainerLocation(Inventory inventory) {
      InventoryHolder holder = inventory.getHolder();
      if (holder instanceof Container container) {
         return container.getLocation();
      }
      if (!(inventory instanceof DoubleChestInventory doubleChestInventory)) {
         return null;
      }

      DoubleChest doubleChest = doubleChestInventory.getHolder();
      if (doubleChest == null) {
         return null;
      }

      InventoryHolder left = doubleChest.getLeftSide();
      if (left instanceof Container leftContainer) {
         return leftContainer.getLocation();
      }
      InventoryHolder right = doubleChest.getRightSide();
      if (right instanceof Container rightContainer) {
         return rightContainer.getLocation();
      }
      return null;
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      this.plugin.clearPlayerRuntimeState(event.getPlayer().getUniqueId());
   }

   @EventHandler
   public void onBucketFill(PlayerBucketFillEvent event) {
      Player player = event.getPlayer();
      String bucketType = event.getItemStack().getType().name();
      Location location = event.getBlockClicked().getLocation();
      this.debugLog("liquids", "Bucket filled: " + bucketType + " by " + player.getName());

      if (!bucketType.contains("LAVA_BUCKET") && !bucketType.contains("WATER_BUCKET")) {
         return;
      }

      String liquidType = bucketType.replace("_BUCKET", "");
      if (this.plugin.getFilterRegistry().shouldTrackBlock(liquidType)) {
         this.filterLog("BUCKET_FILL " + bucketType + " | " + player.getName());
         this.plugin.getFilterRegistry().checkAndRecord(player, "BLOCK_BREAK", liquidType, location);
      }
   }

   private boolean isContainer(Material material) {
      String name = material.name();
      return name.contains("CHEST")
            || name.contains("BARREL")
            || name.contains("SHULKER")
            || name.equals("HOPPER")
            || name.equals("DROPPER")
            || name.equals("DISPENSER")
            || name.contains("FURNACE")
            || name.equals("BLAST_FURNACE")
            || name.equals("SMOKER")
            || name.equals("BREWING_STAND")
            || name.equals("LECTERN")
            || name.equals("ENDER_CHEST");
   }
}
