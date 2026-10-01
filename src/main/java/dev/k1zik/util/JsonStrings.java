package dev.k1zik.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonStrings {
   private JsonStrings() {
   }

   public static String escape(String text) {
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

   public static String object(Map<String, ?> fields) {
      StringBuilder json = new StringBuilder(128);
      json.append('{');
      boolean first = true;
      for (Map.Entry<String, ?> entry : fields.entrySet()) {
         if (!first) {
            json.append(',');
         }
         first = false;
         json.append('"').append(escape(entry.getKey())).append("\":");
         Object value = entry.getValue();
         if (value == null) {
            json.append("null");
         } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
         } else {
            json.append('"').append(escape(String.valueOf(value))).append('"');
         }
      }
      json.append('}');
      return json.toString();
   }

   public static Map<String, Object> map() {
      return new LinkedHashMap<>();
   }
}
