package dev.raceapi.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RaceNetwork {

    private RaceNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("raceapi")
                .versioned("1")
                .optional();
        registrar.playToClient(SyncRacePayload.TYPE, SyncRacePayload.STREAM_CODEC, SyncRacePayload::handle);
        registrar.playToClient(SyncRacesPayload.TYPE, SyncRacesPayload.STREAM_CODEC, SyncRacesPayload::handle);
        registrar.playToClient(CooldownPayload.TYPE, CooldownPayload.STREAM_CODEC, CooldownPayload::handle);
        registrar.playToClient(ResourcePayload.TYPE, ResourcePayload.STREAM_CODEC, ResourcePayload::handle);
        registrar.playToClient(PowerStatePayload.TYPE, PowerStatePayload.STREAM_CODEC, PowerStatePayload::handle);
        registrar.playToServer(PowerKeyPayload.TYPE, PowerKeyPayload.STREAM_CODEC, PowerKeyPayload::handle);
        registrar.playToServer(JumpPayload.TYPE, JumpPayload.STREAM_CODEC, JumpPayload::handle);
    }
}
