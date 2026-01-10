package kr.lunaf.nationSystem.api;

import kr.lunaf.nationSystem.domain.NationMembership;
import kr.lunaf.nationSystem.domain.WarState;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface NationSystemApi {
    Optional<NationMembership> getMembership(UUID playerUuid);

    Optional<Long> getNationId(UUID playerUuid);

    Optional<String> getNationName(UUID playerUuid);

    boolean isInWar(long nationId);

    Optional<WarState> getWarState(long nationId);

    void sendNationMessage(long nationId, String message);

    CompletableFuture<Boolean> enqueueWar(UUID playerUuid);
}
