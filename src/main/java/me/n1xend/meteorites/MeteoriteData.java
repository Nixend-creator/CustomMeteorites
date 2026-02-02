package me.n1xend.meteorites;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.*;

public class MeteoriteData implements ConfigurationSerializable {
    private final UUID uuid;
    private final String worldName;
    private final int x, y, z;
    private final long createdAt;
    private final long cleanupDelayMs;
    private final Set<String> meteoriteMaterials;

    public MeteoriteData(Location location, long cleanupDelayMs, Set<String> materials) {
        this.uuid = UUID.randomUUID();
        this.worldName = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        this.createdAt = System.currentTimeMillis();
        this.cleanupDelayMs = cleanupDelayMs;
        this.meteoriteMaterials = new HashSet<>(materials);
    }

    public MeteoriteData(Map<String, Object> map) {
        this.uuid = UUID.fromString((String) map.get("uuid"));
        this.worldName = (String) map.get("world");
        this.x = ((Number) map.get("x")).intValue();
        this.y = ((Number) map.get("y")).intValue();
        this.z = ((Number) map.get("z")).intValue();
        this.createdAt = ((Number) map.get("createdAt")).longValue();
        this.cleanupDelayMs = ((Number) map.get("cleanupDelayMs")).longValue();

        @SuppressWarnings("unchecked")
        List<String> mats = (List<String>) map.get("materials");
        this.meteoriteMaterials = new HashSet<>(mats != null ? mats : Collections.emptyList());
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("uuid", uuid.toString());
        map.put("world", worldName);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("createdAt", createdAt);
        map.put("cleanupDelayMs", cleanupDelayMs);
        map.put("materials", new ArrayList<>(meteoriteMaterials));
        return map;
    }

    public UUID getUuid() { return uuid; }

    public Location getLocation(org.bukkit.Server server) {
        World world = server.getWorld(worldName);
        return world != null ? new Location(world, x + 0.5, y, z + 0.5) : null;
    }

    public long getRemainingTime() {
        long elapsed = System.currentTimeMillis() - createdAt;
        return Math.max(0, cleanupDelayMs - elapsed);
    }

    public boolean isExpired() { return getRemainingTime() <= 0; }

    public Set<String> getMeteoriteMaterials() { return meteoriteMaterials; }

    // 🔧 ДОБАВЛЕН МЕТОД ДЛЯ УНИКАЛЬНОГО КЛЮЧА
    public String getUniqueKey() {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MeteoriteData)) return false;
        MeteoriteData that = (MeteoriteData) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() { return Objects.hash(uuid); }
}