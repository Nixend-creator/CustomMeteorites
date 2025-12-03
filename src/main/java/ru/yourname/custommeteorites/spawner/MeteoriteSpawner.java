package ru.yourname.custommeteorites.spawner;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3; // Добавлен импорт
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.yourname.custommeteorites.config.ConfigManager;
import ru.yourname.custommeteorites.generator.MeteoriteGenerator; // Убедитесь, что импорт правильный

import java.util.List;
import java.util.Map;
import java.util.Random;

public class MeteoriteSpawner {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MeteoriteGenerator generator; // Убедитесь, что тип правильный
    private final Random random = new Random();

    public MeteoriteSpawner(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.generator = new MeteoriteGenerator(plugin, configManager); // Убедитесь, что конструктор существует
    }

    public void startScheduler() {
        if (!configManager.isRandomMeteoritesEnabled()) {
            plugin.getLogger().info("Метеориты отключены в конфиге.");
            return;
        }
        // Используем интервал из конфига
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnRandomMeteorite, 0, configManager.getInterval() * 20L); // 20 тиков = 1 секунда
    }

    private void spawnRandomMeteorite() {
        String worldName = configManager.getTargetWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Мир " + worldName + " не найден для падения метеорита.");
            return;
        }

        // Попытаться найти точку в заданном регионе
        Location spawnLocation = findSafeSpawnLocation(world);
        if (spawnLocation != null) {
            // Выбрать тип метеорита на основе шансов
            String selectedMeteoriteId = selectMeteoriteType();
            generator.createMeteoriteAt(spawnLocation, selectedMeteoriteId);
        } else {
            plugin.getLogger().warning("Не удалось найти безопасную точку для метеорита в мире " + worldName);
        }
    }

    private String selectMeteoriteType() {
        Map<String, Object> meteoritesConfig = configManager.getMeteoritesConfig();
        int totalWeight = 0;
        for (Object obj : meteoritesConfig.values()) {
            if (obj instanceof Map) {
                Map<String, Object> meteoriteData = (Map<String, Object>) obj;
                totalWeight += (Integer) meteoriteData.getOrDefault("chance", 1);
            }
        }

        int randomValue = random.nextInt(totalWeight);
        int currentWeight = 0;
        for (Map.Entry<String, Object> entry : meteoritesConfig.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> meteoriteData = (Map<String, Object>) entry.getValue();
                currentWeight += (Integer) meteoriteData.getOrDefault("chance", 1);
                if (randomValue < currentWeight) {
                    return entry.getKey();
                }
            }
        }
        // Fallback на первый
        return meteoritesConfig.keySet().iterator().next();
    }

    private Location findSafeSpawnLocation(World world) {
        int attempts = 0;
        final int maxAttempts = 20; // Увеличим попытки

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
        return null; // Не нашли подходящую точку
    }

    private boolean isLocationSafe(Location location) {
        World world = location.getWorld();
        if (world == null) return false;

        // 1. Проверка минимального расстояния от игроков
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && player.getLocation().distance(location) < 100) { // Используем фиксированное значение или из конфига
                return false;
            }
        }

        // 2. Проверка минимального расстояния от спавна
        Location spawnLocation = world.getSpawnLocation();
        if (location.distance(spawnLocation) < 500) { // Используем фиксированное значение или из конфига
            return false;
        }

        // 3. Проверка WorldGuard
        if (configManager.isWorldGuardIntegrationEnabled()) {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(world));
            if (regions != null) {
                // Исправлено: используем BukkitAdapter.asBlockVector
                BlockVector3 worldEditLoc = BukkitAdapter.asBlockVector(location);
                ApplicableRegionSet regionSet = regions.getApplicableRegions(worldEditLoc);

                if (configManager.isProtectAllWorldGuardZones()) {
                    if (!regionSet.getRegions().isEmpty()) {
                        return false; // Защитить все регионы
                    }
                } else {
                    List<String> safeZoneNames = configManager.getWorldGuardSafeZoneNames();
                    for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : regionSet.getRegions()) {
                        if (safeZoneNames.contains(region.getId())) {
                            // Проверяем буфер - Исправлено: используем BlockVector3 и add()
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
                                return false; // Точка внутри буфера защищённой зоны
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    // Геттер для generator
    public MeteoriteGenerator getGenerator() {
        return generator;
    }
}
