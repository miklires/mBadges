package io.github.miklires.mbadges.command;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.api.model.BadgeOperationReason;
import io.github.miklires.mbadges.api.model.BadgeResult;
import io.github.miklires.mbadges.api.model.BadgeState;
import io.github.miklires.mbadges.gui.BadgeGui;
import io.github.miklires.mbadges.message.MessageService;
import io.github.miklires.mbadges.service.BadgeService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BadgeCommand implements BasicCommand {
    private final MBadgesPlugin plugin;
    private final BadgeService service;
    private final BadgeGui gui;
    private final MessageService messages;

    public BadgeCommand(MBadgesPlugin plugin, BadgeService service, BadgeGui gui, MessageService messages) {
        this.plugin = plugin;
        this.service = service;
        this.gui = gui;
        this.messages = messages;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("mbadge.use")) {
            messages.send(player, "no-permission");
            return;
        }
        if (!plugin.ready()) {
            messages.send(player, "loading");
            return;
        }
        String action = args.length == 0 ? "gui" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "gui" -> {
                if (player.hasPermission("mbadge.gui")) gui.open(player);
                else messages.send(player, "no-permission");
            }
            case "list" -> list(player);
            case "set" -> change(player, args, true);
            case "remove" -> change(player, args, false);
            case "clear" -> service.clearEquippedBadges(player.getUniqueId(), player.getName(), BadgeOperationReason.PLAYER)
                    .thenAccept(result -> reply(player, () -> messages.send(player,
                            result.successful() ? "cleared" : "not-equipped")));
            case "info" -> info(player, args);
            default -> messages.send(player, "unknown-badge", Map.of("badge", action));
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        if (!(source.getSender() instanceof Player player) || !player.hasPermission("mbadge.use")) return List.of();
        if (args.length <= 1) return filter(List.of("gui", "list", "set", "remove", "clear", "info"), value(args, 0));
        if (args.length == 2 && List.of("set", "remove", "info").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(service.getBadges().stream().map(badge -> badge.id()).toList(), args[1]);
        }
        return List.of();
    }

    private void list(Player player) {
        var snapshot = service.cached(player.getUniqueId());
        messages.send(player, "list-header", Map.of(
                "player", player.getName(),
                "owned", Integer.toString(snapshot.owned().size()),
                "equipped", Integer.toString(snapshot.equipped().size())
        ));
        service.getBadges().stream().filter(badge -> snapshot.owned().containsKey(badge.id())).forEach(badge -> {
            BadgeState state = state(player, badge.id());
            messages.send(player, "list-entry", Map.of(
                    "state", state == BadgeState.EQUIPPED ? "[E] " : "",
                    "badge", badge.name(),
                    "description", badge.description()
            ));
        });
    }

    private void info(Player player, String[] args) {
        if (args.length < 2) return;
        service.getBadge(args[1]).ifPresentOrElse(badge -> messages.send(player, "info", Map.of(
                "badge", badge.name(), "description", badge.description(),
                "state", state(player, badge.id()).name().toLowerCase(Locale.ROOT))),
                () -> messages.send(player, "unknown-badge", Map.of("badge", args[1])));
    }

    private void change(Player player, String[] args, boolean equip) {
        if (args.length < 2) return;
        var future = equip
                ? service.equipBadge(player.getUniqueId(), args[1], player.getName(), BadgeOperationReason.PLAYER)
                : service.unequipBadge(player.getUniqueId(), args[1], player.getName(), BadgeOperationReason.PLAYER);
        future.thenAccept(result -> reply(player, () -> sendResult(player, result, !equip)));
    }

    private BadgeState state(Player player, String badgeId) {
        var badge = service.getBadge(badgeId).orElse(null);
        if (badge == null || !badge.enabled()) return BadgeState.DISABLED;
        var snapshot = service.cached(player.getUniqueId());
        var owned = snapshot.owned(badgeId).orElse(null);
        if (owned == null) return BadgeState.LOCKED;
        if (owned.expired(Instant.now())) return BadgeState.EXPIRED;
        if (!badge.permission().isBlank() && !player.hasPermission(badge.permission())) return BadgeState.LOCKED;
        return snapshot.equipped(badgeId).isPresent() ? BadgeState.EQUIPPED : BadgeState.OWNED;
    }

    private void sendResult(Player player, BadgeResult result, boolean removing) {
        String key = switch (result.status()) {
            case SUCCESS -> removing ? "badge-unequipped" : "badge-equipped";
            case UNKNOWN_BADGE -> "unknown-badge";
            case NOT_OWNED -> "badge-not-owned";
            case DISABLED -> "badge-disabled";
            case EXPIRED -> "badge-expired";
            case MISSING_PERMISSION -> "badge-permission";
            case ALREADY_EQUIPPED -> "already-equipped";
            case NOT_EQUIPPED -> "not-equipped";
            case SLOT_LIMIT -> "slot-limit";
            default -> "internal-error";
        };
        messages.send(player, key, Map.of("badge", result.badgeId(),
                "slots", Integer.toString(service.getSlotLimit(player.getUniqueId()))));
    }

    private void reply(Player player, Runnable action) {
        plugin.scheduler().player(player, action);
    }

    private Collection<String> filter(Collection<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private String value(String[] args, int index) {
        return args.length > index ? args[index] : "";
    }
}
