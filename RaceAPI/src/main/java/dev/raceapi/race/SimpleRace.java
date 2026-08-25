package dev.raceapi.race;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Mutable, chainable {@link Race} implementation for quick Java registration:
 * <pre>{@code
 * SimpleRace race = new SimpleRace(Identifier.fromNamespaceAndPath("mymod", "avian"))
 *         .displayName(Component.literal("Avian"))
 *         .description(Component.literal("Born to fly."))
 *         .powers(new AvianFlight(), new HeightWeakness())
 *         .difficulty(2);
 * RaceRegistry.register(race);
 * }</pre>
 */
public final class SimpleRace implements Race {

    private final Identifier id;
    private Component displayName;
    private Component description;
    private List<Power> powers = List.of();
    private ItemStack icon = ItemStack.EMPTY;
    private Identifier iconId;
    private int difficulty;
    private float scale = 1.0f;
    private double width = 0.6;
    private double height = 1.8;
    private boolean hidden;
    private Consumer<ServerPlayer> onSelect;
    private Consumer<ServerPlayer> onRemove;

    public SimpleRace(Identifier id) {
        this.id = id;
        this.displayName = Component.literal(id.getPath());
        this.description = Component.empty();
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return displayName;
    }

    public SimpleRace displayName(Component displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public Component getDescription() {
        return description;
    }

    public SimpleRace description(Component description) {
        this.description = description;
        return this;
    }

    @Override
    public List<Power> getPowers() {
        return powers;
    }

    public SimpleRace powers(List<Power> powers) {
        this.powers = List.copyOf(powers);
        return this;
    }

    public SimpleRace powers(Power... powers) {
        this.powers = List.of(powers);
        return this;
    }

    @Override
    public ItemStack getIcon() {
        if (!icon.isEmpty()) return icon;
        if (iconId != null) {
            icon = BuiltInRegistries.ITEM.get(iconId)
                    .map(ItemStack::new)
                    .orElse(ItemStack.EMPTY);
        }
        return icon;
    }

    public SimpleRace icon(ItemStack icon) {
        this.icon = icon;
        return this;
    }

    public SimpleRace iconId(Identifier iconId) {
        this.iconId = iconId;
        return this;
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    public SimpleRace difficulty(int difficulty) {
        this.difficulty = difficulty;
        return this;
    }

    @Override
    public float getScale() {
        return scale;
    }

    public SimpleRace scale(float scale) {
        this.scale = scale;
        return this;
    }

    @Override
    public double getWidth() {
        return width;
    }

    public SimpleRace width(double width) {
        this.width = width;
        return this;
    }

    @Override
    public double getHeight() {
        return height;
    }

    public SimpleRace height(double height) {
        this.height = height;
        return this;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    public SimpleRace hidden(boolean hidden) {
        this.hidden = hidden;
        return this;
    }

    @Override
    public void onSelect(ServerPlayer player) {
        if (onSelect != null) {
            onSelect.accept(player);
        }
    }

    public SimpleRace onSelect(Consumer<ServerPlayer> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    @Override
    public void onRemove(ServerPlayer player) {
        if (onRemove != null) {
            onRemove.accept(player);
        }
    }

    public SimpleRace onRemove(Consumer<ServerPlayer> onRemove) {
        this.onRemove = onRemove;
        return this;
    }
}
