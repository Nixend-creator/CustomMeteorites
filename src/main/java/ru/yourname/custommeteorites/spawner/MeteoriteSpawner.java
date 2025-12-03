package ru.yourname.custommeteorites.spawner;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.yourname.custommeteorites.config.ConfigManager;
import ru.yourname.custommeteorites.generator.MeteoriteGenerator;

import java.util.*;

public class MeteoriteSpawner {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MeteoriteGenerator generator;
    private final Random random = new Random();

    public MeteoriteSpawner(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.generator = new MeteoriteGenerator(plugin, configManager);
    }

    public void startScheduler() {
        if (!configManager.isRandomMeteoritesEnabled()) {
            plugin.getLogger().info("Метеориты отключены в конфиге.");
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnRandomMeteorite, 0, configManager.getInterval() * 20L);
    }

    private void spawnRandomMeteorite() {
        String worldName = configManager.getTargetWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Мир " + worldName + " не найден для падения метеорита.");
            return;
        }

        Location spawnLocation = findSafeSpawnLocation(world);
        if (spawnLocation != null) {
            String selectedMeteoriteId = selectMeteoriteType();
            generator.createMeteoriteAt(spawnLocation, selectedMeteoriteId);
        } else {
            plugin.getLogger().warning("Не удалось найти безопасную точку для метеорита в мире " + worldName);
        }
    }

    private String selectMeteoriteType() {
        org.bukkit.configuration.ConfigurationSection meteoritesSection = configManager.getMeteoritesConfig();
        if (meteoritesSection == null || meteoritesSection.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("Нет доступных типов метеоритов в конфигурации!");
            return "1"; // Fallback
        }

        int totalWeight = 0;
        for (String key : meteoritesSection.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection meteoriteData = meteoritesSection.getConfigurationSection(key);
            if (meteoriteData != null) {
                totalWeight += meteoriteData.getInt("chance", 1);
            }
        }

        if (totalWeight <= 0) {
            plugin.getLogger().warning("Суммарный шанс всех метеоритов <= 0. Используется fallback.");
            return "1"; // Fallback
        }

        int randomValue = random.nextInt(totalWeight);
        int currentWeight = 0;
        for (String key : meteoritesSection.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection meteoriteData = meteoritesSection.getConfigurationSection(key);
            if (meteoriteData != null) {
                currentWeight += meteoriteData.getInt("chance", 1);
                if (randomValue < currentWeight) {
                    return key;
                }
            }
        }
        return meteoritesSection.getKeys(false).iterator().next();
    }

    private Location findSafeSpawnLocation(World world) {
        int attempts = 0;
        final int maxAttempts = 20;

        int minX = configManager.getMinSpawnX();
        int maxX = configManager.getMaxSpawnX();
        int minZ = configManager.getMinSpawnZ();
        int maxZ = configManager.getMaxSpawnZ();

        while (attempts < maxAttempts) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);

            Location location = new Location(world, x, configManager.getSpawnHeight(), z);

            if (isLocationSafe(location)) {
                return location;
            }
            attempts++;
        }
        return null;
    }

    private boolean isLocationSafe(Location location) {
        World world = location.getWorld();
        if (world == null) return false;

        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && player.getLocation().distance(location) < 100) {
                return false;
            }
        }

        Location spawnLocation = world.getSpawnLocation();
        if (location.distance(spawnLocation) < 500) {
            return false;
        }

        if (configManager.isWorldGuardIntegrationEnabled()) {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(world));
            if (regions != null) {
                BlockVector3 worldEditLoc = BukkitAdapter.asBlockVector(location);
                ApplicableRegionSet regionSet = regions.getApplicableRegions(worldEditLoc);

                if (configManager.isProtectAllWorldGuardZones()) {
                    if (!regionSet.getRegions().isEmpty()) {
                        return false;
                    }
                } else {
                    List<String> safeZoneNames = configManager.getWorldGuardSafeZoneNames();
                    for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : regionSet.getRegions()) {
                        if (safeZoneNames.contains(region.getId())) {
                            BlockVector3 bufferMin = worldEditLoc.add(
                                    -configManager.getWorldGuardSafeZoneBuffer(),
                                    0,
                                    -configManager.getWorldGuardSafeZoneBuffer()
                            );
                            BlockVector3 bufferMax = worldEditLoc.add(
                                    configManager.getWorldGuardSafeZoneBuffer(),
                                    0,
                                    configManager.getWorldGuardSafeZoneBuffer()
                            );

                            if (region.contains(bufferMin) && region.contains(bufferMax)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    public MeteoriteGenerator getGenerator() {
        return generator;
    }
}
