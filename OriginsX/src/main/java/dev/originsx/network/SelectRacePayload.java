package dev.originsx.network;

import dev.raceapi.player.RaceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -&gt; server request to select a race. Empty string clears the race.
 */
public record SelectRacePayload(String raceId) implements CustomPacketPayload {

    public static final Type<SelectRacePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("originsx", "select_race"));

    public static final StreamCodec<FriendlyByteBuf, SelectRacePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SelectRacePayload::raceId, SelectRacePayload::new);

    public static void handle(SelectRacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow() != PacketFlow.SERVERBOUND) {
                return;
            }
            if (context.player() instanceof ServerPlayer player) {
                String id = payload.raceId();
                if (!id.isEmpty() && Identifier.tryParse(id) == null) {
                    // malformed id from a modified client: ignore instead of
                    // falling through to null, which would clear the race
                    return;
                }
                try {
                    RaceManager.setRace(player, id.isEmpty() ? null : Identifier.tryParse(id));
                } catch (IllegalArgumentException e) {
                    // unknown race id (e.g. the race vanished via /reload while
                    // the selection screen was open): sync the actual state back
                    dev.raceapi.network.SyncRacePayload.send(player, dev.raceapi.player.RaceManager.getRace(player));
                }
            }
        });
    }

    @Override
    public Type<SelectRacePayload> type() {
        return TYPE;
    }
}
