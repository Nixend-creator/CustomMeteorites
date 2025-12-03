package ru.yourname.custommeteorites;

import org.bukkit.plugin.java.JavaPlugin;
import ru.yourname.custommeteorites.commands.MeteorCommand;
import ru.yourname.custommeteorites.config.ConfigManager;
import ru.yourname.custommeteorites.spawner.MeteoriteSpawner;
// Добавлен импорт
import ru.yourname.custommeteorites.generator.MeteoriteGenerator;

public final class CustomMeteoritesPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MeteoriteSpawner meteoriteSpawner;

    @Override
    public void onEnable() {
        // Инициализация
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();

        this.meteoriteSpawner = new MeteoriteSpawner(this, configManager);

        // Регистрация команд
        getCommand("meteor").setExecutor(new MeteorCommand(this));

        // Запуск планировщика метеоритов
        meteoriteSpawner.startScheduler();

        getLogger().info("Плагин CustomMeteorites включён!");
    }

    @Override
    public void onDisable() {
        // Используем геттер из meteoriteSpawner
        if (meteoriteSpawner != null) {
            // Убедитесь, что getGenerator() возвращает MeteoriteGenerator
            MeteoriteGenerator generator = meteoriteSpawner.getGenerator(); // Теперь компилятор знает, что это за тип
            if (generator != null) {
                generator.cancelCleanupTasks(); // Вызов метода отмены задач
            }
        }
        getLogger().info("Плагин CustomMeteorites выключен!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    // Метод для получения генератора из других классов (например, MeteoriteSpawner)
    public MeteoriteGenerator getGenerator() {
        return meteoriteSpawner != null ? meteoriteSpawner.getGenerator() : null;
    }
}