package dev.raceapi.network;

import dev.raceapi.client.CooldownClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -&gt; client notification that an active power went on cooldown, or that
 * its remaining cooldown changed (e.g. when a race is re-applied). The client
 * uses this to draw the cooldown HUD indicator.
 */
public record CooldownPayload(String powerId, int cooldownTicks, int remainingTicks) implements CustomPacketPayload {

    public static final Type<CooldownPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("raceapi", "cooldown"));

    public static final StreamCodec<FriendlyByteBuf, CooldownPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CooldownPayload::powerId,
                    ByteBufCodecs.VAR_INT, CooldownPayload::cooldownTicks,
                    ByteBufCodecs.VAR_INT, CooldownPayload::remainingTicks,
                    CooldownPayload::new);

    public static void send(ServerPlayer player, String powerId, int cooldownTicks, int remainingTicks) {
        PacketDistributor.sendToPlayer(player, new CooldownPayload(powerId, cooldownTicks, remainingTicks));
    }

    public static void handle(CooldownPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                CooldownClient.set(payload.powerId(), payload.cooldownTicks(), payload.remainingTicks()));
    }

    @Override
    public Type<CooldownPayload> type() {
        return TYPE;
    }
}
