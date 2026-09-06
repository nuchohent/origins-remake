package dev.originsx.looks.net;

import dev.originsx.looks.LooksMod;
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
 * Server -&gt; client broadcast of one player's selected races (one per origin
 * layer) so cosmetics render on every visible player. An empty map means "no
 * race" (cleared).
 */
public record PlayerRacePayload(String uuid, Map<String, String> layers) implements CustomPacketPayload {

    public static final Type<PlayerRacePayload> TYPE = new Type<>(
            LooksMod.id("player_race"));

    public static final StreamCodec<FriendlyByteBuf, PlayerRacePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PlayerRacePayload::uuid,
                    ByteBufCodecs.map(HashMap::new,
                            ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8),
                    PlayerRacePayload::layers,
                    PlayerRacePayload::new);

    /** Tells everyone what layers {@code player} now has selected. */
    public static void broadcast(ServerPlayer player, Selection selection) {
        Map<String, String> layers = new HashMap<>();
        if (selection != null) {
            for (var race : selection.races()) {
                layers.put(race.getLayer(), race.getId().toString());
            }
        }
        PacketDistributor.sendToAllPlayers(new PlayerRacePayload(
                player.getUUID().toString(), layers));
    }

    public static void handle(PlayerRacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.getDist()
                    != net.neoforged.api.distmarker.Dist.CLIENT) {
                return;
            }
            dev.originsx.looks.client.PlayerRaceClient.apply(payload.uuid(), payload.layers());
        });
    }

    @Override
    public CustomPacketPayload.Type<PlayerRacePayload> type() {
        return TYPE;
    }
}
