package kr.lunaf.nationSystem.domain;

public record BuildingDefinition(
    BuildingType type,
    String displayName,
    int buildTimeSeconds,
    String structurePath,
    long price,
    int minLevel,
    int maxPerNation
) {
}
