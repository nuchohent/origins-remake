package dev.originsx.looks;

import dev.originsx.looks.net.LooksNetwork;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OriginsX Looks — character appearance cosmetics for OriginsX races.
 * <p>
 * Races declare a {@code "cosmetics"} array in their JSON definition
 * (see {@code Race#getSourceJson}); this mod renders those items attached to
 * the player model bones and ships a live editor for custom races.
 */
@Mod(LooksMod.MOD_ID)
public final class LooksMod {
    public static final String MOD_ID = "originsx_looks";
    public static final String NAME = "OriginsX Looks";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public LooksMod(IEventBus modBus) {
        modBus.addListener(LooksNetwork::register);
        // broadcast every player's race so cosmetics render on all clients
        NeoForge.EVENT_BUS.addListener(dev.originsx.looks.server.PlayerRaceTracker::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(dev.originsx.looks.server.PlayerRaceTracker::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(dev.originsx.looks.server.PlayerRaceTracker::onRaceChanged);
        if (net.neoforged.fml.loading.FMLEnvironment.getDist()
                == net.neoforged.api.distmarker.Dist.CLIENT) {
            dev.originsx.looks.client.LooksClient.init(modBus);
        }
    }
}
