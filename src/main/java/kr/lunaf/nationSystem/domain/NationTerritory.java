package kr.lunaf.nationSystem.domain;

import java.time.Instant;

public record NationTerritory(
    long id,
    long nationId,
    String world,
    int centerX,
    int centerY,
    int centerZ,
    int size,
    WallStatus wallStatus,
    Instant wallExpiresAt
) {
}
