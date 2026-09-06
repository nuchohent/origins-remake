package dev.originsx.skilltree.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class SkillTreeItems {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems("originsx_skilltree");

    public static final DeferredItem<SkillShardItem> SKILL_SHARD = ITEMS.registerItem("skill_shard",
            SkillShardItem::new);

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "originsx_skilltree");

    public static final Supplier<? extends CreativeModeTab> DISPLAY_TAB = TABS.register("originsx_skilltree",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.originsx_skilltree"))
                    .icon(() -> new ItemStack(SKILL_SHARD.get()))
                    .displayItems((params, output) -> {
                        output.accept(SKILL_SHARD.get());
                    })
                    .build());

    private SkillTreeItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
