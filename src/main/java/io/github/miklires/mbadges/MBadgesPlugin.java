package io.github.miklires.mbadges;

import io.github.miklires.mbadges.config.ConfigFiles;
import io.github.miklires.mbadges.util.PluginScheduler;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class MBadgesPlugin extends JavaPlugin {
    private ConfigFiles configFiles;
    private PluginScheduler scheduler;

    @Override
    public void onEnable() {
        try {
            configFiles = new ConfigFiles(this);
            configFiles.load();
            scheduler = new PluginScheduler(this);
            startMetrics();
            getLogger().info("mBadges " + getPluginMeta().getVersion() + " enabled");
        } catch (RuntimeException exception) {
            getLogger().severe("mBadges could not start: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("mBadges disabled");
    }

    public ConfigFiles configFiles() {
        return configFiles;
    }

    public PluginScheduler scheduler() {
        return scheduler;
    }

    private void startMetrics() {
        if (!getConfig().getBoolean("metrics.enabled", true)) return;
        int id = Math.max(0, getConfig().getInt("metrics.bstats-id", 33353));
        if (id > 0) new Metrics(this, id);
    }
}
