package dev.k1zik;

import java.io.File;
import java.io.IOException;
import dev.k1zik.alert.AlertManager;
import dev.k1zik.command.CommandHandler;
import dev.k1zik.filter.FilterManager;
import dev.k1zik.listener.UniversalListener;
import dev.k1zik.logging.FileLogger;
import dev.k1zik.service.AlertCooldownService;
import dev.k1zik.service.BypassService;
import dev.k1zik.service.IntegrationService;
import dev.k1zik.service.MessageService;
import dev.k1zik.tracker.AntiTheftTracker;
import dev.k1zik.tracker.PlayerActionTracker;
import dev.k1zik.tracker.ShulkerOwnership;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class GriefScan extends JavaPlugin {
   private static GriefScan instance;
   private MessageService messageService;
   private BypassService bypassService;
   private AlertCooldownService alertCooldownService;
   private IntegrationService integrationService;
   private FilterManager filterManager;
   private AlertManager alertManager;
   private PlayerActionTracker limitTracker;
   private FileLogger fileLogger;
   private AntiTheftTracker antiTheftTracker;
   private ShulkerOwnership shulkerOwnership;

   @Override
   public void onEnable() {
      instance = this;
      this.saveDefaultConfig();
      this.applyConfigDefaults();
      this.messageService = new MessageService(this);
      this.messageService.ensureLanguageResources();
      this.messageService.loadFromConfig();
      this.bypassService = new BypassService(this);
      this.alertCooldownService = new AlertCooldownService(this);
      this.integrationService = new IntegrationService(this);
      this.filterManager = new FilterManager(this);
      this.alertManager = new AlertManager(this);
      this.limitTracker = new PlayerActionTracker(this);
      this.fileLogger = new FileLogger(this);
      this.shulkerOwnership = new ShulkerOwnership(this);
      this.antiTheftTracker = new AntiTheftTracker(this);

      PluginCommand cmd = this.getCommand("griefscan");
      if (cmd == null) {
         this.getLogger().severe(this.messageService.msg("command_missing"));
      } else {
         CommandHandler commandHandler = new CommandHandler(this);
         cmd.setExecutor(commandHandler);
         cmd.setTabCompleter(commandHandler);
         this.getLogger().info(this.messageService.msg("command_registered"));
      }

      try {
         this.getServer().getPluginManager().registerEvents(new UniversalListener(this), this);
         this.getLogger().info(this.messageService.msg("listener_registered"));
      } catch (Exception exception) {
         this.getLogger().severe(this.messageService.format("listener_error", "error", exception.getMessage()));
         exception.printStackTrace();
      }

      String on = this.messageService.msg("on");
      String off = this.messageService.msg("off");
      this.getLogger().info("==========================================");
      this.getLogger().info(this.messageService.msg("enabled_banner"));
      this.getLogger().info(this.messageService.format("filters_loaded", "count",
            String.valueOf(this.filterManager.getLoadedFiltersCount())));
      this.getLogger().info(this.messageService.format("logging_status", "status",
            this.getConfig().getBoolean("actions.log_to_file", true) ? on : off));
      boolean autoBanEnabled = this.getConfig().getBoolean("auto_ban.enabled", false);
      int threshold = this.getConfig().getInt("auto_ban.violations_threshold", 3);
      this.getLogger().info(this.messageService.format("auto_ban_status", "status",
            autoBanEnabled ? this.messageService.format("on_threshold", "threshold", String.valueOf(threshold)) : off));
      this.getLogger().info(this.messageService.format("discord_status", "status",
            this.getConfig().getBoolean("notify.discord", false) ? on : off));
      this.getLogger().info(this.messageService.format("antitheft_status_boot", "status",
            this.getConfig().getBoolean("anti_theft.enabled", true) ? on : off));
      this.getLogger().info("==========================================");
   }

   public void applyConfigDefaults() {
      this.getConfig().addDefault("language", "en");
      this.getConfig().addDefault("alerts.cooldown_seconds", 30);
      this.getConfig().addDefault("alerts.trigger_cooldown_seconds", 10);
      this.getConfig().addDefault("notify.admins_chat", true);
      this.getConfig().addDefault("notify.discord_action_buttons", true);
      this.getConfig().addDefault("exempt.players", java.util.List.of());
      this.getConfig().addDefault("exempt.uuids", java.util.List.of());
      this.getConfig().addDefault("integrations.coreprotect_lookup", "/co lookup %player% time:1d");
      this.getConfig().addDefault("integrations.litebans_history", "/litebans:history %player%");
      this.getConfig().addDefault("integrations.teleport_command", "/tppos %x% %y% %z%");
      this.getConfig().addDefault("integrations.kick_command", "/kick %player%");
      this.getConfig().addDefault("integrations.ban_command", "/ban %player%");
      this.getConfig().addDefault("anti_theft.track_ender_chest", true);
      this.getConfig().addDefault("anti_theft.track_hopper", true);
      this.getConfig().addDefault("anti_theft.track_shulker_deposit", true);
      this.getConfig().addDefault("anti_theft.log_actions", false);
      this.getConfig().addDefault("auto_ban.enabled", false);
      this.getConfig().addDefault("auto_ban.violations_threshold", 3);
      this.getConfig().addDefault("auto_ban.ban_reason", "Automatic ban: repeated rule violations");
      this.getConfig().addDefault("auto_ban.ban_command", "ban %player% %reason%");
      this.getConfig().addDefault("auto_ban.discord_alert", true);
      this.getConfig().addDefault("playtime", "30h");
      this.getConfig().options().copyDefaults(true);
   }

   @Override
   public void onDisable() {
      if (this.fileLogger != null) {
         try {
            this.fileLogger.generateReport();
         } catch (Exception exception) {
            this.getLogger().warning(this.messageService != null
                  ? this.messageService.format("report_error", "error", exception.getMessage())
                  : "Failed to generate report: " + exception.getMessage());
         }
         this.fileLogger.close();
      }

      if (this.shulkerOwnership != null) {
         try {
            this.shulkerOwnership.saveSync();
         } catch (Exception exception) {
            this.getLogger().warning(this.messageService != null
                  ? this.messageService.format("shulker_save_error", "error", exception.getMessage())
                  : "Failed to save shulker_owners.yml: " + exception.getMessage());
         }
      }

      this.getLogger().info(this.messageService != null
            ? this.messageService.msg("disabled_banner")
            : "GriefScan disabled.");
   }

   public static GriefScan getInstance() {
      return instance;
   }

   public MessageService getMessageService() {
      return this.messageService;
   }

   public BypassService getBypassService() {
      return this.bypassService;
   }

   public AlertCooldownService getAlertCooldownService() {
      return this.alertCooldownService;
   }

   public IntegrationService getIntegrationService() {
      return this.integrationService;
   }

   public FilterManager getFilterManager() {
      return this.filterManager;
   }

   public AlertManager getAlertManager() {
      return this.alertManager;
   }

   public PlayerActionTracker getLimitTracker() {
      return this.limitTracker;
   }

   public FileLogger getFileLogger() {
      return this.fileLogger;
   }

   public AntiTheftTracker getAntiTheftTracker() {
      return this.antiTheftTracker;
   }

   public ShulkerOwnership getShulkerOwnership() {
      return this.shulkerOwnership;
   }

   public void reloadSystem() {
      this.reloadConfig();
      this.applyConfigDefaults();
      if (this.messageService != null) {
         this.messageService.loadFromConfig();
      }
      if (this.alertCooldownService != null) {
         this.alertCooldownService.clear();
      }
      if (this.filterManager != null) {
         this.filterManager.loadUniversalFilters();
      }
      if (this.antiTheftTracker != null) {
         this.antiTheftTracker.reloadConfig();
      }
      this.getLogger().info(this.messageService.format("config_reloaded_log", "count",
            String.valueOf(this.filterManager != null ? this.filterManager.getLoadedFiltersCount() : 0)));
   }

   public boolean setLanguage(String lang) {
      String normalized = this.messageService.normalizeLanguageCode(lang);
      if (normalized == null) {
         return false;
      }
      this.getConfig().set("language", normalized);
      this.saveConfigPreserveCommentsFallback();
      this.messageService.loadLanguage(normalized);
      return true;
   }

   private void saveConfigPreserveCommentsFallback() {
      try {
         this.saveConfig();
      } catch (Exception exception) {
         File configFile = new File(this.getDataFolder(), "config.yml");
         YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
         yaml.set("language", this.getConfig().getString("language", "en"));
         try {
            yaml.save(configFile);
         } catch (IOException ioException) {
            this.getLogger().warning("Failed to save language setting: " + ioException.getMessage());
         }
      }
   }
}
