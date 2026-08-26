package dev.originsx.looks.client;

import dev.originsx.looks.LooksMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts the cosmetics of an avatar entity into per-frame
 * {@link ItemStackRenderState}s during the extract phase (items cannot be
 * baked during submit). The result travels on the render state via
 * {@link LooksClient#RENDER_DATA} and is consumed by {@link CosmeticsLayer}.
 */
@EventBusSubscriber(modid = LooksMod.MOD_ID, value = Dist.CLIENT)
public final class CosmeticsStateModifier {

    /** One extracted cosmetic: source entry + its item render state. */
    public record Extracted(Cosmetics.Entry entry, ItemStackRenderState itemState) {
    }

    private CosmeticsStateModifier() {
    }

    public static void onRegisterModifiers(net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & net.minecraft.client.entity.ClientAvatarEntity> void accept(
                    T entity, net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
                state.setRenderData(LooksClient.RENDER_DATA, extract(entity));
            }
        });
    }

    /**
     * Public so the editor viewport can run the same extraction manually:
     * the viewport renders via {@code renderer.createRenderState} directly and
     * never goes through the render-feature phase where NeoForge applies
     * registered state modifiers — without this call the cosmetics would
     * never appear (nor update) in the preview.
     */
    public static List<Extracted> extract(Avatar entity) {
        if (entity.isInvisible() || entity.isSpectator()) {
            return List.of();
        }
        List<Cosmetics.Entry> entries = LooksClient.resolveFor(entity);
        if (entries.isEmpty()) {
            return List.of();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return List.of();
        }
        ItemModelResolver resolver = mc.getItemModelResolver();
        List<Extracted> out = new ArrayList<>(entries.size());
        for (Cosmetics.Entry entry : entries) {
            Extracted extracted = bake(entry, resolver, entity);
            if (extracted != null) {
                out.add(extracted);
            }
        }
        return out;
    }

    @Nullable
    private static Extracted bake(Cosmetics.Entry entry,
                                  ItemModelResolver resolver,
                                  Avatar entity) {
        try {
            ItemStackRenderState itemState = new ItemStackRenderState();
            // FIXED = the armor-stand/item-frame display; rot/scale in the entry
            // transform it further onto the bone
            resolver.updateForLiving(itemState, entry.stack(), ItemDisplayContext.FIXED, entity);
            return itemState.isEmpty() ? null : new Extracted(entry, itemState);
        } catch (Exception e) {
            LooksMod.LOGGER.warn("Failed to bake cosmetic item {}",
                    entry.stack().getItem(), e);
            return null;
        }
    }
}
