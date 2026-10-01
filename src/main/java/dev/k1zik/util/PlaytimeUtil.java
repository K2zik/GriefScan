package dev.k1zik.util;

import java.util.Locale;
import java.util.logging.Logger;

public final class PlaytimeUtil {
   private PlaytimeUtil() {
   }

   /**
    * Parses values like {@code 30h}, {@code 90m}, {@code 2d}, or bare hours.
    */
   public static double parseHours(String raw, double fallback, Logger logger) {
      if (raw == null || raw.isBlank()) {
         return fallback;
      }

      String value = raw.trim().toLowerCase(Locale.ROOT);
      double multiplier = 1.0;
      if (value.endsWith("h")) {
         value = value.substring(0, value.length() - 1).trim();
      } else if (value.endsWith("m")) {
         value = value.substring(0, value.length() - 1).trim();
         multiplier = 1.0 / 60.0;
      } else if (value.endsWith("d")) {
         value = value.substring(0, value.length() - 1).trim();
         multiplier = 24.0;
      }

      try {
         return Double.parseDouble(value) * multiplier;
      } catch (NumberFormatException exception) {
         if (logger != null) {
            logger.warning("Invalid playtime '" + raw + "', using " + fallback + "h");
         }
         return fallback;
      }
   }
}
