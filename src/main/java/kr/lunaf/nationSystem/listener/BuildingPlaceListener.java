package kr.lunaf.nationSystem.listener;

import kr.lunaf.nationSystem.config.Messages;
import kr.lunaf.nationSystem.config.BuildingsConfig;
import kr.lunaf.nationSystem.domain.Building;
import kr.lunaf.nationSystem.domain.BuildingType;
import kr.lunaf.nationSystem.service.BuildingService;
import kr.lunaf.nationSystem.util.CustomItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public class BuildingPlaceListener implements Listener {
    private final Plugin plugin;
    private final Messages messages;
    private final BuildingService buildingService;
    private final CustomItems customItems;
    private final BuildingsConfig buildingsConfig;

    public BuildingPlaceListener(
        Plugin plugin,
        Messages messages,
        BuildingService buildingService,
        CustomItems customItems,
        BuildingsConfig buildingsConfig
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.buildingService = buildingService;
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
        Optional<BuildingType> type = customItems.getBuildingType(item);
        if (type.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Location base = event.getClickedBlock().getLocation();
        EquipmentSlot hand = event.getHand();
        buildingService.placeBuilding(event.getPlayer().getUniqueId(), base, type.get(), event.getPlayer().getFacing())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(event.getPlayer(), "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> {
                        removeOne(event.getPlayer(), hand);
                        Building building = result.data();
                        String display = building.type().key();
                        var definition = buildingsConfig.get(building.type());
                        if (definition != null) {
                            display = definition.displayName();
                        }
                        messages.send(event.getPlayer(), "info.build-started", java.util.Map.of(
                            "type", display
                        ));
                    }
                    case NOT_IN_NATION -> messages.send(event.getPlayer(), "error.not-in-nation");
                    case NOT_OWNER -> messages.send(event.getPlayer(), "error.not-owner");
                    case NOT_IN_TERRITORY -> messages.send(event.getPlayer(), "error.not-in-territory");
                    case WALL_NOT_BUILT -> messages.send(event.getPlayer(), "error.wall-not-built");
                    case INVALID_Y -> messages.send(event.getPlayer(), "error.invalid-location");
                    case TOO_CLOSE -> messages.send(event.getPlayer(), "error.build-too-close");
                    case LEVEL_TOO_LOW -> messages.send(event.getPlayer(), "error.build-level-too-low");
                    case LIMIT_REACHED -> messages.send(event.getPlayer(), "error.build-limit-reached");
                    case INVALID_TYPE -> messages.send(event.getPlayer(), "error.invalid-args");
                    case STRUCTURE_MISSING -> messages.send(event.getPlayer(), "error.structure-missing");
                    default -> messages.send(event.getPlayer(), "error.unknown");
                }
            }));
    }

    private void removeOne(org.bukkit.entity.Player player, EquipmentSlot hand) {
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
