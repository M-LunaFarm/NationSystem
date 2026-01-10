package kr.lunaf.nationSystem.listener;

import kr.lunaf.nationSystem.config.Messages;
import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.config.BuildingsConfig;
import kr.lunaf.nationSystem.service.NamePromptService;
import kr.lunaf.nationSystem.service.TerritoryService;
import kr.lunaf.nationSystem.util.CustomItems;
import kr.lunaf.nationSystem.domain.BuildingType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public class ProclamationListener implements Listener {
    private final Plugin plugin;
    private final Messages messages;
    private final PluginConfig pluginConfig;
    private final TerritoryService territoryService;
    private final NamePromptService namePromptService;
    private final CustomItems customItems;
    private final BuildingsConfig buildingsConfig;

    public ProclamationListener(
        Plugin plugin,
        Messages messages,
        PluginConfig pluginConfig,
        TerritoryService territoryService,
        NamePromptService namePromptService,
        CustomItems customItems,
        BuildingsConfig buildingsConfig
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.pluginConfig = pluginConfig;
        this.territoryService = territoryService;
        this.namePromptService = namePromptService;
        this.customItems = customItems;
        this.buildingsConfig = buildingsConfig;
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

        Optional<String> pending = namePromptService.getName(player.getUniqueId());
        String nationName = pending.orElse(null);

        territoryService.createTerritory(player.getUniqueId(), center, nationName)
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> {
                        removeOne(player, hand);
                        namePromptService.clear(player.getUniqueId());
                        if (result.data().createdNation()) {
                            messages.send(player, "info.created", java.util.Map.of("name", result.data().nationName()));
                        } else {
                            messages.send(player, "info.territory-created", java.util.Map.of("name", result.data().nationName()));
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
}
