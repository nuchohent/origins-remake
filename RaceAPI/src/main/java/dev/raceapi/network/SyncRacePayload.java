package dev.raceapi.network;

import dev.raceapi.client.SelectedRaceClient;
import dev.raceapi.race.Race;
import dev.raceapi.race.Selection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -&gt; client notification of the player's currently selected race per
 * origin layer. An empty map means "no race".
 */
public record SyncRacePayload(Map<String, String> layers) implements CustomPacketPayload {

    public static final Type<SyncRacePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("raceapi", "sync_race"));

    public static final StreamCodec<FriendlyByteBuf, SyncRacePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.map(HashMap::new,
                            ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8),
                    SyncRacePayload::layers, SyncRacePayload::new);

    public static void send(ServerPlayer player, Race race) {
        Map<String, String> layers = new HashMap<>();
        if (race != null) {
            layers.put(race.getLayer(), race.getId().toString());
        }
        send(player, new SyncRacePayload(layers));
    }

    public static void send(ServerPlayer player, Selection selection) {
        Map<String, String> layers = new HashMap<>();
        for (Race race : selection.races()) {
            layers.put(race.getLayer(), race.getId().toString());
        }
        send(player, new SyncRacePayload(layers));
    }

    private static void send(ServerPlayer player, SyncRacePayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void handle(SyncRacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SelectedRaceClient.set(payload.layers()));
    }

    @Override
    public Type<SyncRacePayload> type() {
        return TYPE;
    }
}