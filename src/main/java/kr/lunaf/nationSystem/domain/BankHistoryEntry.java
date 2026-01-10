package kr.lunaf.nationSystem.domain;

import java.time.Instant;
import java.util.UUID;

public record BankHistoryEntry(
    long id,
    long nationId,
    BankHistoryType type,
    long amount,
    UUID actorUuid,
    Instant createdAt
) {
}
