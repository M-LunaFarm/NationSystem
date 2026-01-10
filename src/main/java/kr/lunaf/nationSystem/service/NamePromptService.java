package kr.lunaf.nationSystem.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NamePromptService {
    private final Map<UUID, String> pendingNames = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> waiting = new ConcurrentHashMap<>();

    public void startPrompt(UUID playerUuid) {
        waiting.put(playerUuid, true);
        pendingNames.remove(playerUuid);
    }

    public boolean isWaiting(UUID playerUuid) {
        return waiting.getOrDefault(playerUuid, false);
    }

    public void setName(UUID playerUuid, String name) {
        pendingNames.put(playerUuid, name);
        waiting.remove(playerUuid);
    }

    public Optional<String> getName(UUID playerUuid) {
        return Optional.ofNullable(pendingNames.get(playerUuid));
    }

    public Optional<String> consumeName(UUID playerUuid) {
        String name = pendingNames.remove(playerUuid);
        waiting.remove(playerUuid);
        return Optional.ofNullable(name);
    }

    public void clear(UUID playerUuid) {
        pendingNames.remove(playerUuid);
        waiting.remove(playerUuid);
    }
}
