package dev.k1zik.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;

/**
 * Pure clustering helpers used by AntiTheft and unit tests.
 */
public final class LocationClusterUtil {
   private LocationClusterUtil() {
   }

   public static List<Cluster> buildClusters(List<Location> locations, double minDistance) {
      List<Cluster> clusters = new ArrayList<>();
      for (Location location : locations) {
         boolean added = false;
         for (Cluster cluster : clusters) {
            if (cluster.isNear(location, minDistance)) {
               cluster.add(location);
               added = true;
               break;
            }
         }
         if (!added) {
            Cluster cluster = new Cluster();
            cluster.add(location);
            clusters.add(cluster);
         }
      }
      return clusters;
   }

   public static boolean hasDistantPair(List<Cluster> clusters, double minDistance) {
      for (int first = 0; first < clusters.size(); first++) {
         for (int second = first + 1; second < clusters.size(); second++) {
            if (clusters.get(first).distanceTo(clusters.get(second)) >= minDistance) {
               return true;
            }
         }
      }
      return false;
   }

   public static final class Cluster {
      private final List<Location> locations = new ArrayList<>();

      public void add(Location location) {
         this.locations.add(location);
      }

      public boolean isNear(Location location, double maxDistance) {
         for (Location existing : this.locations) {
            if (existing.getWorld() != null
                  && existing.getWorld().equals(location.getWorld())
                  && existing.distance(location) <= maxDistance) {
               return true;
            }
         }
         return false;
      }

      public double distanceTo(Cluster other) {
         double minimum = Double.MAX_VALUE;
         for (Location first : this.locations) {
            for (Location second : other.locations) {
               if (first.getWorld() != null && first.getWorld().equals(second.getWorld())) {
                  minimum = Math.min(minimum, first.distance(second));
               }
            }
         }
         return minimum == Double.MAX_VALUE ? 0.0 : minimum;
      }

      public int size() {
         return this.locations.size();
      }
   }
}
