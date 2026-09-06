package dev.originsx.skilltree.client;

import dev.originsx.skilltree.SkillTreeMod;
import dev.originsx.skilltree.item.SkillTreeItems;
import net.mcexpanded.fancytabsections.FancyTabSections;
import net.mcexpanded.fancytabsections.Section.SectionColored;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Registers Fancy Tab Sections banner sections inside the OriginsX Skill Tree
 * creative tab. Client-only, same reasoning as {@code OriginsXTabs}.
 */
public final class SkillTreeTabs {

    private SkillTreeTabs() {
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
                Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "originsx_skilltree"),
                new SectionColored(Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "skills"))
                        .setTitle(Component.translatable("section.originsx_skilltree.skills"))
                        .setBannerColor(0xFF3D5C8A)
                        .setBannerBorderColor(0xFF88CCFF)
                        .setTextColor(0xFFFFFFFF)
                        .setTextOutline(0xFF14141A)
                        .setTextShadow(true)
                        .add(SkillTreeItems.SKILL_SHARD)
        );
    }
}