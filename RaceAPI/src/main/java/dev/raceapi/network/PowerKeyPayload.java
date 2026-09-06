package dev.raceapi.network;

import dev.raceapi.player.RaceManager;
import dev.raceapi.power.PowerCooldowns;
import dev.raceapi.race.Power;
import dev.raceapi.race.Race;
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
                // Bound abilities span every selected origin layer; slots are
                // assigned in layer order so each layer's active powers still
                // address their own keybind.
                dev.raceapi.race.Selection selection = RaceManager.getSelection(player);
                if (selection.isEmpty()) {
                    return;
                }
                List<Power> bound = new ArrayList<>();
                for (Race race : selection.races()) {
                    for (Power power : dev.raceapi.api.PowerPipeline.effective(player, race)) {
                        if (power.hasBinding()) {
                            bound.add(power);
                        }
                    }
                }
                int slot = payload.slot();
                if (slot >= 0 && slot < bound.size()) {
                    Power power = bound.get(slot);
                    // a power that is still on cooldown would reject the press
                    // internally anyway — don't drain the resource pool for a
                    // guaranteed no-op
                    Identifier trackedId = power.getWrapped().getId();
                    int cooldown = power.getCooldownTicks();
                    if (cooldown > 0 && PowerCooldowns.remaining(player, trackedId, cooldown) > 0) {
                        return;
                    }
                    // resource (mana/stamina) cost: player-aware variant, so
                    // wrappers like ConditionalPower can suppress the cost of
                    // presses that are guaranteed to do nothing
                    boolean continuing = PowerCooldowns.hasPendingUse(player, trackedId);
                    long lastUseBefore = PowerCooldowns.lastUseTick(player, trackedId);
                    double cost = power.getResourceCost(player);
                    if (!continuing && cost > 0 && !dev.raceapi.player.Resources.tryConsume(player, cost)) {
                        player.sendOverlayMessage(
                                net.minecraft.network.chat.Component.translatable(
                                        "raceapi.resource.insufficient"));
                        return;
                    }
                    power.onKeyPressed(player);
                    if (!continuing && cost > 0 && cooldown > 0
                            && PowerCooldowns.lastUseTick(player, trackedId) == lastUseBefore
                            && !PowerCooldowns.hasPendingUse(player, trackedId)) {
                        dev.raceapi.player.Resources.add(player, cost);
                    }
                }
            }
        });
    }

    @Override
    public Type<PowerKeyPayload> type() {
        return TYPE;
    }
}
