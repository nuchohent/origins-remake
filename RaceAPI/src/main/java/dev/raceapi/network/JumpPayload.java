package dev.raceapi.network;

import dev.raceapi.RaceAPI;
import dev.raceapi.util.RaceUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server notification that the local player jumped. The server never
 * calls {@code jumpFromGround()} for non-passenger players (the jump is fully
 * client-side), so jump-triggered powers (high jump, jump restriction, ...)
 * can only be activated via this packet.
 */
public record JumpPayload() implements CustomPacketPayload {

    private static final String LAST_JUMP_KEY = "raceapi_last_jump";
    private static final long MIN_JUMP_INTERVAL_TICKS = 5;

    public static final Type<JumpPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("raceapi", "player_jump"));

    public static final StreamCodec<FriendlyByteBuf, JumpPayload> STREAM_CODEC =
            StreamCodec.unit(new JumpPayload());

    public static void handle(JumpPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow() != PacketFlow.SERVERBOUND) {
                return;
            }
            if (context.player() instanceof ServerPlayer player) {
                CompoundTag data = player.getPersistentData();
                long now = RaceUtils.serverLevel(player).getGameTime();
                long last = data.getLongOr(LAST_JUMP_KEY, 0L);
                if (last != 0L && now - last < MIN_JUMP_INTERVAL_TICKS) {
                    return;
                }
                data.putLong(LAST_JUMP_KEY, now);
                RaceAPI.fireJump(player);
            }
        });
    }

    @Override
    public Type<JumpPayload> type() {
        return TYPE;
    }
}
