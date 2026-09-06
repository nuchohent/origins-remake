package dev.raceapi.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.raceapi.data.ParseErrors;
import dev.raceapi.player.RaceManager;
import dev.raceapi.race.Race;
import dev.raceapi.race.RaceRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class RaceCommands {

    private RaceCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raceapi")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list")
                        .executes(context -> {
                            Collection<Race> races = RaceRegistry.playable();
                            context.getSource().sendSuccess(
                                    () -> Component.translatable("raceapi.command.list.header", races.size()),
                                    false);
                            for (Race race : races) {
                                context.getSource().sendSuccess(
                                        () -> Component.literal(" - " + race.getId()), false);
                            }
                            return races.size();
                        }))
                .then(Commands.literal("validate")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            int problems = 0;

                            for (String error : ParseErrors.all()) {
                                source.sendFailure(Component.literal(" [datapack] " + error));
                                problems++;
                            }

                            Map<String, Integer> activeSlots = new HashMap<>();
                            for (Race race : RaceRegistry.all()) {
                                // mirror the runtime slot assignment from
                                // PowerKeyPayload: slots count position among
                                // hasBinding() powers of the race's power list
                                int boundIndex = -1;
                                for (dev.raceapi.race.Power power : race.getPowers()) {
                                    if (!power.hasBinding()) {
                                        continue;
                                    }
                                    boundIndex++;
                                    int slot = power.getBindSlot() >= 0
                                            ? power.getBindSlot()
                                            : boundIndex;
                                    String key = race.getId() + "#" + slot;
                                    Integer previous = activeSlots.putIfAbsent(key, 1);
                                    if (previous != null) {
                                        source.sendFailure(Component.literal(
                                                " [race " + race.getId() + "] active power slot " + slot + " is used twice"));
                                        problems++;
                                    }
                                }
                            }

                            if (problems == 0) {
                                source.sendSuccess(
                                        () -> Component.translatable("raceapi.command.validate.ok"), false);
                                return 0;
                            }
                            int finalProblems = problems;
                            source.sendSuccess(
                                    () -> Component.translatable("raceapi.command.validate.header", finalProblems), false);
                            return problems;
                        }))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("race", IdentifierArgument.id())
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            Identifier raceId = IdentifierArgument.getId(context, "race");
                                            try {
                                                RaceManager.setRace(player, raceId);
                                                context.getSource().sendSuccess(
                                                        () -> Component.translatable("raceapi.command.set",
                                                                player.getName().getString(), raceId), false);
                                                return 1;
                                            } catch (IllegalArgumentException e) {
                                                context.getSource().sendFailure(
                                                        Component.translatable("raceapi.command.unknown_race", raceId));
                                                return 0;
                                            }
                                        }))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    RaceManager.setRace(player, null);
                                    context.getSource().sendSuccess(
                                            () -> Component.translatable("raceapi.command.clear",
                                                    player.getName().getString()), false);
                                    return 1;
                                })
                                .then(Commands.argument("layer", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            String layer = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "layer");
                                            RaceManager.clearLayer(player, layer);
                                            context.getSource().sendSuccess(
                                                    () -> Component.translatable("raceapi.command.clear_layer",
                                                            player.getName().getString(), layer), false);
                                            return 1;
                                        })))));
    }
}
