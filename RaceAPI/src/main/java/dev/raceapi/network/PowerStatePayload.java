package dev.raceapi.network;

import dev.raceapi.client.PowerStateClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client sync of a toggle power state (ON/OFF) for the HUD
 * indicator: the power id and whether it is currently switched on.
 */
public record PowerStatePayload(String powerId, boolean on) implements CustomPacketPayload {

    public static final Type<PowerStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("raceapi", "power_state"));

    public static final StreamCodec<FriendlyByteBuf, PowerStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PowerStatePayload::powerId,
                    ByteBufCodecs.BOOL, PowerStatePayload::on,
                    PowerStatePayload::new);

    public static void send(ServerPlayer player, String powerId, boolean on) {
        PacketDistributor.sendToPlayer(player, new PowerStatePayload(powerId, on));
    }

    public static void handle(PowerStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                PowerStateClient.set(payload.powerId(), payload.on()));
    }

    @Override
    public Type<PowerStatePayload> type() {
        return TYPE;
    }
}
