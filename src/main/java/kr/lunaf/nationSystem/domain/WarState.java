package kr.lunaf.nationSystem.domain;

public record WarState(
    long nationA,
    long nationB,
    WarPhase phase,
    int remainingSeconds
) {
}
