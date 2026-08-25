package dev.originsx;

import dev.originsx.item.OriginsXItems;
import dev.originsx.network.OriginsXNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(OriginsX.MOD_ID)
public final class OriginsX {
    public static final String MOD_ID = "originsx";
    public static final String NAME = "OriginsX";

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue RACE_AUTO_OPEN_ONCE = CLIENT_BUILDER
            .comment("Whether the race selection screen has already been auto-opened for this install. "
                    + "Once true, the screen will not auto-open again on later world joins.")
            .define("raceAutoOpenOnce", false);
    public static final ModConfigSpec CLIENT_CONFIG = CLIENT_BUILDER.build();

    public OriginsX(IEventBus modBus) {
        modBus.addListener(OriginsXNetwork::register);
        OriginsXItems.register(modBus);
        // client-only setup (screens, keybinds, HUD): must never load on a
        // dedicated server — OriginsXClient pulls client classes like Screen
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            dev.originsx.client.OriginsXClient.init(modBus);
        }
        ModLoadingContext.get().getActiveContainer()
                .registerConfig(ModConfig.Type.CLIENT, CLIENT_CONFIG, "originsx-client.toml");

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        OriginsXCommands.register(event.getDispatcher());
    }
}
