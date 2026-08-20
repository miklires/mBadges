package io.github.miklires.mbadges.sync;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.service.BadgeService;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncService {
    private final MBadgesPlugin plugin;
    private final BadgeService service;
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile long cursor = System.currentTimeMillis();

    public SyncService(MBadgesPlugin plugin, BadgeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("sync.enabled", false)) return;
        long interval = Math.max(2, plugin.getConfig().getLong("sync.polling-interval-seconds", 10));
        plugin.scheduler().asyncTimer(this::poll, interval, interval, TimeUnit.SECONDS);
        plugin.getLogger().info("multi-server polling enabled with a " + interval + " second interval");
    }

    private void poll() {
        if (!polling.compareAndSet(false, true)) return;
        long since = cursor;
        service.pollChanges(since).whenComplete((next, error) -> {
            if (error == null) cursor = next;
            else plugin.getLogger().warning("badge sync polling failed: " + rootMessage(error));
            polling.set(false);
        });
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
