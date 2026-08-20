package io.github.miklires.mbadges.badge;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.api.model.Badge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class BadgeRegistry {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern TEXTURE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,120}\\.png");

    private final MBadgesPlugin plugin;
    private volatile Map<String, Badge> badges = Map.of();

    public BadgeRegistry(MBadgesPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() {
        YamlConfiguration definitions = plugin.configFiles().file("badges.yml");
        YamlConfiguration pack = plugin.configFiles().file("resourcepack.yml");
        File mappingFile = new File(plugin.getDataFolder(), pack.getString("mapping-file", "generated/glyphs.yml"));
        YamlConfiguration mapping = YamlConfiguration.loadConfiguration(mappingFile);
        int start = GlyphAllocator.parseHex(pack.getString("glyph-range.start"), 0xE000);
        int end = GlyphAllocator.parseHex(pack.getString("glyph-range.end"), 0xF8FF);
        Map<String, Integer> assigned = readMappings(mapping);
        Set<Integer> used = new HashSet<>();
        Map<String, Badge> loaded = new HashMap<>();
        ConfigurationSection section = definitions.getConfigurationSection("badges");
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                String id = rawId.toLowerCase(Locale.ROOT);
                if (!ID.matcher(id).matches()) {
                    plugin.getLogger().warning("ignored badge with invalid id: " + rawId);
                    continue;
                }
                ConfigurationSection badgeSection = section.getConfigurationSection(rawId);
                if (badgeSection == null) continue;
                String texture = badgeSection.getString("texture", id + ".png");
                if (!TEXTURE.matcher(texture).matches()) {
                    plugin.getLogger().warning("ignored badge " + id + " with invalid texture filename");
                    continue;
                }
                int glyph = assigned.getOrDefault(id, 0);
                if (glyph < start || glyph > end || used.contains(glyph)) {
                    glyph = GlyphAllocator.next(start, end, used);
                    assigned.put(id, glyph);
                }
                used.add(glyph);
                loaded.put(id, new Badge(
                        id,
                        badgeSection.getString("name", id),
                        badgeSection.getString("description", ""),
                        texture,
                        glyph,
                        badgeSection.getString("permission", "mbadge.badge." + id),
                        badgeSection.getString("category", "general"),
                        badgeSection.getInt("priority", 0),
                        badgeSection.getBoolean("hidden", false),
                        badgeSection.getBoolean("enabled", true),
                        badgeSection.getBoolean("temporary", false)
                ));
            }
        }
        ArrayList<Badge> ordered = new ArrayList<>(loaded.values());
        ordered.sort(Comparator.comparingInt(Badge::priority).reversed().thenComparing(Badge::id));
        LinkedHashMap<String, Badge> stableOrder = new LinkedHashMap<>();
        ordered.forEach(badge -> stableOrder.put(badge.id(), badge));
        badges = Map.copyOf(stableOrder);
        writeMappings(mappingFile, mapping, assigned);
        plugin.getLogger().info("loaded " + badges.size() + " badge definitions");
    }

    public Optional<Badge> get(String id) {
        return Optional.ofNullable(id == null ? null : badges.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<Badge> all() {
        return badges.values();
    }

    private Map<String, Integer> readMappings(YamlConfiguration mapping) {
        Map<String, Integer> result = new HashMap<>();
        ConfigurationSection section = mapping.getConfigurationSection("glyphs");
        if (section == null) return result;
        for (String id : section.getKeys(false)) {
            int glyph = section.getInt(id, 0);
            if (glyph > 0) result.put(id.toLowerCase(Locale.ROOT), glyph);
        }
        return result;
    }

    private void writeMappings(File file, YamlConfiguration mapping, Map<String, Integer> assigned) {
        mapping.set("config-version", 1);
        mapping.set("glyphs", null);
        assigned.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> mapping.set("glyphs." + entry.getKey(), entry.getValue()));
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("could not create glyph mapping directory");
        }
        try {
            mapping.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("could not save glyph mapping", exception);
        }
    }
}
