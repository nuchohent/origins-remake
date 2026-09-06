package dev.raceapi.network;

import dev.raceapi.client.ResourceClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client sync of the race resource pool (mana/stamina) for the
 * HUD bar: current value and pool size.
 */
public record ResourcePayload(double current, double max) implements CustomPacketPayload {

    public static final Type<ResourcePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("raceapi", "resource"));

    public static final StreamCodec<FriendlyByteBuf, ResourcePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.DOUBLE, ResourcePayload::current,
                    ByteBufCodecs.DOUBLE, ResourcePayload::max,
                    ResourcePayload::new);

    public static void send(ServerPlayer player, double current, double max) {
        PacketDistributor.sendToPlayer(player, new ResourcePayload(current, max));
    }

    public static void handle(ResourcePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                ResourceClient.set(payload.current(), payload.max()));
    }

    @Override
    public Type<ResourcePayload> type() {
        return TYPE;
    }
}
