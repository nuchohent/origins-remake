package dev.originsx.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class OriginsXNetwork {

    private OriginsXNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("originsx")
                .versioned("1")
                .optional();
        registrar.playToServer(SelectRacePayload.TYPE, SelectRacePayload.STREAM_CODEC, SelectRacePayload::handle);
        registrar.playToServer(ImportRacePayload.TYPE, ImportRacePayload.STREAM_CODEC, ImportRacePayload::handle);
    }
}
