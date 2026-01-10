package kr.lunaf.nationSystem.listener;

import kr.lunaf.nationSystem.domain.DailyQuestType;
import kr.lunaf.nationSystem.service.NationService;
import kr.lunaf.nationSystem.service.QuestService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;


public class QuestProgressListener implements Listener {
    private final NationService nationService;
    private final QuestService questService;

    public QuestProgressListener(NationService nationService, QuestService questService) {
        this.nationService = nationService;
        this.questService = questService;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        triggerProgress(event.getPlayer().getUniqueId(), type, 1);
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Material type = event.getItemType();
        triggerProgress(event.getPlayer().getUniqueId(), type, event.getItemAmount());
    }

    private void triggerProgress(java.util.UUID playerUuid, Material material, int amount) {
        DailyQuestType questType = null;
        if (material == Material.DIAMOND_ORE || material == Material.DEEPSLATE_DIAMOND_ORE) {
            questType = DailyQuestType.MINE_DIAMOND;
        } else if (material == Material.EMERALD_ORE || material == Material.DEEPSLATE_EMERALD_ORE) {
            questType = DailyQuestType.MINE_EMERALD;
        } else if (material == Material.IRON_INGOT) {
            questType = DailyQuestType.SMELT_IRON;
        } else if (material == Material.GOLD_INGOT) {
            questType = DailyQuestType.SMELT_GOLD;
        }
        if (questType == null) {
            return;
        }
        DailyQuestType finalQuestType = questType;
        kr.lunaf.nationSystem.domain.NationMembership membership = nationService.getCachedMembership(playerUuid);
        if (membership != null) {
            questService.addProgress(membership.nationId(), finalQuestType, amount);
            return;
        }
        nationService.getMembership(playerUuid).thenAccept(result -> {
            if (!result.isSuccess()) {
                return;
            }
            questService.addProgress(result.data().nationId(), finalQuestType, amount);
        });
    }
}
