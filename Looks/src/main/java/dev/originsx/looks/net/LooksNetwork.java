package dev.originsx.looks.net;

import dev.originsx.looks.LooksMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class LooksNetwork {

    private LooksNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(LooksMod.MOD_ID)
                .versioned("1")
                .optional();
        registrar.playToClient(PlayerRacePayload.TYPE, PlayerRacePayload.STREAM_CODEC,
                PlayerRacePayload::handle);
    }
}
