package me.n1xend.meteorites;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MeteoriteManager {
    private final CustomMeteorites plugin;
    private final File saveFile;
    private final Map<UUID, MeteoriteData> activeMeteorites = new HashMap<>();
    private final Set<String> processingKeys = Collections.synchronizedSet(new HashSet<>());

    public MeteoriteManager(CustomMeteorites plugin) {
        this.plugin = plugin;
        this.saveFile = new File(plugin.getDataFolder(), "active_meteorites.yml");
        loadMeteorites();
        startCleanupWatcher();
    }

    private void loadMeteorites() {
        if (!saveFile.exists()) return;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(saveFile);
            List<Map<?, ?>> list = yaml.getMapList("meteorites");
            for (Map<?, ?> rawMap : list) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) rawMap;
                    MeteoriteData data = new MeteoriteData(map);
                    if (data.isExpired()) {
                        plugin.getLogger().info(plugin.getLangManager().getMessage("cleanup.skipped_expired"));
                        continue;
                    }
                    activeMeteorites.put(data.getUuid(), data);
                    scheduleCleanup(data);
                    plugin.getLogger().info(plugin.getLangManager().getMessage(
                            "cleanup.restored",
                            "uuid", data.getUuid().toString().substring(0, 8),
                            "location", data.getUniqueKey(),
                            "minutes", String.valueOf(data.getRemainingTime() / 60000)
                    ));
                } catch (Exception e) {
                    plugin.getLogger().warning(plugin.getLangManager().getMessage("error.loading_meteorite") + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe(plugin.getLangManager().getMessage("error.critical_loading") + ": " + e.getMessage());
        }
    }

    public void saveMeteorites() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        synchronized (activeMeteorites) {
            for (MeteoriteData data : activeMeteorites.values()) {
                if (!data.isExpired()) list.add(data.serialize());
            }
        }
        yaml.set("meteorites", list);
        try { yaml.save(saveFile); } catch (IOException e) { /* ignore */ }
    }

    public boolean registerMeteorite(Location center, long cleanupDelayTicks, Set<String> materials) {
        String key = center.getWorld().getName() + ":" + center.getBlockX() + ":" + center.getBlockY() + ":" + center.getBlockZ();
        synchronized (processingKeys) {
            if (processingKeys.contains(key)) {
                plugin.getLogger().warning(plugin.getLangManager().getMessage("error.duplicate_meteorite", "location", key));
                return false;
            }
            processingKeys.add(key);
        }
        try {
            long cleanupDelayMs = cleanupDelayTicks * 50L;
            MeteoriteData data = new MeteoriteData(center, cleanupDelayMs, materials);
            synchronized (activeMeteorites) { activeMeteorites.put(data.getUuid(), data); }
            scheduleCleanup(data);
            saveMeteorites();
            plugin.getLogger().info(plugin.getLangManager().getMessage(
                    "cleanup.registered",
                    "uuid", data.getUuid().toString().substring(0, 8),
                    "location", key,
                    "minutes", String.valueOf(cleanupDelayMs / 60000)
            ));
            return true;
        } finally {
            synchronized (processingKeys) { processingKeys.remove(key); }
        }
    }

    private void scheduleCleanup(MeteoriteData data) {
        long delayTicks = data.getRemainingTime() / 50;
        new BukkitRunnable() {
            @Override public void run() { cleanupMeteorite(data); }
        }.runTaskLater(plugin, delayTicks);
    }

    private void cleanupMeteorite(MeteoriteData data) {
        Location loc = data.getLocation(Bukkit.getServer());
        if (loc == null) {
            plugin.getLogger().warning(plugin.getLangManager().getMessage("error.world_not_found", "location", data.getUniqueKey()));
            removeMeteorite(data.getUuid());
            return;
        }
        Set<Material> meteoriteBlocks = new HashSet<>();
        for (String matName : data.getMeteoriteMaterials()) {
            try { meteoriteBlocks.add(Material.valueOf(matName.toUpperCase())); } catch (Exception ignored) {}
        }
        if (meteoriteBlocks.isEmpty()) {
            plugin.getLogger().warning(plugin.getLangManager().getMessage("error.no_materials", "uuid", data.getUuid().toString().substring(0, 8)));
            removeMeteorite(data.getUuid());
            return;
        }
        int radius = plugin.getConfigManager().getCleanupRadius();
        int cleaned = 0;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = loc.clone().add(dx, dy, dz).getBlock();
                    if (meteoriteBlocks.contains(block.getType())) {
                        block.setType(Material.AIR);
                        cleaned++;
                    }
                }
        plugin.getLogger().info(plugin.getLangManager().getMessage(
                "cleanup.removed_blocks",
                "count", String.valueOf(cleaned),
                "uuid", data.getUuid().toString().substring(0, 8)
        ));
        removeMeteorite(data.getUuid());
    }

    private void removeMeteorite(UUID uuid) {
        synchronized (activeMeteorites) { activeMeteorites.remove(uuid); }
        saveMeteorites();
    }

    private void startCleanupWatcher() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<UUID> toRemove = new ArrayList<>();
                synchronized (activeMeteorites) {
                    for (MeteoriteData data : activeMeteorites.values())
                        if (data.isExpired()) toRemove.add(data.getUuid());
                }
                for (UUID uuid : toRemove) {
                    MeteoriteData data = activeMeteorites.get(uuid);
                    if (data != null) {
                        Location loc = data.getLocation(Bukkit.getServer());
                        if (loc != null) cleanupMeteorite(data);
                    }
                }
                if (!toRemove.isEmpty()) {
                    plugin.getLogger().info(plugin.getLangManager().getMessage(
                            "cleanup.watcher_cleaned",
                            "count", String.valueOf(toRemove.size())
                    ));
                }
            }
        }.runTaskTimer(plugin, 1200, 1200);
    }

    public void shutdown() { saveMeteorites(); }
}