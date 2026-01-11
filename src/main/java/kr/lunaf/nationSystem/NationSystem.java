package kr.lunaf.nationSystem;

import kr.lunaf.nationSystem.command.NationCommand;
import kr.lunaf.nationSystem.config.BuildingsConfig;
import kr.lunaf.nationSystem.config.Messages;
import kr.lunaf.nationSystem.config.PluginConfig;
import kr.lunaf.nationSystem.config.QuestsConfig;
import kr.lunaf.nationSystem.config.WarConfig;
import kr.lunaf.nationSystem.db.DatabaseManager;
import kr.lunaf.nationSystem.db.SchemaManager;
import kr.lunaf.nationSystem.listener.BuildingPlaceListener;
import kr.lunaf.nationSystem.listener.ChatListener;
import kr.lunaf.nationSystem.listener.ProclamationListener;
import kr.lunaf.nationSystem.listener.QuestProgressListener;
import kr.lunaf.nationSystem.listener.StorageListener;
import kr.lunaf.nationSystem.repository.BankHistoryRepository;
import kr.lunaf.nationSystem.repository.BuildingRepository;
import kr.lunaf.nationSystem.repository.DailyQuestRepository;
import kr.lunaf.nationSystem.repository.NationMemberRepository;
import kr.lunaf.nationSystem.repository.NationRepository;
import kr.lunaf.nationSystem.repository.NationSettingsRepository;
import kr.lunaf.nationSystem.repository.NationStorageRepository;
import kr.lunaf.nationSystem.repository.PlayerSettingsRepository;
import kr.lunaf.nationSystem.repository.PresentClaimRepository;
import kr.lunaf.nationSystem.repository.TerritoryRepository;
import kr.lunaf.nationSystem.service.BankService;
import kr.lunaf.nationSystem.service.BuildingService;
import kr.lunaf.nationSystem.service.EconomyService;
import kr.lunaf.nationSystem.service.InvitationService;
import kr.lunaf.nationSystem.service.NamePromptService;
import kr.lunaf.nationSystem.service.NationService;
import kr.lunaf.nationSystem.service.NationLevelService;
import kr.lunaf.nationSystem.service.PresentService;
import kr.lunaf.nationSystem.service.QuestService;
import kr.lunaf.nationSystem.service.ShopService;
import kr.lunaf.nationSystem.service.StorageService;
import kr.lunaf.nationSystem.service.StructureService;
import kr.lunaf.nationSystem.service.TerritoryService;
import kr.lunaf.nationSystem.service.WarService;
import kr.lunaf.nationSystem.api.NationSystemApiImpl;
import kr.lunaf.nationSystem.skript.SkriptBridge;
import kr.lunaf.nationSystem.skript.SkriptHook;
import kr.lunaf.nationSystem.util.CustomItems;
import kr.lunaf.nationSystem.util.SyncExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NationSystem extends JavaPlugin {
    private PluginConfig pluginConfig;
    private Messages messages;
    private DatabaseManager databaseManager;
    private ExecutorService dbExecutor;
    private NationService nationService;
    private EconomyService economyService;
    private TerritoryService territoryService;
    private BuildingService buildingService;
    private QuestService questService;
    private ShopService shopService;
    private WarService warService;
    private BankService bankService;
    private NationLevelService nationLevelService;
    private StorageService storageService;
    private PresentService presentService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);
        messages = new Messages(this);
        BuildingsConfig buildingsConfig = new BuildingsConfig(this);
        QuestsConfig questsConfig = new QuestsConfig(this);
        WarConfig warConfig = new WarConfig(this);

        databaseManager = new DatabaseManager(pluginConfig.databaseConfig());
        new SchemaManager(databaseManager).initialize();
        dbExecutor = Executors.newFixedThreadPool(Math.max(2, pluginConfig.databaseConfig().poolSize()));

        NationRepository nationRepository = new NationRepository(databaseManager);
        NationMemberRepository memberRepository = new NationMemberRepository(databaseManager);
        NationSettingsRepository settingsRepository = new NationSettingsRepository(databaseManager);
        PlayerSettingsRepository playerSettingsRepository = new PlayerSettingsRepository(databaseManager);
        TerritoryRepository territoryRepository = new TerritoryRepository(databaseManager);
        BuildingRepository buildingRepository = new BuildingRepository(databaseManager);
        DailyQuestRepository dailyQuestRepository = new DailyQuestRepository(databaseManager);
        BankHistoryRepository bankHistoryRepository = new BankHistoryRepository(databaseManager);
        NationStorageRepository nationStorageRepository = new NationStorageRepository(databaseManager);
        PresentClaimRepository presentClaimRepository = new PresentClaimRepository(databaseManager);

        InvitationService invitationService = new InvitationService(pluginConfig);
        NamePromptService namePromptService = new NamePromptService();
        economyService = new EconomyService(this);
        StructureService structureService = new StructureService(pluginConfig, getDataFolder());
        nationService = new NationService(
            pluginConfig,
            databaseManager,
            nationRepository,
            memberRepository,
            settingsRepository,
            playerSettingsRepository,
            invitationService,
            economyService,
            dbExecutor,
            new SyncExecutor(this)
        );
        territoryService = new TerritoryService(
            pluginConfig,
            databaseManager,
            nationRepository,
            memberRepository,
            settingsRepository,
            territoryRepository,
            structureService,
            nationService,
            dbExecutor,
            new SyncExecutor(this)
        );
        buildingService = new BuildingService(
            pluginConfig,
            buildingsConfig,
            databaseManager,
            buildingRepository,
            nationRepository,
            territoryRepository,
            memberRepository,
            structureService,
            dbExecutor,
            new SyncExecutor(this)
        );
        CustomItems customItems = new CustomItems(this);
        questService = new QuestService(
            questsConfig,
            databaseManager,
            dailyQuestRepository,
            memberRepository,
            nationRepository,
            territoryRepository,
            nationService,
            dbExecutor
        );
        shopService = new ShopService(
            buildingsConfig,
            memberRepository,
            nationRepository,
            buildingService,
            economyService,
            customItems,
            dbExecutor,
            new SyncExecutor(this)
        );
        warService = new WarService(
            warConfig,
            nationRepository,
            memberRepository,
            nationService,
            dbExecutor
        );
        bankService = new BankService(
            databaseManager,
            nationRepository,
            memberRepository,
            bankHistoryRepository,
            buildingService,
            economyService,
            dbExecutor,
            new SyncExecutor(this)
        );
        nationLevelService = new NationLevelService(
            pluginConfig,
            databaseManager,
            nationRepository,
            memberRepository,
            bankHistoryRepository,
            nationService,
            dbExecutor
        );
        storageService = new StorageService(
            pluginConfig,
            databaseManager,
            nationService,
            buildingService,
            nationStorageRepository,
            dbExecutor,
            new SyncExecutor(this)
        );
        presentService = new PresentService(
            pluginConfig,
            databaseManager,
            memberRepository,
            nationRepository,
            presentClaimRepository,
            buildingService,
            economyService,
            dbExecutor,
            new SyncExecutor(this)
        );

        PluginCommand nationCommand = getCommand("nation");
        if (nationCommand != null) {
            NationCommand executor = new NationCommand(
                this,
                messages,
                nationService,
                pluginConfig,
                customItems,
                buildingsConfig,
                questService,
                shopService,
                warService,
                bankService,
                nationLevelService,
                territoryService,
                storageService,
                presentService
            );
            nationCommand.setExecutor(executor);
            nationCommand.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(
            new ChatListener(messages, pluginConfig, namePromptService, nationService),
            this
        );
        getServer().getPluginManager().registerEvents(
            new ProclamationListener(this, messages, pluginConfig, territoryService, namePromptService, customItems, buildingsConfig, nationService),
            this
        );
        getServer().getPluginManager().registerEvents(
            new BuildingPlaceListener(this, messages, buildingService, customItems, buildingsConfig),
            this
        );
        getServer().getPluginManager().registerEvents(
            new QuestProgressListener(nationService, questService),
            this
        );
        getServer().getPluginManager().registerEvents(
            new StorageListener(storageService),
            this
        );

        NationSystemApiImpl api = new NationSystemApiImpl(nationService, warService);
        getServer().getServicesManager().register(kr.lunaf.nationSystem.api.NationSystemApi.class, api, this, org.bukkit.plugin.ServicePriority.Normal);
        SkriptBridge.setApi(api);
        new SkriptHook(this).register();

        getServer().getScheduler().runTaskTimerAsynchronously(
            this,
            () -> territoryService.expirePendingTerritories(),
            20L * 60,
            20L * 60
        );
        getServer().getScheduler().runTaskTimerAsynchronously(
            this,
            () -> buildingService.processBuildingCompletion(),
            20L,
            20L
        );
        getServer().getScheduler().runTaskTimerAsynchronously(
            this,
            () -> {
                warService.tickMatching();
                warService.tickWars();
            },
            20L,
            20L
        );
    }

    @Override
    public void onDisable() {
        if (dbExecutor != null) {
            dbExecutor.shutdownNow();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }
}
