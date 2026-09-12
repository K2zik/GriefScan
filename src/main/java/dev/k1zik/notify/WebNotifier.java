package dev.k1zik.notify;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import dev.k1zik.GriefScan;
import dev.k1zik.util.SchedulerUtil;

public class WebNotifier {
   public static void send(GriefScan plugin, String message, String webhookUrl) {
      if (!plugin.getConfig().getBoolean("notify.website", false)
            || webhookUrl == null
            || webhookUrl.isEmpty()) {
         return;
      }

      SchedulerUtil.runAsync(plugin, () -> {
         try {
            sendWebhookRequest(webhookUrl, createJsonPayload(message));
            plugin.getLogger().info("Website notification sent: " + message);
         } catch (IOException exception) {
            plugin.getLogger().warning("Failed to send website notification: " + exception.getMessage());
         }
      });
   }

   private static String createJsonPayload(String message) {
      return String.format(
            "{\"content\": \"%s\", \"timestamp\": %d}",
            escapeJson(message),
            System.currentTimeMillis());
   }

   private static String escapeJson(String text) {
      return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
   }

   private static void sendWebhookRequest(String webhookUrl, String jsonPayload) throws IOException {
      URL url = new URL(webhookUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("User-Agent", "GriefScan-Plugin");
      connection.setDoOutput(true);

      try (OutputStream outputStream = connection.getOutputStream()) {
         outputStream.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
      }

      int responseCode = connection.getResponseCode();
      connection.disconnect();
      if (responseCode < 200 || responseCode >= 300) {
         throw new IOException("HTTP error code: " + responseCode);
      }
   }
}
