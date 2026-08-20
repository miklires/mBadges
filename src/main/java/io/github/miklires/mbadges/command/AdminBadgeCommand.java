package io.github.miklires.mbadges.command;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.github.miklires.mbadges.api.model.BadgeOperationReason;
import io.github.miklires.mbadges.api.model.BadgeResult;
import io.github.miklires.mbadges.message.MessageService;
import io.github.miklires.mbadges.resourcepack.ResourcePackService;
import io.github.miklires.mbadges.service.BadgeService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AdminBadgeCommand implements BasicCommand {
    private final MBadgesPlugin plugin;
    private final BadgeService service;
    private final ResourcePackService resourcePack;
    private final MessageService messages;

    public AdminBadgeCommand(MBadgesPlugin plugin, BadgeService service, ResourcePackService resourcePack,
                             MessageService messages) {
        this.plugin = plugin;
        this.service = service;
        this.resourcePack = resourcePack;
        this.messages = messages;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (!plugin.ready()) {
            messages.send(sender, "loading");
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "no-permission");
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        String permission = switch (action) {
            case "give" -> "mbadge.admin.give";
            case "take" -> "mbadge.admin.take";
            case "equip", "unequip" -> "mbadge.admin.equip";
            case "clear" -> "mbadge.admin.clear";
            case "list" -> "mbadge.admin.list";
            case "reload" -> "mbadge.admin.reload";
            case "resourcepack" -> "mbadge.admin.resourcepack";
            default -> "mbadge.admin";
        };
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "no-permission");
            return;
        }
        switch (action) {
            case "give" -> give(sender, args);
            case "take", "equip", "unequip" -> badgeOperation(sender, action, args);
            case "clear" -> clear(sender, args);
            case "list" -> list(sender, args);
            case "reload" -> reload(sender);
            case "resourcepack" -> rebuild(sender, args);
            default -> messages.send(sender, "no-permission");
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (args.length <= 1) {
            return filter(List.of("give", "take", "equip", "unequip", "clear", "list", "reload", "resourcepack"), value(args, 0));
        }
        if (args.length == 2 && !List.of("reload", "resourcepack").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(player -> player.getName()).toList(), args[1]);
        }
        if (args.length == 3 && List.of("give", "take", "equip", "unequip").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(service.getBadges().stream().map(badge -> badge.id()).toList(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resourcepack")) return filter(List.of("rebuild"), args[1]);
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) return filter(List.of("--duration"), args[3]);
        return List.of();
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) return;
        Instant expires = null;
        if (args.length >= 5 && args[3].equalsIgnoreCase("--duration")) {
            try {
                expires = Instant.now().plus(DurationParser.parse(args[4]));
            } catch (RuntimeException exception) {
                messages.send(sender, "invalid-duration");
                return;
            }
        }
        Instant finalExpires = expires;
        resolve(sender, args[1], playerId -> service.giveBadge(playerId, args[2], finalExpires,
                sender.getName(), "", BadgeOperationReason.ADMIN).thenAccept(result -> reply(sender, () -> {
            String key = switch (result.status()) {
                case SUCCESS -> "badge-given";
                case UNKNOWN_BADGE -> "unknown-badge";
                case ALREADY_OWNED -> "already-owned";
                case DISABLED -> finalExpires != null ? "temporary-not-supported" : "badge-disabled";
                default -> "internal-error";
            };
            messages.send(sender, key, Map.of("badge", args[2], "player", args[1]));
        })));
    }

    private void badgeOperation(CommandSender sender, String action, String[] args) {
        if (args.length < 3) return;
        resolve(sender, args[1], playerId -> {
            var future = switch (action) {
                case "take" -> service.removeBadge(playerId, args[2], sender.getName(), BadgeOperationReason.ADMIN);
                case "equip" -> service.forceEquip(playerId, args[2], sender.getName());
                case "unequip" -> service.unequipBadge(playerId, args[2], sender.getName(), BadgeOperationReason.ADMIN);
                default -> throw new IllegalStateException("unknown operation");
            };
            future.thenAccept(result -> reply(sender, () -> {
                String key = switch (result.status()) {
                    case SUCCESS -> action.equals("take") ? "badge-taken" : action.equals("equip") ? "badge-equipped" : "badge-unequipped";
                    case UNKNOWN_BADGE -> "unknown-badge";
                    case NOT_OWNED -> "not-owned";
                    case NOT_EQUIPPED -> "not-equipped";
                    case SLOT_LIMIT -> "slot-limit";
                    default -> "internal-error";
                };
                messages.send(sender, key, Map.of("badge", args[2], "player", args[1],
                        "slots", Integer.toString(service.getSlotLimit(playerId))));
            }));
        });
    }

    private void clear(CommandSender sender, String[] args) {
        if (args.length < 2) return;
        resolve(sender, args[1], playerId -> service.clearEquippedBadges(playerId, sender.getName(), BadgeOperationReason.ADMIN)
                .thenAccept(result -> reply(sender, () -> messages.send(sender,
                        result.successful() ? "cleared" : "not-equipped"))));
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length < 2) return;
        resolve(sender, args[1], playerId -> service.getOwnedBadges(playerId).thenCombine(
                service.getEquippedBadges(playerId), (owned, equipped) -> new int[]{owned.size(), equipped.size()})
                .thenAccept(counts -> reply(sender, () -> messages.send(sender, "list-header", Map.of(
                        "player", args[1], "owned", Integer.toString(counts[0]), "equipped", Integer.toString(counts[1]))))));
    }

    private void reload(CommandSender sender) {
        service.reloadConfiguration().thenCompose(ignored -> resourcePack.build(false)).thenAccept(result ->
                reply(sender, () -> messages.send(sender, "reloaded"))).exceptionally(error -> {
            reply(sender, () -> messages.send(sender, "internal-error"));
            return null;
        });
    }

    private void rebuild(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("rebuild")) return;
        resourcePack.build(true).thenAccept(result -> reply(sender, () -> messages.send(sender,
                "resourcepack-built", Map.of("file", result.file().toString())))).exceptionally(error -> {
            plugin.getLogger().warning("resource pack rebuild failed: " + error.getMessage());
            reply(sender, () -> messages.send(sender, "resourcepack-failed"));
            return null;
        });
    }

    private void resolve(CommandSender sender, String input, java.util.function.Consumer<UUID> action) {
        service.findPlayer(input).thenAccept(playerId -> {
            if (playerId == null) reply(sender, () -> messages.send(sender, "unknown-player", Map.of("player", input)));
            else action.accept(playerId);
        }).exceptionally(error -> {
            reply(sender, () -> messages.send(sender, "internal-error"));
            return null;
        });
    }

    private void reply(CommandSender sender, Runnable action) {
        if (sender instanceof org.bukkit.entity.Player player) plugin.scheduler().player(player, action);
        else plugin.scheduler().global(action);
    }

    private Collection<String> filter(Collection<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private String value(String[] args, int index) {
        return args.length > index ? args[index] : "";
    }
}
