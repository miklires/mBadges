package io.github.miklires.mbadges;

import io.github.miklires.mbadges.config.ConfigFiles;
import io.github.miklires.mbadges.api.MBadgesApi;
import io.github.miklires.mbadges.badge.BadgeRegistry;
import io.github.miklires.mbadges.badge.BadgeRenderer;
import io.github.miklires.mbadges.cache.BadgeCache;
import io.github.miklires.mbadges.command.AdminBadgeCommand;
import io.github.miklires.mbadges.command.BadgeCommand;
import io.github.miklires.mbadges.command.ReadyCommand;
import io.github.miklires.mbadges.database.DatabaseStorage;
import io.github.miklires.mbadges.display.DisplayService;
import io.github.miklires.mbadges.gui.BadgeGui;
import io.github.miklires.mbadges.integration.placeholder.MBadgesExpansion;
import io.github.miklires.mbadges.listener.PlayerDataListener;
import io.github.miklires.mbadges.message.MessageService;
import io.github.miklires.mbadges.resourcepack.ResourcePackService;
import io.github.miklires.mbadges.rest.RestApiService;
import io.github.miklires.mbadges.service.BadgeService;
import io.github.miklires.mbadges.sync.SyncService;
import io.github.miklires.mbadges.util.PluginScheduler;
import io.github.miklires.mbadges.update.UpdateChecker;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class MBadgesPlugin extends JavaPlugin {
    private ConfigFiles configFiles;
    private PluginScheduler scheduler;
    private BadgeRegistry registry;
    private DatabaseStorage storage;
    private BadgeService badgeService;
    private MessageService messages;
    private ResourcePackService resourcePack;
    private RestApiService restApi;
    private BasicCommand playerCommand;
    private BasicCommand adminCommand;
    private volatile boolean ready;
    private volatile boolean stopping;

    @Override
    public void onEnable() {
        try {
            configFiles = new ConfigFiles(this);
            configFiles.load();
            scheduler = new PluginScheduler(this);
            messages = new MessageService(this);
            registerCommands();
            startMetrics();
            scheduler.async(this::initializeCore);
            getLogger().info("mBadges " + getPluginMeta().getVersion() + " is starting");
        } catch (RuntimeException exception) {
            getLogger().severe("mBadges could not start: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        stopping = true;
        ready = false;
        getServer().getServicesManager().unregisterAll(this);
        if (restApi != null) restApi.close();
        if (resourcePack != null) resourcePack.close();
        if (badgeService != null) badgeService.close();
        if (storage != null) storage.close();
        getLogger().info("mBadges disabled");
    }

    public ConfigFiles configFiles() {
        return configFiles;
    }

    public PluginScheduler scheduler() {
        return scheduler;
    }

    public BadgeService badgeService() {
        return badgeService;
    }

    public BadgeRegistry registry() {
        return registry;
    }

    public boolean ready() {
        return ready && !stopping;
    }

    private void startMetrics() {
        if (!getConfig().getBoolean("metrics.enabled", true)) return;
        int id = Math.max(0, getConfig().getInt("metrics.bstats-id", 33353));
        if (id > 0) new Metrics(this, id);
    }

    private void initializeCore() {
        try {
            registry = new BadgeRegistry(this);
            registry.reload();
            storage = new DatabaseStorage(this);
            storage.start();
            storage.syncDefinitions(registry.all());
            BadgeCache cache = new BadgeCache();
            BadgeRenderer renderer = new BadgeRenderer(this, registry, cache);
            BadgeService service = new BadgeService(this, registry, storage, cache, renderer);
            ResourcePackService packService = new ResourcePackService(this, registry, messages);
            if (stopping) {
                service.close();
                packService.close();
                storage.close();
                return;
            }
            badgeService = service;
            resourcePack = packService;
            scheduler.global(() -> finishInitialization(service, packService));
        } catch (RuntimeException exception) {
            getLogger().severe("mBadges could not initialize: " + exception.getMessage());
            scheduler.global(() -> getServer().getPluginManager().disablePlugin(this));
        }
    }

    private void finishInitialization(BadgeService service, ResourcePackService packService) {
        if (stopping || !isEnabled()) return;
        DisplayService display = new DisplayService(this, service, messages);
        BadgeGui gui = new BadgeGui(this, service, messages);
        service.setDisplayUpdater(() -> scheduler.global(display::updateAll));
        playerCommand = new BadgeCommand(this, service, gui, messages);
        adminCommand = new AdminBadgeCommand(this, service, packService, messages);
        getServer().getServicesManager().register(MBadgesApi.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(service, display, packService), this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MBadgesExpansion(this, service).register();
            getLogger().info("PlaceholderAPI integration enabled");
        }
        restApi = new RestApiService(this, service);
        scheduler.async(restApi::start);
        new SyncService(this, service).start();
        new UpdateChecker(this).start();
        ready = true;
        getServer().getOnlinePlayers().forEach(player -> service.loadPlayer(player.getUniqueId(), player.getName())
                .thenRun(() -> display.update(player)));
        if (packService.mode() == io.github.miklires.mbadges.resourcepack.ResourcePackMode.INTERNAL
                && configFiles.file("resourcepack.yml").getBoolean("auto-build", true)) {
            packService.build(false).exceptionally(error -> {
                getLogger().warning("automatic resource pack generation failed: " + error.getMessage());
                return null;
            });
        }
        long interval = Math.max(5, getConfig().getLong("expiration.check-interval-seconds", 60));
        scheduler.asyncTimer(() -> service.expireDueBadges().thenRun(() -> scheduler.global(() ->
                getServer().getOnlinePlayers().forEach(service::enforceAccess))), interval, interval, TimeUnit.SECONDS);
        getLogger().info("mBadges is ready with " + registry.all().size() + " badges");
    }

    private void registerCommands() {
        ReadyCommand playerGateway = new ReadyCommand(this, () -> playerCommand);
        ReadyCommand adminGateway = new ReadyCommand(this, () -> adminCommand);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("badge", playerGateway);
            event.registrar().register("mbadge", adminGateway);
        });
    }
}
