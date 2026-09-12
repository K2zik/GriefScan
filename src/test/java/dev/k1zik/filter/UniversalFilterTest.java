package dev.k1zik.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class UniversalFilterTest {
   @Test
   void matchesLavaPlacement() {
      UniversalFilter filter = new UniversalFilter(
            "LAVA_PLACEMENT",
            true,
            5,
            30,
            "notify",
            List.of(),
            List.of("LAVA"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("WORLD"));

      assertTrue(filter.matches("BLOCK_PLACE", "LAVA", "world"));
      assertTrue(filter.matches("BLOCK_PLACE", "LAVA_CAULDRON", "world"));
      assertFalse(filter.matches("BLOCK_PLACE", "TNT", "world"));
      assertFalse(filter.matches("BLOCK_PLACE", "LAVA", "world_nether"));
   }

   @Test
   void respectsHeightLimits() {
      UniversalFilter filter = new UniversalFilter(
            "LAVA_PLACEMENT",
            true,
            5,
            30,
            "notify",
            List.of(),
            List.of("LAVA"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            40,
            100);

      assertTrue(filter.hasHeightLimit());
      assertTrue(filter.matchesHeight(40));
      assertTrue(filter.matchesHeight(100));
      assertFalse(filter.matchesHeight(39));
      assertFalse(filter.matchesHeight(101));
   }

   @Test
   void matchesEntityKillFrames() {
      UniversalFilter filter = new UniversalFilter(
            "FRAME_BREAKING",
            true,
            10,
            60,
            "notify",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("ITEM_FRAME"),
            List.of("ENTITY_KILL"),
            List.of());

      assertTrue(filter.matches("ENTITY_KILL", "ITEM_FRAME", null));
      assertTrue(filter.matches("ENTITY_KILL", "GLOW_ITEM_FRAME", null));
      assertFalse(filter.matches("ENTITY_KILL", "VILLAGER", null));
   }
}
