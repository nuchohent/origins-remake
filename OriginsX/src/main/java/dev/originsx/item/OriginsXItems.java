package dev.originsx.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class OriginsXItems {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems("originsx");

    public static final DeferredItem<RaceMedalItem> RACE_MEDAL = ITEMS.registerItem("race_medal",
            RaceMedalItem::new);

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "originsx");

    public static final Supplier<? extends CreativeModeTab> DISPLAY_TAB = TABS.register("originsx",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.originsx"))
                    .icon(() -> new ItemStack(RACE_MEDAL.get()))
                    .displayItems((params, output) -> {
                        output.accept(RACE_MEDAL.get());
                    })
                    .build());

    private OriginsXItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}