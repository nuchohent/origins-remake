package dev.originsx.skilltree.net;

import dev.originsx.skilltree.SkillTreeMod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SkillTreeNetwork {

    private SkillTreeNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SkillTreeMod.MOD_ID)
                .versioned("1")
                .optional();
        registrar.playToClient(SyncSkillStatePayload.TYPE, SyncSkillStatePayload.STREAM_CODEC,
                SyncSkillStatePayload::handle);
        registrar.playToServer(UnlockNodePayload.TYPE, UnlockNodePayload.STREAM_CODEC,
                UnlockNodePayload::handle);
        registrar.playToServer(RequestSkillStatePayload.TYPE, RequestSkillStatePayload.STREAM_CODEC,
                RequestSkillStatePayload::handle);
    }
}
