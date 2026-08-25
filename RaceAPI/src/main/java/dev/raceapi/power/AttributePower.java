package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Applies a flat {@link AttributeModifier} to a player attribute while active.
 * The modifier is keyed by a fixed UUID derived from the power id so it can be
 * removed cleanly on {@link #onRemove(ServerPlayer)}.
 */
public class AttributePower implements Power {

    private final Identifier id;
    private final Holder<Attribute> attribute;
    private final double amount;
    private final AttributeModifier.Operation operation;
    private final boolean hidden;
    private final int difficulty;

    public AttributePower(Identifier id, Holder<Attribute> attribute, double amount,
                          AttributeModifier.Operation operation, boolean hidden, int difficulty) {
        this.id = id;
        this.attribute = attribute;
        this.amount = amount;
        this.operation = operation;
        this.hidden = hidden;
        this.difficulty = difficulty;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.attribute.name",
                Component.translatable(attribute.value().getDescriptionId()),
                formatAmount());
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.attribute.desc",
                Component.translatable(attribute.value().getDescriptionId()),
                formatAmount());
    }

    private String formatAmount() {
        String sign = amount >= 0 ? "+" : "";
        if (operation == AttributeModifier.Operation.ADD_VALUE) {
            return sign + trimDouble(amount);
        }
        return sign + Math.round(amount * 100) + "%";
    }

    private static String trimDouble(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    /**
     * Modifier id keyed per power id: two attribute powers on the same
     * attribute (e.g. {@code max_health_plus} + {@code frail}) must not
     * overwrite each other. Skill-tree tier swaps reuse the same power id, so
     * the key is stable across tier changes (addOrUpdate overwrites in place);
     * any stale key from removed powers is swept by RaceManager's namespace
     * wipe on race change / respawn / reload.
     */
    private Identifier modifierKey() {
        return Identifier.fromNamespaceAndPath("raceapi", "attr/" + id.getNamespace() + "." + id.getPath());
    }

    @Override
    public void onAttach(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(modifierKey(), amount, operation));
        }
    }

    @Override
    public void onRemove(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierKey());
        }
    }
}
