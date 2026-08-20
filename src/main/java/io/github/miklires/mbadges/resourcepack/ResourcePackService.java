package io.github.miklires.mbadges.resourcepack;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.api.model.Badge;
import io.github.miklires.mbadges.badge.BadgeRegistry;
import io.github.miklires.mbadges.message.MessageService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourcePackService implements AutoCloseable {
    private final MBadgesPlugin plugin;
    private final BadgeRegistry registry;
    private final MessageService messages;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("mBadges-pack", 0).factory());
    private volatile ResourcePackBuild latest;

    public ResourcePackService(MBadgesPlugin plugin, BadgeRegistry registry, MessageService messages) {
        this.plugin = plugin;
        this.registry = registry;
        this.messages = messages;
    }

    public CompletableFuture<ResourcePackBuild> build(boolean force) {
        return CompletableFuture.supplyAsync(() -> buildNow(force), executor);
    }

    public void send(Player player) {
        YamlConfiguration config = plugin.configFiles().file("resourcepack.yml");
        if (!config.getBoolean("delivery.send-on-join", false)) return;
        String url = config.getString("delivery.url", "").trim();
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            plugin.getLogger().warning("resourcepack delivery.url must use http or https");
            return;
        }
        String configuredHash = config.getString("delivery.sha1", "").trim();
        String hash = configuredHash.isEmpty() && latest != null ? latest.sha1() : configuredHash;
        byte[] bytes;
        try {
            bytes = hash.matches("[0-9a-fA-F]{40}") ? HexFormat.of().parseHex(hash) : null;
        } catch (IllegalArgumentException ignored) {
            bytes = null;
        }
        boolean required = config.getBoolean("delivery.required", false);
        var prompt = messages.parse(config.getString("delivery.prompt", ""), Map.of());
        player.setResourcePack(url, bytes, prompt, required);
    }

    public ResourcePackMode mode() {
        return ResourcePackMode.parse(plugin.configFiles().file("resourcepack.yml").getString("mode", "INTERNAL"));
    }

    private ResourcePackBuild buildNow(boolean force) {
        ResourcePackMode mode = mode();
        if (mode != ResourcePackMode.INTERNAL) {
            validateExternalMode(mode);
            return new ResourcePackBuild(Path.of(""), "", mode.name(), 0, false);
        }
        YamlConfiguration config = plugin.configFiles().file("resourcepack.yml");
        Path source = plugin.getDataFolder().toPath().resolve(config.getString("source-directory", "badges")).normalize();
        Path output = plugin.getDataFolder().toPath().resolve(config.getString("output-file", "generated/mbadges-resource-pack.zip")).normalize();
        Path metadataPath = plugin.getDataFolder().toPath().resolve(config.getString("metadata-file", "generated/resource-pack.yml")).normalize();
        ensureInsideData(source);
        ensureInsideData(output);
        ensureInsideData(metadataPath);
        try {
            Files.createDirectories(source);
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            if (metadataPath.getParent() != null) Files.createDirectories(metadataPath.getParent());
            List<PackBadge> valid = validateTextures(source);
            String fingerprint = fingerprint(valid, config);
            YamlConfiguration metadata = YamlConfiguration.loadConfiguration(metadataPath.toFile());
            if (!force && fingerprint.equals(metadata.getString("fingerprint")) && Files.isRegularFile(output)) {
                ResourcePackBuild result = new ResourcePackBuild(output, metadata.getString("sha1", ""),
                        fingerprint, valid.size(), false);
                latest = result;
                return result;
            }
            writeZip(output, valid, config);
            String sha1 = digest(output, "SHA-1");
            metadata.set("config-version", 1);
            metadata.set("file", output.toString());
            metadata.set("sha1", sha1);
            metadata.set("fingerprint", fingerprint);
            metadata.set("badge-count", valid.size());
            metadata.set("size", Files.size(output));
            metadata.set("generated-at", Instant.now().toString());
            metadata.save(metadataPath.toFile());
            ResourcePackBuild result = new ResourcePackBuild(output, sha1, fingerprint, valid.size(), true);
            latest = result;
            plugin.getLogger().info("generated resource pack with " + valid.size() + " badges");
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("could not generate resource pack: " + exception.getMessage(), exception);
        }
    }

    private List<PackBadge> validateTextures(Path source) {
        List<PackBadge> result = new ArrayList<>();
        registry.all().stream().filter(Badge::enabled).sorted(Comparator.comparing(Badge::id)).forEach(badge -> {
            Path file = source.resolve(badge.texture()).normalize();
            if (!file.getParent().equals(source) || !Files.isRegularFile(file)) {
                plugin.getLogger().warning("badge " + badge.id() + " has no PNG file: " + badge.texture());
                return;
            }
            try (var input = new BufferedInputStream(Files.newInputStream(file))) {
                BufferedImage image = ImageIO.read(input);
                if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                        || image.getWidth() > 256 || image.getHeight() > 256) {
                    plugin.getLogger().warning("badge " + badge.id() + " has an invalid PNG size");
                    return;
                }
                result.add(new PackBadge(badge, file));
            } catch (IOException exception) {
                plugin.getLogger().warning("could not read PNG for badge " + badge.id() + ": " + exception.getMessage());
            }
        });
        return result;
    }

    private void writeZip(Path output, List<PackBadge> badges, YamlConfiguration config) throws IOException {
        String namespace = safeNamespace(config.getString("namespace", "mbadges"));
        String fontFile = config.getString("font.file", "default.json").replaceAll("[^A-Za-z0-9._/-]", "");
        if (fontFile.isBlank() || fontFile.contains("..")) fontFile = "default.json";
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(output)))) {
            addText(zip, "pack.mcmeta", "{\"pack\":{\"description\":\"mBadges generated glyphs\",\"min_format\":[88,0],\"max_format\":[88,0]}}");
            addText(zip, "assets/minecraft/font/" + fontFile, fontJson(badges, namespace, config));
            for (PackBadge badge : badges) {
                addFile(zip, "assets/" + namespace + "/textures/font/" + badge.badge().texture(), badge.file());
            }
        }
    }

    private String fontJson(List<PackBadge> badges, String namespace, YamlConfiguration config) {
        int ascent = Math.max(-256, Math.min(256, config.getInt("font.ascent", 8)));
        int height = Math.max(1, Math.min(256, config.getInt("font.height", 8)));
        StringBuilder json = new StringBuilder("{\"providers\":[");
        for (int index = 0; index < badges.size(); index++) {
            if (index > 0) json.append(',');
            Badge badge = badges.get(index).badge();
            json.append("{\"type\":\"bitmap\",\"file\":\"")
                    .append(namespace).append(":font/").append(jsonEscape(badge.texture()))
                    .append("\",\"ascent\":").append(ascent)
                    .append(",\"height\":").append(height)
                    .append(",\"chars\":[\"").append(unicodeEscape(badge.glyph())).append("\"]}");
        }
        return json.append("]}").toString();
    }

    private String fingerprint(List<PackBadge> badges, YamlConfiguration config) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(config.getString("namespace", "mbadges").getBytes(StandardCharsets.UTF_8));
            digest.update(Integer.toString(config.getInt("font.ascent", 8)).getBytes(StandardCharsets.UTF_8));
            digest.update(Integer.toString(config.getInt("font.height", 8)).getBytes(StandardCharsets.UTF_8));
            for (PackBadge badge : badges) {
                digest.update(badge.badge().id().getBytes(StandardCharsets.UTF_8));
                digest.update(Integer.toString(badge.badge().glyph()).getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(badge.file()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String digest(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (var input = new BufferedInputStream(Files.newInputStream(file))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is unavailable", exception);
        }
    }

    private void addText(ZipOutputStream zip, String name, String value) throws IOException {
        addBytes(zip, name, value.getBytes(StandardCharsets.UTF_8));
    }

    private void addFile(ZipOutputStream zip, String name, Path file) throws IOException {
        addBytes(zip, name, Files.readAllBytes(file));
    }

    private void addBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name.replace('\\', '/'));
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private void validateExternalMode(ResourcePackMode mode) {
        if (mode == ResourcePackMode.ORAXEN && !plugin.getServer().getPluginManager().isPluginEnabled("Oraxen")) {
            plugin.getLogger().warning("resource pack mode ORAXEN is active but Oraxen is unavailable");
        }
        if (mode == ResourcePackMode.ITEMSADDER && !plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            plugin.getLogger().warning("resource pack mode ITEMSADDER is active but ItemsAdder is unavailable");
        }
    }

    private void ensureInsideData(Path path) {
        Path root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        if (!path.toAbsolutePath().normalize().startsWith(root)) {
            throw new IllegalArgumentException("resource pack path leaves the plugin directory");
        }
    }

    private String safeNamespace(String value) {
        String namespace = value == null ? "mbadges" : value.toLowerCase().replaceAll("[^a-z0-9_.-]", "");
        return namespace.isBlank() ? "mbadges" : namespace;
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unicodeEscape(int glyph) {
        if (glyph <= 0xFFFF) return String.format("\\u%04x", glyph);
        char[] chars = Character.toChars(glyph);
        return String.format("\\u%04x\\u%04x", (int) chars[0], (int) chars[1]);
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    private record PackBadge(Badge badge, Path file) { }
}
