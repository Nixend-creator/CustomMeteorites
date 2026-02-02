package me.n1xend.meteorites.commands;

import me.n1xend.meteorites.CustomMeteorites;
import me.n1xend.meteorites.LangManager;
import me.n1xend.meteorites.config.ConfigManager;
import me.n1xend.meteorites.generator.MeteoriteGenerator;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MeteorCommand implements CommandExecutor {

    private final CustomMeteorites plugin;
    private final ConfigManager configManager;
    private final MeteoriteGenerator generator;
    private final LangManager langManager;

    public MeteorCommand(CustomMeteorites plugin,
                         ConfigManager configManager,
                         MeteoriteGenerator generator,
                         LangManager langManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.generator = generator;
        this.langManager = langManager;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!sender.hasPermission("custommeteorites.admin")) {
            sender.sendMessage(langManager.getMessage("command.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(langManager.getMessage("command.players_only"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(langManager.getMessage("command.spawn.usage"));
                    return true;
                }
                String id = args[1];
                var meteoritesSection = configManager.getMeteoritesConfig();
                if (meteoritesSection == null || meteoritesSection.getConfigurationSection(id) == null) {
                    sender.sendMessage(langManager.getMessage("command.spawn.not_found", "id", id));
                    return true;
                }
                Location loc = p.getLocation();
                generator.createMeteoriteAt(loc, id);
                sender.sendMessage(langManager.getMessage("command.spawn.success", "id", id));
            }

            case "reload" -> {
                try {
                    configManager.reload();
                    langManager.reload();
                    sender.sendMessage(langManager.getMessage("command.reload.success"));
                } catch (Exception e) {
                    sender.sendMessage(langManager.getMessage("command.reload.failed"));
                    plugin.getLogger().severe("Reload failed: " + e.getMessage());
                }
            }

            case "start" -> {
                if (plugin.getRandomMeteorTask() != null && !plugin.getRandomMeteorTask().isCancelled()) {
                    sender.sendMessage(langManager.getMessage("command.start.already_running"));
                } else {
                    plugin.startRandomMeteorites();
                    sender.sendMessage(langManager.getMessage("command.start.success"));
                }
            }

            case "stop" -> {
                if (plugin.getRandomMeteorTask() == null || plugin.getRandomMeteorTask().isCancelled()) {
                    sender.sendMessage(langManager.getMessage("command.stop.not_running"));
                } else {
                    plugin.stopRandomMeteorites();
                    sender.sendMessage(langManager.getMessage("command.stop.success"));
                }
            }

            case "version" -> {
                sender.sendMessage(langManager.getMessage("command.version.info",
                        "version", CustomMeteorites.VERSION));
            }

            default -> {
                sender.sendMessage(langManager.getMessage("command.unknown"));
                sendHelp(sender);
            }
        }

        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(langManager.getMessage("command.help.header"));
        s.sendMessage(langManager.getMessage("command.help.title"));
        s.sendMessage(langManager.getMessage("command.help.spawn"));
        s.sendMessage(langManager.getMessage("command.help.start"));
        s.sendMessage(langManager.getMessage("command.help.stop"));
        s.sendMessage(langManager.getMessage("command.help.reload"));
        s.sendMessage(langManager.getMessage("command.help.version"));
        s.sendMessage(langManager.getMessage("command.help.footer"));
    }
}