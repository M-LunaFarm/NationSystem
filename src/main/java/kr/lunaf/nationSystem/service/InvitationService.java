package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.PluginConfig;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InvitationService {
    private final PluginConfig pluginConfig;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    public InvitationService(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public void createInvite(UUID targetUuid, long nationId, String nationName, UUID inviterUuid) {
        Instant expiresAt = Instant.now().plusSeconds(pluginConfig.inviteExpireSeconds());
        invites.put(targetUuid, new Invite(nationId, nationName, inviterUuid, expiresAt));
    }

    public Optional<Invite> getInvite(UUID targetUuid) {
        Invite invite = invites.get(targetUuid);
        if (invite == null) {
            return Optional.empty();
        }
        if (invite.isExpired()) {
            invites.remove(targetUuid);
            return Optional.empty();
        }
        return Optional.of(invite);
    }

    public Optional<Invite> consumeInvite(UUID targetUuid) {
        Optional<Invite> invite = getInvite(targetUuid);
        invite.ifPresent(value -> invites.remove(targetUuid));
        return invite;
    }

    public void clearInvite(UUID targetUuid) {
        invites.remove(targetUuid);
    }

    public record Invite(long nationId, String nationName, UUID inviterUuid, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
