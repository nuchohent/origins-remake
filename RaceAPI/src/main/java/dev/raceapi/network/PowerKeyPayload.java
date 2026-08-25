package dev.raceapi.network;

import dev.raceapi.player.RaceManager;
import dev.raceapi.power.PowerCooldowns;
import dev.raceapi.race.Power;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -&gt; server request to trigger the active power at a given slot. Slots are
 * assigned in the order the bound powers appear in the race's power list (0, 1,
 * 2, ...). Each slot maps to its own client keybind, so several active abilities
 * in one race no longer clash on a single key.
 */
public record PowerKeyPayload(int slot) implements CustomPacketPayload {

    public static final Type<PowerKeyPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("raceapi", "power_key"));

    public static final StreamCodec<FriendlyByteBuf, PowerKeyPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeVarInt(payload.slot()),
                    buf -> new PowerKeyPayload(buf.readVarInt()));

    public static void handle(PowerKeyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow() != PacketFlow.SERVERBOUND) {
                return;
            }
            if (context.player() instanceof ServerPlayer player) {
                var race = RaceManager.getRace(player);
                if (race == null) {
                    return;
                }
                List<Power> bound = new ArrayList<>();
                for (Power power : dev.raceapi.api.PowerPipeline.effective(player, race)) {
                    if (power.hasBinding()) {
                        bound.add(power);
                    }
                }
                int slot = payload.slot();
                if (slot >= 0 && slot < bound.size()) {
                    Power power = bound.get(slot);
                    // a power that is still on cooldown would reject the press
                    // internally anyway — don't drain the resource pool for a
                    // guaranteed no-op
                    int cooldown = power.getCooldownTicks();
                    if (cooldown > 0 && PowerCooldowns.remaining(player, power.getId(), cooldown) > 0) {
                        return;
                    }
                    // resource (mana/stamina) cost: player-aware variant, so
                    // wrappers like ConditionalPower can suppress the cost of
                    // presses that are guaranteed to do nothing
                    double cost = power.getResourceCost(player);
                    if (cost > 0 && !dev.raceapi.player.Resources.tryConsume(player, cost)) {
                        player.sendOverlayMessage(
                                net.minecraft.network.chat.Component.translatable(
                                        "raceapi.resource.insufficient"));
                        return;
                    }
                    power.onKeyPressed(player);
                }
            }
        });
    }

    @Override
    public Type<PowerKeyPayload> type() {
        return TYPE;
    }
}
