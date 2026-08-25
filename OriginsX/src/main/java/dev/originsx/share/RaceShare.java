package dev.originsx.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Encodes/decodes a race definition into a compact shareable string
 * ({@code ORX1:<base64url(gzip(json))>}) that players can paste to each other.
 * <p>
 * The encoded payload is plain data — importing never touches the filesystem
 * beyond writing the race JSON through the same path the creator uses, and
 * {@link #decode} validates the structure before anything is saved.
 */
public final class RaceShare {

    public static final String PREFIX = "ORX1:";
    private static final int MAX_ENCODED_LENGTH = 32768;
    private static final int MAX_JSON_LENGTH = 65536;
    private static final int MAX_POWERS = 40;

    private RaceShare() {
    }

    /** Encodes a race JSON into a share string. */
    public static String encode(JsonObject root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    /**
     * Decodes and validates a share string.
     *
     * @throws IllegalArgumentException with a {@code originsx.share.*} lang key
     *                                  as the message when the string is not a
     *                                  valid share payload
     */
    public static JsonObject decode(String input) {
        if (input == null) {
            throw new IllegalArgumentException("originsx.share.bad_format");
        }
        String trimmed = input.trim();
        if (!trimmed.startsWith(PREFIX)) {
            throw new IllegalArgumentException("originsx.share.bad_format");
        }
        if (trimmed.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("originsx.share.too_big");
        }
        byte[] compressed;
        try {
            compressed = java.util.Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("originsx.share.bad_format");
        }
        if (compressed.length == 0 || compressed.length > MAX_JSON_LENGTH) {
            throw new IllegalArgumentException("originsx.share.too_big");
        }
        ByteArrayOutputStream jsonBytes = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                jsonBytes.write(buffer, 0, read);
                if (jsonBytes.size() > MAX_JSON_LENGTH) {
                    throw new IllegalArgumentException("originsx.share.too_big");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("originsx.share.bad_format");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(jsonBytes.toString(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("originsx.share.bad_format");
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("originsx.share.bad_content");
        }
        JsonObject root = parsed.getAsJsonObject();
        validate(root);
        return root;
    }

    /** Structural validation so a broken share never reaches the datapack. */
    private static void validate(JsonObject root) {
        if (!hasString(root, "display_name") || root.get("display_name").getAsString().isBlank()) {
            throw new IllegalArgumentException("originsx.share.bad_content");
        }
        for (String field : new String[]{"description", "icon", "name_key", "description_key"}) {
            if (root.has(field) && !hasString(root, field)) {
                throw new IllegalArgumentException("originsx.share.bad_content");
            }
        }
        if (root.has("difficulty") && !isIntInRange(root.get("difficulty"), -5, 5)) {
            throw new IllegalArgumentException("originsx.share.bad_content");
        }
        if (root.has("scale") && (!isNumber(root.get("scale"))
                || root.get("scale").getAsDouble() < 0.05
                || root.get("scale").getAsDouble() > 10.0)) {
            throw new IllegalArgumentException("originsx.share.bad_content");
        }
        if (!root.has("powers") || !root.get("powers").isJsonArray()) {
            throw new IllegalArgumentException("originsx.share.bad_content");
        }
        JsonArray powers = root.getAsJsonArray("powers");
        if (powers.size() > MAX_POWERS) {
            throw new IllegalArgumentException("originsx.share.bad_content");
        }
        for (JsonElement power : powers) {
            if (power.isJsonPrimitive()) {
                // reference to a built-in power id
                continue;
            }
            if (!power.isJsonObject() || !hasString(power.getAsJsonObject(), "type")) {
                throw new IllegalArgumentException("originsx.share.bad_content");
            }
        }
    }

    private static boolean hasString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isString();
    }

    private static boolean isNumber(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    private static boolean isIntInRange(JsonElement element, int min, int max) {
        return isNumber(element) && element.getAsInt() >= min && element.getAsInt() <= max;
    }
}
