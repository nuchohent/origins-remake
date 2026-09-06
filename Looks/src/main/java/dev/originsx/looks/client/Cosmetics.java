package dev.originsx.looks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.originsx.looks.LooksMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-side model + parser for the {@code "cosmetics"} array stored in a
 * race JSON:
 * <pre>{@code
 * "cosmetics": [
 *   {"item": "minecraft:end_rod", "part": "head",
 *    "pos": [0.0, 0.5, 0.0], "rot": [45.0, 0.0, 0.0], "scale": 1.0}
 * ]
 * }</pre>
 * {@code pos} offsets from the bone anchor in blocks, {@code rot} are XYZ
 * Euler angles in degrees applied X then Y then Z, {@code scale} multiplies
 * the item size.
 */
@OnlyIn(Dist.CLIENT)
public final class Cosmetics {

    public enum Part {
        HEAD("head"),
        BODY("body"),
        LEFT_ARM("left_arm"),
        RIGHT_ARM("right_arm"),
        LEFT_LEG("left_leg"),
        RIGHT_LEG("right_leg"),
        CAPE("cape");

        public final String jsonName;

        Part(String jsonName) {
            this.jsonName = jsonName;
        }

        public static Part byName(String name) {
            String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
            for (Part part : values()) {
                if (part.jsonName.equals(normalized)) {
                    return part;
                }
            }
            return null;
        }

        public String translationKey() {
            return "gui." + LooksMod.MOD_ID + ".part." + jsonName;
        }
    }

    /** Animation configuration for a cosmetic entry. */
    public record AnimConfig(float rotateSpeed, float bobAmplitude, float bobSpeed, float pulseAmplitude) {
        public static final AnimConfig DEFAULT = new AnimConfig(0f, 0f, 0f, 0f);

        public boolean isAnimated() {
            return rotateSpeed != 0f || bobAmplitude != 0f || bobSpeed != 0f || pulseAmplitude != 0f;
        }
    }

    /** One attached cosmetic: item + bone + transform + animation. */
    public record Entry(Part part, ItemStack stack, float[] pos, float[] rot, float scale,
                        AnimConfig anim, int zIndex, int tint, boolean glow) {

        /** Default tint = opaque white (still the ordinary ARGB int -1). */
        public static final int DEFAULT_TINT = 0xFFFFFFFF;

        public Entry {
            if (anim == null) anim = AnimConfig.DEFAULT;
            if (zIndex < Integer.MIN_VALUE) zIndex = 0;
            if (tint == 0) tint = DEFAULT_TINT;
        }

        public Entry with(Part newPart, ItemStack newStack,
                          float[] newPos, float[] newRot, float newScale,
                          AnimConfig newAnim, int newZIndex) {
            return new Entry(newPart, newStack, newPos, newRot, newScale,
                    newAnim, newZIndex, tint, glow);
        }

        /** Legacy constructor without anim/zIndex/tint/glow (backward compat). */
        public Entry(Part part, ItemStack stack, float[] pos, float[] rot, float scale) {
            this(part, stack, pos, rot, scale, AnimConfig.DEFAULT, 0, DEFAULT_TINT, false);
        }

        public Entry withLayer(int zIndex) {
            return new Entry(part, stack, pos, rot, scale, anim, zIndex, tint, glow);
        }

        public Entry withTint(int newTint) {
            return new Entry(part, stack, pos, rot, scale, anim, zIndex, newTint, glow);
        }

        public Entry withGlow(boolean newGlow) {
            return new Entry(part, stack, pos, rot, scale, anim, zIndex, tint, newGlow);
        }
    }

    private Cosmetics() {
    }

    public static JsonArray toJson(List<Entry> entries) {
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            JsonObject json = new JsonObject();
            Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(entry.stack().getItem());
            json.addProperty("item", itemId.toString());
            json.addProperty("part", entry.part().jsonName);
            json.add("pos", floats(entry.pos()));
            json.add("rot", floats(entry.rot()));
            json.addProperty("scale", round2(entry.scale()));
            if (entry.anim().isAnimated()) {
                JsonObject anim = new JsonObject();
                if (entry.anim().rotateSpeed() != 0f) anim.addProperty("rotateSpeed", round2(entry.anim().rotateSpeed()));
                if (entry.anim().bobAmplitude() != 0f) anim.addProperty("bobAmplitude", round2(entry.anim().bobAmplitude()));
                if (entry.anim().bobSpeed() != 0f) anim.addProperty("bobSpeed", round2(entry.anim().bobSpeed()));
                if (entry.anim().pulseAmplitude() != 0f) anim.addProperty("pulseAmplitude", round2(entry.anim().pulseAmplitude()));
                json.add("anim", anim);
            }
            if (entry.zIndex() != 0) {
                json.addProperty("zIndex", entry.zIndex());
            }
            if (entry.tint() != Entry.DEFAULT_TINT) {
                json.addProperty("tint", entry.tint());
            }
            if (entry.glow()) {
                json.addProperty("glow", true);
            }
            array.add(json);
        }
        return array;
    }

    /** Never fails: bad entries are skipped, missing fields fall back to defaults. */
    public static List<Entry> parse(JsonArray array) {
        List<Entry> entries = new ArrayList<>();
        if (array == null) {
            return entries;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            if (!json.has("item") || !json.get("item").isJsonPrimitive()) {
                continue;
            }
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getValue(Identifier.tryParse(json.get("item").getAsString()));
            if (item == null) {
                continue;
            }
            Part part = Part.byName(stringOr(json, "part", "head"));
            if (part == null) {
                continue;
            }
            AnimConfig anim = parseAnim(json);
            int zIndex = json.has("zIndex") ? json.get("zIndex").getAsInt() : 0;
            int tint = json.has("tint") ? json.get("tint").getAsInt() : Entry.DEFAULT_TINT;
            if (tint == 0) tint = Entry.DEFAULT_TINT;
            boolean glow = json.has("glow") && json.get("glow").getAsBoolean();
            entries.add(new Entry(
                    part,
                    new ItemStack(item),
                    vecOr(json, "pos", new float[]{0f, 0f, 0f}),
                    vecOr(json, "rot", new float[]{0f, 0f, 0f}),
                    floatOr(json, "scale", 1.0f),
                    anim,
                    zIndex,
                    tint,
                    glow));
        }
        return entries;
    }

    private static AnimConfig parseAnim(JsonObject json) {
        if (!json.has("anim") || !json.get("anim").isJsonObject()) {
            return AnimConfig.DEFAULT;
        }
        JsonObject anim = json.getAsJsonObject("anim");
        float rotateSpeed = floatOr(anim, "rotateSpeed", 0f);
        float bobAmplitude = floatOr(anim, "bobAmplitude", 0f);
        float bobSpeed = floatOr(anim, "bobSpeed", 0f);
        float pulseAmplitude = floatOr(anim, "pulseAmplitude", 0f);
        return new AnimConfig(rotateSpeed, bobAmplitude, bobSpeed, pulseAmplitude);
    }

    private static JsonArray floats(float[] values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(round2(value));
        }
        return array;
    }

    private static float round2(float value) {
        return Math.round(value * 100f) / 100f;
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsString() : fallback;
    }

    private static float floatOr(JsonObject json, String key, float fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsFloat() : fallback;
    }

    private static float[] vecOr(JsonObject json, String key, float[] fallback) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return fallback;
        }
        JsonArray array = json.getAsJsonArray(key);
        float[] out = new float[]{fallback[0], fallback[1], fallback[2]};
        for (int i = 0; i < 3 && i < array.size(); i++) {
            out[i] = array.get(i).isJsonPrimitive() ? array.get(i).getAsFloat() : fallback[i];
        }
        return out;
    }
}