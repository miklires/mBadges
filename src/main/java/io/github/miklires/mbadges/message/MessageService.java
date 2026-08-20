package io.github.miklires.mbadges.message;

import io.github.miklires.mbadges.MBadgesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MessageService {
    private final MBadgesPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MessageService(MBadgesPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(message(sender, key, values));
    }

    public Component message(CommandSender sender, String key, Map<String, String> values) {
        String language = language(sender);
        var config = plugin.configFiles().file("lang/" + language + ".yml");
        String fallback = plugin.configFiles().file("lang/en_US.yml").getString(key, key);
        String value = config.getString(key, fallback);
        String prefix = config.getString("prefix", "");
        TagResolver[] resolvers = values.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        return miniMessage.deserialize(prefix + value, resolvers);
    }

    public Component parse(String value, Map<String, String> placeholders) {
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        return miniMessage.deserialize(value == null ? "" : value, resolvers);
    }

    private String language(CommandSender sender) {
        String configured = plugin.getConfig().getString("language.default", "en_US");
        if (!plugin.getConfig().getBoolean("language.per-player", false) || !(sender instanceof Player player)) {
            return configured.equalsIgnoreCase("ru_RU") ? "ru_RU" : "en_US";
        }
        return player.locale().getLanguage().equalsIgnoreCase("ru") ? "ru_RU" : "en_US";
    }
}
