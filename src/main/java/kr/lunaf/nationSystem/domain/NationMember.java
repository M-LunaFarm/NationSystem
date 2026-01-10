package kr.lunaf.nationSystem.domain;

import java.util.UUID;

public record NationMember(
    long nationId,
    UUID playerUuid,
    NationRole role
) {
}
