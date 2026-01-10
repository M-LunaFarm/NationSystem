package kr.lunaf.nationSystem.listener;

import kr.lunaf.nationSystem.config.Messages;
import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.service.NamePromptService;
import kr.lunaf.nationSystem.service.NationService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
    private final Messages messages;
    private final PluginConfig pluginConfig;
    private final NamePromptService namePromptService;
    private final NationService nationService;

    public ChatListener(Messages messages, PluginConfig pluginConfig, NamePromptService namePromptService, NationService nationService) {
        this.messages = messages;
        this.pluginConfig = pluginConfig;
        this.namePromptService = namePromptService;
        this.nationService = nationService;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (namePromptService.isWaiting(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            String name = event.getMessage().trim();
            if (name.contains(" ")) {
                messages.send(event.getPlayer(), "error.invalid-name");
                return;
            }
            if (name.length() < pluginConfig.nameMinLength() || name.length() > pluginConfig.nameMaxLength()) {
                messages.send(event.getPlayer(), "error.invalid-name");
                return;
            }
            if (!pluginConfig.namePattern().matcher(name).matches()) {
                messages.send(event.getPlayer(), "error.invalid-name");
                return;
            }
            boolean taken = nationService.isNameTaken(name).join();
            if (taken) {
                messages.send(event.getPlayer(), "error.name-taken");
                return;
            }
            namePromptService.setName(event.getPlayer().getUniqueId(), name);
            messages.send(event.getPlayer(), "info.name-set", java.util.Map.of("name", name));
            return;
        }
        if (!nationService.isNationChatEnabled(event.getPlayer().getUniqueId())) {
            return;
        }
        if (nationService.getCachedMembership(event.getPlayer().getUniqueId()) == null) {
            nationService.getMembership(event.getPlayer().getUniqueId()).join();
        }
        if (nationService.getCachedMembership(event.getPlayer().getUniqueId()) == null) {
            return;
        }
        event.setCancelled(true);
        nationService.sendNationChat(
            event.getPlayer().getUniqueId(),
            event.getPlayer().getName(),
            event.getMessage()
        );
    }
}
