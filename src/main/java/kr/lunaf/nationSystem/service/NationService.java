package kr.lunaf.nationSystem.service;

import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.domain.Nation;
import kr.lunaf.nationSystem.domain.NationMember;
import kr.lunaf.nationSystem.domain.NationMembership;
import kr.lunaf.nationSystem.domain.NationRole;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;
import kr.lunaf.nationSystem.repository.NationSettingsRepository;
import kr.lunaf.nationSystem.repository.PlayerSettingsRepository;
import kr.lunaf.nationSystem.service.InvitationService.Invite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

public class NationService {
    private final PluginConfig pluginConfig;
    private final DatabaseManager databaseManager;
    private final NationRepository nationRepository;
    private final NationMemberRepository memberRepository;
    private final NationSettingsRepository settingsRepository;
    private final PlayerSettingsRepository playerSettingsRepository;
    private final InvitationService invitationService;
    private final EconomyService economyService;
    private final ExecutorService dbExecutor;
    private final Executor syncExecutor;
    private final Map<UUID, NationMembership> membershipCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> chatPreferenceCache = new ConcurrentHashMap<>();

    public NationService(
        PluginConfig pluginConfig,
        DatabaseManager databaseManager,
        NationRepository nationRepository,
        NationMemberRepository memberRepository,
        NationSettingsRepository settingsRepository,
        PlayerSettingsRepository playerSettingsRepository,
        InvitationService invitationService,
        EconomyService economyService,
        ExecutorService dbExecutor,
        Executor syncExecutor
    ) {
        this.pluginConfig = pluginConfig;
        this.databaseManager = databaseManager;
        this.nationRepository = nationRepository;
        this.memberRepository = memberRepository;
        this.settingsRepository = settingsRepository;
        this.playerSettingsRepository = playerSettingsRepository;
        this.invitationService = invitationService;
        this.economyService = economyService;
        this.dbExecutor = dbExecutor;
        this.syncExecutor = syncExecutor;
    }

    public CompletableFuture<ServiceResult<NationMembership>> createNation(UUID playerUuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            if (memberRepository.findByPlayer(playerUuid).isPresent()) {
                return ServiceResult.failure(Status.ALREADY_IN_NATION);
            }
            if (nationRepository.findByName(name).isPresent()) {
                return ServiceResult.failure(Status.NAME_TAKEN);
            }
            long cost = pluginConfig.createCost();
            if (cost > 0) {
                if (!economyService.isAvailable()) {
                    return ServiceResult.failure(Status.ECONOMY_UNAVAILABLE);
                }
                boolean withdrawn = economyService.withdrawSync(playerUuid, cost, syncExecutor).join();
                if (!withdrawn) {
                    return ServiceResult.failure(Status.INSUFFICIENT_FUNDS);
                }
            }
            try {
                long nationId = databaseManager.withTransaction(connection -> {
                    long createdId = nationRepository.insertNation(
                        connection,
                        new Nation(0L, name, playerUuid, 1, 0L, 0L, 0)
                    );
                    settingsRepository.insertDefaults(connection, createdId);
                    memberRepository.insertMember(connection, createdId, playerUuid, NationRole.OWNER);
                    return createdId;
                });
                NationMembership membership = new NationMembership(nationId, name, NationRole.OWNER, 1);
                membershipCache.put(playerUuid, membership);
                return ServiceResult.success(membership);
            } catch (Exception e) {
                return ServiceResult.failure(Status.ERROR);
            }
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<NationMembership>> getMembership(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            NationMembership cached = membershipCache.get(playerUuid);
            if (cached != null) {
                return ServiceResult.success(cached);
            }
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            Optional<Nation> nation = nationRepository.findById(member.get().nationId());
            if (nation.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            NationMembership membership = new NationMembership(
                nation.get().id(),
                nation.get().name(),
                member.get().role(),
                nation.get().level()
            );
            membershipCache.put(playerUuid, membership);
            return ServiceResult.success(membership);
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<String>> invite(UUID inviterUuid, UUID targetUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (inviterUuid.equals(targetUuid)) {
                return ServiceResult.failure(Status.SELF_INVITE);
            }
            Optional<NationMember> inviterMember = memberRepository.findByPlayer(inviterUuid);
            if (inviterMember.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (memberRepository.findByPlayer(targetUuid).isPresent()) {
                return ServiceResult.failure(Status.TARGET_IN_NATION);
            }
            NationRole role = inviterMember.get().role();
            if (role != NationRole.OWNER && role != NationRole.SUBKING) {
                return ServiceResult.failure(Status.OWNER_ONLY);
            }
            Optional<Nation> nation = nationRepository.findById(inviterMember.get().nationId());
            if (nation.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            int memberCount = nationRepository.countMembers(nation.get().id());
            if (memberCount >= pluginConfig.maxMembersForLevel(nation.get().level())) {
                return ServiceResult.failure(Status.NATION_FULL);
            }
            invitationService.createInvite(targetUuid, nation.get().id(), nation.get().name(), inviterUuid);
            return ServiceResult.success(nation.get().name());
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<NationMembership>> acceptInvite(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (memberRepository.findByPlayer(playerUuid).isPresent()) {
                return ServiceResult.failure(Status.ALREADY_IN_NATION);
            }
            Optional<Invite> invite = invitationService.consumeInvite(playerUuid);
            if (invite.isEmpty()) {
                return ServiceResult.failure(Status.INVITE_NOT_FOUND);
            }
            Invite data = invite.get();
            Optional<Nation> nation = nationRepository.findById(data.nationId());
            if (nation.isEmpty()) {
                return ServiceResult.failure(Status.INVITE_NOT_FOUND);
            }
            int memberCount = nationRepository.countMembers(nation.get().id());
            if (memberCount >= pluginConfig.maxMembersForLevel(nation.get().level())) {
                return ServiceResult.failure(Status.NATION_FULL);
            }
            try {
                databaseManager.withTransaction(connection -> {
                    memberRepository.insertMember(connection, data.nationId(), playerUuid, NationRole.MEMBER);
                    return null;
                });
                NationMembership membership = new NationMembership(
                    data.nationId(),
                    data.nationName(),
                    NationRole.MEMBER,
                    nation.get().level()
                );
                membershipCache.put(playerUuid, membership);
                return ServiceResult.success(membership);
            } catch (Exception e) {
                return ServiceResult.failure(Status.ERROR);
            }
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<Void>> declineInvite(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Invite> invite = invitationService.consumeInvite(playerUuid);
            if (invite.isEmpty()) {
                return ServiceResult.failure(Status.INVITE_NOT_FOUND);
            }
            return ServiceResult.success(null);
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<Void>> leave(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<NationMember> member = memberRepository.findByPlayer(playerUuid);
            if (member.isEmpty()) {
                return ServiceResult.failure(Status.NOT_IN_NATION);
            }
            if (member.get().role() == NationRole.OWNER) {
                return ServiceResult.failure(Status.OWNER_ONLY);
            }
            try {
                databaseManager.withTransaction(connection -> {
                    memberRepository.deleteMember(connection, member.get().nationId(), playerUuid);
                    return null;
                });
                membershipCache.remove(playerUuid);
                return ServiceResult.success(null);
            } catch (Exception e) {
                return ServiceResult.failure(Status.ERROR);
            }
        }, dbExecutor);
    }

    public CompletableFuture<ServiceResult<Boolean>> toggleChat(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean current = chatPreferenceCache.getOrDefault(playerUuid, false);
            boolean next = !current;
            try {
                databaseManager.withTransaction(connection -> {
                    playerSettingsRepository.setNationChatEnabled(connection, playerUuid, next);
                    return null;
                });
                chatPreferenceCache.put(playerUuid, next);
                return ServiceResult.success(next);
            } catch (Exception e) {
                return ServiceResult.failure(Status.ERROR);
            }
        }, dbExecutor);
    }

    public NationMembership getCachedMembership(UUID playerUuid) {
        return membershipCache.get(playerUuid);
    }

    public boolean isNationChatEnabled(UUID playerUuid) {
        Boolean cached = chatPreferenceCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        Optional<Boolean> stored = playerSettingsRepository.getNationChatEnabled(playerUuid);
        boolean enabled = stored.orElse(false);
        chatPreferenceCache.put(playerUuid, enabled);
        return enabled;
    }

    public CompletableFuture<Boolean> isNameTaken(String name) {
        return CompletableFuture.supplyAsync(() -> nationRepository.findByName(name).isPresent(), dbExecutor);
    }

    public void clearMembership(UUID playerUuid) {
        membershipCache.remove(playerUuid);
    }

    public void clearMembershipsForNation(long nationId) {
        for (UUID memberUuid : memberRepository.listMemberUuids(nationId)) {
            membershipCache.remove(memberUuid);
        }
    }

    public CompletableFuture<Integer> getMemberCountAsync(long nationId) {
        return CompletableFuture.supplyAsync(() -> nationRepository.countMembers(nationId), dbExecutor);
    }

    public void sendNationChat(UUID senderUuid, String senderName, String message) {
        NationMembership membership = getCachedMembership(senderUuid);
        if (membership == null) {
            ServiceResult<NationMembership> loaded = getMembership(senderUuid).join();
            if (!loaded.isSuccess()) {
                return;
            }
            membership = loaded.data();
        }
        long nationId = membership.nationId();
        String formatted = pluginConfig.nationChatFormat()
            .replace("%player%", senderName)
            .replace("%message%", message);
        String finalMessage = formatted;
        java.util.List<UUID> members = memberRepository.listMemberUuids(nationId);
        syncExecutor.execute(() -> {
            net.kyori.adventure.text.Component component =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(finalMessage);
            for (UUID memberUuid : members) {
                Player target = Bukkit.getPlayer(memberUuid);
                if (target != null) {
                    target.sendMessage(component);
                }
            }
        });
    }

    public void sendNationMessage(long nationId, String message) {
        java.util.List<UUID> members = memberRepository.listMemberUuids(nationId);
        syncExecutor.execute(() -> {
            net.kyori.adventure.text.Component component =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(message);
            for (UUID memberUuid : members) {
                Player target = Bukkit.getPlayer(memberUuid);
                if (target != null) {
                    target.sendMessage(component);
                }
            }
        });
    }

    public enum Status {
        SUCCESS,
        ALREADY_IN_NATION,
        NAME_TAKEN,
        NOT_IN_NATION,
        OWNER_ONLY,
        INVITE_NOT_FOUND,
        TARGET_IN_NATION,
        SELF_INVITE,
        NATION_FULL,
        ECONOMY_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        ERROR
    }

    public record ServiceResult<T>(Status status, T data) {
        public static <T> ServiceResult<T> success(T data) {
            return new ServiceResult<>(Status.SUCCESS, data);
        }

        public static <T> ServiceResult<T> failure(Status status) {
            return new ServiceResult<>(status, null);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }
}
