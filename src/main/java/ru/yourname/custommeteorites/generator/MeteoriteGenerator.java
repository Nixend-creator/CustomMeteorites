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
    // Исправлено: тип ключа Map с UUID на Integer
    private final Map<Integer, BukkitTask> cleanupTasks = new HashMap<>();

    public MeteoriteGenerator(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void createMeteoriteAt(Location spawnLocation, String meteoriteId) {
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
        // Находим реальную высоту земли
        coreLocation.setY(spawnLocation.getWorld().getHighestBlockYAt(coreLocation));

        // --- Генерация структуры метеорита ---
        int outerSize = (Integer) meteoriteConfig.getOrDefault("outer-layer-size", 3);
        int innerSize = 0;
        if ((Boolean) meteoriteConfig.getOrDefault("enable-inner-layer", false)) {
            innerSize = (Integer) meteoriteConfig.getOrDefault("inner-layer-size", 2);
        }
        int coreSize = 1; // Размер ядра, можно сделать настраиваемым

        Map<String, Object> coreBlocks = (Map<String, Object>) meteoriteConfig.get("core-block");
        Map<String, Object> innerBlocks = (Map<String, Object>) meteoriteConfig.get("inner-layer-blocks");
        Map<String, Object> outerBlocks = (Map<String, Object>) meteoriteConfig.get("outer-layer-blocks");

        List<Location> corePositions = new ArrayList<>();
        List<Location> innerPositions = new ArrayList<>();
        List<Location> outerPositions = new ArrayList<>();

        // Собираем позиции для каждого слоя (сферическая форма)
        for (int x = -outerSize; x <= outerSize; x++) {
            for (int y = -outerSize; y <= outerSize; y++) {
                for (int z = -outerSize; z <= outerSize; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    Location blockLoc = coreLocation.clone().add(x, y, z);
                    if (blockLoc.getBlockY() > 0) { // Не под землю
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

        // Создаем FallingBlock для каждого слоя
        List<FallingBlock> fallingBlocks = new ArrayList<>();
        fallingBlocks.addAll(createLayer(corePositions, coreBlocks, spawnLocation.getWorld()));
        fallingBlocks.addAll(createLayer(innerPositions, innerBlocks, spawnLocation.getWorld()));
        fallingBlocks.addAll(createLayer(outerPositions, outerBlocks, spawnLocation.getWorld()));

        // Устанавливаем начальную скорость и настройки для каждого FallingBlock
        double speed = (Double) meteoriteConfig.getOrDefault("meteorite-speed", 2.0);
        for (FallingBlock fb : fallingBlocks) {
            // Начальная вертикальная скорость (падение)
            Vector velocity = new Vector(0, -speed, 0);
            // Маленькая случайная горизонтальная скорость для разлёта
            velocity.add(new Vector((Math.random() - 0.5) * 0.5, 0, (Math.random() - 0.5) * 0.5));
            fb.setVelocity(velocity);
            fb.setDropItem(false);

            // Применяем настройки слоёв (если нужно)
            // Это сложнее, т.к. FallingBlock не различает слои после спауна.
            // Пока пропустим, но можно сохранить тип слоя в PersistentDataContainer если нужно.
        }

        // --- Добавляем частицы (если включены) ---
        if (configManager.areParticlesEnabled()) {
            Map<String, Object> particleEffects = configManager.getParticleEffects();
            startParticleEffect(fallingBlocks, particleEffects);
        }

        // --- Планируем обработку приземления ---
        scheduleImpactHandling(coreLocation, meteoriteConfig, fallingBlocks);

        // --- Планируем очистку (если включена) ---
        int cleanupInterval = (Integer) meteoriteConfig.getOrDefault("clean-up-meteorite-blocks-interval", 0);
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
        if (materialMap == null || materialMap.isEmpty()) return Material.STONE; // Fallback
        List<Material> materials = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();

        for (Map.Entry<String, Object> entry : materialMap.entrySet()) {
            Material mat = Material.getMaterial(entry.getKey());
            int weight = ((Number) entry.getValue()).intValue();
            if (mat != null && mat.isBlock()) {
                for (int i = 0; i < weight; i++) {
                    materials.add(mat);
                }
            }
        }

        if (materials.isEmpty()) return Material.STONE; // Fallback
        Collections.shuffle(materials);
        return materials.get(0);
    }

    private void startParticleEffect(List<FallingBlock> fallingBlocks, Map<String, Object> particleEffects) {
        for (FallingBlock fb : fallingBlocks) {
            // Запускаем задачу для отслеживания одного FallingBlock
            // Исправлено: присваиваем результат BukkitTask переменной task
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, particleTask -> {
                if (fb.isDead()) {
                    particleTask.cancel(); // Отменяем задачу particleTask, а не task
                    return;
                }
                // Выбираем случайный эффект частиц
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
            }, 0, configManager.getParticleInterval() * 2L); // Интервал из конфига
        }
    }

    private void scheduleImpactHandling(Location coreLocation, Map<String, Object> meteoriteConfig, List<FallingBlock> fallingBlocks) {
        // Грубая оценка времени падения
        double spawnHeight = configManager.getSpawnHeight();
        double fallSpeed = (Double) meteoriteConfig.getOrDefault("meteorite-speed", 2.0);
        int estimatedFallTicks = (int) (spawnHeight / fallSpeed) * 2 + 40; // +40 для надёжности

        Bukkit.getScheduler().runTaskLater(plugin, () -> handleImpact(coreLocation, meteoriteConfig), estimatedFallTicks);
    }

    private void handleImpact(Location coreLocation, Map<String, Object> meteoriteConfig) {
        World world = coreLocation.getWorld();

        // 1. Взрывы (для каждого слоя)
        // Core
        Map<String, Object> coreSettings = configManager.getCoreSettings();
        if ((Boolean) coreSettings.getOrDefault("enable-explosion", true)) {
            world.createExplosion(coreLocation, ((Double) coreSettings.getOrDefault("explosion-power", 6.0)).floatValue(),
                    (Boolean) coreSettings.getOrDefault("explosion-breaks-blocks", true),
                    (Boolean) coreSettings.getOrDefault("explosion-sets-fire", true));
        }
        // Inner (предположим, взрыв в центре с уменьшенной силой)
        Map<String, Object> innerSettings = configManager.getInnerLayerSettings();
        if ((Boolean) innerSettings.getOrDefault("enable-explosion", false)) {
            world.createExplosion(coreLocation, ((Double) innerSettings.getOrDefault("explosion-power", 1.0)).floatValue(),
                    (Boolean) innerSettings.getOrDefault("explosion-breaks-blocks", false),
                    (Boolean) innerSettings.getOrDefault("explosion-sets-fire", true));
        }
        // Outer (предположим, взрыв в центре с уменьшенной силой)
        Map<String, Object> outerSettings = configManager.getOuterLayerSettings();
        if ((Boolean) outerSettings.getOrDefault("enable-explosion", false)) {
            world.createExplosion(coreLocation, ((Double) outerSettings.getOrDefault("explosion-power", 1.0)).floatValue(),
                    (Boolean) outerSettings.getOrDefault("explosion-breaks-blocks", false),
                    (Boolean) outerSettings.getOrDefault("explosion-sets-fire", true));
        }

        // 2. Молния (только для ядра)
        if ((Boolean) configManager.getCoreSettings().getOrDefault("enable-lighting-strike", true)) {
            world.strikeLightningEffect(coreLocation);
        }

        // 3. Сундук с лутом
        if (configManager.isTreasureEnabled()) {
            Location treasureLoc = coreLocation.clone(); // Пока просто в центре
            Block treasureBlock = treasureLoc.getBlock();
            Material treasureMat = Material.valueOf(configManager.getTreasureType()); // "CHEST" или "BARREL"
            if (treasureMat == Material.CHEST || treasureMat == Material.BARREL) {
                treasureBlock.setType(treasureMat);
                BlockState state = treasureBlock.getState();
                if (state instanceof Container container) {
                    Inventory inv = container.getInventory();
                    TreasureLoot.fillChest(inv, configManager.getTreasureContent());
                }
            }
        }

        // 4. Охранник
        if (configManager.isGuardianEnabled()) {
            spawnGuardian(coreLocation);
        }

        // 5. Сообщение и команды при падении (из core-settings)
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

        // Проверяем шанс
        if (Math.random() * 100 >= (Double) guardianData.getOrDefault("chance", 10.0)) {
            return; // Охранник не появился
        }

        // Находим точку рядом
        Location spawnLoc = coreLocation.clone().add(
                (new Random().nextInt(3) - 1) * 2, // -2, 0, 2
                0,
                (new Random().nextInt(3) - 1) * 2
        );

        // Определяем тип моба
        EntityType entityType = EntityType.valueOf((String) guardianData.getOrDefault("guardian-mob-type", "ZOMBIE"));
        if (!entityType.isAlive()) {
            plugin.getLogger().warning("Неверный тип моба для охранника: " + entityType);
            return;
        }

        // Спауним моба
        LivingEntity guardian = (LivingEntity) coreLocation.getWorld().spawnEntity(spawnLoc, entityType);

        // Применяем настройки
        String displayName = (String) guardianData.getOrDefault("guardian-display-name", "Охранник");
        guardian.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
        guardian.setCustomNameVisible(true);

        // Здоровье
        double health = (Double) guardianData.getOrDefault("guardian-health", (double) guardian.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
        guardian.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
        guardian.setHealth(health);

        // Урон
        double damage = (Double) guardianData.getOrDefault("guardian-attack-damage", 5.0); // Базовое значение
        guardian.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);

        // Скорость
        double speed = (Double) guardianData.getOrDefault("guardian-movement-speed", 0.25); // Базовое значение
        guardian.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);

        // Снаряжение
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

        // Сообщение игроку
        String playerMessage = (String) guardianData.getOrDefault("player-message", "");
        if (!playerMessage.isEmpty()) {
            for (Player player : coreLocation.getWorld().getPlayers()) {
                if (player.getLocation().distance(spawnLoc) < 10) { // В радиусе 10 блоков
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerMessage));
                }
            }
        }

        // Звук
        String soundName = (String) guardianData.getOrDefault("guardian-spawn-sound", "");
        if (!soundName.isEmpty()) {
            float volume = ((Double) guardianData.getOrDefault("guardian-spawn-sound-volume", 1.0)).floatValue();
            float pitch = ((Double) guardianData.getOrDefault("guardian-spawn-sound-pitch", 1.0)).floatValue();
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
        BukkitTask cleanupTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Очищаем область
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Location blockLoc = coreLocation.clone().add(x, y, z);
                        double distance = Math.sqrt(x * x + y * y + z * z);
                        if (distance <= radius) {
                            Block block = blockLoc.getBlock();
                            if (block.getType() != Material.AIR) { // Не удаляем воздух
                                block.setType(Material.AIR);
                            }
                        }
                    }
                }
            }
            // Удаляем задачу из списка после выполнения
            // Исправлено: используем getTaskId() для Integer ключа
            cleanupTasks.remove(cleanupTask.getTaskId());
            plugin.getLogger().info("Метеорит очищен по таймеру.");
        }, delaySeconds * 20L);

        // Сохраняем задачу, чтобы можно было отменить при выгрузке плагина
        // Исправлено: используем getTaskId() для Integer ключа
        cleanupTasks.put(cleanupTask.getTaskId(), cleanupTask);
    }

    // Метод для отмены всех задач при выгрузке плагина
    public void cancelCleanupTasks() {
        for (BukkitTask task : cleanupTasks.values()) {
            task.cancel();
        }
        cleanupTasks.clear();
    }
}
