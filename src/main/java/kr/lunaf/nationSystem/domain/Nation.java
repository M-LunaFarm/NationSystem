package kr.lunaf.nationSystem.domain;

import java.util.UUID;

public record Nation(
    long id,
    String name,
    UUID ownerUuid,
    int level,
    long exp,
    long bankBalance,
    int score
) {
}
