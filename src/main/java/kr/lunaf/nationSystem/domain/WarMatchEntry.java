package kr.lunaf.nationSystem.domain;

public record WarMatchEntry(
    long nationId,
    int nationLevel,
    int waitedSeconds
) {
}
