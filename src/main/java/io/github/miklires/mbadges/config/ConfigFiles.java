package io.github.miklires.mbadges.config;

import io.github.miklires.mbadges.MBadgesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ConfigFiles {
    private static final int CONFIG_VERSION = 1;
    private static final String[] FILES = {
            "database.yml", "badges.yml", "resourcepack.yml", "gui.yml",
            "lang/en_US.yml", "lang/ru_RU.yml"
    };

    private final MBadgesPlugin plugin;
    private final Map<String, YamlConfiguration> configurations = new LinkedHashMap<>();

    public ConfigFiles(MBadgesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        mergeMainDefaults();
        for (String name : FILES) loadFile(name);
        validate();
    }

    public void reloadSafe() {
        plugin.reloadConfig();
        mergeMainDefaults();
        for (String name : FILES) loadFile(name);
        validate();
    }

    public YamlConfiguration file(String name) {
        return Objects.requireNonNull(configurations.get(name), "configuration not loaded: " + name);
    }

    private void mergeMainDefaults() {
        int version = plugin.getConfig().getInt("config-version", 0);
        plugin.getConfig().options().copyDefaults(true);
        if (version <= CONFIG_VERSION) {
            plugin.getConfig().set("config-version", CONFIG_VERSION);
            plugin.saveConfig();
        } else {
            plugin.getLogger().warning("config.yml is from a newer mBadges version");
        }
    }

    private void loadFile(String name) {
        File target = new File(plugin.getDataFolder(), name.replace('/', File.separatorChar));
        if (!target.isFile()) plugin.saveResource(name, false);
        YamlConfiguration current = YamlConfiguration.loadConfiguration(target);
        try (var stream = plugin.getResource(name)) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                current.setDefaults(defaults);
                current.options().copyDefaults(true);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("could not read defaults for " + name, exception);
        }
        int version = current.getInt("config-version", CONFIG_VERSION);
        if (version > CONFIG_VERSION) {
            plugin.getLogger().warning(name + " is from a newer mBadges version");
        } else if (current.contains("config-version")) {
            current.set("config-version", CONFIG_VERSION);
        }
        try {
            current.save(target);
        } catch (IOException exception) {
            throw new IllegalStateException("could not save " + name, exception);
        }
        configurations.put(name, current);
    }

    private void validate() {
        bounded("equipment.default-slots", 1, 1, hardLimit());
        bounded("equipment.max-badges-hard-limit", 10, 1, 100);
        bounded("expiration.check-interval-seconds", 60, 5, 86_400);
        bounded("sync.polling-interval-seconds", 10, 2, 3_600);
        bounded("rest-api.port", 8766, 1, 65_535);
        bounded("rest-api.requests-per-minute", 60, 1, 100_000);
        String type = file("database.yml").getString("type", "sqlite").toLowerCase();
        if (!type.equals("sqlite") && !type.equals("mysql") && !type.equals("postgresql")) {
            plugin.getLogger().warning("invalid database.yml path type: " + type + "; using sqlite");
            file("database.yml").set("type", "sqlite");
        }
    }

    public int hardLimit() {
        return Math.max(1, Math.min(100, plugin.getConfig().getInt("equipment.max-badges-hard-limit", 10)));
    }

    private int bounded(String path, int fallback, int min, int max) {
        int value = plugin.getConfig().getInt(path, fallback);
        if (value < min || value > max) {
            plugin.getLogger().warning("invalid config.yml path " + path + ": " + value + "; using " + fallback);
            return fallback;
        }
        return value;
    }
}
