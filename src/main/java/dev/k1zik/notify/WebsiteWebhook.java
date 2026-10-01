package dev.k1zik.notify;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import dev.k1zik.GriefScan;
import dev.k1zik.util.HttpJsonClient;
import dev.k1zik.util.JsonStrings;
import dev.k1zik.util.SchedulerUtil;

public class WebsiteWebhook {
   public record AlertPayload(
         String content,
         String player,
         UUID uuid,
         String filter,
         int count,
         String world,
         int x,
         int y,
         int z,
         long timestamp) {

      public String toJson() {
         Map<String, Object> fields = JsonStrings.map();
         fields.put("content", this.content);
         fields.put("player", this.player);
         fields.put("uuid", this.uuid != null ? this.uuid.toString() : null);
         fields.put("filter", this.filter);
         fields.put("count", this.count);
         fields.put("world", this.world);
         fields.put("x", this.x);
         fields.put("y", this.y);
         fields.put("z", this.z);
         fields.put("timestamp", this.timestamp);
         return JsonStrings.object(fields);
      }
   }

   public static void send(GriefScan plugin, AlertPayload payload, String webhookUrl) {
      if (!plugin.getConfig().getBoolean("notify.website", false)
            || webhookUrl == null
            || webhookUrl.isEmpty()
            || payload == null) {
         return;
      }

      SchedulerUtil.runAsync(plugin, () -> {
         try {
            int responseCode = HttpJsonClient.post(webhookUrl, payload.toJson());
            if (responseCode < 200 || responseCode >= 300) {
               throw new IOException("HTTP error code: " + responseCode);
            }
            plugin.getLogger().info("Website notification sent: " + payload.filter() + " / " + payload.player());
         } catch (IOException exception) {
            plugin.getLogger().warning("Failed to send website notification: " + exception.getMessage());
         }
      });
   }
}
