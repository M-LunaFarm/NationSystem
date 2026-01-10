package kr.lunaf.nationSystem.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class QuestsConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public QuestsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) {
            plugin.saveResource("quests.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public int dailyCount() {
        return config.getInt("quests.daily.count", 4);
    }

    public int rewardMinExp() {
        return config.getInt("quests.daily.reward-exp-min", 40);
    }

    public int rewardMaxExp() {
        return config.getInt("quests.daily.reward-exp-max", 70);
    }
}
