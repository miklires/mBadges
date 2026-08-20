package io.github.miklires.mbadges.listener;

import io.github.miklires.mbadges.service.BadgeService;
import io.github.miklires.mbadges.display.DisplayService;
import io.github.miklires.mbadges.resourcepack.ResourcePackService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataListener implements Listener {
    private final BadgeService service;
    private final DisplayService display;
    private final ResourcePackService resourcePack;

    public PlayerDataListener(BadgeService service, DisplayService display, ResourcePackService resourcePack) {
        this.service = service;
        this.display = display;
        this.resourcePack = resourcePack;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.loadPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName())
                .thenRun(() -> display.update(event.getPlayer()));
        resourcePack.send(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        display.remove(event.getPlayer());
        service.unloadPlayer(event.getPlayer().getUniqueId());
    }
}
