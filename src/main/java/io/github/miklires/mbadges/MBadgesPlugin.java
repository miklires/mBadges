package io.github.miklires.mbadges;

import io.github.miklires.mbadges.config.ConfigFiles;
import io.github.miklires.mbadges.api.MBadgesApi;
import io.github.miklires.mbadges.badge.BadgeRegistry;
import io.github.miklires.mbadges.badge.BadgeRenderer;
import io.github.miklires.mbadges.cache.BadgeCache;
import io.github.miklires.mbadges.database.DatabaseStorage;
import io.github.miklires.mbadges.listener.PlayerDataListener;
import io.github.miklires.mbadges.service.BadgeService;
import io.github.miklires.mbadges.util.PluginScheduler;
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
    private volatile boolean stopping;

    @Override
    public void onEnable() {
        try {
            configFiles = new ConfigFiles(this);
            configFiles.load();
            scheduler = new PluginScheduler(this);
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
        getServer().getServicesManager().unregisterAll(this);
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
        return badgeService != null && !stopping;
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
            if (stopping) {
                service.close();
                storage.close();
                return;
            }
            badgeService = service;
            scheduler.global(() -> finishInitialization(service));
        } catch (RuntimeException exception) {
            getLogger().severe("mBadges could not initialize: " + exception.getMessage());
            scheduler.global(() -> getServer().getPluginManager().disablePlugin(this));
        }
    }

    private void finishInitialization(BadgeService service) {
        if (stopping || !isEnabled()) return;
        getServer().getServicesManager().register(MBadgesApi.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(service), this);
        getServer().getOnlinePlayers().forEach(player -> service.loadPlayer(player.getUniqueId(), player.getName()));
        long interval = Math.max(5, getConfig().getLong("expiration.check-interval-seconds", 60));
        scheduler.asyncTimer(service::expireDueBadges, interval, interval, TimeUnit.SECONDS);
        getLogger().info("mBadges is ready with " + registry.all().size() + " badges");
    }
}
