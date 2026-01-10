package kr.lunaf.nationSystem.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum BuildingType {
    BANK("bank"),
    CHEST("chest"),
    PRESENT("present"),
    SHOP("shop"),
    RESTAURANT("restaurant"),
    SHELTER("shelter"),
    ISLAND("island"),
    LAB("lab"),
    FISHING("fishing"),
    MINE("mine");

    private final String key;

    BuildingType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<BuildingType> fromKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(type -> type.key.equals(normalized))
            .findFirst();
    }
}
