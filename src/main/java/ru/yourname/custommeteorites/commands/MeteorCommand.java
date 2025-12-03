package ru.yourname.custommeteorites.commands;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.yourname.custommeteorites.CustomMeteoritesPlugin;
import ru.yourname.custommeteorites.generator.MeteoriteGenerator;

public class MeteorCommand implements CommandExecutor {

    private final CustomMeteoritesPlugin plugin;

    public MeteorCommand(CustomMeteoritesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Использование: /meteor <force|reload>");
            return true;
        }

        if (args[0].equalsIgnoreCase("force")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Эту команду может использовать только игрок.");
                return true;
            }
            if (!sender.hasPermission("custommeteorites.admin")) {
                sender.sendMessage(ChatColor.RED + "У вас нет прав на выполнение этой команды.");
                return true;
            }

            Player player = (Player) sender;
            Location location = player.getLocation();
            MeteoriteGenerator generator = new MeteoriteGenerator(plugin, plugin.getConfigManager());
            generator.createMeteoriteAt(location, "1"); // Пример: вызвать метеорит типа 1
            sender.sendMessage(ChatColor.GREEN + "Метеорит принудительно вызван на ваших координатах.");

        } else if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("custommeteorites.admin")) {
                sender.sendMessage(ChatColor.RED + "У вас нет прав на выполнение этой команды.");
                return true;
            }

            plugin.getConfigManager().loadConfig();
            sender.sendMessage(ChatColor.GREEN + "Конфигурация плагина перезагружена.");

        } else {
            sender.sendMessage(ChatColor.RED + "Неизвестная подкоманда. Использование: /meteor <force|reload>");
        }

        return true;
    }
}
