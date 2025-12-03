package ru.yourname.custommeteorites.generator;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.yourname.custommeteorites.config.ConfigManager;
import ru.yourname.custommeteorites.generator.TreasureLoot;

import java.util.*;

public class MeteoriteGenerator {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    // Тип ключа Map — Integer (taskId)
    private final Map<Integer, BukkitTask> cleanupTasks = new HashMap<>();

    public MeteoriteGenerator(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @SuppressWarnings("unchecked")
    public void createMeteoriteAt(Location spawnLocation, String meteoriteId) {
        if (spawnLocation == null || spawnLocation.getWorld() == null) return;

        Map<String, Object> meteoriteConfig = (Map<String, Object>) configManager.getMeteoritesConfig().get(meteoriteId);
        if (meteoriteConfig == null) {
            plugin.getLogger().severe("Конфигурация для метеорита " + meteoriteId + " не найдена!");
            return;
        }

        // Отправляем сообщение о появлении
        String message = (String) meteoriteConfig.getOrDefault("chat-message", "&6[Метеориты]&f Метеорит был замечен!");
        if (message != null && !message.isEmpty()) {
            message = message
                    .replace("%locationX%", String.valueOf(spawnLocation.getBlockX()))
                    .replace("%locationZ%", String.valueOf(spawnLocation.getBlockZ()));
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        }

        // Выполняем команды при появлении
        List<String> spawnCommands = (List<String>) meteoriteConfig.getOrDefault("meteorite-spawn-commands", Collections.emptyList());
        for (String command : spawnCommands) {
            if (command != null && !command.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                        .replace("%locationX%", String.valueOf(spawnLocation.getBlockX()))
                        .replace("%locationZ%", String.valueOf(spawnLocation.getBlockZ())));
            }
        }

        // Определяем центр метеорита (где упадет ядро)
        Location coreLocation = new Location(spawnLocation.getWorld(), spawnLocation.getX(), 0, spawnLocation.getZ());
        coreLocation.setY(spawnLocation.getWorld().getHighestBlockYAt(coreLocation));

        // --- Генерация структуры метеорита ---
        int outerSize = ((Number) meteoriteConfig.getOrDefault("outer-layer-size", 3)).intValue();
        int innerSize = 0;
        if ((Boolean) meteoriteConfig.getOrDefault("enable-inner-layer", false)) {
            innerSize = ((Number) meteoriteConfig.getOrDefault("inner-layer-size", 2)).intValue();
        }
        int coreSize = 1; // Размер ядра, можно сделать настраиваемым

        Map<String, Object> coreBlocks = (Map<String, Object>) meteoriteConfig.get("core-block");
        Map<String, Object> innerBlocks = (Map<String, Object>) meteoriteConfig.get("inner-layer-blocks");
        Map<String, Object> outerBlocks = (Map<String, Object>) meteoriteConfig.get("outer-layer-blocks");

        List<Location> corePositions = new ArrayList<>();
        List<Location> innerPositions = new ArrayList<>();
        List<Location> outerPositions = new ArrayList<>();

        for (int x = -outerSize; x <= outerSize; x++) {
            for (int y = -outerSize; y <= outerSize; y++) {
                for (int z = -outerSize; z <= outerSize; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    Location blockLoc = coreLocation.clone().add(x, y, z);
                    if (blockLoc.getBlockY() > 0) {
                        if (distance <= coreSize - 0.5) corePositions.add(blockLoc);
                        else if (distance <= innerSize - 0.5) innerPositions.add(blockLoc);
                        else if (distance <= outerSize - 0.5) outerPositions.add(blockLoc);
                    }
                }
            }
        }

        // Создаем FallingBlock для каждого слоя
        List<FallingBlock> fallingBlocks = new ArrayList<>();
        fallingBlocks.addAll(createLayer(corePositions, coreBlocks, spawnLocation.getWorld()));
        fallingBlocks.addAll(createLayer(innerPositions, innerBlocks, spawnLocation.getWorld()));
        fallingBlocks.addAll(createLayer(outerPositions, outerBlocks, spawnLocation.getWorld()));

        double speed = ((Number) meteoriteConfig.getOrDefault("meteorite-speed", 2.0)).doubleValue();
        for (FallingBlock fb : fallingBlocks) {
            Vector velocity = new Vector(0, -speed, 0);
            velocity.add(new Vector((Math.random() - 0.5) * 0.5, 0, (Math.random() - 0.5) * 0.5));
            fb.setVelocity(velocity);
            fb.setDropItem(false);
        }

        // Частицы
        if (configManager.areParticlesEnabled()) {
            Map<String, Object> particleEffects = configManager.getParticleEffects();
            if (particleEffects == null) particleEffects = Collections.emptyMap();
            startParticleEffect(fallingBlocks, particleEffects);
        }

        // Планируем обработку приземления
        scheduleImpactHandling(coreLocation, meteoriteConfig, fallingBlocks);

        // Планируем очистку
        int cleanupInterval = ((Number) meteoriteConfig.getOrDefault("clean-up-meteorite-blocks-interval", 0)).intValue();
        if (cleanupInterval > 0) scheduleCleanup(coreLocation, outerSize, cleanupInterval);
    }

    private List<FallingBlock> createLayer(List<Location> positions, Map<String, Object> materialMap, World world) {
        List<FallingBlock> blocks = new ArrayList<>();
        if (positions == null || positions.isEmpty()) return blocks;

        for (Location loc : positions) {
            Material material = getRandomMaterial(materialMap);
            if (material != null && material.isBlock()) {
                BlockData blockData = material.createBlockData();
                // spawn a little above to avoid clipping
                Location spawn = loc.clone().add(0, 1, 0);
                FallingBlock fb = world.spawnFallingBlock(spawn, blockData);
                blocks.add(fb);
            }
        }
        return blocks;
    }

    private Material getRandomMaterial(Map<String, Object> materialMap) {
        if (materialMap == null || materialMap.isEmpty()) return Material.STONE;
        List<Material> materials = new ArrayList<>();
        for (Map.Entry<String, Object> entry : materialMap.entrySet()) {
            Material mat = Material.getMaterial(entry.getKey());
            int weight = 1;
            if (entry.getValue() instanceof Number) weight = ((Number) entry.getValue()).intValue();
            if (mat != null && mat.isBlock()) {
                for (int i = 0; i < Math.max(1, weight); i++) materials.add(mat);
            }
        }
        if (materials.isEmpty()) return Material.STONE;
        Collections.shuffle(materials);
        return materials.get(0);
    }

    @SuppressWarnings("unchecked")
    private void startParticleEffect(List<FallingBlock> fallingBlocks, Map<String, Object> particleEffects) {
        if (fallingBlocks == null || fallingBlocks.isEmpty()) return;

        for (FallingBlock fb : new ArrayList<>(fallingBlocks)) {
            // protect against null maps
            Map<String, Object> effects = particleEffects == null ? Collections.emptyMap() : particleEffects;

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (fb == null || fb.isDead() || fb.isOnGround()) {
                        cancel();
                        return;
                    }

                    if (effects.isEmpty()) {
                        // default particle
                        fb.getWorld().spawnParticle(Particle.FLAME, fb.getLocation(), 2, 0.1, 0.1, 0.1, 0.02);
                        return;
                    }

                    try {
                        List<String> effectKeys = new ArrayList<>(effects.keySet());
                        String randomEffectKey = effectKeys.get(new Random().nextInt(effectKeys.size()));
                        Map<String, Object> effectData = (Map<String, Object>) effects.get(randomEffectKey);

                        if (!(Boolean) effectData.getOrDefault("enabled", true)) return;
                        if (Math.random() * 100 > ((Number) effectData.getOrDefault("chance", 100.0)).doubleValue()) return;

                        String particleName = (String) effectData.getOrDefault("particle-effect", "FLAME");
                        Particle particle = Particle.valueOf(particleName);
                        int amount = ((Number) effectData.getOrDefault("amount", 1)).intValue();
                        double spreadX = ((Number) effectData.getOrDefault("spreadX", effectData.getOrDefault("spread", 0.1))).doubleValue();
                        double spreadY = ((Number) effectData.getOrDefault("spreadY", effectData.getOrDefault("spread", 0.1))).doubleValue();
                        double spreadZ = ((Number) effectData.getOrDefault("spreadZ", effectData.getOrDefault("spread", 0.1))).doubleValue();
                        double speed = ((Number) effectData.getOrDefault("speed", 0.05)).doubleValue();

                        fb.getWorld().spawnParticle(particle, fb.getLocation(), amount, spreadX, spreadY, spreadZ, speed);
                    } catch (Throwable t) {
                        // don't spam console, but cancel if something goes wrong repeatedly
                        cancel();
                    }
                }
            }.runTaskTimerAsynchronously(plugin, 0L, Math.max(1L, configManager.getParticleInterval()) * 2L);

            // we don't store these tasks in cleanupTasks (cleanupTasks used for block cleanup only)
        }
    }

    private void scheduleImpactHandling(Location coreLocation, Map<String, Object> meteoriteConfig, List<FallingBlock> fallingBlocks) {
        // грубая оценка времени падения
        double spawnHeight = configManager.getSpawnHeight();
        double fallSpeed = ((Number) meteoriteConfig.getOrDefault("meteorite-speed", 2.0)).doubleValue();
        int estimatedFallTicks = (int) ((spawnHeight / Math.max(0.1, fallSpeed)) * 2) + 40;

        new BukkitRunnable() {
            @Override
            public void run() {
                handleImpact(coreLocation, meteoriteConfig);
            }
        }.runTaskLater(plugin, estimatedFallTicks);
    }

    @SuppressWarnings("unchecked")
    private void handleImpact(Location coreLocation, Map<String, Object> meteoriteConfig) {
        World world = coreLocation.getWorld();
        if (world == null) return;

        Map<String, Object> coreSettings = configManager.getCoreSettings();
        if ((Boolean) coreSettings.getOrDefault("enable-explosion", true)) {
            world.createExplosion(coreLocation, ((Number) coreSettings.getOrDefault("explosion-power", 6.0)).floatValue(),
                    (Boolean) coreSettings.getOrDefault("explosion-breaks-blocks", true),
                    (Boolean) coreSettings.getOrDefault("explosion-sets-fire", true));
        }

        Map<String, Object> innerSettings = configManager.getInnerLayerSettings();
        if ((Boolean) innerSettings.getOrDefault("enable-explosion", false)) {
            world.createExplosion(coreLocation, ((Number) innerSettings.getOrDefault("explosion-power", 1.0)).floatValue(),
                    (Boolean) innerSettings.getOrDefault("explosion-breaks-blocks", false),
                    (Boolean) innerSettings.getOrDefault("explosion-sets-fire", true));
        }

        Map<String, Object> outerSettings = configManager.getOuterLayerSettings();
        if ((Boolean) outerSettings.getOrDefault("enable-explosion", false)) {
            world.createExplosion(coreLocation, ((Number) outerSettings.getOrDefault("explosion-power", 1.0)).floatValue(),
                    (Boolean) outerSettings.getOrDefault("explosion-breaks-blocks", false),
                    (Boolean) outerSettings.getOrDefault("explosion-sets-fire", true));
        }

        if ((Boolean) configManager.getCoreSettings().getOrDefault("enable-lighting-strike", true)) {
            world.strikeLightningEffect(coreLocation);
        }

        if (configManager.isTreasureEnabled()) {
            Location treasureLoc = coreLocation.clone();
            Block treasureBlock = treasureLoc.getBlock();
            Material treasureMat = Material.valueOf(configManager.getTreasureType());
            if (treasureMat == Material.CHEST || treasureMat == Material.BARREL) {
                treasureBlock.setType(treasureMat);
                BlockState state = treasureBlock.getState();
                if (state instanceof Container container) {
                    Inventory inv = container.getInventory();
                    TreasureLoot.fillChest(inv, configManager.getTreasureContent());
                }
            }
        }

        if (configManager.isGuardianEnabled()) {
            spawnGuardian(coreLocation);
        }

        String impactMessage = (String) configManager.getCoreSettings().getOrDefault("message", "");
        if (impactMessage != null && !impactMessage.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', impactMessage));
        }
        List<String> impactCommands = (List<String>) configManager.getCoreSettings().getOrDefault("commands", Collections.emptyList());
        for (String command : impactCommands) {
            if (command != null && !command.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                        .replace("%locationX%", String.valueOf(coreLocation.getBlockX()))
                        .replace("%locationY%", String.valueOf(coreLocation.getBlockY()))
                        .replace("%locationZ%", String.valueOf(coreLocation.getBlockZ())));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void spawnGuardian(Location coreLocation) {
        Map<String, Object> possibleGuardians = configManager.getPossibleGuardians();
        List<String> enabledGuardians = new ArrayList<>();
        for (Map.Entry<String, Object> entry : possibleGuardians.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> guardianData = (Map<String, Object>) entry.getValue();
                if ((Boolean) guardianData.getOrDefault("enabled", false)) {
                    enabledGuardians.add(entry.getKey());
                }
            }
        }

        if (enabledGuardians.isEmpty()) return;

        String selectedGuardianId = enabledGuardians.get(new Random().nextInt(enabledGuardians.size()));
        Map<String, Object> guardianData = (Map<String, Object>) possibleGuardians.get(selectedGuardianId);

        if (Math.random() * 100 >= ((Number) guardianData.getOrDefault("chance", 10.0)).doubleValue()) return;

        Location spawnLoc = coreLocation.clone().add(
                (new Random().nextInt(3) - 1) * 2,
                0,
                (new Random().nextInt(3) - 1) * 2
        );

        EntityType entityType = EntityType.valueOf((String) guardianData.getOrDefault("guardian-mob-type", "ZOMBIE"));
        if (!entityType.isAlive()) {
            plugin.getLogger().warning("Неверный тип моба для охранника: " + entityType);
            return;
        }

        LivingEntity guardian = (LivingEntity) coreLocation.getWorld().spawnEntity(spawnLoc, entityType);

        String displayName = (String) guardianData.getOrDefault("guardian-display-name", "Охранник");
        guardian.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
        guardian.setCustomNameVisible(true);

        double health = ((Number) guardianData.getOrDefault("guardian-health", guardian.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue())).doubleValue();
        guardian.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        guardian.setHealth(Math.min(guardian.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue(), health));

        double damage = ((Number) guardianData.getOrDefault("guardian-attack-damage", 5.0)).doubleValue();
        guardian.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);

        double speed = ((Number) guardianData.getOrDefault("guardian-movement-speed", 0.25)).doubleValue();
        guardian.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);

        if ((Boolean) guardianData.getOrDefault("enable-guardian-equipment", false)) {
            Map<String, Object> equipment = (Map<String, Object>) guardianData.get("guardian-equipment");
            if (equipment != null) {
                org.bukkit.inventory.EntityEquipment equip = guardian.getEquipment();
                if (equip != null) {
                    equip.setItem(org.bukkit.inventory.EquipmentSlot.HAND, createItemStack((String) equipment.get("main-hand")));
                    equip.setItem(org.bukkit.inventory.EquipmentSlot.OFF_HAND, createItemStack((String) equipment.get("off-hand")));
                    equip.setItem(org.bukkit.inventory.EquipmentSlot.HEAD, createItemStack((String) equipment.get("helmet")));
                    equip.setItem(org.bukkit.inventory.EquipmentSlot.CHEST, createItemStack((String) equipment.get("chestplate")));
                    equip.setItem(org.bukkit.inventory.EquipmentSlot.LEGS, createItemStack((String) equipment.get("leggings")));
                    equip.setItem(org.bukkit.inventory.EquipmentSlot.FEET, createItemStack((String) equipment.get("boots")));
                    equip.setItemInMainHandDropChance(0F);
                    equip.setItemInOffHandDropChance(0F);
                    equip.setHelmetDropChance(0F);
                    equip.setChestplateDropChance(0F);
                    equip.setLeggingsDropChance(0F);
                    equip.setBootsDropChance(0F);
                }
            }
        }

        String playerMessage = (String) guardianData.getOrDefault("player-message", "");
        if (!playerMessage.isEmpty()) {
            for (Player player : coreLocation.getWorld().getPlayers()) {
                if (player.getLocation().distance(spawnLoc) < 10) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerMessage));
                }
            }
        }

        String soundName = (String) guardianData.getOrDefault("guardian-spawn-sound", "");
        if (!soundName.isEmpty()) {
            float volume = ((Number) guardianData.getOrDefault("guardian-spawn-sound-volume", 1.0)).floatValue();
            float pitch = ((Number) guardianData.getOrDefault("guardian-spawn-sound-pitch", 1.0)).floatValue();
            coreLocation.getWorld().playSound(spawnLoc, soundName, volume, pitch);
        }
    }

    private ItemStack createItemStack(String materialName) {
        if (materialName == null || materialName.isEmpty()) return null;
        Material mat = Material.getMaterial(materialName);
        if (mat == null) return null;
        return new ItemStack(mat);
    }

    private void scheduleCleanup(Location coreLocation, int radius, int delaySeconds) {
        BukkitTask cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            Location blockLoc = coreLocation.clone().add(x, y, z);
                            double distance = Math.sqrt(x * x + y * y + z * z);
                            if (distance <= radius) {
                                Block block = blockLoc.getBlock();
                                if (block.getType() != Material.AIR) {
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }
                }
                cleanupTasks.remove(this.getTaskId());
                plugin.getLogger().info("Метеорит очищен по таймеру.");
            }
        }.runTaskLater(plugin, Math.max(1L, delaySeconds) * 20L);

        cleanupTasks.put(cleanupTask.getTaskId(), cleanupTask);
    }

    public void cancelCleanupTasks() {
        for (BukkitTask task : cleanupTasks.values()) {
            try { task.cancel(); } catch (Throwable ignored) {}
        }
        cleanupTasks.clear();
    }
}
