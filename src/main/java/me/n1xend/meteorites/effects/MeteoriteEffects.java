package me.n1xend.meteorites.effects;

import me.n1xend.meteorites.LangManager;
import me.n1xend.meteorites.config.ConfigManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList; // 🔧 ДОБАВЛЕН ИМПОРТ
import java.util.List;
import java.util.Random;

public class MeteoriteEffects {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final LangManager langManager;

    public MeteoriteEffects(JavaPlugin plugin, ConfigManager config, LangManager langManager) {
        this.plugin = plugin;
        this.config = config;
        this.langManager = langManager;
    }

    public void atmosphereTrail(List<FallingBlock> blocks) {
        ConfigurationSection sec = config.getAtmosphereSettings();
        if (sec == null || !sec.getBoolean("enabled", true)) return;

        final Particle particle = getSafeParticle(sec.getString("particle", "FLAME"));
        final Sound sound = getSafeSound(sec.getString("sound", "ENTITY_BLAZE_SHOOT"));

        final int interval = sec.getInt("interval-ticks", 3);
        final double minY = sec.getDouble("min-y", 90);

        new BukkitRunnable() {
            @Override
            public void run() {
                boolean alive = false;
                for (FallingBlock fb : blocks) {
                    if (fb == null || fb.isDead()) continue;
                    alive = true;
                    Location loc = fb.getLocation();
                    if (loc.getY() < minY) continue;
                    World w = loc.getWorld();
                    if (w == null) continue;

                    w.spawnParticle(particle, loc, 6, 0.3, 0.3, 0.3, 0.01);

                    if (sound != null) {
                        w.playSound(loc, sound, 0.5f, 1.3f);
                    }
                }
                if (!alive) cancel();
            }
        }.runTaskTimer(plugin, 0L, Math.max(1L, interval));
    }

    public void startParticleEffect(List<FallingBlock> fallingBlocks, ConfigurationSection effects) {
        for (FallingBlock fb : fallingBlocks) {
            if (fb == null || fb.isDead()) continue;

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (fb.isDead()) {
                        cancel();
                        return;
                    }

                    List<String> keys = new ArrayList<>(effects.getKeys(false)); // ← РАБОТАЕТ С ИМПОРТОМ
                    if (keys.isEmpty()) return;

                    String key = keys.get(new Random().nextInt(keys.size()));
                    ConfigurationSection effect = effects.getConfigurationSection(key);
                    if (effect == null || !effect.getBoolean("enabled", true)) return;
                    if (new Random().nextInt(100) >= effect.getInt("chance", 100)) return;

                    String particleName = effect.getString("particle-effect", "FLAME");
                    Particle particle;
                    try {
                        particle = Particle.valueOf(particleName.trim().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                        return;
                    }

                    int amount = Math.max(0, effect.getInt("amount", 1));
                    double spread = effect.getDouble("spread", 0.1);
                    double speed = effect.getDouble("speed", 0.05);

                    World world = fb.getWorld();
                    if (world != null) {
                        world.spawnParticle(particle, fb.getLocation(), amount, spread, spread, spread, speed);
                    }
                }
            }.runTaskTimer(plugin, 0L, Math.max(1L, config.getParticleInterval() * 2L));
        }
    }

    // ... остальные методы без изменений (spawnShockwave, runRadar, playLootAnimation, getDirection, getSafeParticle, getSafeSound) ...

    public void spawnShockwave(Location center) {
        ConfigurationSection sec = config.getShockwaveSettings();
        if (sec == null || !sec.getBoolean("enabled", true)) return;

        World w = center.getWorld();
        if (w == null) return;

        double radius = sec.getDouble("radius", 12.0);
        double knockback = sec.getDouble("knockback-strength", 1.5);
        double damage = sec.getDouble("damage", 4.0);
        boolean slow = sec.getBoolean("apply-slow", true);
        int slowDuration = sec.getInt("slow-duration", 60);
        int slowAmplifier = sec.getInt("slow-amplifier", 0);

        w.spawnParticle(Particle.CLOUD, center, 60,
                radius / 2, 1, radius / 2, 0.02);

        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);

        for (Entity e : w.getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e instanceof ArmorStand) continue;

            Vector dir = e.getLocation().toVector().subtract(center.toVector());
            if (dir.lengthSquared() == 0) dir = new Vector(0, 1, 0);
            dir.normalize().multiply(knockback);
            dir.setY(Math.max(0.4, dir.getY() + 0.3));

            e.setVelocity(dir);

            if (damage > 0) living.damage(damage);

            if (slow) {
                living.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        slowDuration,
                        slowAmplifier,
                        false,
                        true
                ));
            }
        }
    }

    public void runRadar(Location impact) {
        ConfigurationSection sec = config.getRadarSettings();
        if (sec == null || !sec.getBoolean("enabled", true)) return;

        int radius = sec.getInt("notify-radius", 500);
        String template = sec.getString("message",
                "&6[Метеорит] &fX:&e%x% &fZ:&e%z% &7(&a%dist%м&7; направление: &b%dir%&7)");

        World w = impact.getWorld();
        if (w == null) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld() != w) continue;

            double dist = p.getLocation().distance(impact);
            if (dist > radius) continue;

            String dir = getDirection(p, impact);
            String msg = langManager.processPlaceholders(template,
                    "x", String.valueOf(impact.getBlockX()),
                    "z", String.valueOf(impact.getBlockZ()),
                    "dist", String.valueOf((int) dist),
                    "dir", dir
            );

            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
    }

    private String getDirection(Player p, Location target) {
        double dx = target.getX() - p.getLocation().getX();
        double dz = target.getZ() - p.getLocation().getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;

        if (angle < 22.5 || angle >= 337.5) return langManager.getMessage("direction.north");
        if (angle < 67.5) return langManager.getMessage("direction.northeast");
        if (angle < 112.5) return langManager.getMessage("direction.east");
        if (angle < 157.5) return langManager.getMessage("direction.southeast");
        if (angle < 202.5) return langManager.getMessage("direction.south");
        if (angle < 247.5) return langManager.getMessage("direction.southwest");
        if (angle < 292.5) return langManager.getMessage("direction.west");
        return langManager.getMessage("direction.northwest");
    }

    public void playLootAnimation(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;

        w.spawnParticle(Particle.END_ROD, loc.clone().add(0.5, 1, 0.5),
                40, 0.3, 0.5, 0.3, 0.01);

        w.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.3f);
    }

    private Particle getSafeParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return Particle.FLAME;
        }
    }

    private Sound getSafeSound(String name) {
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}