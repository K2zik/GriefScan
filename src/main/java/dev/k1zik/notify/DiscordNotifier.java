package dev.k1zik.notify;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import dev.k1zik.GriefScan;
import dev.k1zik.util.SchedulerUtil;

public class DiscordNotifier {
   public static void send(GriefScan plugin, String message) {
      send(plugin, message, plugin.getConfig().getBoolean("notify.discord", false));
   }

   public static void send(GriefScan plugin, String message, boolean enabled) {
      sendAlertEmbed(plugin, "GriefScan Alert", message, List.of(), enabled);
   }

   public static void sendAlertEmbed(
         GriefScan plugin,
         String title,
         String description,
         List<ActionButton> actions,
         boolean enabled) {
      if (!enabled) {
         return;
      }
      String webhook = plugin.getConfig().getString("notify.webhook");
      if (webhook == null || webhook.isEmpty() || webhook.contains("YOUR_WEBHOOK_HERE")) {
         plugin.getLogger().warning("Discord webhook is not configured!");
         return;
      }

      SchedulerUtil.runAsync(plugin, () -> {
         try {
            String processed = description.replace("\\n", "\n");
            StringBuilder json = new StringBuilder();
            json.append("{\"embeds\":[{");
            json.append("\"title\":\"").append(escapeJson(title)).append("\",");
            json.append("\"description\":\"").append(escapeJson(processed)).append("\",");
            json.append("\"color\":15158332");
            if (!actions.isEmpty()) {
               StringBuilder fieldValue = new StringBuilder();
               for (ActionButton action : actions) {
                  fieldValue.append("**").append(action.label()).append(":** `")
                        .append(action.command()).append("`\\n");
               }
               json.append(",\"fields\":[{")
                     .append("\"name\":\"Quick actions (copy/paste)\",")
                     .append("\"value\":\"").append(escapeJson(fieldValue.toString().replace("\\n", "\n"))).append("\",")
                     .append("\"inline\":false}]");
            }
            json.append("}]}");

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            URL url = new URL(webhook);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
               os.write(bytes);
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != 204 && responseCode != 200) {
               plugin.getLogger().warning("Discord webhook returned code: " + responseCode);
            }
            conn.disconnect();
         } catch (Exception exception) {
            plugin.getLogger().warning("Failed to send Discord notification: " + exception.getMessage());
         }
      });
   }

   private static String escapeJson(String text) {
      if (text == null) {
         return "";
      }
      return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
   }

   public record ActionButton(String label, String command) {
   }

   public static List<ActionButton> defaultActions(
         String kick, String ban, String teleport, String coreProtect, String liteBans) {
      List<ActionButton> actions = new ArrayList<>();
      actions.add(new ActionButton("Kick", kick));
      actions.add(new ActionButton("Ban", ban));
      actions.add(new ActionButton("Teleport", teleport));
      actions.add(new ActionButton("CoreProtect", coreProtect));
      actions.add(new ActionButton("LiteBans", liteBans));
      return actions;
   }
}
