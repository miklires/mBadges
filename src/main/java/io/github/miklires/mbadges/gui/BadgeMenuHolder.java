package io.github.miklires.mbadges.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class BadgeMenuHolder implements InventoryHolder {
    private final UUID playerId;
    private final int page;
    private final String category;
    private Inventory inventory;

    public BadgeMenuHolder(UUID playerId, int page, String category) {
        this.playerId = playerId;
        this.page = page;
        this.category = category;
    }

    public UUID playerId() { return playerId; }
    public int page() { return page; }
    public String category() { return category; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
