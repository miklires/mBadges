package io.github.miklires.mbadges.service;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.api.MBadgesApi;
import io.github.miklires.mbadges.api.event.BadgeEquipEvent;
import io.github.miklires.mbadges.api.event.BadgeExpireEvent;
import io.github.miklires.mbadges.api.event.BadgeGiveEvent;
import io.github.miklires.mbadges.api.event.BadgeRemoveEvent;
import io.github.miklires.mbadges.api.event.BadgeUnequipEvent;
import io.github.miklires.mbadges.api.model.Badge;
import io.github.miklires.mbadges.api.model.BadgeOperationReason;
import io.github.miklires.mbadges.api.model.BadgeResult;
import io.github.miklires.mbadges.api.model.EquippedBadge;
import io.github.miklires.mbadges.api.model.OwnedBadge;
import io.github.miklires.mbadges.badge.BadgeRegistry;
import io.github.miklires.mbadges.badge.BadgeRenderer;
import io.github.miklires.mbadges.cache.BadgeCache;
import io.github.miklires.mbadges.cache.PlayerBadgeSnapshot;
import io.github.miklires.mbadges.database.DatabaseStorage;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BadgeService implements MBadgesApi, AutoCloseable {
    private final MBadgesPlugin plugin;
    private final BadgeRegistry registry;
    private final DatabaseStorage storage;
    private final BadgeCache cache;
    private final BadgeRenderer renderer;
    private final ExecutorService executor;
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private volatile Runnable displayUpdater = () -> { };

    public BadgeService(MBadgesPlugin plugin, BadgeRegistry registry, DatabaseStorage storage,
                        BadgeCache cache, BadgeRenderer renderer) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
        this.cache = cache;
        this.renderer = renderer;
        int threads = Math.max(2, Math.min(8, plugin.configFiles().file("database.yml").getInt("pool-size", 10)));
        this.executor = Executors.newFixedThreadPool(threads, Thread.ofPlatform().name("mBadges-db-", 0).factory());
    }

    public void setDisplayUpdater(Runnable displayUpdater) {
        this.displayUpdater = displayUpdater == null ? () -> { } : displayUpdater;
    }

    public CompletableFuture<PlayerBadgeSnapshot> loadPlayer(UUID playerId, String name) {
        return CompletableFuture.supplyAsync(() -> {
            storage.rememberPlayer(playerId, name);
            PlayerBadgeSnapshot snapshot = PlayerBadgeSnapshot.from(storage.load(playerId));
            cache.put(playerId, snapshot);
            return snapshot;
        }, executor).whenComplete((value, error) -> {
            if (error == null) displayUpdater.run();
            else plugin.getLogger().warning("could not load badges for " + playerId + ": " + rootMessage(error));
        });
    }

    public CompletableFuture<PlayerBadgeSnapshot> reloadPlayer(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerBadgeSnapshot snapshot = PlayerBadgeSnapshot.from(storage.load(playerId));
            cache.put(playerId, snapshot);
            return snapshot;
        }, executor).whenComplete((value, error) -> {
            if (error == null) displayUpdater.run();
        });
    }

    public void unloadPlayer(UUID playerId) {
        cache.remove(playerId);
        playerLocks.remove(playerId);
    }

    public CompletableFuture<BadgeResult> forceEquip(UUID playerId, String badgeId, String source) {
        return equipInternal(playerId, badgeId, source, BadgeOperationReason.ADMIN, true);
    }

    public CompletableFuture<Boolean> swapSlots(UUID playerId, int first, int second) {
        return supplyLocked(playerId, () -> {
            PlayerBadgeSnapshot snapshot = loadSnapshot(playerId);
            boolean firstExists = snapshot.equipped().stream().anyMatch(value -> value.slot() == first);
            boolean secondExists = snapshot.equipped().stream().anyMatch(value -> value.slot() == second);
            if (!firstExists && !secondExists) return false;
            boolean changed = storage.swapSlots(playerId, first, second);
            refresh(playerId);
            return changed;
        });
    }

    public CompletableFuture<List<UUID>> expireDueBadges() {
        return CompletableFuture.supplyAsync(() -> {
            Instant now = Instant.now();
            List<UUID> changed = cachePlayers().stream().filter(playerId -> {
                synchronized (lock(playerId)) {
                    PlayerBadgeSnapshot snapshot = loadSnapshot(playerId);
                    boolean any = false;
                    for (OwnedBadge owned : snapshot.owned().values()) {
                        if (!owned.expired(now)) continue;
                        Badge badge = registry.get(owned.badgeId()).orElse(null);
                        if (storage.remove(playerId, owned.badgeId())) {
                            any = true;
                            if (badge != null) plugin.getServer().getPluginManager()
                                    .callEvent(new BadgeExpireEvent(true, playerId, badge));
                        }
                    }
                    if (any) refresh(playerId);
                    return any;
                }
            }).toList();
            return changed;
        }, executor).whenComplete((value, error) -> {
            if (error == null && !value.isEmpty()) displayUpdater.run();
        });
    }

    public PlayerBadgeSnapshot cached(UUID playerId) {
        return cache.getOrEmpty(playerId);
    }

    @Override
    public Optional<Badge> getBadge(String id) {
        return registry.get(id);
    }

    @Override
    public Collection<Badge> getBadges() {
        return registry.all();
    }

    @Override
    public CompletableFuture<List<OwnedBadge>> getOwnedBadges(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> loadSnapshot(playerId).owned().values().stream().toList(), executor);
    }

    @Override
    public CompletableFuture<List<EquippedBadge>> getEquippedBadges(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> loadSnapshot(playerId).equipped(), executor);
    }

    @Override
    public CompletableFuture<Boolean> hasBadge(UUID playerId, String badgeId) {
        return CompletableFuture.supplyAsync(() -> loadSnapshot(playerId).owned().containsKey(badgeId), executor);
    }

    @Override
    public CompletableFuture<BadgeResult> giveBadge(UUID playerId, String badgeId, Instant expiresAt,
                                                    String source, String metadata, BadgeOperationReason reason) {
        Badge badge = registry.get(badgeId).orElse(null);
        if (badge == null) return completed(BadgeResult.Status.UNKNOWN_BADGE, badgeId);
        if (expiresAt != null && !badge.temporary()) return completed(BadgeResult.Status.DISABLED, badge.id());
        return supplyBadgeLocked(playerId, badgeId, () -> {
            PlayerBadgeSnapshot snapshot = loadSnapshot(playerId);
            if (snapshot.owned().containsKey(badge.id())) return BadgeResult.of(BadgeResult.Status.ALREADY_OWNED, badge.id());
            OwnedBadge owned = new OwnedBadge(playerId, badge.id(), Instant.now(), expiresAt, source, metadata);
            if (!storage.give(owned)) return BadgeResult.of(BadgeResult.Status.ALREADY_OWNED, badge.id());
            refresh(playerId);
            plugin.getServer().getPluginManager().callEvent(new BadgeGiveEvent(true, playerId, badge, reason, source));
            return BadgeResult.of(BadgeResult.Status.SUCCESS, badge.id());
        });
    }

    @Override
    public CompletableFuture<BadgeResult> removeBadge(UUID playerId, String badgeId, String source,
                                                      BadgeOperationReason reason) {
        Badge badge = registry.get(badgeId).orElse(null);
        if (badge == null) return completed(BadgeResult.Status.UNKNOWN_BADGE, badgeId);
        return supplyBadgeLocked(playerId, badgeId, () -> {
            if (!loadSnapshot(playerId).owned().containsKey(badge.id())) {
                return BadgeResult.of(BadgeResult.Status.NOT_OWNED, badge.id());
            }
            if (!storage.remove(playerId, badge.id())) return BadgeResult.of(BadgeResult.Status.NOT_OWNED, badge.id());
            refresh(playerId);
            plugin.getServer().getPluginManager().callEvent(new BadgeRemoveEvent(true, playerId, badge, reason, source));
            return BadgeResult.of(BadgeResult.Status.SUCCESS, badge.id());
        }).whenComplete((value, error) -> { if (error == null && value.successful()) displayUpdater.run(); });
    }

    @Override
    public CompletableFuture<BadgeResult> equipBadge(UUID playerId, String badgeId, String source,
                                                     BadgeOperationReason reason) {
        return equipInternal(playerId, badgeId, source, reason, false);
    }

    private CompletableFuture<BadgeResult> equipInternal(UUID playerId, String badgeId, String source,
                                                         BadgeOperationReason reason, boolean force) {
        Badge badge = registry.get(badgeId).orElse(null);
        if (badge == null) return completed(BadgeResult.Status.UNKNOWN_BADGE, badgeId);
        if (!badge.enabled()) return completed(BadgeResult.Status.DISABLED, badge.id());
        Access access = access(playerId, badge, force);
        if (!access.allowed()) return completed(BadgeResult.Status.MISSING_PERMISSION, badge.id());
        return supplyBadgeLocked(playerId, badgeId, () -> {
            PlayerBadgeSnapshot snapshot = loadSnapshot(playerId);
            OwnedBadge owned = snapshot.owned(badge.id()).orElse(null);
            if (owned == null) return BadgeResult.of(BadgeResult.Status.NOT_OWNED, badge.id());
            if (owned.expired(Instant.now())) return BadgeResult.of(BadgeResult.Status.EXPIRED, badge.id());
            if (snapshot.equipped(badge.id()).isPresent()) return BadgeResult.of(BadgeResult.Status.ALREADY_EQUIPPED, badge.id());
            int slot = firstFreeSlot(snapshot, access.slots());
            if (slot < 1) return BadgeResult.of(BadgeResult.Status.SLOT_LIMIT, badge.id());
            BadgeEquipEvent event = new BadgeEquipEvent(true, playerId, badge, slot, reason, source);
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return BadgeResult.of(BadgeResult.Status.CANCELLED, badge.id());
            if (!storage.equip(new EquippedBadge(playerId, badge.id(), slot, Instant.now()))) {
                return BadgeResult.of(BadgeResult.Status.SLOT_LIMIT, badge.id());
            }
            refresh(playerId);
            return new BadgeResult(BadgeResult.Status.SUCCESS, badge.id(), slot);
        }).whenComplete((value, error) -> { if (error == null && value.successful()) displayUpdater.run(); });
    }

    @Override
    public CompletableFuture<BadgeResult> unequipBadge(UUID playerId, String badgeId, String source,
                                                       BadgeOperationReason reason) {
        Badge badge = registry.get(badgeId).orElse(null);
        if (badge == null) return completed(BadgeResult.Status.UNKNOWN_BADGE, badgeId);
        return supplyBadgeLocked(playerId, badgeId, () -> {
            if (loadSnapshot(playerId).equipped(badge.id()).isEmpty()) {
                return BadgeResult.of(BadgeResult.Status.NOT_EQUIPPED, badge.id());
            }
            BadgeUnequipEvent event = new BadgeUnequipEvent(true, playerId, badge, reason, source);
            plugin.getServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) return BadgeResult.of(BadgeResult.Status.CANCELLED, badge.id());
            storage.unequip(playerId, badge.id());
            refresh(playerId);
            return BadgeResult.of(BadgeResult.Status.SUCCESS, badge.id());
        }).whenComplete((value, error) -> { if (error == null && value.successful()) displayUpdater.run(); });
    }

    @Override
    public CompletableFuture<BadgeResult> clearEquippedBadges(UUID playerId, String source,
                                                              BadgeOperationReason reason) {
        return supplyBadgeLocked(playerId, "", () -> {
            PlayerBadgeSnapshot snapshot = loadSnapshot(playerId);
            if (snapshot.equipped().isEmpty()) return BadgeResult.of(BadgeResult.Status.NOT_EQUIPPED, "");
            for (EquippedBadge equipped : snapshot.equipped()) {
                Badge badge = registry.get(equipped.badgeId()).orElse(null);
                if (badge == null) continue;
                BadgeUnequipEvent event = new BadgeUnequipEvent(true, playerId, badge, reason, source);
                plugin.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) return BadgeResult.of(BadgeResult.Status.CANCELLED, badge.id());
            }
            storage.clear(playerId);
            refresh(playerId);
            return BadgeResult.of(BadgeResult.Status.SUCCESS, "");
        }).whenComplete((value, error) -> { if (error == null && value.successful()) displayUpdater.run(); });
    }

    @Override
    public int getSlotLimit(UUID playerId) {
        return access(playerId, null, false).slots();
    }

    @Override
    public String render(UUID playerId) {
        return renderer.render(playerId);
    }

    private Access access(UUID playerId, Badge badge, boolean force) {
        int hardLimit = plugin.configFiles().hardLimit();
        int slots = Math.max(1, Math.min(hardLimit, plugin.getConfig().getInt("equipment.default-slots", 1)));
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            for (int value = 1; value <= hardLimit; value++) {
                if (player.hasPermission("mbadge.max." + value)) slots = value;
            }
        }
        boolean allowed = force || badge == null || badge.permission().isBlank()
                || (player != null && player.hasPermission(badge.permission()));
        return new Access(allowed, slots);
    }

    private int firstFreeSlot(PlayerBadgeSnapshot snapshot, int limit) {
        for (int slot = 1; slot <= limit; slot++) {
            int candidate = slot;
            if (snapshot.equipped().stream().noneMatch(value -> value.slot() == candidate)) return slot;
        }
        return 0;
    }

    private PlayerBadgeSnapshot loadSnapshot(UUID playerId) {
        return cache.get(playerId).orElseGet(() -> {
            PlayerBadgeSnapshot snapshot = PlayerBadgeSnapshot.from(storage.load(playerId));
            cache.put(playerId, snapshot);
            return snapshot;
        });
    }

    private void refresh(UUID playerId) {
        cache.put(playerId, PlayerBadgeSnapshot.from(storage.load(playerId)));
    }

    private List<UUID> cachePlayers() {
        return cache.players().stream().toList();
    }

    private Object lock(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, ignored -> new Object());
    }

    private <T> CompletableFuture<T> supplyLocked(UUID playerId, java.util.function.Supplier<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock(playerId)) {
                return work.get();
            }
        }, executor);
    }

    private CompletableFuture<BadgeResult> supplyBadgeLocked(UUID playerId, String badgeId,
                                                              java.util.function.Supplier<BadgeResult> work) {
        return supplyLocked(playerId, work).exceptionally(error -> {
            plugin.getLogger().warning("badge operation failed for " + playerId + ": " + rootMessage(error));
            return BadgeResult.of(BadgeResult.Status.STORAGE_ERROR, badgeId);
        });
    }

    private CompletableFuture<BadgeResult> completed(BadgeResult.Status status, String badgeId) {
        return CompletableFuture.completedFuture(BadgeResult.of(status, badgeId));
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    private record Access(boolean allowed, int slots) { }
}
