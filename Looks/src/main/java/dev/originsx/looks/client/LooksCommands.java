package dev.originsx.looks.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = "originsx_looks", value = Dist.CLIENT)
public final class LooksCommands {

    private LooksCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("looks");

        cmd.then(Commands.literal("reload")
                .executes(ctx -> {
                    LooksClient.clearPreview();
                    ctx.getSource().sendSuccess(() -> Component.literal("Looks: preview cleared"), false);
                    return 1;
                }))
                .then(Commands.literal("debug")
                        .executes(ctx -> {
                            var mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            var entries = LooksClient.resolveFor(mc.player);
                            ctx.getSource().sendSuccess(() -> Component.literal("Looks: " + entries.size() + " cosmetics active"), false);
                            return 0;
                        }));

        event.getDispatcher().register(cmd);
    }
}