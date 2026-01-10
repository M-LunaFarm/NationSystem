package kr.lunaf.nationSystem.domain;

import java.time.LocalDate;

public record DailyQuest(
    long id,
    long nationId,
    DailyQuestType type,
    int requiredAmount,
    int progressAmount,
    boolean completed,
    LocalDate questDate
) {
}
