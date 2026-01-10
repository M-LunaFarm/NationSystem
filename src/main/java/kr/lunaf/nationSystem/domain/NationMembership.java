package kr.lunaf.nationSystem.domain;

public record NationMembership(
    long nationId,
    String nationName,
    NationRole role,
    int level
) {
}
