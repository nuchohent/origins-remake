package dev.originsx.skilltree.net;

import dev.originsx.skilltree.SkillTreeMod;
import dev.originsx.skilltree.tree.SkillGate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -&gt; server request to unlock or upgrade one node by id.
 */
public record UnlockNodePayload(String nodeId) implements CustomPacketPayload {

    public static final Type<UnlockNodePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "unlock_node"));

    public static final StreamCodec<FriendlyByteBuf, UnlockNodePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, UnlockNodePayload::nodeId, UnlockNodePayload::new);

    public static void handle(UnlockNodePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow() != PacketFlow.SERVERBOUND) {
                return;
            }
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            int[] tierOut = new int[1];
            SkillGate.UnlockResult result = SkillGate.tryUnlock(player, payload.nodeId(), tierOut);
            if (result == SkillGate.UnlockResult.SUCCESS) {
                // Re-attach the whole race so the new tier takes effect instantly
                dev.raceapi.player.RaceManager.applyPersistedRace(player, false);
            }
            // failures (requirements, max tier...) are visible in the tree UI;
            // no chat spam
            SyncSkillStatePayload.send(player);
        });
    }

    @Override
    public Type<UnlockNodePayload> type() {
        return TYPE;
    }
}
