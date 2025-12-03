package ru.yourname.custommeteorites.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить config.yml: " + e.getMessage());
        }
    }

    // --- Старые геттеры ---
    public boolean isRandomMeteoritesEnabled() { return config.getBoolean("enable-random-meteorites", true); }
    public int getInterval() { return config.getInt("random-meteorite-interval", 7200); }
    public String getTargetWorldName() { return config.getString("random-meteorite-world", "world"); }
    public int getMaxSpawnX() { return config.getInt("random-meteorite-max-spawn-x-coord", 2500); }
    public int getMaxSpawnZ() { return config.getInt("random-meteorite-max-spawn-z-coord", 2500); }
    public int getMinSpawnX() { return config.getInt("random-meteorite-min-spawn-x-coord", -2500); }
    public int getMinSpawnZ() { return config.getInt("random-meteorite-min-spawn-z-coord", -2500); }
    public int getSpawnHeight() { return config.getInt("random-meteorite-spawn-height", 150); }

    public boolean isWorldGuardIntegrationEnabled() { return config.getBoolean("enable-worldguard-safe-zones", false); }
    public List<String> getWorldGuardSafeZoneNames() { return config.getStringList("worldguard-safe-zone-names"); }
    public boolean isProtectAllWorldGuardZones() { return config.getBoolean("protect-all-worldguard-zones", false); }
    public int getWorldGuardSafeZoneBuffer() { return config.getInt("worldguard-safe-zone-buffer", 100); }

    public boolean isGriefPreventionIntegrationEnabled() { return config.getBoolean("enable-griefprevention-safe-zones", false); } // Пока не используется
    public int getGriefPreventionSafeZoneBuffer() { return config.getInt("griefprevention-safe-zone-buffer", 50); } // Пока не используется

    // --- Настройки метеоритов ---
    public Map<String, Object> getMeteoritesConfig() { return config.getConfigurationSection("meteorites").getValues(false); }

    // --- Настройки слоёв ---
    public Map<String, Object> getCoreSettings() { return config.getConfigurationSection("core-settings").getValues(false); }
    public Map<String, Object> getInnerLayerSettings() { return config.getConfigurationSection("inner-layer-settings").getValues(false); }
    public Map<String, Object> getOuterLayerSettings() { return config.getConfigurationSection("outer-layer-settings").getValues(false); }

    // --- Настройки частиц ---
    public boolean areParticlesEnabled() { return config.getBoolean("enable-meteorite-particles", true); }
    public int getParticleInterval() { return config.getInt("meteorite-particle-interval", 5); }
    public Map<String, Object> getParticleEffects() { return config.getConfigurationSection("possible-meteorite-particle-effects").getValues(false); }

    // --- Настройки сокровищ ---
    public boolean isTreasureEnabled() { return config.getBoolean("enable-meteorite-treasure", true); }
    public String getTreasureType() { return config.getString("treasure-barrel-or-chest", "CHEST"); }
    public Map<String, Object> getTreasureContent() { return config.getConfigurationSection("treasure-content").getValues(false); }

    // --- Настройки охранника ---
    public boolean isGuardianEnabled() { return config.getBoolean("enable-treasure-guardian", true); }
    public Map<String, Object> getPossibleGuardians() { return config.getConfigurationSection("possible-guardians").getValues(false); }
}
