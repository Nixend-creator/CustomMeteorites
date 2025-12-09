package me.n1xend.meteorites;

import me.n1xend.meteorites.commands.MeteorCommand;
import me.n1xend.meteorites.config.ConfigManager;
import me.n1xend.meteorites.generator.MeteoriteGenerator;
import me.n1xend.meteorites.listener.MeteoriteBlockListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CustomMeteorites extends JavaPlugin {

    private ConfigManager configManager;
    private MeteoriteGenerator meteoriteGenerator;
    private BukkitTask randomMeteorTask;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.loadConfig();

        meteoriteGenerator = new MeteoriteGenerator(this, configManager);

        getServer().getPluginManager().registerEvents(
                new MeteoriteBlockListener(this, meteoriteGenerator),
                this
        );

        getCommand("meteor").setExecutor(
                new MeteorCommand(this, configManager, meteoriteGenerator)
        );

        if (configManager.isRandomMeteoritesEnabled()) {
            startRandomMeteorites();
        }

        getLogger().info("[CustomMeteorites] Enabled");
    }

    @Override
    public void onDisable() {
        stopRandomMeteorites();
        if (meteoriteGenerator != null) {
            meteoriteGenerator.cancelCleanupTasks();
        }
        getLogger().info("[CustomMeteorites] Disabled");
    }

    // === Рандомні метеорити ===

    public void startRandomMeteorites() {
        if (randomMeteorTask != null && !randomMeteorTask.isCancelled()) return;

        int interval = Math.max(60, configManager.getInterval());
        randomMeteorTask = getServer().getScheduler().runTaskTimer(
                this,
                this::spawnRandomMeteorite,
                interval * 20L,
                interval * 20L
        );
        getLogger().info("[CustomMeteorites] Random meteorites started (interval " + interval + "s).");
    }

    public void stopRandomMeteorites() {
        if (randomMeteorTask != null) {
            randomMeteorTask.cancel();
            randomMeteorTask = null;
            getLogger().info("[CustomMeteorites] Random meteorites stopped.");
        }
    }

    private void spawnRandomMeteorite() {
        String worldName = configManager.getTargetWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("[CustomMeteorites] World '" + worldName + "' not found.");
            return;
        }

        int minX = configManager.getMinSpawnX();
        int maxX = configManager.getMaxSpawnX();
        int minZ = configManager.getMinSpawnZ();
        int maxZ = configManager.getMaxSpawnZ();
        int y = configManager.getSpawnHeight();

        int x = randomInt(minX, maxX);
        int z = randomInt(minZ, maxZ);

        Location loc = new Location(world, x + 0.5, y, z + 0.5);

        String meteorId = pickRandomMeteoriteId();
        if (meteorId == null) {
            getLogger().warning("[CustomMeteorites] No meteorites configured in config.yml");
            return;
        }

        meteoriteGenerator.createMeteoriteAt(loc, meteorId);
    }

    private String pickRandomMeteoriteId() {
        var section = configManager.getMeteoritesConfig();
        if (section == null) return null;

        List<String> ids = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;

        for (String key : section.getKeys(false)) {
            int chance = section.getInt(key + ".chance", 1);
            if (chance <= 0) continue;
            ids.add(key);
            weights.add(chance);
            total += chance;
        }

        if (ids.isEmpty() || total <= 0) return null;

        int roll = random.nextInt(total);
        int sum = 0;
        for (int i = 0; i < ids.size(); i++) {
            sum += weights.get(i);
            if (roll < sum) return ids.get(i);
        }
        return ids.get(0);
    }

    private int randomInt(int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MeteoriteGenerator getMeteoriteGenerator() {
        return meteoriteGenerator;
    }
}
