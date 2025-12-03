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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.yourname.custommeteorites.config.ConfigManager;
import ru.yourname.custommeteorites.generator.TreasureLoot;

import java.util.*;

public class MeteoriteGenerator {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<Integer, BukkitTask> cleanupTasks = new HashMap<>();

    public MeteoriteGenerator(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void createMeteoriteAt(Location spawnLocation, String meteoriteId) {
        org.bukkit.configuration.ConfigurationSection meteoriteSection = configManager.getMeteoritesConfig().getConfigurationSection(meteoriteId);
        if (meteoriteSection == null) {
            plugin.getLogger().severe("Конфигурация для метеорита " + meteoriteId + " не найдена!");
            return;
        }
        Map<String, Object> meteoriteConfig = meteoriteSection.getValues(false);

        // Отправляем сообщение о появлении
        String message = meteoriteSection.getString("chat-message", "&6[Метеориты]&f Метеорит был замечен!");
        if (message != null && !message.isEmpty()) {
            message = message
                    .replace("%locationX%", String.valueOf(spawnLocation.getBlockX()))
                    .replace("%locationZ%", String.valueOf(spawnLocation.getBlockZ()));
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        }

        // Выполняем команды при появлении
        List<String> spawnCommands = meteoriteSection.getStringList("meteorite-spawn-commands");
        for (String command : spawnCommands) {
            if (command != null && !command.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                        .replace("%locationX%", String.valueOf(spawnLocation.getBlockX()))
                        .replace("%locationZ%", String.valueOf(spawnLocation.getBlockZ())));
            }
        }

        Location coreLocation = new Location(spawnLocation.getWorld(), spawnLocation.getX(), 0, spawnLocation.getZ());
        coreLocation.setY(spawnLocation.getWorld().getHighestBlockYAt(coreLocation));

        int outerSize = meteoriteSection.getInt("outer-layer-size", 3);
        int innerSize = 0;
        if (meteoriteSection.getBoolean("enable-inner-layer", false)) {
            innerSize = meteoriteSection.getInt("inner-layer-size", 2);
        }
        int coreSize = 1;

        Map<String, Object> coreBlocks = (Map<String, Object>) meteoriteSection.getConfigurationSection("core-block").getValues(false);
        Map<String, Object> innerBlocks = (Map<String, Object>) meteoriteSection.getConfigurationSection("inner-layer-blocks").getValues(false);
        Map<String, Object> outerBlocks = (Map<String, Object>) meteoriteSection.getConfigurationSection("outer-layer-blocks").getValues(false);

        List<Location> corePositions = new ArrayList<>();
        List<Location> innerPositions = new ArrayList<>();
        List<Location> outerPositions = new ArrayList<>();

        for (int x = -outerSize; x <= outerSize; x++) {
            for (int y = -outerSize; y <= outerSize; y++) {
                for (int z = -outerSize; z <= outerSize; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    Location blockLoc = coreLocation.clone().add(x, y, z);
                    if (blockLoc.getBlockY() > 0) {
                        if (distance <= coreSize - 0.5) {
                            corePositions.add(blockLoc);
                        } else if (distance <= innerSize - 0.5) {
                            innerPositions.add(blockLoc);
                        } else if (distance <= outerSize - 0.5) {
                            outerPositions.add(blockLoc);
                        }
                    }
                }
            }
        }

        List<FallingBlock> fallingBlocks = new ArrayList<>();
        fallingBlocks.addAll(createLayer(corePositions, coreBlocks, spawnLocation.getWorld()));
        fallingBlocks.addAll(createLayer(innerPositions, innerBlocks, spawnLocation.getWorld()));
        fallingBlocks.addAll(createLayer(outerPositions, outerBlocks, spawnLocation.getWorld()));

        double speed = meteoriteSection.getDouble("meteorite-speed", 2.0);
        for (FallingBlock fb : fallingBlocks) {
            Vector velocity = new Vector(0, -speed, 0);
            velocity.add(new Vector((Math.random() - 0.5) * 0.5, 0, (Math.random() - 0.5) * 0.5));
            fb.setVelocity(velocity);
            fb.setDropItem(false);
        }

        if (configManager.areParticlesEnabled()) {
            org.bukkit.configuration.ConfigurationSection particleEffects = configManager.getParticleEffects();
            if (particleEffects != null) {
                startParticleEffect(fallingBlocks, particleEffects.getValues(false));
            }
        }

        scheduleImpactHandling(coreLocation, meteoriteConfig, fallingBlocks);

        int cleanupInterval = meteoriteSection.getInt("clean-up-meteorite-blocks-interval", 0);
        if (cleanupInterval > 0) {
            scheduleCleanup(coreLocation, outerSize, cleanupInterval);
        }
    }

    private List<FallingBlock> createLayer(List<Location> positions, Map<String, Object> materialMap, World world) {
        List<FallingBlock> blocks = new ArrayList<>();
        for (Location loc : positions) {
            Material material = getRandomMaterial(materialMap);
            if (material != null && material.isBlock()) {
                BlockData blockData = material.createBlockData();
                FallingBlock fb = world.spawnFallingBlock(loc, blockData);
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

    private void startParticleEffect(List<FallingBlock> fallingBlocks, Map<String, Object> particleEffects) {
        for (FallingBlock fb : fallingBlocks) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, particleEffectTask -> {
                if (fb.isDead()) {
                    particleEffectTask.cancel();
                    return;
                }
                List<String> effectKeys = new ArrayList<>(particleEffects.keySet());
                String randomEffectKey = effectKeys.get(new Random().nextInt(effectKeys.size()));
                Map<String, Object> effectData = (Map<String, Object>) particleEffects.get(randomEffectKey);

                if (!(Boolean) effectData.getOrDefault("enabled", true)) return;
                if (Math.random() * 100 > (Double) effectData.getOrDefault("chance", 100.0)) return;

                String particleName = (String) effectData.getOrDefault("particle-effect", "FLAME");
                Particle particle = Particle.valueOf(particleName);
                int amount = (Integer) effectData.getOrDefault("amount", 1);
                double spreadX = (Double) effectData.getOrDefault("spread", 0.1);
                double spreadY = (Double) effectData.getOrDefault("spread", 0.1);
                double spreadZ = (Double) effectData.getOrDefault("spread", 0.1);
                double speed = (Double) effectData.getOrDefault("speed", 0.05);

                fb.getWorld().spawnParticle(particle, fb.getLocation(), amount, spreadX, spreadY, spreadZ, speed);
            }, 0, configManager.getParticleInterval() * 2L);
        }
    }

    private void scheduleImpactHandling(Location coreLocation, Map<String, Object> meteoriteConfig, List<FallingBlock> fallingBlocks) {
        double spawnHeight = configManager.getSpawnHeight();
        double fallSpeed = (Double) meteoriteConfig.getOrDefault("meteorite-speed", 2.0);
        int estimatedFallTicks = (int) (spawnHeight / fallSpeed) * 2 + 40;

        Bukkit.getScheduler().runTaskLater(plugin, () -> handleImpact(coreLocation, meteoriteConfig), estimatedFallTicks);
    }

    private void handleImpact(Location coreLocation, Map<String, Object> meteoriteConfig) {
        World world = coreLocation.getWorld();

        org.bukkit.configuration.ConfigurationSection coreSettings = configManager.getCoreSettings();
        if (coreSettings != null && coreSettings.getBoolean("enable-explosion", true)) {
            world.createExplosion(coreLocation, (float) coreSettings.getDouble("explosion-power", 6.0),
                    coreSettings.getBoolean("explosion-breaks-blocks", true),
                    coreSettings.getBoolean("explosion-sets-fire", true));
        }

        org.bukkit.configuration.ConfigurationSection innerSettings = configManager.getInnerLayerSettings();
        if (innerSettings != null && innerSettings.getBoolean("enable-explosion", false)) {
            world.createExplosion(coreLocation, (float) innerSettings.getDouble("explosion-power", 1.0),
                    innerSettings.getBoolean("explosion-breaks-blocks", false),
                    innerSettings.getBoolean("explosion-sets-fire", true));
        }

        org.bukkit.configuration.ConfigurationSection outerSettings = configManager.getOuterLayerSettings();
        if (outerSettings != null && outerSettings.getBoolean("enable-explosion", false)) {
            world.createExplosion(coreLocation, (float) outerSettings.getDouble("explosion-power", 1.0),
                    outerSettings.getBoolean("explosion-breaks-blocks", false),
                    outerSettings.getBoolean("explosion-sets-fire", true));
        }

        if (coreSettings != null && coreSettings.getBoolean("enable-lighting-strike", true)) {
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

        String impactMessage = coreSettings != null ? coreSettings.getString("message", "") : "";
        if (impactMessage != null && !impactMessage.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', impactMessage));
        }
    }

    private void spawnGuardian(Location coreLocation) {
        org.bukkit.configuration.ConfigurationSection possibleGuardians = configManager.getPossibleGuardians();
        if (possibleGuardians == null) return;

        List<String> enabledGuardians = new ArrayList<>();
        for (String key : possibleGuardians.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection guardianData = possibleGuardians.getConfigurationSection(key);
            if (guardianData != null && guardianData.getBoolean("enabled", false)) {
                enabledGuardians.add(key);
            }
        }

        if (enabledGuardians.isEmpty()) return;

        String selectedGuardianId = enabledGuardians.get(new Random().nextInt(enabledGuardians.size()));
        org.bukkit.configuration.ConfigurationSection guardianData = possibleGuardians.getConfigurationSection(selectedGuardianId);

        if (guardianData == null) return;
        if (Math.random() * 100 >= guardianData.getDouble("chance", 10.0)) return;

        Location spawnLoc = coreLocation.clone().add(
                (new Random().nextInt(3) - 1) * 2,
                0,
                (new Random().nextInt(3) - 1) * 2
        );

        EntityType entityType = EntityType.valueOf(guardianData.getString("guardian-mob-type", "ZOMBIE"));
        if (!entityType.isAlive()) {
            plugin.getLogger().warning("Неверный тип моба для охранника: " + entityType);
            return;
        }

        LivingEntity guardian = (LivingEntity) coreLocation.getWorld().spawnEntity(spawnLoc, entityType);

        String displayName = guardianData.getString("guardian-display-name", "Охранник");
        guardian.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
        guardian.setCustomNameVisible(true);

        double health = guardianData.getDouble("guardian-health", guardian.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        guardian.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        guardian.setHealth((float) Math.min(health, guardian.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));

        double damage = guardianData.getDouble("guardian-attack-damage", 5.0);
        guardian.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);

        double speed = guardianData.getDouble("guardian-movement-speed", 0.25);
        guardian.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);

        if (guardianData.getBoolean("enable-guardian-equipment", false)) {
            org.bukkit.configuration.ConfigurationSection equipment = guardianData.getConfigurationSection("guardian-equipment");
            if (equipment != null) {
                org.bukkit.inventory.EntityEquipment equip = guardian.getEquipment();
                if (equip != null) {
                    equip.setItem(EquipmentSlot.HAND, createItemStack(equipment.getString("main-hand")));
                    equip.setItem(EquipmentSlot.OFF_HAND, createItemStack(equipment.getString("off-hand")));
                    equip.setItem(EquipmentSlot.HEAD, createItemStack(equipment.getString("helmet")));
                    equip.setItem(EquipmentSlot.CHEST, createItemStack(equipment.getString("chestplate")));
                    equip.setItem(EquipmentSlot.LEGS, createItemStack(equipment.getString("leggings")));
                    equip.setItem(EquipmentSlot.FEET, createItemStack(equipment.getString("boots")));
                    equip.setItemInMainHandDropChance(0F);
                    equip.setItemInOffHandDropChance(0F);
                    equip.setHelmetDropChance(0F);
                    equip.setChestplateDropChance(0F);
                    equip.setLeggingsDropChance(0F);
                    equip.setBootsDropChance(0F);
                }
            }
        }

        String playerMessage = guardianData.getString("player-message", "");
        if (!playerMessage.isEmpty()) {
            for (Player player : coreLocation.getWorld().getPlayers()) {
                if (player.getLocation().distance(spawnLoc) < 10) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerMessage));
                }
            }
        }

        String soundName = guardianData.getString("guardian-spawn-sound", "");
        if (!soundName.isEmpty()) {
            float volume = (float) guardianData.getDouble("guardian-spawn-sound-volume", 1.0);
            float pitch = (float) guardianData.getDouble("guardian-spawn-sound-pitch", 1.0);
            coreLocation.getWorld().playSound(spawnLoc, Sound.valueOf(soundName.toUpperCase()), volume, pitch);
        }
    }

    private ItemStack createItemStack(String materialName) {
        if (materialName == null || materialName.isEmpty()) return null;
        Material mat = Material.getMaterial(materialName);
        if (mat == null) return null;
        return new ItemStack(mat);
    }

    private void scheduleCleanup(Location coreLocation, int radius, int delaySeconds) {
        BukkitTask cleanupTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
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
            cleanupTasks.remove(cleanupTask.getTaskId());
            plugin.getLogger().info("Метеорит очищен по таймеру.");
        }, delaySeconds * 20L);

        cleanupTasks.put(cleanupTask.getTaskId(), cleanupTask);
    }

    public void cancelCleanupTasks() {
        for (BukkitTask task : cleanupTasks.values()) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        cleanupTasks.clear();
    }
}
