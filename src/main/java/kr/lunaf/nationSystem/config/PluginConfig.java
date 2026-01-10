package kr.lunaf.nationSystem.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PluginConfig {
    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final Pattern namePattern;
    private final List<Long> levelUpExp;
    private final List<Long> levelUpMoney;
    private final List<Integer> maxMembersByLevel;
    private final List<Integer> maxTerritoriesByLevel;
    private final List<Integer> maxKnightsByLevel;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        String regex = config.getString("nation.name-regex", "^[a-zA-Z0-9_]+$");
        this.namePattern = Pattern.compile(regex);
        this.levelUpExp = loadLongList("nation.level-up.exp", defaultLevelExp());
        this.levelUpMoney = loadLongList("nation.level-up.money", defaultLevelMoney());
        this.maxMembersByLevel = loadIntList("nation.limits.max-members-by-level", List.of(defaultMaxMembers()));
        this.maxTerritoriesByLevel = loadIntList("nation.limits.max-territories-by-level", List.of(maxTerritoriesPerNation()));
        this.maxKnightsByLevel = loadIntList("nation.limits.max-knights-by-level", List.of(0));
    }

    public DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
            config.getString("database.type", "mariadb"),
            config.getString("database.host", "localhost"),
            config.getInt("database.port", 3306),
            config.getString("database.name", "nationsystem"),
            config.getString("database.user", "root"),
            config.getString("database.password", ""),
            config.getInt("database.pool-size", 10),
            config.getBoolean("database.use-ssl", false)
        );
    }

    public int nameMinLength() {
        return config.getInt("nation.name-min-length", 2);
    }

    public int nameMaxLength() {
        return config.getInt("nation.name-max-length", 8);
    }

    public Pattern namePattern() {
        return namePattern;
    }

    public long createCost() {
        return config.getLong("nation.create-cost", 0);
    }

    public int inviteExpireSeconds() {
        return config.getInt("nation.invite-expire-seconds", 60);
    }

    public int defaultMaxMembers() {
        return config.getInt("nation.max-members-default", 20);
    }

    public int maxMembersForLevel(int level) {
        return valueForLevel(maxMembersByLevel, level, defaultMaxMembers());
    }

    public int maxTerritoriesForLevel(int level) {
        return valueForLevel(maxTerritoriesByLevel, level, maxTerritoriesPerNation());
    }

    public int maxKnightsForLevel(int level) {
        return valueForLevel(maxKnightsByLevel, level, 0);
    }

    public long levelUpExpCost(int currentLevel) {
        return valueForLevelLong(levelUpExp, currentLevel);
    }

    public long levelUpMoneyCost(int currentLevel) {
        return valueForLevelLong(levelUpMoney, currentLevel);
    }

    public int maxLevel() {
        int expLevels = levelUpExp.size();
        int moneyLevels = levelUpMoney.size();
        return Math.min(expLevels, moneyLevels) + 1;
    }

    public String territoryWorld() {
        return config.getString("territory.world", "world");
    }

    public int territorySize() {
        return config.getInt("territory.size", 103);
    }

    public int territoryMinDistance() {
        return config.getInt("territory.min-distance", 300);
    }

    public int territoryXzLimit() {
        return config.getInt("territory.xz-limit", 7000);
    }

    public int territoryYMin() {
        return config.getInt("territory.y-min", 35);
    }

    public int territoryYMax() {
        return config.getInt("territory.y-max", 80);
    }

    public int wallExpireMinutes() {
        return config.getInt("territory.wall-expire-minutes", 60);
    }

    public int maxTerritoriesPerNation() {
        return config.getInt("territory.max-per-nation", 1);
    }

    public String wallStructurePath() {
        return config.getString("structures.wall-basic", "structures/wall/basic_wall.nbt");
    }

    public String centerStructurePath() {
        return config.getString("structures.center", "structures/build/center.nbt");
    }

    public int buildingMinSpacing() {
        return config.getInt("building.min-spacing", 21);
    }

    public int storageSize() {
        return config.getInt("storage.size", 54);
    }

    public int presentCooldownHours() {
        return config.getInt("present.cooldown-hours", 20);
    }

    public long presentRewardMoney() {
        return config.getLong("present.reward-money", 0);
    }

    public long presentRewardExp() {
        return config.getLong("present.reward-exp", 0);
    }

    public String nationChatPrefix() {
        return config.getString("chat.nation-chat-prefix", "&6[Nation]&r");
    }

    public String nationChatFormat() {
        return config.getString("chat.nation-chat-format", "&6[Nation]&r &7%player%: &f%message%");
    }

    private List<Long> defaultLevelExp() {
        List<Long> values = new ArrayList<>();
        Collections.addAll(values,
            1000L,
            5000L,
            10000L,
            30000L,
            60000L,
            150000L,
            300000L,
            500000L,
            1000000L
        );
        return values;
    }

    private List<Long> defaultLevelMoney() {
        List<Long> values = new ArrayList<>();
        Collections.addAll(values,
            5000000L,
            10000000L,
            30000000L,
            70000000L,
            100000000L,
            500000000L,
            1000000000L,
            3000000000L,
            5000000000L
        );
        return values;
    }

    private List<Long> loadLongList(String path, List<Long> fallback) {
        List<Long> values = config.getLongList(path);
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values;
    }

    private List<Integer> loadIntList(String path, List<Integer> fallback) {
        List<Integer> values = config.getIntegerList(path);
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values;
    }

    private int valueForLevel(List<Integer> values, int level, int fallback) {
        if (values.isEmpty()) {
            return fallback;
        }
        int index = Math.max(1, level) - 1;
        if (index < values.size()) {
            return values.get(index);
        }
        return values.get(values.size() - 1);
    }

    private long valueForLevelLong(List<Long> values, int level) {
        if (values.isEmpty()) {
            return -1;
        }
        int index = Math.max(1, level) - 1;
        if (index < values.size()) {
            return values.get(index);
        }
        return -1;
    }
}
