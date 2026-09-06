package dev.originsx.client;

import dev.originsx.OriginsX;
import dev.originsx.item.OriginsXItems;
import net.mcexpanded.fancytabsections.FancyTabSections;
import net.mcexpanded.fancytabsections.Section.SectionColored;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Registers Fancy Tab Sections banner sections inside the OriginsX creative tab.
 * <p>
 * Client-only: sections are visual and pull in {@code GuiGraphicsExtractor} and
 * other client classes, so this is only invoked on the client distribution.
 */
public final class OriginsXTabs {

    private OriginsXTabs() {
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
                Identifier.fromNamespaceAndPath(OriginsX.MOD_ID, "originsx"),
                new SectionColored(Identifier.fromNamespaceAndPath(OriginsX.MOD_ID, "races"))
                        .setTitle(Component.translatable("section.originsx.races"))
                        .setBannerColor(0xFF3D5C8A)
                        .setBannerBorderColor(0xFF88CCFF)
                        .setTextColor(0xFFFFFFFF)
                        .setTextOutline(0xFF14141A)
                        .setTextShadow(true)
                        .add(OriginsXItems.RACE_MEDAL)
        );
    }
}
