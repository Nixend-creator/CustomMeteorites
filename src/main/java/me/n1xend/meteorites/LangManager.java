package me.n1xend.meteorites;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LangManager {
    private final JavaPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private String language = "ru";

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadLanguages() {
        language = plugin.getConfig().getString("language", "ru").toLowerCase();
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) langFolder.mkdirs();

        // Сохраняем дефолтные файлы локализации из ресурсов
        saveDefaultLang("ru.yml");
        saveDefaultLang("en.yml");

        // Загружаем выбранный язык
        File langFile = new File(langFolder, language + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file '" + language + ".yml' not found, using default ru.yml");
            language = "ru";
            langFile = new File(langFolder, "ru.yml");
        }

        YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
        for (String key : langConfig.getKeys(true)) {
            if (langConfig.isString(key)) {
                messages.put(key, langConfig.getString(key));
            }
        }

        plugin.getLogger().info("Loaded language: " + language.toUpperCase());
    }

    private void saveDefaultLang(String fileName) {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File file = new File(langFolder, fileName);
        if (!file.exists()) {
            plugin.saveResource("lang/" + fileName, false);
        }
    }

    public String getMessage(String key, String... placeholders) {
        String msg = messages.getOrDefault(key, "&c[MISSING: " + key + "]");
        return processPlaceholders(msg, placeholders);
    }

    public String processPlaceholders(String text, String... placeholders) {
        String result = text;
        for (int i = 0; i < placeholders.length; i += 2) {
            String placeholder = "%" + placeholders[i] + "%";
            String value = (i + 1 < placeholders.length) ? placeholders[i + 1] : "";
            result = result.replace(placeholder, value);
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    public void reload() {
        messages.clear();
        loadLanguages();
    }

    public String getLanguage() {
        return language;
    }
}