package kr.lunaf.nationSystem.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

import kr.lunaf.nationSystem.domain.BuildingType;

public class CustomItems {
    private final NamespacedKey key;

    public CustomItems(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "nation-item");
    }

    public ItemStack createProclamationItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e[N] 국가 선포권"));
        meta.setLore(List.of(
            ChatColor.translateAlternateColorCodes('&', "&7우클릭으로 국가 선포 위치를 지정합니다.")
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "proclamation");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCompletedCoreItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&4&l[N] &f완성된 코어"));
        meta.setLore(List.of(
            ChatColor.translateAlternateColorCodes('&', "&7우클릭으로 성벽을 건설합니다.")
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "completed_core");
        item.setItemMeta(meta);
        return item;
    }

    public boolean isProclamationItem(ItemStack item) {
        return hasItemKey(item, "proclamation");
    }

    public boolean isCompletedCoreItem(ItemStack item) {
        return hasItemKey(item, "completed_core");
    }

    public ItemStack createBuildingItem(BuildingType type, String displayName) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e[N] " + displayName + " \uAC74\uBB3C"));
        meta.setLore(List.of(
            ChatColor.translateAlternateColorCodes('&', "&7\uc6b0\ud074\ub9ad\uc73c\ub85c \uac74\ubb3c\uc744 \uc124\uce58\ud569\ub2c8\ub2e4.")
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "building:" + type.key());
        item.setItemMeta(meta);
        return item;
    }

    public Optional<BuildingType> getBuildingType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String stored = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (stored == null || !stored.startsWith("building:")) {
            return Optional.empty();
        }
        String keyValue = stored.substring("building:".length());
        return BuildingType.fromKey(keyValue);
    }

    private boolean hasItemKey(ItemStack item, String value) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String stored = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return value.equals(stored);
    }
}
