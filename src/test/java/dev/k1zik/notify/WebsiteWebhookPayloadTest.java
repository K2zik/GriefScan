package dev.k1zik.notify;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebsiteWebhookPayloadTest {
   @Test
   void buildsStructuredJson() {
      UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
      WebsiteWebhook.AlertPayload payload = new WebsiteWebhook.AlertPayload(
            "Alert: Steve triggered LAVA_PLACEMENT",
            "Steve",
            id,
            "LAVA_PLACEMENT",
            5,
            "world",
            120,
            64,
            -30,
            1725148800000L);

      String json = payload.toJson();
      assertTrue(json.contains("\"player\":\"Steve\""));
      assertTrue(json.contains("\"uuid\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\""));
      assertTrue(json.contains("\"filter\":\"LAVA_PLACEMENT\""));
      assertTrue(json.contains("\"count\":5"));
      assertTrue(json.contains("\"world\":\"world\""));
      assertTrue(json.contains("\"x\":120"));
      assertTrue(json.contains("\"y\":64"));
      assertTrue(json.contains("\"z\":-30"));
      assertTrue(json.contains("\"timestamp\":1725148800000"));
      assertTrue(json.contains("\"content\":\"Alert: Steve triggered LAVA_PLACEMENT\""));
   }
}
