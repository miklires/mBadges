package io.github.miklires.mbadges.display;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.message.MessageService;
import io.github.miklires.mbadges.service.BadgeService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;

public final class DisplayService {
    private final MBadgesPlugin plugin;
    private final BadgeService service;
    private final MessageService messages;

    public DisplayService(MBadgesPlugin plugin, BadgeService service, MessageService messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
    }

    public void updateAll() {
        plugin.getServer().getOnlinePlayers().forEach(this::update);
    }

    public void update(Player player) {
        plugin.scheduler().player(player, () -> {
            String rendered = service.render(player.getUniqueId());
            Component prefix = messages.parse(rendered, Map.of());
            if (plugin.getConfig().getBoolean("display.tab-list.enabled", true)) {
                player.playerListName(prefix.append(Component.text(player.getName())));
            } else {
                player.playerListName(Component.text(player.getName()));
            }
            updateNametag(player, prefix);
        });
    }

    public void remove(Player player) {
        if (!plugin.getConfig().getBoolean("display.nametag.enabled", false)) return;
        plugin.scheduler().global(() -> {
            Scoreboard scoreboard = plugin.getServer().getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(teamName(player.getUniqueId()));
            if (team != null) team.unregister();
        });
    }

    private void updateNametag(Player player, Component prefix) {
        if (!plugin.getConfig().getBoolean("display.nametag.enabled", false)) return;
        plugin.scheduler().global(() -> {
            Scoreboard scoreboard = plugin.getServer().getScoreboardManager().getMainScoreboard();
            String name = teamName(player.getUniqueId());
            Team team = scoreboard.getTeam(name);
            if (team == null) team = scoreboard.registerNewTeam(name);
            team.prefix(prefix);
            team.addEntry(player.getName());
        });
    }

    private String teamName(UUID playerId) {
        return "mb_" + playerId.toString().replace("-", "").substring(0, 12);
    }
}
