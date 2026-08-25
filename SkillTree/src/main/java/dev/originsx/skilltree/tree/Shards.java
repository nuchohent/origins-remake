package dev.originsx.skilltree.tree;

import dev.originsx.skilltree.SkillTreeMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Shard currency helpers shared by server logic and the client HUD counter.
 */
public final class Shards {

    public static final Identifier ITEM_ID =
            Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skill_shard");

    private Shards() {
    }

    /** How many skill shards the player carries. Works on both sides. */
    public static int count(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && isShard(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean has(ServerPlayer player, int amount) {
        return count(player) >= amount;
    }

    public static void consume(ServerPlayer player, int amount) {
        int left = amount;
        var items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size() && left > 0; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && isShard(stack)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        player.getInventory().setChanged();
    }

    private static boolean isShard(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).equals(ITEM_ID);
    }
}
