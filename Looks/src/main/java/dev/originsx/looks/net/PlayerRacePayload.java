package dev.originsx.looks.net;

import dev.originsx.looks.LooksMod;
import dev.originsx.looks.server.PlayerRaceTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -&gt; client broadcast of one player's selected race so cosmetics can
 * render on every visible player. An empty race id means "no race" (cleared).
 */
public record PlayerRacePayload(String uuid, String raceId) implements CustomPacketPayload {

    public static final Type<PlayerRacePayload> TYPE = new Type<>(
            LooksMod.id("player_race"));

    public static final StreamCodec<FriendlyByteBuf, PlayerRacePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PlayerRacePayload::uuid,
                    ByteBufCodecs.STRING_UTF8, PlayerRacePayload::raceId,
                    PlayerRacePayload::new);

    /** Tells everyone what race {@code player} now has (empty string clears). */
    public static void broadcast(ServerPlayer player, String raceId) {
        PacketDistributor.sendToAllPlayers(new PlayerRacePayload(
                player.getUUID().toString(), raceId));
    }

    public static void handle(PlayerRacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.getDist()
                    != net.neoforged.api.distmarker.Dist.CLIENT) {
                return;
            }
            dev.originsx.looks.client.PlayerRaceClient.apply(payload.uuid(), payload.raceId());
        });
    }

    @Override
    public CustomPacketPayload.Type<PlayerRacePayload> type() {
        return TYPE;
    }
}
