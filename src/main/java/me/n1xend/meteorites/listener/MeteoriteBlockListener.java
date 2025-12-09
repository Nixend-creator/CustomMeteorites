package me.n1xend.meteorites.listener;

import me.n1xend.meteorites.generator.MeteoriteGenerator;
import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class MeteoriteBlockListener implements Listener {

    public static final String METEOR_META_KEY = "custommeteorites_meteorId";

    private final JavaPlugin plugin;
    private final MeteoriteGenerator generator;

    public MeteoriteBlockListener(JavaPlugin plugin, MeteoriteGenerator generator) {
        this.plugin = plugin;
        this.generator = generator;
    }

    @EventHandler
    public void onMeteoriteBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        if (!fb.hasMetadata(METEOR_META_KEY)) return;

        int meteorId = -1;
        List<MetadataValue> values = fb.getMetadata(METEOR_META_KEY);
        for (MetadataValue value : values) {
            if (value.getOwningPlugin() == plugin) {
                meteorId = value.asInt();
                break;
            }
        }
        if (meteorId == -1) return;

        Location loc = event.getBlock().getLocation();
        generator.addMeteoriteBlock(meteorId, loc);
    }
}
