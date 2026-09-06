package dev.originsx.looks.client;

import dev.originsx.looks.LooksMod;
import dev.originsx.looks.item.LooksItems;
import net.mcexpanded.fancytabsections.FancyTabSections;
import net.mcexpanded.fancytabsections.Section.SectionColored;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Registers Fancy Tab Sections banner sections inside the OriginsX Looks
 * creative tab. Client-only, same reasoning as {@code OriginsXTabs}.
 */
public final class LooksTabs {

    private LooksTabs() {
    }

    public static void register() {
        // Fancy Tab Sections is OPTIONAL: if the player did not install the
        // standalone "fancytabsections" mod these banner sections are skipped
        // and the plain creative tab (with all items) is used instead.
        if (!net.neoforged.fml.ModList.get().isLoaded("fancytabsections")) {
            return;
        }
        // Palette matches dev.originsx.client.gui.RaceSelectionScreen / UiPalette
        // (race selection menu): steel-blue banner + light-blue accent border + white text.
        FancyTabSections.addSection(
                Identifier.fromNamespaceAndPath(LooksMod.MOD_ID, "originsx_looks"),
                new SectionColored(Identifier.fromNamespaceAndPath(LooksMod.MOD_ID, "cosmetics"))
                        .setTitle(Component.translatable("section.originsx_looks.cosmetics"))
                        .setBannerColor(0xFF3D5C8A)
                        .setBannerBorderColor(0xFF88CCFF)
                        .setTextColor(0xFFFFFFFF)
                        .setTextOutline(0xFF14141A)
                        .setTextShadow(true)
                        .add(LooksItems.LOOK_MIRROR)
        );
    }
}