package kr.lunaf.nationSystem.domain;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Optional;

public enum DailyQuestType {
    FARM_WHEAT(1, "[농사] 밀 수확", 60, QuestKind.ITEM_DELIVERY, new Material[]{Material.WHEAT}),
    FARM_SUGAR_CANE(2, "[농사] 사탕수수 수확", 120, QuestKind.ITEM_DELIVERY, new Material[]{Material.SUGAR_CANE}),
    FARM_POTATO(3, "[농사] 감자 수확", 120, QuestKind.ITEM_DELIVERY, new Material[]{Material.POTATO}),
    FARM_CARROT(4, "[농사] 당근 수확", 120, QuestKind.ITEM_DELIVERY, new Material[]{Material.CARROT}),
    FARM_PUMPKIN(5, "[농사] 호박 수확", 80, QuestKind.ITEM_DELIVERY, new Material[]{Material.PUMPKIN}),
    FARM_MELON(6, "[농사] 수박 수확", 160, QuestKind.ITEM_DELIVERY, new Material[]{Material.MELON}),
    SMELT_IRON(7, "[광물] 철 굽기", 100, QuestKind.SMELT, new Material[]{Material.IRON_INGOT}),
    SMELT_GOLD(8, "[광물] 금 굽기", 40, QuestKind.SMELT, new Material[]{Material.GOLD_INGOT}),
    MINE_DIAMOND(9, "[광물] 다이아몬드 캐기", 15, QuestKind.MINE, new Material[]{Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE}),
    MINE_EMERALD(10, "[광물] 에메랄드 캐기", 7, QuestKind.MINE, new Material[]{Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE});

    private final int id;
    private final String displayName;
    private final int baseAmount;
    private final QuestKind kind;
    private final Material[] materials;

    DailyQuestType(int id, String displayName, int baseAmount, QuestKind kind, Material[] materials) {
        this.id = id;
        this.displayName = displayName;
        this.baseAmount = baseAmount;
        this.kind = kind;
        this.materials = materials;
    }

    public int id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int baseAmount() {
        return baseAmount;
    }

    public QuestKind kind() {
        return kind;
    }

    public Material[] materials() {
        return materials;
    }

    public static Optional<DailyQuestType> fromId(int id) {
        return Arrays.stream(values()).filter(type -> type.id == id).findFirst();
    }
}
