package kr.lunaf.nationSystem.config;

import kr.lunaf.nationSystem.domain.BuildingDefinition;
import kr.lunaf.nationSystem.domain.BuildingType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public class BuildingsConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private final Map<BuildingType, BuildingDefinition> definitions = new EnumMap<>(BuildingType.class);

    public BuildingsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "buildings.yml");
        if (!file.exists()) {
            plugin.saveResource("buildings.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        definitions.clear();
        ConfigurationSection section = config.getConfigurationSection("buildings");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            BuildingType.fromKey(key).ifPresent(type -> {
                String base = "buildings." + key + ".";
                String display = config.getString(base + "display-name", key);
                int time = config.getInt(base + "build-time-seconds", 60);
                String path = config.getString(base + "structure", "");
                long price = config.getLong(base + "price", 0);
                int minLevel = config.getInt(base + "min-level", 1);
                int maxPerNation = config.getInt(base + "max-per-nation", 1);
                definitions.put(type, new BuildingDefinition(type, display, time, path, price, minLevel, maxPerNation));
            });
        }
    }

    public BuildingDefinition get(BuildingType type) {
        return definitions.get(type);
    }

    public Map<BuildingType, BuildingDefinition> all() {
        return Map.copyOf(definitions);
    }
}
