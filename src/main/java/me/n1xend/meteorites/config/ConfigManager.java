package me.n1xend.meteorites.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(JavaPlugin plugin) { this.plugin = plugin; }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) plugin.saveResource("config.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reload() { loadConfig(); }

    public void saveConfig() {
        if (config == null || configFile == null) return;
        try { config.save(configFile); }
        catch (IOException e) { plugin.getLogger().severe("Failed to save config.yml: " + e.getMessage()); }
    }

    public FileConfiguration getRawConfig() { return config; }

    // === ГЛОБАЛЬНЫЕ НАСТРОЙКИ ===
    public boolean isRandomMeteoritesEnabled() { return config.getBoolean("settings.enable-random-meteorites", true); }
    public int getInterval() { return config.getInt("settings.random-meteorite-interval", 10800); }
    public String getTargetWorldName() { return config.getString("settings.random-meteorite-world", "world"); }
    public int getMaxSpawnX() { return config.getInt("settings.spawn-area.max-x", 2500); }
    public int getMaxSpawnZ() { return config.getInt("settings.spawn-area.max-z", 2500); }
    public int getMinSpawnX() { return config.getInt("settings.spawn-area.min-x", -2500); }
    public int getMinSpawnZ() { return config.getInt("settings.spawn-area.min-z", -2500); }
    public int getSpawnHeight() { return config.getInt("settings.spawn-height", 150); }
    public int getCleanupRadius() { return config.getInt("settings.cleanup-radius", 8); }

    // === МЕТЕОРИТЫ ===
    public ConfigurationSection getMeteoritesConfig() { return config.getConfigurationSection("meteorites"); }

    // === ВЗРЫВЫ ===
    public ConfigurationSection getCoreSettings() { return config.getConfigurationSection("explosions.core"); }
    public ConfigurationSection getInnerLayerSettings() { return config.getConfigurationSection("explosions.inner-layer"); }
    public ConfigurationSection getOuterLayerSettings() { return config.getConfigurationSection("explosions.outer-layer"); }

    // === ЧАСТИЦЫ ===
    public boolean areParticlesEnabled() { return config.getBoolean("particles.enabled", true); }
    public int getParticleInterval() { return config.getInt("particles.interval", 5); }
    public ConfigurationSection getParticleEffects() { return config.getConfigurationSection("particles.effects"); }

    // === СОКРОВИЩА ===
    public boolean isTreasureEnabled() { return config.getBoolean("treasure.enabled", true); }
    public String getTreasureType() { return config.getString("treasure.container-type", "CHEST"); }

    public ConfigurationSection getTreasureContent() { return config.getConfigurationSection("treasure.items"); }

    // === ОХРАННИКИ ===
    public boolean isGuardianEnabled() { return config.getBoolean("guardians.enabled", true); }
    public ConfigurationSection getPossibleGuardians() { return config.getConfigurationSection("guardians.types"); }

    // === АТМОСФЕРА ===
    public ConfigurationSection getAtmosphereSettings() { return config.getConfigurationSection("atmosphere"); }

    // === УДАРНАЯ ВОЛНА ===
    public ConfigurationSection getShockwaveSettings() { return config.getConfigurationSection("shockwave"); }

    // === РАДАР ===
    public ConfigurationSection getRadarSettings() { return config.getConfigurationSection("radar"); }
}