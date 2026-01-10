package kr.lunaf.nationSystem.command;

import kr.lunaf.nationSystem.config.Messages;
import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.domain.NationMembership;
import kr.lunaf.nationSystem.config.BuildingsConfig;
import kr.lunaf.nationSystem.domain.BuildingDefinition;
import kr.lunaf.nationSystem.domain.BuildingType;
import kr.lunaf.nationSystem.domain.NationTerritory;
import kr.lunaf.nationSystem.service.BankService;
import kr.lunaf.nationSystem.service.NationService;
import kr.lunaf.nationSystem.service.NationLevelService;
import kr.lunaf.nationSystem.service.PresentService;
import kr.lunaf.nationSystem.service.QuestService;
import kr.lunaf.nationSystem.service.ShopService;
import kr.lunaf.nationSystem.service.StorageService;
import kr.lunaf.nationSystem.service.TerritoryService;
import kr.lunaf.nationSystem.service.WarService;
import kr.lunaf.nationSystem.util.CustomItems;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NationCommand implements TabExecutor {
    private final Plugin plugin;
    private final Messages messages;
    private final NationService nationService;
    private final PluginConfig pluginConfig;
    private final CustomItems customItems;
    private final BuildingsConfig buildingsConfig;
    private final QuestService questService;
    private final ShopService shopService;
    private final WarService warService;
    private final BankService bankService;
    private final NationLevelService levelService;
    private final TerritoryService territoryService;
    private final StorageService storageService;
    private final PresentService presentService;

    public NationCommand(
        Plugin plugin,
        Messages messages,
        NationService nationService,
        PluginConfig pluginConfig,
        CustomItems customItems,
        BuildingsConfig buildingsConfig,
        QuestService questService,
        ShopService shopService,
        WarService warService,
        BankService bankService,
        NationLevelService levelService,
        TerritoryService territoryService,
        StorageService storageService,
        PresentService presentService
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.nationService = nationService;
        this.pluginConfig = pluginConfig;
        this.customItems = customItems;
        this.buildingsConfig = buildingsConfig;
        this.questService = questService;
        this.shopService = shopService;
        this.warService = warService;
        this.bankService = bankService;
        this.levelService = levelService;
        this.territoryService = territoryService;
        this.storageService = storageService;
        this.presentService = presentService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "error.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.sendList(sender, "info.help");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create", "생성" -> handleCreate(player, args);
            case "info", "정보" -> handleInfo(player);
            case "bank", "금고" -> handleBank(player, args);
            case "invite", "초대" -> handleInvite(player, args);
            case "accept", "수락" -> handleAccept(player);
            case "decline", "거절" -> handleDecline(player);
            case "leave", "탈퇴" -> handleLeave(player);
            case "move", "이동" -> handleMove(player, args);
            case "chat", "채팅" -> handleChat(player);
            case "levelup", "레벨업" -> handleLevelUp(player);
            case "quest", "퀘스트" -> handleQuest(player, args);
            case "shop", "상점" -> handleShop(player, args);
            case "war", "전쟁" -> handleWar(player, args);
            case "storage", "창고" -> handleStorage(player);
            case "present", "선물" -> handlePresent(player);
            case "giveitem" -> handleGiveItem(player, args);
            default -> messages.sendList(sender, "info.help");
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "error.invalid-args");
            return;
        }
        String name = args[1];
        if (name.length() < pluginConfig.nameMinLength() || name.length() > pluginConfig.nameMaxLength()) {
            messages.send(player, "error.invalid-name");
            return;
        }
        if (!pluginConfig.namePattern().matcher(name).matches()) {
            messages.send(player, "error.invalid-name");
            return;
        }
        CompletableFuture<NationService.ServiceResult<NationMembership>> future =
            nationService.createNation(player.getUniqueId(), name);
        future.whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null || result == null) {
                messages.send(player, "error.unknown");
                return;
            }
            switch (result.status()) {
                case SUCCESS -> messages.send(player, "info.created", Map.of("name", name));
                case ALREADY_IN_NATION -> messages.send(player, "error.already-in-nation");
                case NAME_TAKEN -> messages.send(player, "error.name-taken");
                case ECONOMY_UNAVAILABLE -> messages.send(player, "error.economy-unavailable");
                case INSUFFICIENT_FUNDS -> messages.send(player, "error.insufficient-funds");
                default -> messages.send(player, "error.unknown");
            }
        }));
    }

    private void handleInfo(Player player) {
        nationService.getMembership(player.getUniqueId()).whenComplete((result, throwable) -> {
            if (throwable != null || result == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "error.unknown"));
                return;
            }
            if (!result.isSuccess()) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "error.not-in-nation"));
                return;
            }
            NationMembership membership = result.data();
            nationService.getMemberCountAsync(membership.nationId()).whenComplete((count, countErr) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (countErr != null) {
                        messages.send(player, "error.unknown");
                        return;
                    }
                    messages.send(player, "info.info", Map.of(
                        "name", membership.nationName(),
                        "level", String.valueOf(membership.level()),
                        "members", String.valueOf(count)
                    ));
                })
            );
        });
    }

    private void handleBank(Player player, String[] args) {
        if (args.length >= 2) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("deposit") || action.equals("넣기")) {
                if (args.length < 3) {
                    messages.send(player, "error.invalid-args");
                    return;
                }
                long amount;
                try {
                    amount = Long.parseLong(args[2]);
                } catch (NumberFormatException e) {
                    messages.send(player, "error.invalid-amount");
                    return;
                }
                if (amount <= 0) {
                    messages.send(player, "error.invalid-amount");
                    return;
                }
                bankService.deposit(player.getUniqueId(), amount)
                    .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null || result == null) {
                            messages.send(player, "error.unknown");
                            return;
                        }
                        switch (result.status()) {
                            case SUCCESS -> messages.send(player, "info.bank-deposit", Map.of("amount", String.valueOf(amount)));
                            case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                            case NO_BANK_BUILDING -> messages.send(player, "error.no-bank-building");
                            case ECONOMY_UNAVAILABLE -> messages.send(player, "error.economy-unavailable");
                            case INSUFFICIENT_FUNDS -> messages.send(player, "error.insufficient-funds");
                            default -> messages.send(player, "error.unknown");
                        }
                    }));
                return;
            }
            if (action.equals("history") || action.equals("기록")) {
                bankService.getHistory(player.getUniqueId(), 10)
                    .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null || result == null) {
                            messages.send(player, "error.unknown");
                            return;
                        }
                        if (!result.isSuccess()) {
                            messages.send(player, result.status() == BankService.Status.NOT_IN_NATION
                                ? "error.not-in-nation" : "error.no-bank-building");
                            return;
                        }
                        messages.send(player, "info.bank-history-header");
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MM.dd HH:mm");
                        for (var entry : result.data()) {
                            String date = formatter.format(entry.createdAt().atZone(java.time.ZoneId.systemDefault()));
                            String type = entry.type() == kr.lunaf.nationSystem.domain.BankHistoryType.DEPOSIT ? "입금" : "사용";
                            messages.send(player, "info.bank-history-entry", Map.of(
                                "type", type,
                                "date", date,
                                "amount", String.valueOf(entry.amount())
                            ));
                        }
                    }));
                return;
            }
        }
        bankService.getBalance(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> messages.sendList(player, "info.bank-balance", Map.of(
                        "balance", String.valueOf(result.data())
                    ));
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    case NO_BANK_BUILDING -> messages.send(player, "error.no-bank-building");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "error.invalid-args");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "error.target-not-found");
            return;
        }
        nationService.invite(player.getUniqueId(), target.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> {
                        messages.send(player, "info.invite-sent", Map.of("player", target.getName()));
                        messages.send(target, "info.invite-received", Map.of("name", result.data()));
                    }
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    case OWNER_ONLY -> messages.send(player, "error.owner-only");
                    case TARGET_IN_NATION -> messages.send(player, "error.target-in-nation");
                    case SELF_INVITE -> messages.send(player, "error.self-invite");
                    case NATION_FULL -> messages.send(player, "error.nation-full");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleAccept(Player player) {
        nationService.acceptInvite(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> messages.send(player, "info.invite-accepted", Map.of("name", result.data().nationName()));
                    case ALREADY_IN_NATION -> messages.send(player, "error.already-in-nation");
                    case INVITE_NOT_FOUND -> messages.send(player, "error.invite-only");
                    case NATION_FULL -> messages.send(player, "error.nation-full");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleDecline(Player player) {
        nationService.declineInvite(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> messages.send(player, "info.invite-declined");
                    case INVITE_NOT_FOUND -> messages.send(player, "error.invite-only");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleLeave(Player player) {
        nationService.leave(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> messages.send(player, "info.left");
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    case OWNER_ONLY -> messages.send(player, "error.owner-only");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleChat(Player player) {
        nationService.toggleChat(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                if (!result.isSuccess()) {
                    messages.send(player, "error.unknown");
                    return;
                }
                if (result.data()) {
                    messages.send(player, "info.chat-on");
                } else {
                    messages.send(player, "info.chat-off");
                }
            }));
    }

    private void handleMove(Player player, String[] args) {
        territoryService.listTerritories(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                if (!result.isSuccess()) {
                    messages.send(player, "error.not-in-nation");
                    return;
                }
                List<NationTerritory> territories = result.data();
                if (territories.isEmpty()) {
                    messages.send(player, "error.no-territory");
                    return;
                }
                if (args.length < 2) {
                    messages.send(player, "info.move-header");
                    for (int i = 0; i < territories.size(); i++) {
                        NationTerritory territory = territories.get(i);
                        messages.send(player, "info.move-entry", Map.of(
                            "index", String.valueOf(i + 1),
                            "world", territory.world(),
                            "x", String.valueOf(territory.centerX()),
                            "y", String.valueOf(territory.centerY()),
                            "z", String.valueOf(territory.centerZ())
                        ));
                    }
                    return;
                }
                int index;
                try {
                    index = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    messages.send(player, "error.invalid-args");
                    return;
                }
                if (index < 1 || index > territories.size()) {
                    messages.send(player, "error.invalid-args");
                    return;
                }
                NationTerritory territory = territories.get(index - 1);
                org.bukkit.World world = Bukkit.getWorld(territory.world());
                if (world == null) {
                    messages.send(player, "error.invalid-location");
                    return;
                }
                int highest = world.getHighestBlockYAt(territory.centerX(), territory.centerZ());
                org.bukkit.Location target = new org.bukkit.Location(
                    world,
                    territory.centerX() + 0.5,
                    highest + 1.0,
                    territory.centerZ() + 0.5
                );
                player.teleport(target);
                messages.send(player, "info.moved", Map.of("index", String.valueOf(index)));
            }));
    }

    private void handleLevelUp(Player player) {
        levelService.levelUp(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> messages.send(player, "info.level-up", Map.of("level", String.valueOf(result.data().level())));
                    case OWNER_ONLY -> messages.send(player, "error.owner-only");
                    case NOT_ENOUGH_EXP -> messages.send(player, "error.level-not-enough-exp");
                    case NOT_ENOUGH_MONEY -> messages.send(player, "error.level-not-enough-money");
                    case MAX_LEVEL -> messages.send(player, "error.level-max");
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleStorage(Player player) {
        storageService.openStorage(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> player.openInventory(result.data());
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    case NO_STORAGE_BUILDING -> messages.send(player, "error.no-storage-building");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handlePresent(Player player) {
        presentService.claim(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> messages.send(player, "info.present-claimed", Map.of(
                        "money", String.valueOf(result.data().money()),
                        "exp", String.valueOf(result.data().exp())
                    ));
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    case NO_PRESENT_BUILDING -> messages.send(player, "error.no-present-building");
                    case COOLDOWN -> messages.send(player, "error.present-cooldown", Map.of(
                        "seconds", String.valueOf(result.data().remainingSeconds())
                    ));
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleQuest(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("deliver")) {
            if (args.length < 3) {
                messages.send(player, "error.invalid-args");
                return;
            }
            int questId;
            try {
                questId = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                messages.send(player, "error.invalid-args");
                return;
            }
            questService.listDailyQuests(player.getUniqueId()).whenComplete((listResult, listErr) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (listErr != null || listResult == null) {
                        messages.send(player, "error.quest-not-ready");
                        return;
                    }
                    if (!listResult.isSuccess()) {
                        if (listResult.status() == QuestService.Status.WALL_NOT_BUILT) {
                            messages.send(player, "error.wall-not-built");
                        } else {
                            messages.send(player, "error.quest-not-ready");
                        }
                        return;
                    }
                    listResult.data().stream()
                        .filter(q -> q.type().id() == questId)
                        .findFirst()
                        .ifPresentOrElse(q -> {
                            if (q.type().kind() != kr.lunaf.nationSystem.domain.QuestKind.ITEM_DELIVERY) {
                                messages.send(player, "error.quest-not-item");
                                return;
                            }
                            org.bukkit.Material mat = q.type().materials()[0];
                            int needed = q.requiredAmount() - q.progressAmount();
                            int available = countItem(player, mat);
                            int amount = Math.min(needed, available);
                            if (amount <= 0) {
                                messages.send(player, "error.invalid-args");
                                return;
                            }
                            removeItem(player, mat, amount);
                            questService.deliverItems(player.getUniqueId(), questId, amount)
                                .whenComplete((deliverResult, deliverErr) ->
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (deliverErr != null || deliverResult == null) {
                                            messages.send(player, "error.unknown");
                                            return;
                                        }
                                        switch (deliverResult.status()) {
                                            case SUCCESS -> {
                                                if (deliverResult.data().completed()) {
                                                    messages.send(player, "info.quest-complete",
                                                        Map.of("name", deliverResult.data().type().displayName()));
                                                } else {
                                                    messages.send(player, "info.quest-progress", Map.of(
                                                        "name", deliverResult.data().type().displayName(),
                                                        "progress", String.valueOf(deliverResult.data().progressAmount()),
                                                        "required", String.valueOf(deliverResult.data().requiredAmount())
                                                    ));
                                                }
                                            }
                                            case ALREADY_COMPLETED -> messages.send(player, "error.quest-already-complete");
                                            case INVALID_TYPE -> messages.send(player, "error.quest-not-item");
                                            default -> messages.send(player, "error.unknown");
                                        }
                                    })
                                );
                        }, () -> messages.send(player, "error.quest-invalid"));
                })
            );
            return;
        }

        questService.getOrCreateDailyQuests(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> {
                        messages.send(player, "info.quest-header");
                        for (kr.lunaf.nationSystem.domain.DailyQuest quest : result.data()) {
                            messages.send(player, "info.quest-entry", Map.of(
                                "id", String.valueOf(quest.type().id()),
                                "name", quest.type().displayName(),
                                "progress", String.valueOf(quest.progressAmount()),
                                "required", String.valueOf(quest.requiredAmount())
                            ));
                        }
                    }
                    case OWNER_ONLY -> messages.send(player, "error.quest-owner-only");
                    case WALL_NOT_BUILT -> messages.send(player, "error.wall-not-built");
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleShop(Player player, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("buy")) {
            messages.send(player, "error.invalid-args");
            return;
        }
        String typeKey = args[2].toLowerCase(Locale.ROOT);
        BuildingType type = BuildingType.fromKey(typeKey).orElse(null);
        if (type == null) {
            messages.send(player, "error.invalid-args");
            return;
        }
        shopService.buyBuildingItem(player, type)
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> {
                        BuildingDefinition def = buildingsConfig.get(type);
                        messages.send(player, "info.shop-bought", Map.of("name", def != null ? def.displayName() : type.key()));
                    }
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    case OWNER_ONLY -> messages.send(player, "error.owner-only");
                    case LEVEL_TOO_LOW -> messages.send(player, "error.build-level-too-low");
                    case NO_SHOP_BUILDING -> messages.send(player, "error.no-shop-building");
                    case ECONOMY_UNAVAILABLE -> messages.send(player, "error.economy-unavailable");
                    case INSUFFICIENT_FUNDS -> messages.send(player, "error.insufficient-funds");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleWar(Player player, String[] args) {
        if (args.length >= 2 && (args[1].equalsIgnoreCase("toggle") || args[1].equalsIgnoreCase("전쟁시간"))) {
            if (!player.hasPermission("nations.admin.*")) {
                messages.send(player, "error.no-permission");
                return;
            }
            boolean next = !warService.isMatchOpen();
            warService.setMatchOpen(next);
            if (!next) {
                warService.clearMatching();
            }
            messages.send(player, next ? "info.war-open" : "info.war-closed");
            return;
        }
        warService.enqueue(player.getUniqueId())
            .whenComplete((result, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || result == null) {
                    messages.send(player, "error.unknown");
                    return;
                }
                switch (result.status()) {
                    case SUCCESS -> messages.send(player, "info.war-queued");
                    case MATCH_CLOSED -> messages.send(player, "error.war-not-open");
                    case NOT_IN_NATION -> messages.send(player, "error.not-in-nation");
                    case OWNER_ONLY -> messages.send(player, "error.owner-only");
                    case ALREADY_QUEUED -> messages.send(player, "error.war-already-queued");
                    default -> messages.send(player, "error.unknown");
                }
            }));
    }

    private void handleGiveItem(Player player, String[] args) {
        if (!player.hasPermission("nations.admin.*")) {
            messages.send(player, "error.no-permission");
            return;
        }
        if (args.length < 3) {
            messages.send(player, "error.invalid-args");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "error.target-not-found");
            return;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        switch (type) {
            case "proclamation" -> target.getInventory().addItem(customItems.createProclamationItem());
            case "core" -> target.getInventory().addItem(customItems.createCompletedCoreItem());
            default -> {
                java.util.Optional<BuildingType> buildingType = BuildingType.fromKey(type);
                if (buildingType.isEmpty()) {
                    messages.send(player, "error.invalid-args");
                    return;
                }
                BuildingDefinition definition = buildingsConfig.get(buildingType.get());
                if (definition == null) {
                    messages.send(player, "error.invalid-args");
                    return;
                }
                target.getInventory().addItem(customItems.createBuildingItem(buildingType.get(), definition.displayName()));
            }
        }
        messages.send(player, "info.item-given", Map.of("player", target.getName()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of(
                "create", "info", "bank", "invite", "accept", "decline", "leave", "move", "chat",
                "levelup", "quest", "shop", "war", "storage", "present", "giveitem"
            );
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String sub : subs) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("giveitem")) {
            List<String> types = new ArrayList<>();
            types.add("proclamation");
            types.add("core");
            for (BuildingType type : BuildingType.values()) {
                types.add(type.key());
            }
            return types;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("quest")) {
            return List.of("deliver");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("shop")) {
            return List.of("buy");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bank")) {
            return List.of("deposit", "history");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("war")) {
            return List.of("toggle");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("shop") && args[1].equalsIgnoreCase("buy")) {
            List<String> types = new ArrayList<>();
            for (BuildingType type : BuildingType.values()) {
                types.add(type.key());
            }
            return types;
        }
        return List.of();
    }

    private int countItem(Player player, org.bukkit.Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItem(Player player, org.bukkit.Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) {
                continue;
            }
            int take = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) {
                contents[i] = null;
            }
            if (remaining <= 0) {
                break;
            }
        }
        player.getInventory().setContents(contents);
    }
}
