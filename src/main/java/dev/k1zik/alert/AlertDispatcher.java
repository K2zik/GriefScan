package dev.k1zik.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import dev.k1zik.GriefScan;
import dev.k1zik.filter.ScanFilter;
import dev.k1zik.notify.DiscordWebhook;
import dev.k1zik.notify.WebsiteWebhook;
import dev.k1zik.service.AlertCooldownService;
import dev.k1zik.service.IntegrationService;
import dev.k1zik.service.MessageService;
import dev.k1zik.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class AlertDispatcher {
   private final GriefScan plugin;

   public AlertDispatcher(GriefScan plugin) {
      this.plugin = plugin;
   }

   public void triggerAlert(Player player, String filterKey, int count) {
      ScanFilter filter = this.plugin.getFilterRegistry().getFilter(filterKey);
      if (filter == null) {
         return;
      }
      triggerAlert(player, filterKey, count, filter, null);
   }

   public void triggerAlert(Player player, String filterKey, int count, ScanFilter filter, Location location) {
      if (filter == null || this.plugin.getBypassService().isExempt(player)) {
         return;
      }

      int triggerCooldown = this.plugin.getConfig().getInt("alerts.trigger_cooldown_seconds", 10);
      if (triggerCooldown > 0
            && !this.plugin.getAlertCooldownService().tryAlert(
                  player.getUniqueId(), "trigger:" + filterKey, triggerCooldown)) {
         return;
      }

      List<String> actions = parseActions(filter.getAction());
      if (actions.contains("notify")) {
         sendNotifications(player, filterKey, count, location);
      }
      if (actions.contains("command")) {
         executeCommands(player, filter.getCommands(), location);
      }
      if (this.plugin.getConfig().getBoolean("actions.log_to_file", true)) {
         this.plugin.getViolationLog().logViolation(player, filterKey, location);
      }
   }

   private void sendNotifications(Player player, String filterKey, int count, Location location) {
      String playerName = player.getName();
      MessageService messages = this.plugin.getMessageService();
      AlertCooldownService cooldown = this.plugin.getAlertCooldownService();
      IntegrationService integrations = this.plugin.getIntegrationService();
      Location resolved = location != null ? location : player.getLocation();

      if (this.plugin.getConfig().getBoolean("notify.console", true)
            && cooldown.tryAlert(player.getUniqueId(), "console")) {
         String template = this.plugin.getConfig().getString(
               "notify.messageConsole", messages.raw("alert_console_default"));
         Bukkit.getLogger().warning(formatMessage(template, playerName, filterKey, count, player, resolved));
      }

      if (this.plugin.getConfig().getBoolean("notify.admins_chat", true)
            && cooldown.tryAlert(player.getUniqueId(), "admins")) {
         sendClickableAdminAlert(player, filterKey, count, resolved);
      }

      if (this.plugin.getConfig().getBoolean("notify.discord", false)
            && cooldown.tryAlert(player.getUniqueId(), "discord")) {
         String template = this.plugin.getConfig().getString(
               "notify.messageDiscord", messages.raw("alert_discord_default"));
         String body = formatMessage(template, playerName, filterKey, count, player, resolved);
         DiscordWebhook.sendAlertEmbed(
               this.plugin,
               "GriefScan Alert",
               body,
               DiscordWebhook.defaultActions(
                     integrations.kickCommand(player),
                     integrations.banCommand(player),
                     integrations.teleportCommand(player, resolved),
                     integrations.coreProtectLookup(player),
                     integrations.liteBansHistory(player)),
               true);
      }

      if (this.plugin.getConfig().getBoolean("notify.website", false)
            && cooldown.tryAlert(player.getUniqueId(), "website")) {
         String webhookUrl = this.plugin.getConfig().getString("notify.website_url");
         if (webhookUrl != null && !webhookUrl.isEmpty()) {
            String template = this.plugin.getConfig().getString(
                  "notify.messageWebsite", messages.raw("alert_website_default"));
            String content = formatMessage(template, playerName, filterKey, count, player, resolved);
            String worldName = resolved.getWorld() != null ? resolved.getWorld().getName() : "unknown";
            WebsiteWebhook.send(
                  this.plugin,
                  new WebsiteWebhook.AlertPayload(
                        content,
                        playerName,
                        player.getUniqueId(),
                        filterKey,
                        count,
                        worldName,
                        resolved.getBlockX(),
                        resolved.getBlockY(),
                        resolved.getBlockZ(),
                        System.currentTimeMillis()),
                  webhookUrl);
         }
      }
   }

   private void sendClickableAdminAlert(Player player, String filterKey, int count, Location location) {
      IntegrationService integrations = this.plugin.getIntegrationService();
      String teleport = integrations.teleportCommand(player, location);
      String commandWithoutSlash = teleport.startsWith("/") ? teleport.substring(1) : teleport;

      Component coords = Component.text(
                  location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ(),
                  NamedTextColor.YELLOW)
            .decorate(TextDecoration.UNDERLINED)
            .clickEvent(ClickEvent.runCommand(commandWithoutSlash))
            .hoverEvent(HoverEvent.showText(Component.text("Click to teleport")));

      Component message = Component.text("[GriefScan] ", NamedTextColor.RED)
            .append(Component.text(player.getName(), NamedTextColor.GOLD))
            .append(Component.text(" → ", NamedTextColor.GRAY))
            .append(Component.text(filterKey + " x" + count, NamedTextColor.WHITE))
            .append(Component.text(" @ ", NamedTextColor.GRAY))
            .append(Component.text(
                  location.getWorld() != null ? location.getWorld().getName() + " " : "",
                  NamedTextColor.AQUA))
            .append(coords);

      for (Player admin : Bukkit.getOnlinePlayers()) {
         if (admin.hasPermission("griefscan.admin")) {
            admin.sendMessage(message);
         }
      }
   }

   private String formatMessage(
         String template,
         String playerName,
         String filterKey,
         int count,
         Player player,
         Location location) {
      Location resolved = location != null ? location : player.getLocation();
      String worldName = resolved.getWorld() != null ? resolved.getWorld().getName() : "unknown";
      IntegrationService integrations = this.plugin.getIntegrationService();
      return template
            .replace("%player%", playerName)
            .replace("%uuid%", player.getUniqueId().toString())
            .replace("%filter%", filterKey)
            .replace("%count%", String.valueOf(count))
            .replace("%world%", worldName)
            .replace("%x%", String.valueOf(resolved.getBlockX()))
            .replace("%y%", String.valueOf(resolved.getBlockY()))
            .replace("%z%", String.valueOf(resolved.getBlockZ()))
            .replace("%tp%", integrations.teleportCommand(player, resolved))
            .replace("%coreprotect%", integrations.coreProtectLookup(player))
            .replace("%litebans%", integrations.liteBansHistory(player))
            .replace("%kick%", integrations.kickCommand(player))
            .replace("%ban%", integrations.banCommand(player));
   }

   private void executeCommands(Player player, List<String> commands, Location location) {
      if (commands == null || commands.isEmpty()) {
         return;
      }

      Location resolved = location != null ? location : player.getLocation();
      String worldName = resolved.getWorld() != null ? resolved.getWorld().getName() : "unknown";

      for (String command : commands) {
         if (command == null || command.trim().isEmpty()) {
            continue;
         }
         String finalCommand = command
               .replace("%player%", player.getName())
               .replace("%uuid%", player.getUniqueId().toString())
               .replace("%world%", worldName)
               .replace("%x%", String.valueOf(resolved.getBlockX()))
               .replace("%y%", String.valueOf(resolved.getBlockY()))
               .replace("%z%", String.valueOf(resolved.getBlockZ()));
         this.plugin.getLogger().info(
               this.plugin.getMessageService().format("command_dispatch_log", "command", finalCommand));
         SchedulerUtil.runGlobal(this.plugin,
               () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
      }
   }

   private List<String> parseActions(String action) {
      List<String> actions = new ArrayList<>();
      if (action == null || action.isEmpty()) {
         return actions;
      }
      for (String part : action.split("[,\\s]+")) {
         if (!part.trim().isEmpty()) {
            actions.add(part.trim().toLowerCase(Locale.ROOT));
         }
      }
      return actions;
   }
}
