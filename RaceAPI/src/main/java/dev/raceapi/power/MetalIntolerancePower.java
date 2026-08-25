package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import java.util.Set;

public class MetalIntolerancePower implements Power {

    private final Identifier id;
    private final int difficulty;
    private final double penaltyPerItem;
    private static final Set<ItemLike> METAL_ITEMS = Set.of(
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE,
            Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE
    );

    private static final Identifier MODIFIER_ID =
            Identifier.fromNamespaceAndPath("raceapi", "metal_intolerance");

    public MetalIntolerancePower(Identifier id, int difficulty, double penaltyPerItem) {
        this.id = id;
        this.difficulty = difficulty;
        this.penaltyPerItem = penaltyPerItem;
    }

    @Override public Identifier getId() { return id; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.metal_intolerance.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.metal_intolerance.desc");
    }

    @Override public int getDifficulty() { return difficulty; }

    @Override
    public void onTick(ServerPlayer player) {
        if (player.tickCount % 10 != 0) {
            return;
        }
        int metalCount = countMetalItems(player);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        maxHealth.removeModifier(MODIFIER_ID);
        if (metalCount > 0) {
            double penalty = maxHealth.getBaseValue() * penaltyPerItem * metalCount;
            maxHealth.addTransientModifier(new AttributeModifier(MODIFIER_ID, penalty,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(MODIFIER_ID);
        }
    }

    private int countMetalItems(ServerPlayer player) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && METAL_ITEMS.stream().anyMatch(item -> stack.is(item.asItem()))) {
                count++;
            }
        }
        return count;
    }
}
