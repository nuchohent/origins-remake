package dev.originsx.skilltree.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The skill tree currency. Obtained by crafting; spent in the skill tree
 * screen to unlock and upgrade race powers.
 */
public class SkillShardItem extends Item {

    public SkillShardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.originsx_skilltree.skill_shard.tooltip")
                .withStyle(ChatFormatting.BLUE));
    }
}