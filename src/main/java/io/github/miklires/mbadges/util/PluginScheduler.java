package io.github.miklires.mbadges.util;

import io.github.miklires.mbadges.MBadgesPlugin;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public final class PluginScheduler {
    private final MBadgesPlugin plugin;

    public PluginScheduler(MBadgesPlugin plugin) {
        this.plugin = plugin;
    }

    public void global(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    public void player(Player player, Runnable task) {
        player.getScheduler().execute(plugin, task, null, 1L);
    }

    public void async(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    public void asyncLater(Runnable task, long delay, TimeUnit unit) {
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(), delay, unit);
    }

    public void asyncTimer(Runnable task, long delay, long period, TimeUnit unit) {
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(), delay, period, unit);
    }
}
