package kr.lunaf.nationSystem.listener;

import kr.lunaf.nationSystem.service.StorageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class StorageListener implements Listener {
    private final StorageService storageService;

    public StorageListener(StorageService storageService) {
        this.storageService = storageService;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        storageService.handleClose(event.getInventory());
    }
}
