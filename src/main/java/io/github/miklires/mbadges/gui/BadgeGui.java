package io.github.miklires.mbadges.gui;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.api.model.Badge;
import io.github.miklires.mbadges.api.model.BadgeOperationReason;
import io.github.miklires.mbadges.api.model.BadgeResult;
import io.github.miklires.mbadges.api.model.BadgeState;
import io.github.miklires.mbadges.cache.PlayerBadgeSnapshot;
import io.github.miklires.mbadges.message.MessageService;
import io.github.miklires.mbadges.service.BadgeService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BadgeGui implements Listener {
    private final MBadgesPlugin plugin;
    private final BadgeService service;
    private final MessageService messages;
    private final NamespacedKey badgeKey;

    public BadgeGui(MBadgesPlugin plugin, BadgeService service, MessageService messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
        this.badgeKey = new NamespacedKey(plugin, "badge_id");
    }

    public void open(Player player) {
        open(player, 0, "all");
    }

    public void open(Player player, int requestedPage, String requestedCategory) {
        if (!plugin.ready()) {
            messages.send(player, "loading");
            return;
        }
        int size = normalizedSize(plugin.configFiles().file("gui.yml").getInt("size", 54));
        int pageSize = Math.min(size - 9, Math.max(1, plugin.configFiles().file("gui.yml").getInt("page-size", 45)));
        String category = categories().contains(requestedCategory) ? requestedCategory : "all";
        List<Badge> badges = visibleBadges(player, category);
        int maxPage = Math.max(0, (badges.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(maxPage, requestedPage));
        BadgeMenuHolder holder = new BadgeMenuHolder(player.getUniqueId(), page, category);
        Component title = messages.parse(plugin.configFiles().file("gui.yml").getString("title", "Badge collection"), Map.of());
        Inventory inventory = plugin.getServer().createInventory(holder, size, title);
        holder.inventory(inventory);
        PlayerBadgeSnapshot snapshot = service.cached(player.getUniqueId());
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < badges.size(); index++) {
            Badge badge = badges.get(start + index);
            inventory.setItem(index, badgeItem(player, badge, snapshot));
        }
        int previous = slot("previous-page-slot", size - 9, size);
        int status = slot("status-slot", size - 5, size);
        int next = slot("next-page-slot", size - 1, size);
        if (page > 0) inventory.setItem(previous, simple(player, Material.ARROW, "gui-previous-page"));
        if (page < maxPage) inventory.setItem(next, simple(player, Material.ARROW, "gui-next-page"));
        inventory.setItem(status, statusItem(player, snapshot, service.getSlotLimit(player.getUniqueId()), category));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BadgeMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(holder.playerId())) return;
        int size = event.getInventory().getSize();
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= size) return;
        if (raw == slot("previous-page-slot", size - 9, size)) {
            open(player, holder.page() - 1, holder.category());
            return;
        }
        if (raw == slot("next-page-slot", size - 1, size)) {
            open(player, holder.page() + 1, holder.category());
            return;
        }
        if (raw == slot("status-slot", size - 5, size)) {
            List<String> categories = categories();
            int current = Math.max(0, categories.indexOf(holder.category()));
            open(player, 0, categories.get((current + 1) % categories.size()));
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String badgeId = clicked.getItemMeta().getPersistentDataContainer().get(badgeKey, PersistentDataType.STRING);
        if (badgeId == null) return;
        var equipped = service.cached(player.getUniqueId()).equipped(badgeId);
        if (event.isRightClick() && equipped.isPresent()) {
            int from = equipped.get().slot();
            int target = event.isShiftClick() ? from - 1 : from + 1;
            int limit = service.getSlotLimit(player.getUniqueId());
            if (target >= 1 && target <= limit) {
                service.swapSlots(player.getUniqueId(), from, target)
                        .thenRun(() -> reopen(player, holder));
            }
            return;
        }
        var operation = equipped.isPresent()
                ? service.unequipBadge(player.getUniqueId(), badgeId, "gui", BadgeOperationReason.PLAYER)
                : service.equipBadge(player.getUniqueId(), badgeId, "gui", BadgeOperationReason.PLAYER);
        operation.thenAccept(result -> {
            sendResult(player, result, equipped.isPresent());
            reopen(player, holder);
        });
    }

    private void reopen(Player player, BadgeMenuHolder holder) {
        plugin.scheduler().player(player, () -> open(player, holder.page(), holder.category()));
    }

    private List<Badge> visibleBadges(Player player, String category) {
        PlayerBadgeSnapshot snapshot = service.cached(player.getUniqueId());
        return service.getBadges().stream()
                .filter(badge -> category.equals("all") || badge.category().equalsIgnoreCase(category))
                .filter(badge -> !badge.hidden() || snapshot.owned().containsKey(badge.id())
                        || badge.permission().isBlank() || player.hasPermission(badge.permission()))
                .sorted(Comparator.comparingInt(Badge::priority).reversed().thenComparing(Badge::id))
                .toList();
    }

    private ItemStack badgeItem(Player player, Badge badge, PlayerBadgeSnapshot snapshot) {
        BadgeState state = state(player, badge, snapshot);
        String materialName = switch (state) {
            case EQUIPPED -> "equipped-material";
            case OWNED -> "owned-material";
            case EXPIRED, DISABLED -> "expired-material";
            case LOCKED -> "locked-material";
        };
        Material material = material(materialName, Material.NAME_TAG);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.parse(badge.name(), Map.of()));
        List<Component> lore = new ArrayList<>();
        lore.add(messages.parse("<gray>" + badge.description(), Map.of()));
        lore.add(messages.component(player, "gui-category", Map.of("category", badge.category())));
        String stateKey = switch (state) {
            case EQUIPPED -> "gui-equipped";
            case OWNED -> "gui-owned";
            case EXPIRED -> "gui-expired";
            case DISABLED -> "gui-disabled";
            case LOCKED -> "gui-locked";
        };
        lore.add(messages.component(player, stateKey, Map.of("slot", Integer.toString(
                snapshot.equipped(badge.id()).map(value -> value.slot()).orElse(0)))));
        if (state == BadgeState.EQUIPPED) {
            lore.add(messages.component(player, "gui-click-unequip", Map.of()));
            lore.add(messages.component(player, "gui-click-right", Map.of()));
            lore.add(messages.component(player, "gui-click-left", Map.of()));
        } else if (state == BadgeState.OWNED) {
            lore.add(messages.component(player, "gui-click-equip", Map.of()));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(badgeKey, PersistentDataType.STRING, badge.id());
        item.setItemMeta(meta);
        return item;
    }

    private BadgeState state(Player player, Badge badge, PlayerBadgeSnapshot snapshot) {
        if (!badge.enabled()) return BadgeState.DISABLED;
        var owned = snapshot.owned(badge.id()).orElse(null);
        if (owned == null) return BadgeState.LOCKED;
        if (owned.expired(Instant.now())) return BadgeState.EXPIRED;
        if (!badge.permission().isBlank() && !player.hasPermission(badge.permission())) return BadgeState.LOCKED;
        return snapshot.equipped(badge.id()).isPresent() ? BadgeState.EQUIPPED : BadgeState.OWNED;
    }

    private ItemStack statusItem(Player player, PlayerBadgeSnapshot snapshot, int slots, String category) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(player, "gui-status", Map.of(
                "used", Integer.toString(snapshot.equipped().size()), "max", Integer.toString(slots))));
        meta.lore(List.of(
                messages.component(player, "gui-category", Map.of("category", category)),
                messages.component(player, "gui-click-category", Map.of())
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack simple(Player player, Material material, String key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.component(player, key, Map.of()));
        item.setItemMeta(meta);
        return item;
    }

    private List<String> categories() {
        Set<String> categories = new LinkedHashSet<>();
        categories.add("all");
        service.getBadges().stream().map(Badge::category).map(value -> value.toLowerCase(Locale.ROOT))
                .sorted().forEach(categories::add);
        return List.copyOf(categories);
    }

    private Material material(String path, Material fallback) {
        String value = plugin.configFiles().file("gui.yml").getString(path, fallback.name());
        Material parsed = Material.matchMaterial(value);
        return parsed == null ? fallback : parsed;
    }

    private int slot(String path, int fallback, int size) {
        return Math.max(0, Math.min(size - 1, plugin.configFiles().file("gui.yml").getInt(path, fallback)));
    }

    private int normalizedSize(int configured) {
        return Math.max(9, Math.min(54, ((configured + 8) / 9) * 9));
    }

    private void sendResult(Player player, BadgeResult result, boolean removing) {
        String key = switch (result.status()) {
            case SUCCESS -> removing ? "badge-unequipped" : "badge-equipped";
            case NOT_OWNED -> "badge-not-owned";
            case DISABLED -> "badge-disabled";
            case EXPIRED -> "badge-expired";
            case MISSING_PERMISSION -> "badge-permission";
            case ALREADY_EQUIPPED -> "already-equipped";
            case NOT_EQUIPPED -> "not-equipped";
            case SLOT_LIMIT -> "slot-limit";
            default -> "internal-error";
        };
        messages.send(player, key, Map.of("badge", result.badgeId(), "slots", String.valueOf(service.getSlotLimit(player.getUniqueId()))));
    }
}
