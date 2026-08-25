package dev.originsx.skilltree.net;

import dev.originsx.skilltree.SkillTreeMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -&gt; server request for a fresh {@link SyncSkillStatePayload}.
 * Sent when the skill tree screen opens without up-to-date state.
 */
public record RequestSkillStatePayload() implements CustomPacketPayload {

    public static final RequestSkillStatePayload INSTANCE = new RequestSkillStatePayload();

    public static final Type<RequestSkillStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "request_state"));

    public static final StreamCodec<FriendlyByteBuf, RequestSkillStatePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public static void handle(RequestSkillStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow() != PacketFlow.SERVERBOUND) {
                return;
            }
            if (context.player() instanceof ServerPlayer player) {
                SyncSkillStatePayload.send(player);
            }
        });
    }

    @Override
    public Type<RequestSkillStatePayload> type() {
        return TYPE;
    }
}
