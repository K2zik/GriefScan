package dev.k1zik.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class LocationClusterUtilTest {
   @Test
   void buildsSeparateClustersForDistantPoints() {
      World world = mock(World.class);
      when(world.getName()).thenReturn("world");

      Location a = location(world, 0, 64, 0);
      Location b = location(world, 5, 64, 0);
      Location c = location(world, 100, 64, 0);

      List<LocationClusterUtil.Cluster> clusters = LocationClusterUtil.buildClusters(List.of(a, b, c), 20);
      assertEquals(2, clusters.size());
      assertTrue(LocationClusterUtil.hasDistantPair(clusters, 20));
   }

   @Test
   void noDistantPairWhenAllNear() {
      World world = mock(World.class);
      when(world.getName()).thenReturn("world");

      Location a = location(world, 0, 64, 0);
      Location b = location(world, 5, 64, 0);
      Location c = location(world, 10, 64, 0);

      List<LocationClusterUtil.Cluster> clusters = LocationClusterUtil.buildClusters(List.of(a, b, c), 20);
      assertEquals(1, clusters.size());
      assertFalse(LocationClusterUtil.hasDistantPair(clusters, 20));
   }

   private static Location location(World world, int x, int y, int z) {
      Location location = mock(Location.class);
      when(location.getWorld()).thenReturn(world);
      when(location.getBlockX()).thenReturn(x);
      when(location.getBlockY()).thenReturn(y);
      when(location.getBlockZ()).thenReturn(z);
      when(location.distance(org.mockito.ArgumentMatchers.any(Location.class))).thenAnswer(invocation -> {
         Location other = invocation.getArgument(0);
         double dx = x - other.getBlockX();
         double dy = y - other.getBlockY();
         double dz = z - other.getBlockZ();
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      });
      return location;
   }
}
