package dev.originsx;

import com.mojang.brigadier.CommandDispatcher;
import dev.raceapi.player.RaceManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class OriginsXCommands {

    private OriginsXCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("originsx")
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("race", IdentifierArgument.id())
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            Identifier raceId = IdentifierArgument.getId(context, "race");
                                            try {
                                                RaceManager.setRace(player, raceId);
                                                context.getSource().sendSuccess(
                                                        () -> Component.translatable("originsx.command.set",
                                                                player.getName().getString(), raceId), false);
                                                return 1;
                                            } catch (IllegalArgumentException e) {
                                                context.getSource().sendFailure(
                                                        Component.translatable("originsx.command.unknown_race", raceId));
                                                return 0;
                                            }
                                        }))))
                .then(Commands.literal("clear")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    RaceManager.setRace(player, null);
                                    context.getSource().sendSuccess(
                                            () -> Component.translatable("originsx.command.clear",
                                                    player.getName().getString()), false);
                                    return 1;
                                }))));
    }
}
