package me.n1xend.meteorites.generator;

import me.n1xend.meteorites.CustomMeteorites;
import me.n1xend.meteorites.LangManager;
import me.n1xend.meteorites.MeteoriteManager;
import me.n1xend.meteorites.config.ConfigManager;
import me.n1xend.meteorites.effects.MeteoriteEffects;
import me.n1xend.meteorites.listener.MeteoriteBlockListener;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class MeteoriteGenerator {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final LangManager langManager;
    private final MeteoriteEffects effects;
    private final Random random = new Random();

    private final Map<Integer, BukkitTask> cleanupTasks = new HashMap<>();
    private final Map<Integer, Set<Location>> meteoriteBlocks = new HashMap<>();

    public MeteoriteGenerator(JavaPlugin plugin, ConfigManager configManager, LangManager langManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.langManager = langManager;
        this.effects = new MeteoriteEffects(plugin, configManager, langManager);
    }

    public void createMeteoriteAt(Location spawnLocation, String meteoriteId) {
        if (meteoriteId == null) {
            plugin.getLogger().warning(langManager.getMessage("error.null_meteorite_id"));
            return;
        }

        ConfigurationSection meteoritesSection = configManager.getMeteoritesConfig();
        if (meteoritesSection == null) {
            plugin.getLogger().severe(langManager.getMessage("error.no_meteorites_section"));
            return;
        }

        ConfigurationSection meteoriteSection = meteoritesSection.getConfigurationSection(meteoriteId);
        if (meteoriteSection == null) {
            plugin.getLogger().severe(langManager.getMessage("error.meteorite_not_found", "id", meteoriteId));
            return;
        }

        // Отправка сообщения о спавне через локализацию
        String message = meteoriteSection.getString("chat-message");
        if (message != null && !message.trim().isEmpty()) {
            message = langManager.processPlaceholders(message,
                    "x", String.valueOf(spawnLocation.getBlockX()),
                    "z", String.valueOf(spawnLocation.getBlockZ())
            );
            Bukkit.broadcastMessage(message);
        }

        // Выполнение команд при спавне
        List<String> spawnCommands = meteoriteSection.getStringList("meteorite-spawn-commands");
        if (spawnCommands != null) {
            for (String cmd : spawnCommands) {
                if (cmd != null && !cmd.trim().isEmpty()) {
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(),
                            cmd.replace("%x%", String.valueOf(spawnLocation.getBlockX()))
                                    .replace("%z%", String.valueOf(spawnLocation.getBlockZ()))
                    );
                }
            }
        }

        World world = spawnLocation.getWorld();
        if (world == null) {
            plugin.getLogger().warning(langManager.getMessage("error.world_null"));
            return;
        }

        Location coreLocation = spawnLocation.clone();
        int surfaceY = world.getHighestBlockYAt(coreLocation.getBlockX(), coreLocation.getBlockZ(), HeightMap.OCEAN_FLOOR);
        coreLocation.setY(surfaceY + 1);

        int outerSize = meteoriteSection.getInt("outer-layer-size", 3);
        int innerSize = meteoriteSection.getBoolean("enable-inner-layer", false)
                ? meteoriteSection.getInt("inner-layer-size", 2)
                : 0;
        int coreSize = 1;

        ConfigurationSection coreBlocksSec = meteoriteSection.getConfigurationSection("core-block");
        ConfigurationSection innerBlocksSec = meteoriteSection.getConfigurationSection("inner-layer-blocks");
        ConfigurationSection outerBlocksSec = meteoriteSection.getConfigurationSection("outer-layer-blocks");

        List<Location> corePositions = new ArrayList<>();
        List<Location> innerPositions = new ArrayList<>();
        List<Location> outerPositions = new ArrayList<>();

        for (int x = -outerSize; x <= outerSize; x++) {
            for (int y = -outerSize; y <= outerSize; y++) {
                for (int z = -outerSize; z <= outerSize; z++) {
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    Location blockLoc = coreLocation.clone().add(x, y, z);
                    if (blockLoc.getBlockY() <= 0 || blockLoc.getBlockY() >= world.getMaxHeight()) continue;

                    if (dist <= coreSize - 0.5) {
                        corePositions.add(blockLoc);
                    } else if (dist <= innerSize - 0.5) {
                        innerPositions.add(blockLoc);
                    } else if (dist <= outerSize - 0.5) {
                        outerPositions.add(blockLoc);
                    }
                }
            }
        }

        int meteorId = random.nextInt(Integer.MAX_VALUE);
        meteoriteBlocks.putIfAbsent(meteorId, new HashSet<>());

        // Сбор всех материалов метеорита для персистентной очистки
        Set<String> allMaterials = new HashSet<>();
        if (coreBlocksSec != null) allMaterials.addAll(coreBlocksSec.getKeys(false));
        if (innerBlocksSec != null) allMaterials.addAll(innerBlocksSec.getKeys(false));
        if (outerBlocksSec != null) allMaterials.addAll(outerBlocksSec.getKeys(false));

        List<FallingBlock> fallingBlocks = new ArrayList<>();
        fallingBlocks.addAll(createLayer(corePositions, coreBlocksSec, world, meteorId));
        fallingBlocks.addAll(createLayer(innerPositions, innerBlocksSec, world, meteorId));
        fallingBlocks.addAll(createLayer(outerPositions, outerBlocksSec, world, meteorId));

        double speed = meteoriteSection.getDouble("meteorite-speed", 2.0);
        for (FallingBlock fb : fallingBlocks) {
            if (fb == null || fb.isDead()) continue;
            Vector velocity = new Vector(
                    (random.nextDouble() - 0.5) * 0.5,
                    -speed,
                    (random.nextDouble() - 0.5) * 0.5
            );
            fb.setVelocity(velocity);
            fb.setDropItem(false);
            fb.setMetadata(MeteoriteBlockListener.METEOR_META_KEY,
                    new FixedMetadataValue(plugin, meteorId));
        }

        effects.atmosphereTrail(fallingBlocks);

        if (configManager.areParticlesEnabled() && !fallingBlocks.isEmpty()) {
            ConfigurationSection particleEffects = configManager.getParticleEffects();
            if (particleEffects != null) {
                effects.startParticleEffect(fallingBlocks, particleEffects);
            }
        }

        int cleanupInterval = meteoriteSection.getInt("clean-up-meteorite-blocks-interval", 0);
        scheduleImpactHandling(coreLocation, meteoriteSection, cleanupInterval, meteorId, allMaterials);
    }

    private List<FallingBlock> createLayer(List<Location> positions,
                                           ConfigurationSection materialSection,
                                           World world,
                                           int meteorId) {
        List<FallingBlock> blocks = new ArrayList<>();
        if (materialSection == null || positions.isEmpty()) return blocks;

        for (Location loc : positions) {
            Material material = getRandomMaterial(materialSection);
            if (material == null || !material.isBlock()) continue;

            try {
                FallingBlock fb = world.spawnFallingBlock(loc, material.createBlockData());
                fb.setDropItem(false);
                fb.setMetadata(MeteoriteBlockListener.METEOR_META_KEY,
                        new FixedMetadataValue(plugin, meteorId));
                blocks.add(fb);
            } catch (Exception e) {
                plugin.getLogger().warning(langManager.getMessage("error.falling_block_spawn") + " " + loc + ": " + e.getMessage());
            }
        }
        return blocks;
    }

    private Material getRandomMaterial(ConfigurationSection section) {
        List<Material> pool = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key.toUpperCase());
            if (mat == null || !mat.isBlock()) continue;
            int weight = section.getInt(key, 1);
            for (int i = 0; i < Math.max(1, weight); i++) {
                pool.add(mat);
            }
        }
        if (pool.isEmpty()) return Material.STONE;
        return pool.get(random.nextInt(pool.size()));
    }

    private void scheduleImpactHandling(Location coreLocation,
                                        ConfigurationSection meteoriteSection,
                                        int cleanupInterval,
                                        int meteorId,
                                        Set<String> allMaterials) {
        double spawnHeight = configManager.getSpawnHeight();
        double speed = meteoriteSection.getDouble("meteorite-speed", 2.0);
        int fallTicks = Math.max(20,
                (int) ((spawnHeight - coreLocation.getY()) / Math.max(0.001, speed) * 20)) + 40;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            handleImpact(coreLocation, meteoriteSection, meteorId);

            // 🔥 РЕГИСТРАЦИЯ ДЛЯ ПЕРСИСТЕНТНОЙ ОЧИСТКИ
            if (plugin instanceof CustomMeteorites customPlugin && customPlugin.getMeteoriteManager() != null) {
                customPlugin.getMeteoriteManager().registerMeteorite(coreLocation, cleanupInterval, allMaterials);
            }

            // Старая система очистки как fallback
            if (cleanupInterval > 0) {
                int radius = meteoriteSection.getInt("outer-layer-size", 3);
                scheduleCleanup(coreLocation, radius, cleanupInterval, meteorId);
            }
        }, fallTicks);
    }

    private void handleImpact(Location coreLocation,
                              ConfigurationSection meteoriteSection,
                              int meteorId) {
        World world = coreLocation.getWorld();
        if (world == null) return;

        createExplosionIfEnabled(configManager.getCoreSettings(), coreLocation);
        if (meteoriteSection.getBoolean("enable-inner-layer", false)) {
            createExplosionIfEnabled(configManager.getInnerLayerSettings(), coreLocation);
        }
        createExplosionIfEnabled(configManager.getOuterLayerSettings(), coreLocation);

        ConfigurationSection coreSet = configManager.getCoreSettings();
        if (coreSet != null && coreSet.getBoolean("enable-lighting-strike", true)) {
            world.strikeLightningEffect(coreLocation);
        }

        if (configManager.isTreasureEnabled()) {
            Material type = Material.matchMaterial(configManager.getTreasureType());
            if (type == Material.CHEST || type == Material.BARREL) {
                Block block = coreLocation.getBlock();
                block.setType(type);
                addMeteoriteBlock(meteorId, block.getLocation());

                if (block.getState() instanceof Container container) {
                    TreasureLoot.fillChest(container.getInventory(), configManager.getTreasureContent(), langManager);
                    effects.playLootAnimation(block.getLocation());
                }
            }
        }

        if (configManager.isGuardianEnabled()) {
            spawnGuardian(coreLocation);
        }

        // Сообщение об ударе через локализацию
        if (coreSet != null) {
            String msgKey = "impact.message";
            String msg = langManager.getMessage(msgKey);
            if (!msg.contains("MISSING") && !msg.trim().isEmpty()) {
                Bukkit.broadcastMessage(msg);
            }
        }

        effects.spawnShockwave(coreLocation);
        effects.runRadar(coreLocation);
    }

    private void createExplosionIfEnabled(ConfigurationSection settings, Location loc) {
        if (settings != null && settings.getBoolean("enable-explosion", false)) {
            float power = (float) settings.getDouble("explosion-power", 1.0);
            boolean breakBlocks = settings.getBoolean("explosion-breaks-blocks", true);
            boolean setFire = settings.getBoolean("explosion-sets-fire", false);
            loc.getWorld().createExplosion(loc, power, setFire, breakBlocks);
        }
    }

    private void spawnGuardian(Location coreLocation) {
        ConfigurationSection possibleGuardians = configManager.getPossibleGuardians();
        if (possibleGuardians == null) return;

        List<String> enabled = new ArrayList<>();
        for (String key : possibleGuardians.getKeys(false)) {
            ConfigurationSection data = possibleGuardians.getConfigurationSection(key);
            if (data != null && data.getBoolean("enabled", false)) {
                enabled.add(key);
            }
        }
        if (enabled.isEmpty()) return;

        String id = enabled.get(random.nextInt(enabled.size()));
        ConfigurationSection data = possibleGuardians.getConfigurationSection(id);
        if (data == null) return;
        if (random.nextInt(100) >= data.getInt("chance", 10)) return;

        Location spawn = coreLocation.clone().add(
                (random.nextInt(3) - 1) * 2.0,
                1.0,
                (random.nextInt(3) - 1) * 2.0
        );

        EntityType type;
        try {
            type = EntityType.valueOf(data.getString("guardian-mob-type", "ZOMBIE").trim().toUpperCase());
        } catch (Exception e) {
            plugin.getLogger().warning(langManager.getMessage("error.invalid_mob_type", "type", data.getString("guardian-mob-type")));
            return;
        }

        if (!type.isAlive()) return;
        LivingEntity guardian = (LivingEntity) coreLocation.getWorld().spawnEntity(spawn, type);

        guardian.setCustomName(ChatColor.translateAlternateColorCodes('&',
                data.getString("guardian-display-name", "Охранник")));
        guardian.setCustomNameVisible(true);

        double health = data.getDouble("guardian-health", 20.0);
        AttributeInstance maxHealthAttr = guardian.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(health);
            guardian.setHealth(Math.min(health, guardian.getMaxHealth()));
        }

        AttributeInstance attackAttr = guardian.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(data.getDouble("guardian-attack-damage", 5.0));
        }

        AttributeInstance speedAttr = guardian.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(data.getDouble("guardian-movement-speed", 0.25));
        }

        if (data.getBoolean("enable-guardian-equipment", false)) {
            ConfigurationSection equipSec = data.getConfigurationSection("guardian-equipment");
            if (equipSec != null) {
                EntityEquipment equip = guardian.getEquipment();
                if (equip != null) {
                    equip.setItem(EquipmentSlot.HAND, createItemStack(equipSec.getString("main-hand")));
                    equip.setItem(EquipmentSlot.OFF_HAND, createItemStack(equipSec.getString("off-hand")));
                    equip.setItem(EquipmentSlot.HEAD, createItemStack(equipSec.getString("helmet")));
                    equip.setItem(EquipmentSlot.CHEST, createItemStack(equipSec.getString("chestplate")));
                    equip.setItem(EquipmentSlot.LEGS, createItemStack(equipSec.getString("leggings")));
                    equip.setItem(EquipmentSlot.FEET, createItemStack(equipSec.getString("boots")));

                    try {
                        equip.setHelmetDropChance(0f);
                        equip.setChestplateDropChance(0f);
                        equip.setLeggingsDropChance(0f);
                        equip.setBootsDropChance(0f);
                        equip.setItemInMainHandDropChance(0f);
                        equip.setItemInOffHandDropChance(0f);
                    } catch (NoSuchMethodError ignored) {
                    }
                }
            }
        }

        String msg = data.getString("player-message");
        if (msg != null && !msg.isEmpty()) {
            msg = langManager.processPlaceholders(msg);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld() == coreLocation.getWorld()
                        && p.getLocation().distance(spawn) < 10) {
                    p.sendMessage(msg);
                }
            }
        }

        String soundName = data.getString("guardian-spawn-sound");
        if (soundName != null && !soundName.trim().isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.trim().toUpperCase());
                float vol = (float) data.getDouble("guardian-spawn-sound-volume", 1.0);
                float pitch = (float) data.getDouble("guardian-spawn-sound-pitch", 1.0);
                coreLocation.getWorld().playSound(spawn, sound, vol, pitch);
            } catch (Exception e) {
                plugin.getLogger().warning(langManager.getMessage("error.invalid_sound", "sound", soundName));
            }
        }
    }

    private ItemStack createItemStack(String materialName) {
        if (materialName == null || materialName.trim().isEmpty()) return null;
        Material mat = Material.matchMaterial(materialName.trim().toUpperCase());
        return (mat != null) ? new ItemStack(mat) : null;
    }

    private void scheduleCleanup(Location coreLocation, int radius, int delaySeconds, int meteorId) {
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                World world = coreLocation.getWorld();
                if (world == null) {
                    cleanupTasks.remove(getTaskId());
                    meteoriteBlocks.remove(meteorId);
                    return;
                }

                Set<Location> blocks = meteoriteBlocks.remove(meteorId);
                if (blocks != null && !blocks.isEmpty()) {
                    for (Location loc : blocks) {
                        Block b = loc.getBlock();
                        if (b.getType() != Material.AIR) {
                            b.setType(Material.AIR);
                        }
                    }
                } else {
                    // Fallback: очистка по радиусу
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                                if (dist <= radius) {
                                    Block b = coreLocation.clone().add(dx, dy, dz).getBlock();
                                    if (b.getType() != Material.AIR) {
                                        b.setType(Material.AIR);
                                    }
                                }
                            }
                        }
                    }
                }

                cleanupTasks.remove(getTaskId());
                plugin.getLogger().info(langManager.getMessage("cleanup.fallback_cleaned", "id", String.valueOf(meteorId)));
            }
        };

        BukkitTask task = runnable.runTaskLater(plugin, delaySeconds * 20L);
        cleanupTasks.put(task.getTaskId(), task);
    }

    public void addMeteoriteBlock(int meteorId, Location location) {
        meteoriteBlocks.computeIfAbsent(meteorId, id -> new HashSet<>())
                .add(location.clone());
    }

    public void cancelCleanupTasks() {
        for (BukkitTask task : cleanupTasks.values()) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        cleanupTasks.clear();
        meteoriteBlocks.clear();
    }

    public Location findRandomSpawnLocation() {
        String worldName = configManager.getTargetWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning(langManager.getMessage("error.world_not_found", "location", worldName));
            return null;
        }

        int minX = configManager.getMinSpawnX();
        int maxX = configManager.getMaxSpawnX();
        int minZ = configManager.getMinSpawnZ();
        int maxZ = configManager.getMaxSpawnZ();
        int y = configManager.getSpawnHeight();

        int x = random.nextInt(maxX - minX + 1) + minX;
        int z = random.nextInt(maxZ - minZ + 1) + minZ;

        return new Location(world, x + 0.5, y, z + 0.5);
    }
}