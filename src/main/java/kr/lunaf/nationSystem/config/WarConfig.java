package kr.lunaf.nationSystem.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WarConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public WarConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "war.yml");
        if (!file.exists()) {
            plugin.saveResource("war.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean matchOpen() {
        return config.getBoolean("war.match-open", true);
    }

    public int prepareSeconds() {
        return config.getInt("war.prepare-seconds", 180);
    }

    public int battleSeconds() {
        return config.getInt("war.battle-seconds", 1800);
    }

    public List<MatchThreshold> matchThresholds() {
        List<MatchThreshold> thresholds = new ArrayList<>();
        List<?> list = config.getList("war.match-thresholds");
        if (list == null) {
            return thresholds;
        }
        for (Object obj : list) {
            if (obj instanceof java.util.Map<?, ?> map) {
                int time = toInt(map.get("time-seconds"), 60);
                int diff = toInt(map.get("max-level-diff"), 1);
                thresholds.add(new MatchThreshold(time, diff));
            }
        }
        if (thresholds.isEmpty()) {
            thresholds.add(new MatchThreshold(60, 1));
        }
        return thresholds;
    }

    public record MatchThreshold(int timeSeconds, int maxLevelDiff) {
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
