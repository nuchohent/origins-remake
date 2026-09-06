package dev.originsx.looks.item;

import dev.originsx.looks.client.LooksClient;
import dev.raceapi.client.SelectedRaceClient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Cosmetic mirror — opens the appearance editor for the current race.
 * Server-side this does nothing; the editor is a client UI.
 */
public class LookMirrorItem extends Item {

    public LookMirrorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        Identifier raceId = SelectedRaceClient.getOrNull();
        LooksClient.openEditor(raceId == null ? "" : raceId.toString());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.originsx_looks.mirror.tooltip"));
    }
}