package kr.lunaf.nationSystem.domain;

public record NationSettings(
    long nationId,
    boolean pvpEnabled,
    boolean inviteLock,
    boolean chatDefault
) {
}
