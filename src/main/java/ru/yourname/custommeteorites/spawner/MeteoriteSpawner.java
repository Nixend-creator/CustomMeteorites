package ru.yourname.custommeteorites.meteorite;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.yourname.custommeteorites.Main;

import java.util.*;

/**
 * Улучшенная реализация MeteoriteGenerator (вариант B + D + F):
 * - anti-lag спавн по чанкам / ограничение блоков за тик
 * - бонусный лут при падении
 * - автоматический timed-reload конфигов
 */
public class MeteoriteGenerator {

    private final Main plugin;
    private final ConfigurationSection meteoriteConfig;
    private final Random random = new Random();

    // limits
    private static final int MAX_BLOCKS_PER_TICK = 40; // anti-lag

    public MeteoriteGenerator(Main plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.meteoriteConfig = config;

        // Если в конфиге указан auto-reload, запустим задачу
        scheduleConfigAutoReload();
    }

    /**
     * Основной вызов: спавнит метеорит около игрока (или в targetLoc)
     */
    public void spawnMeteorite(Player target) {
        if (meteoriteConfig == null) {
            plugin.getLogger().warning("Meteorite config section is missing!");
            return;
        }

        Location targetLoc = target.getLocation().clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
        World world = targetLoc.getWorld();
        if (world == null) return;

        int spawnHeight = Math.min(world.getMaxHeight() - 5, plugin.getConfig().getInt("random-meteorite-spawn-height", 150));
        Location spawnLoc = new Location(world, targetLoc.getX(), spawnHeight, targetLoc.getZ());

        String sizeKey = getRandomKey(meteoriteConfig);
        if (sizeKey == null) {
            plugin.getLogger().warning("Meteorite type not found in config!");
            return;
        }

        ConfigurationSection sizeSection = meteoriteConfig.getConfigurationSection(sizeKey);
        if (sizeSection == null) return;

        int outerSize = sizeSection.getInt("outer-layer-size", 3);
        int innerSize = sizeSection.getBoolean("enable-inner-layer", false) ? sizeSection.getInt("inner-layer-size", 2) : 0;
        int coreSize = 1;

        // Соберём позиции для слоёв (в локальных координатах)
        List<Location> positions = new ArrayList<>();
        for (int x = -outerSize; x <= outerSize; x++) {
            for (int y = -outerSize; y <= outerSize; y++) {
                for (int z = -outerSize; z <= outerSize; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance <= outerSize - 0.5) {
                        Location l = spawnLoc.clone().add(x, y, z);
                        if (l.getBlockY() > 0 && l.getBlockY() < world.getMaxHeight()) positions.add(l);
                    }
                }
            }
        }

        // Подготавливаем материалы по слоям
        List<Material> coreMats = loadWeightedMaterials(sizeSection.getConfigurationSection("core-block"));
        List<Material> innerMats = loadWeightedMaterials(sizeSection.getConfigurationSection("inner-layer-blocks"));
        List<Material> outerMats = loadWeightedMaterials(sizeSection.getConfigurationSection("outer-layer-blocks"));

        // Создадим очередь спавна FallingBlock'ов — ограничиваем количество за тик
        Queue<FallingSpawn> queue = new LinkedList<>();
        for (Location loc : positions) {
            double distCenter = loc.distance(spawnLoc);
            Material mat = Material.STONE;
            if (distCenter <= coreSize + 0.5 && !coreMats.isEmpty()) mat = coreMats.get(random.nextInt(coreMats.size()));
            else if (distCenter <= innerSize + 0.5 && !innerMats.isEmpty()) mat = innerMats.get(random.nextInt(innerMats.size()));
            else if (!outerMats.isEmpty()) mat = outerMats.get(random.nextInt(outerMats.size()));

            queue.add(new FallingSpawn(loc, mat));
        }

        // По чанкам — сперва пометим чанки и загрузим их, затем спавним по частям
        Set<Chunk> neededChunks = new HashSet<>();
        for (FallingSpawn fs : queue) {
            neededChunks.add(fs.location.getChunk());
        }
        for (Chunk c : neededChunks) {
            if (!c.isLoaded()) c.setForceLoaded(true);
        }

        // Спавним по MAX_BLOCKS_PER_TICK за тик
        new BukkitRunnable() {
            @Override
            public void run() {
                int count = 0;
                while (count < MAX_BLOCKS_PER_TICK && !queue.isEmpty()) {
                    FallingSpawn fs = queue.poll();
                    try {
                        if (fs.location.getWorld() == null) continue;
                        if (fs.location.getBlockY() <= 0 || fs.location.getBlockY() >= fs.location.getWorld().getMaxHeight()) continue;
                        FallingBlock fb = fs.location.getWorld().spawnFallingBlock(fs.location, fs.material.createBlockData());
                        fb.setDropItem(false);
                        fb.setVelocity(new Vector((random.nextDouble() - 0.5) * 0.5, -sizeSection.getDouble("meteorite-speed", 2.0), (random.nextDouble() - 0.5) * 0.5));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Ошибка при спавне FallingBlock: " + e.getMessage());
                    }
                    count++;
                }

                if (queue.isEmpty()) {
                    // выведем сообщение и отменим
                    this.cancel();
                    // отпустим чанки
                    for (Chunk c : neededChunks) {
                        try { c.setForceLoaded(false); } catch (Exception ignored) {}
                    }
                    // Запланируем проверку падения и обработку удара через примерное время
                    int fallTicks = Math.max(20, (int) Math.ceil((spawnLoc.getY() - 1) / Math.max(0.1, sizeSection.getDouble("meteorite-speed", 2.0))));
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            handleImpact(spawnLoc, sizeSection);
                        }
                    }.runTaskLater(plugin, fallTicks + 20L);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Индикация — настраиваем сообщение
        String chat = sizeSection.getString("chat-message", "&6[Метеорит]&f Метеорит замечен!");
        if (chat != null && !chat.isEmpty()) {
            chat = chat.replace("%x%", String.valueOf(spawnLoc.getBlockX())).replace("%z%", String.valueOf(spawnLoc.getBlockZ()));
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', chat));
        }
    }


    private void handleImpact(Location coreLocation, ConfigurationSection sizeSection) {
        World world = coreLocation.getWorld();
        if (world == null) return;

        // Взрыв
        ConfigurationSection coreSettings = plugin.getConfig().getConfigurationSection("core-settings");
        if (coreSettings != null && coreSettings.getBoolean("enable-explosion", true)) {
            world.createExplosion(coreLocation, (float) coreSettings.getDouble("explosion-power", 6.0), coreSettings.getBoolean("explosion-breaks-blocks", true));
        }

        // Бонусный лут
        dropBonusLoot(coreLocation, sizeSection);

        // Сундук/баррель с содержимым (если включено в глобальном конфигах)
        if (plugin.getConfig().getBoolean("enable-meteorite-treasure", true)) {
            String tb = plugin.getConfig().getString("treasure-barrel-or-chest", "CHEST");
            Material mat = Material.matchMaterial(tb);
            if (mat == Material.CHEST || mat == Material.BARREL) {
                coreLocation.getBlock().setType(mat);
                if (coreLocation.getBlock().getState() instanceof org.bukkit.block.Container container) {
                    TreasureLoot.fillChest(container.getInventory(), plugin.getConfig().getConfigurationSection("treasure-content"));
                }
            }
        }

        // Охранник
        if (plugin.getConfig().getBoolean("enable-treasure-guardian", true)) {
            spawnGuardian(coreLocation);
        }

        // Сообщение об ударе
        String msg = plugin.getConfig().getConfigurationSection("core-settings").getString("message", "&6[Метеорит]&f Метеорит упал!");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private void dropBonusLoot(Location loc, ConfigurationSection sizeSection) {
        /*
n         * Бросим пару случайных предметов возле центра с шансом, зависящим от секции метеорита
         */
        int bonusChance = sizeSection.getInt("bonus-loot-chance", sizeSection.getInt("chance", 10)); // fallback
        int attempts = Math.max(1, sizeSection.getInt("bonus-loot-attempts", 2));

        ConfigurationSection treasureSection = plugin.getConfig().getConfigurationSection("treasure-content");
        if (treasureSection == null) return;

        for (int a = 0; a < attempts; a++) {
            if (random.nextInt(100) >= bonusChance) continue;
            // случайный элемент из treasure-content
            List<String> keys = new ArrayList<>(treasureSection.getKeys(false));
            if (keys.isEmpty()) continue;
            String chosen = keys.get(random.nextInt(keys.size()));
            ConfigurationSection item = treasureSection.getConfigurationSection(chosen);
            if (item == null || !item.getBoolean("enabled", false)) continue;

            String type = item.getString("item-type", "STONE");
            Material mat = Material.matchMaterial(type);
            if (mat == null) mat = Material.STONE;
            int amount = Math.max(1, item.getInt("amount", 1));

            ItemStack stack = new ItemStack(mat, amount);
            // простые параметры (displayName, enchants и т.д.) можно добавить при необходимости

            // бросим предмет рядом
            Location dropLoc = loc.clone().add((random.nextDouble() - 0.5) * 2.0, 1.0, (random.nextDouble() - 0.5) * 2.0);
            loc.getWorld().dropItemNaturally(dropLoc, stack);
        }
    }

    private void spawnGuardian(Location coreLocation) {
        ConfigurationSection guardians = plugin.getConfig().getConfigurationSection("possible-guardians");
        if (guardians == null) return;
        List<String> keys = new ArrayList<>(guardians.getKeys(false));
        if (keys.isEmpty()) return;
        String id = keys.get(random.nextInt(keys.size()));
        ConfigurationSection data = guardians.getConfigurationSection(id);
        if (data == null || !data.getBoolean("enabled", false)) return;
        if (random.nextInt(100) >= data.getInt("chance", 10)) return;

        String mob = data.getString("guardian-mob-type", "ZOMBIE").toUpperCase();
        EntityType type;
        try { type = EntityType.valueOf(mob); } catch (IllegalArgumentException ex) { return; }
        if (!type.isAlive()) return;

        World world = coreLocation.getWorld();
        if (world == null) return;

        Location spawn = coreLocation.clone().add((random.nextInt(3) - 1) * 2, 1, (random.nextInt(3) - 1) * 2);
        if (!(world.spawnEntity(spawn, type) instanceof LivingEntity)) return;

        LivingEntity guardian = (LivingEntity) world.spawnEntity(spawn, type);
        guardian.setCustomName(ChatColor.translateAlternateColorCodes('&', data.getString("guardian-display-name", "Охранник")));
        guardian.setCustomNameVisible(true);
        try {
            if (guardian.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) guardian.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(data.getDouble("guardian-health", 20.0));
            if (guardian.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) guardian.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(data.getDouble("guardian-attack-damage", 5.0));
            if (guardian.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) guardian.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(data.getDouble("guardian-movement-speed", 0.25));
        } catch (Exception ignored) {}
    }

    private List<Material> loadWeightedMaterials(ConfigurationSection section) {
        List<Material> result = new ArrayList<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m == null) continue;
            int weight = 1;
            try { weight = section.getInt(key, 1); } catch (Exception ignored) {}
            for (int i = 0; i < Math.max(1, weight); i++) result.add(m);
        }
        return result;
    }

    private String getRandomKey(ConfigurationSection section) {
        if (section == null) return null;
        List<String> keys = new ArrayList<>(section.getKeys(false));
        if (keys.isEmpty()) return null;
        return keys.get(random.nextInt(keys.size()));
    }

    /**
     * Timed auto-reload config (feature F)
     */
    private void scheduleConfigAutoReload() {
        int interval = plugin.getConfig().getInt("auto-reload-interval-seconds", 0);
        if (interval <= 0) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    plugin.reloadConfig();
                    plugin.getLogger().info("CustomMeteorites: конфиг автоматически перезагружен.");
                } catch (Exception ex) {
                    plugin.getLogger().warning("Ошибка при авто-перезагрузке конфига: " + ex.getMessage());
                }
            }
        }.runTaskTimer(plugin, interval*20L, interval*20L);
    }

    // Вспомогательный объект-описание падения
    private static class FallingSpawn {
        private final Location location;
        private final Material material;
        private FallingSpawn(Location location, Material material) {
            this.location = location;
            this.material = material;
        }
    }
}
