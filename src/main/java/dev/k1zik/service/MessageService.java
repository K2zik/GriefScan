package dev.k1zik.service;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MessageService {
   public static final List<String> SUPPORTED_LANGUAGES =
         List.of("en", "ru", "es", "zh", "hi", "ar", "fr", "de", "ja", "pt");

   private static final LegacyComponentSerializer LEGACY =
         LegacyComponentSerializer.legacyAmpersand();

   private final JavaPlugin plugin;
   private FileConfiguration messages;
   private String language = "en";

   public MessageService(JavaPlugin plugin) {
      this.plugin = plugin;
   }

   public void ensureLanguageResources() {
      for (String lang : SUPPORTED_LANGUAGES) {
         String resource = lang.equals("en") ? "messages.yml" : "messages_" + lang + ".yml";
         File out = new File(plugin.getDataFolder(), resource);
         if (!out.exists()) {
            plugin.saveResource(resource, false);
         }
      }
   }

   public void loadFromConfig() {
      String lang = plugin.getConfig().getString("language", "en");
      loadLanguage(lang == null ? "en" : lang);
   }

   public void loadLanguage(String lang) {
      String normalized = normalizeLanguageCode(lang);
      if (normalized == null) {
         normalized = "en";
      }
      this.language = normalized;

      if (!plugin.getDataFolder().exists()) {
         plugin.getDataFolder().mkdirs();
      }

      String fileName = normalized.equals("en") ? "messages.yml" : "messages_" + normalized + ".yml";
      File msgFile = new File(plugin.getDataFolder(), fileName);
      if (!msgFile.exists()) {
         try {
            plugin.saveResource(fileName, false);
         } catch (IllegalArgumentException ignored) {
            // resource may be missing for optional languages
         }
         msgFile = new File(plugin.getDataFolder(), fileName);
      }
      if (!msgFile.exists()) {
         plugin.getLogger().warning("[GriefScan] Language file " + fileName + " missing, falling back to messages.yml");
         msgFile = new File(plugin.getDataFolder(), "messages.yml");
      }
      messages = YamlConfiguration.loadConfiguration(msgFile);
   }

   public String getLanguage() {
      return language;
   }

   public String normalizeLanguageCode(String languageCode) {
      if (languageCode == null || languageCode.isBlank()) {
         return null;
      }
      String normalized = languageCode.trim().toLowerCase(Locale.ROOT);
      return SUPPORTED_LANGUAGES.contains(normalized) ? normalized : null;
   }

   public String raw(String key) {
      return raw(key, key);
   }

   public String raw(String key, String def) {
      String value = def;
      if (messages != null && messages.contains(key)) {
         value = messages.getString(key, def);
      }
      return value == null ? def : value;
   }

   public String msg(String key) {
      return msg(key, key);
   }

   public String msg(String key, String def) {
      return colorize(raw(key, def));
   }

   public Component component(String key) {
      return LEGACY.deserialize(raw(key));
   }

   public Component component(String key, String... pairs) {
      return LEGACY.deserialize(formatRaw(key, pairs));
   }

   public String format(String key, String... pairs) {
      return colorize(formatRaw(key, pairs));
   }

   public String formatRaw(String key, String... pairs) {
      String value = raw(key);
      for (int i = 0; i + 1 < pairs.length; i += 2) {
         value = value.replace("%" + pairs[i] + "%", pairs[i + 1] == null ? "" : pairs[i + 1]);
      }
      return value;
   }

   public String format(String key, Map<String, String> placeholders) {
      String value = raw(key);
      if (placeholders != null) {
         for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
         }
      }
      return colorize(value);
   }

   public static String colorize(String input) {
      if (input == null) {
         return "";
      }
      return LegacyComponentSerializer.legacySection().serialize(LEGACY.deserialize(input));
   }

   public static Component legacy(String input) {
      return LEGACY.deserialize(input == null ? "" : input);
   }
}
