package me.n1xend.meteorites.config;

import me.n1xend.meteorites.CustomMeteorites;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigUpdater {
    private final CustomMeteorites plugin;

    public ConfigUpdater(CustomMeteorites plugin) {
        this.plugin = plugin;
    }

    public void migrateIfNeeded() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String currentVersion = config.getString("config-version", "1.0.0");

        if (currentVersion.equals("2.0.0")) return;

        plugin.getLogger().info("Migrating config from v" + currentVersion + " to v2.0.0...");

        // Миграция старых путей в новые
        migratePath(config, "enable-random-meteorites", "settings.enable-random-meteorites");
        migratePath(config, "random-meteorite-interval", "settings.random-meteorite-interval");
        migratePath(config, "random-meteorite-world", "settings.random-meteorite-world");
        migratePath(config, "random-meteorite-max-spawn-x-coord", "settings.spawn-area.max-x");
        migratePath(config, "random-meteorite-max-spawn-z-coord", "settings.spawn-area.max-z");
        migratePath(config, "random-meteorite-min-spawn-x-coord", "settings.spawn-area.min-x");
        migratePath(config, "random-meteorite-min-spawn-z-coord", "settings.spawn-area.min-z");
        migratePath(config, "random-meteorite-spawn-height", "settings.spawn-height");
        migratePath(config, "enable-meteorite-treasure", "treasure.enabled");
        migratePath(config, "treasure-barrel-or-chest", "treasure.container-type");
        migratePath(config, "treasure-content", "treasure.items");
        migratePath(config, "enable-treasure-guardian", "guardians.enabled");
        migratePath(config, "possible-guardians", "guardians.types");
        migratePath(config, "atmosphere-effect", "atmosphere");
        migratePath(config, "impact-shockwave", "shockwave");
        migratePath(config, "meteorite-radar", "radar");

        // Миграция секций взрывов
        migrateSection(config, "core-settings", "explosions.core");
        migrateSection(config, "inner-layer-settings", "explosions.inner-layer");
        migrateSection(config, "outer-layer-settings", "explosions.outer-layer");

        config.set("config-version", "2.0.0");

        try {
            config.save(configFile);
            plugin.getLogger().info("Config successfully migrated to v2.0.0!");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save migrated config: " + e.getMessage());
        }
    }

    private void migratePath(YamlConfiguration config, String oldPath, String newPath) {
        if (config.contains(oldPath) && !config.contains(newPath)) {
            config.set(newPath, config.get(oldPath));
            config.set(oldPath, null);
        }
    }

    private void migrateSection(YamlConfiguration config, String oldPath, String newPath) {
        if (config.contains(oldPath) && !config.contains(newPath)) {
            config.set(newPath, config.get(oldPath));
            config.set(oldPath, null);
        }
    }
}