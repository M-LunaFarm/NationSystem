package kr.lunaf.nationSystem.listener;

import kr.lunaf.nationSystem.config.Messages;
import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.config.BuildingsConfig;
import kr.lunaf.nationSystem.domain.NationMembership;
import kr.lunaf.nationSystem.domain.NationRole;
import kr.lunaf.nationSystem.service.NationService;
import kr.lunaf.nationSystem.service.NamePromptService;
import kr.lunaf.nationSystem.service.TerritoryService;
import kr.lunaf.nationSystem.util.CustomItems;
import kr.lunaf.nationSystem.domain.BuildingType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProclamationListener implements Listener {
    private static final String TITLE_RAW = "&f&l[ Proclamation of Nation ]";
    private static final String TITLE = ChatColor.translateAlternateColorCodes('&', TITLE_RAW);
    private static final String TITLE_STRIPPED = ChatColor.stripColor(TITLE);

    private final Plugin plugin;
    private final Messages messages;
    private final PluginConfig pluginConfig;
    private final TerritoryService territoryService;
    private final NamePromptService namePromptService;
    private final CustomItems customItems;
    private final BuildingsConfig buildingsConfig;
    private final NationService nationService;
    private final Map<UUID, PendingProclamation> pending = new ConcurrentHashMap<>();

    public ProclamationListener(
        Plugin plugin,
        Messages messages,
        PluginConfig pluginConfig,
        TerritoryService territoryService,
        NamePromptService namePromptService,
        CustomItems customItems,
        BuildingsConfig buildingsConfig,
        NationService nationService
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.pluginConfig = pluginConfig;
        this.territoryService = territoryService;
        this.namePromptService = namePromptService;
        this.customItems = customItems;
        this.buildingsConfig = buildingsConfig;
        this.nationService = nationService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand();
        if (customItems.isProclamationItem(item)) {
            event.setCancelled(true);
            handleProclamation(event.getPlayer(), event.getClickedBlock().getLocation(), hand);
            return;
        }
        if (customItems.isCompletedCoreItem(item)) {
            event.setCancelled(true);
            handleCore(event.getPlayer(), event.getClickedBlock().getLocation(), hand);
        }
    }

    private void handleProclamation(Player player, Location location, EquipmentSlot hand) {
        World world = location.getWorld();
        if (world == null || !world.getName().equalsIgnoreCase(pluginConfig.territoryWorld())) {
            messages.send(player, "error.invalid-location");
            return;
        }
        Location center = new Location(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
        int y = center.getBlockY();
        if (y <= pluginConfig.territoryYMin() || y >= pluginConfig.territoryYMax()) {
            messages.send(player, "error.invalid-location");
            return;
        }
        int limit = pluginConfig.territoryXzLimit();
        if (Math.abs(center.getBlockX()) > limit || Math.abs(center.getBlockZ()) > limit) {
            messages.send(player, "error.invalid-location");
            return;
        }
        if (territoryService.hasPlayersInArea(world, center, player.getUniqueId())) {
            messages.send(player, "error.players-in-area");
            return;
        }

        nationService.getMembership(player.getUniqueId())
            .whenComplete((membershipResult, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || membershipResult == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                String nationName;
                if (membershipResult.isSuccess()) {
                    NationMembership membership = membershipResult.data();
                    if (membership.role() != NationRole.OWNER) {
                        messages.send(player, "error.not-owner");
                        return;
                    }
                    nationName = membership.nationName();
                } else {
                    Optional<String> pendingName = namePromptService.getName(player.getUniqueId());
                    if (pendingName.isEmpty()) {
                        namePromptService.startPrompt(player.getUniqueId());
                        messages.send(player, "info.name-prompt");
                        return;
                    }
                    nationName = pendingName.get();
                }
                openConfirmation(player, center, nationName, hand);
            }));
    }

    private void handleCore(Player player, Location location, EquipmentSlot hand) {
        territoryService.buildWall(player.getUniqueId(), location)
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> {
                        removeOne(player, hand);
                        messages.send(player, "info.wall-built");
                        var definition = buildingsConfig.get(BuildingType.SHOP);
                        if (definition != null) {
                            player.getInventory().addItem(
                                customItems.createBuildingItem(BuildingType.SHOP, definition.displayName())
                            );
                        }
                    }
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    case NOT_OWNER -> messages.send(player, "error.not-owner");
                    case NOT_IN_TERRITORY -> messages.send(player, "error.not-in-territory");
                    case NO_PENDING_WALL -> messages.send(player, "error.no-pending-wall");
                    case STRUCTURE_MISSING -> messages.send(player, "error.structure-missing");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!TITLE_STRIPPED.equals(title)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PendingProclamation pendingInfo = pending.get(player.getUniqueId());
        if (pendingInfo == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 29) {
            pending.remove(player.getUniqueId());
            player.closeInventory();
            acceptProclamation(player, pendingInfo);
        } else if (slot == 33) {
            pending.remove(player.getUniqueId());
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!TITLE_STRIPPED.equals(title)) {
            return;
        }
        pending.remove(event.getPlayer().getUniqueId());
    }

    private void openConfirmation(Player player, Location center, String nationName, EquipmentSlot hand) {
        Inventory inventory = Bukkit.createInventory(player, 45, TITLE);
        ItemStack info = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f[ 국가 생성 ]"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', "&f" + nationName + " 국가를 설치합니다!"));
        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&', "&f생성될 국가의 위치"));
        lore.add(ChatColor.translateAlternateColorCodes('&', "  &f- x 좌표: " + center.getBlockX()));
        lore.add(ChatColor.translateAlternateColorCodes('&', "  &f- y 좌표: " + center.getBlockY()));
        lore.add(ChatColor.translateAlternateColorCodes('&', "  &f- z 좌표: " + center.getBlockZ()));
        meta.setLore(lore);
        info.setItemMeta(meta);
        inventory.setItem(13, info);
        inventory.setItem(29, createPane(Material.GREEN_STAINED_GLASS_PANE, "&a[ 수락 ]"));
        inventory.setItem(33, createPane(Material.RED_STAINED_GLASS_PANE, "&c[ 거절 ]"));
        pending.put(player.getUniqueId(), new PendingProclamation(center, nationName, hand));
        player.openInventory(inventory);
    }

    private ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        item.setItemMeta(meta);
        return item;
    }

    private void acceptProclamation(Player player, PendingProclamation pendingInfo) {
        World world = pendingInfo.location().getWorld();
        if (world == null) {
            messages.send(player, "error.invalid-location");
            return;
        }
        if (territoryService.hasPlayersInArea(world, pendingInfo.location(), player.getUniqueId())) {
            messages.send(player, "error.players-in-area");
            return;
        }
        territoryService.createTerritory(player.getUniqueId(), pendingInfo.location(), pendingInfo.nationName())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> {
                        removeProclamationItem(player);
                        namePromptService.clear(player.getUniqueId());
                        if (result.data().createdNation()) {
                            messages.send(player, "info.created", Map.of("name", result.data().nationName()));
                        } else {
                            messages.send(player, "info.territory-created", Map.of("name", result.data().nationName()));
                        }
                    }
                    case NAME_REQUIRED -> {
                        namePromptService.startPrompt(player.getUniqueId());
                        messages.send(player, "info.name-prompt");
                    }
                    case NAME_TAKEN -> {
                        namePromptService.startPrompt(player.getUniqueId());
                        messages.send(player, "error.name-taken");
                    }
                    case NOT_OWNER -> messages.send(player, "error.not-owner");
                    case TOO_MANY_TERRITORIES -> messages.send(player, "error.invalid-args");
                    case TOO_CLOSE -> messages.send(player, "error.near-nation");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void removeProclamationItem(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (customItems.isProclamationItem(item)) {
                int amount = item.getAmount();
                if (amount <= 1) {
                    contents[i] = null;
                } else {
                    item.setAmount(amount - 1);
                }
                player.getInventory().setContents(contents);
                return;
            }
        }
    }

    private void removeOne(Player player, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (item == null) {
            return;
        }
        int amount = item.getAmount();
        if (amount <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            item.setAmount(amount - 1);
        }
    }

    private record PendingProclamation(Location location, String nationName, EquipmentSlot hand) {
    }
}
