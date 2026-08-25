package dev.raceapi.network;

import dev.raceapi.client.SelectedRaceClient;
import dev.raceapi.race.Race;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -&gt; client notification of the player's currently selected race.
 * An empty string means "no race".
 */
public record SyncRacePayload(String raceId) implements CustomPacketPayload {

    public static final Type<SyncRacePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("raceapi", "sync_race"));

    public static final StreamCodec<FriendlyByteBuf, SyncRacePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SyncRacePayload::raceId, SyncRacePayload::new);

    public static void send(ServerPlayer player, Race race) {
        PacketDistributor.sendToPlayer(player, new SyncRacePayload(race == null ? "" : race.getId().toString()));
    }

    public static void handle(SyncRacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            String id = payload.raceId();
            SelectedRaceClient.set(id.isEmpty() ? null : Identifier.tryParse(id));
        });
    }

    @Override
    public Type<SyncRacePayload> type() {
        return TYPE;
    }
}
