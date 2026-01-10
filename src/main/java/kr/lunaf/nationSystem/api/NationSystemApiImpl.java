package kr.lunaf.nationSystem.api;

import kr.lunaf.nationSystem.domain.NationMembership;
import kr.lunaf.nationSystem.domain.WarState;
import kr.lunaf.nationSystem.service.NationService;
import kr.lunaf.nationSystem.service.WarService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NationSystemApiImpl implements NationSystemApi {
    private final NationService nationService;
    private final WarService warService;

    public NationSystemApiImpl(NationService nationService, WarService warService) {
        this.nationService = nationService;
        this.warService = warService;
    }

    @Override
    public Optional<NationMembership> getMembership(UUID playerUuid) {
        NationMembership cached = nationService.getCachedMembership(playerUuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        NationService.ServiceResult<NationMembership> result = nationService.getMembership(playerUuid).join();
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        return Optional.of(result.data());
    }

    @Override
    public Optional<Long> getNationId(UUID playerUuid) {
        return getMembership(playerUuid).map(NationMembership::nationId);
    }

    @Override
    public Optional<String> getNationName(UUID playerUuid) {
        return getMembership(playerUuid).map(NationMembership::nationName);
    }

    @Override
    public boolean isInWar(long nationId) {
        return warService.isInWar(nationId);
    }

    @Override
    public Optional<WarState> getWarState(long nationId) {
        return warService.getWarState(nationId);
    }

    @Override
    public void sendNationMessage(long nationId, String message) {
        nationService.sendNationMessage(nationId, message);
    }

    @Override
    public CompletableFuture<Boolean> enqueueWar(UUID playerUuid) {
        return warService.enqueue(playerUuid).thenApply(WarService.ServiceResult::isSuccess);
    }
}
