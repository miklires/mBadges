package io.github.miklires.mbadges.command;

import io.github.miklires.mbadges.MBadgesPlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public final class ReadyCommand implements BasicCommand {
    private final MBadgesPlugin plugin;
    private final Supplier<BasicCommand> delegate;

    public ReadyCommand(MBadgesPlugin plugin, Supplier<BasicCommand> delegate) {
        this.plugin = plugin;
        this.delegate = delegate;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        BasicCommand command = delegate.get();
        if (!plugin.ready() || command == null) {
            source.getSender().sendPlainMessage("mBadges is still starting.");
            return;
        }
        command.execute(source, args);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        BasicCommand command = delegate.get();
        return !plugin.ready() || command == null ? List.of() : command.suggest(source, args);
    }
}
