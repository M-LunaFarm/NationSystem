package kr.lunaf.nationSystem.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

public class Messages {
    private final JavaPlugin plugin;
    private FileConfiguration messages;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String raw = messages.getString(path);
        if (raw == null) {
            raw = path;
        }
        raw = applyPlaceholders(raw, placeholders);
        Component message = LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
        sender.sendMessage(message);
    }

    public void sendList(CommandSender sender, String path) {
        sendList(sender, path, Map.of());
    }

    public void sendList(CommandSender sender, String path, Map<String, String> placeholders) {
        List<String> lines = messages.getStringList(path);
        for (String line : lines) {
            String raw = applyPlaceholders(line, placeholders);
            Component message = LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
            sender.sendMessage(message);
        }
    }

    public String getString(String path) {
        return messages.getString(path, path);
    }

    private String applyPlaceholders(String raw, Map<String, String> placeholders) {
        String out = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return out;
    }
}
