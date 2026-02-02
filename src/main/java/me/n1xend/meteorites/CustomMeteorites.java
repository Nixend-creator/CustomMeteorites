package me.n1xend.meteorites;

import me.n1xend.meteorites.commands.MeteorCommand;
import me.n1xend.meteorites.config.ConfigManager;
import me.n1xend.meteorites.generator.MeteoriteGenerator;
import me.n1xend.meteorites.listener.MeteoriteBlockListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class CustomMeteorites extends JavaPlugin {

    public static final String VERSION = "2.0.0";
    private ConfigManager configManager;
    private LangManager langManager;
    private MeteoriteGenerator meteoriteGenerator;
    private MeteoriteManager meteoriteManager;
    private BukkitTask randomMeteorTask; // ← ПОЛЕ ЗАДАЧИ
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigurationSerialization.registerClass(MeteoriteData.class);

        langManager = new LangManager(this);
        langManager.loadLanguages();

        configManager = new ConfigManager(this);
        configManager.loadConfig();

        meteoriteManager = new MeteoriteManager(this);
        meteoriteGenerator = new MeteoriteGenerator(this, configManager, langManager);

        getServer().getPluginManager().registerEvents(
                new MeteoriteBlockListener(this, meteoriteGenerator),
                this
        );

        getCommand("meteor").setExecutor(
                new MeteorCommand(this, configManager, meteoriteGenerator, langManager)
        );

        if (configManager.isRandomMeteoritesEnabled()) {
            startRandomMeteorites();
        }

        getLogger().info(langManager.getMessage("plugin.enabled", "version", VERSION));
    }

    @Override
    public void onDisable() {
        stopRandomMeteorites();
        if (meteoriteGenerator != null) {
            meteoriteGenerator.cancelCleanupTasks();
        }
        if (meteoriteManager != null) {
            meteoriteManager.shutdown();
        }
        getLogger().info(langManager.getMessage("plugin.disabled"));
    }

    public void startRandomMeteorites() {
        if (randomMeteorTask != null && !randomMeteorTask.isCancelled()) return;

        int interval = Math.max(60, configManager.getInterval());
        randomMeteorTask = getServer().getScheduler().runTaskTimer(
                this,
                this::spawnRandomMeteorite,
                interval * 20L,
                interval * 20L
        );
        getLogger().info(langManager.getMessage("random.enabled", "interval", String.valueOf(interval)));
    }

    public void stopRandomMeteorites() {
        if (randomMeteorTask != null) {
            randomMeteorTask.cancel();
            randomMeteorTask = null;
            getLogger().info(langManager.getMessage("random.disabled"));
        }
    }

    // 🔧 ДОБАВЛЕН ГЕТТЕР ДЛЯ ДОСТУПА ИЗ КОМАНД
    public BukkitTask getRandomMeteorTask() {
        return randomMeteorTask;
    }

    private void spawnRandomMeteorite() {
        var loc = meteoriteGenerator.findRandomSpawnLocation();
        if (loc == null) return;

        String meteorId = pickRandomMeteoriteId();
        if (meteorId == null) {
            getLogger().warning(langManager.getMessage("error.no_meteorites_configured"));
            return;
        }

        meteoriteGenerator.createMeteoriteAt(loc, meteorId);
    }

    private String pickRandomMeteoriteId() {
        var section = configManager.getMeteoritesConfig();
        if (section == null || section.getKeys(false).isEmpty()) return null;

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

    public ConfigManager getConfigManager() { return configManager; }
    public LangManager getLangManager() { return langManager; }
    public MeteoriteGenerator getMeteoriteGenerator() { return meteoriteGenerator; }
    public MeteoriteManager getMeteoriteManager() { return meteoriteManager; }
}