package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Grants the player a specific item when the race is first selected.
 * Uses only vanilla / existing items — no custom items are created.
 */
public class GrantItemPower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final Identifier itemId;
    private final int count;

    public GrantItemPower(Identifier id, int difficulty, Identifier itemId, int count) {
        this.id = id;
        this.difficulty = difficulty;
        this.itemId = itemId;
        this.count = Math.max(1, count);
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.grant_item.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.grant_item.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onAttach(ServerPlayer player) {
        var holder = BuiltInRegistries.ITEM.get(itemId);
        if (holder.isEmpty()) {
            return;
        }
        // once per player lifetime, tracked in WORLD saved data: player
        // persistent data does not survive death (it is not copied to the
        // respawned entity), so flags there re-granted items on every respawn
        var flags = dev.raceapi.data.GrantFlagsData.get(
                dev.raceapi.util.RaceUtils.serverLevel(player));
        if (flags.has(player.getUUID(), id.toString())) {
            return;
        }
        flags.mark(player.getUUID(), id.toString());
        int clampedCount = Math.min(count, Math.max(1, holder.get().value().getDefaultMaxStackSize()));
        ItemStack stack = new ItemStack(holder.get().value(), clampedCount);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
