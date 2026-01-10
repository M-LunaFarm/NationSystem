package kr.lunaf.nationSystem.domain;

import java.time.Instant;

public record Building(
    long id,
    long territoryId,
    BuildingType type,
    BuildingState state,
    String direction,
    String world,
    int baseX,
    int baseY,
    int baseZ,
    int level,
    Instant buildCompleteAt
) {
}
