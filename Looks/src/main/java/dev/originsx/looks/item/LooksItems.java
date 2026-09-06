package dev.originsx.looks.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class LooksItems {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems("originsx_looks");

    public static final DeferredItem<LookMirrorItem> LOOK_MIRROR = ITEMS.registerItem("look_mirror",
            LookMirrorItem::new);

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "originsx_looks");

    public static final Supplier<? extends CreativeModeTab> DISPLAY_TAB = TABS.register("originsx_looks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.originsx_looks"))
                    .icon(() -> new ItemStack(LOOK_MIRROR.get()))
                    .displayItems((params, output) -> {
                        output.accept(LOOK_MIRROR.get());
                    })
                    .build());

    private LooksItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}