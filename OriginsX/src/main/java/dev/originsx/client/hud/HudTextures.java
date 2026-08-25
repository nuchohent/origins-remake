package dev.originsx.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads player-provided PNG files as GUI textures. The config stores a plain
 * file path (absolute, relative to the game dir, or relative to
 * {@code config/originsx/}); the image is uploaded once as a
 * {@link DynamicTexture} and re-uploaded automatically when its mtime changes,
 * so editing the PNG shows up without a restart.
 */
public final class HudTextures {

    /** A loaded custom texture: registry id plus source image dimensions. */
    public record Entry(Identifier id, long mtime, int width, int height) {
    }

    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private HudTextures() {
    }

    /** Resolves the configured path against the usual locations. */
    @Nullable
    public static Path resolve(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path direct = Path.of(configured);
        if (Files.exists(direct)) {
            return direct;
        }
        Path inGameDir = FMLPaths.GAMEDIR.get().resolve(configured);
        if (Files.exists(inGameDir)) {
            return inGameDir;
        }
        Path inConfig = FMLPaths.CONFIGDIR.get().resolve("originsx").resolve(configured);
        return Files.exists(inConfig) ? inConfig : null;
    }

    /** The registered texture for a resolved file, or null when unavailable. */
    @Nullable
    public static synchronized Entry get(@Nullable Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        String key = file.toString();
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            Entry cached = CACHE.get(key);
            if (cached != null && cached.mtime == mtime) {
                return cached;
            }
            NativeImage image;
            try (InputStream stream = Files.newInputStream(file)) {
                image = NativeImage.read(stream);
            }
            Identifier id = Identifier.fromNamespaceAndPath("originsx",
                    "hud_custom_" + COUNTER.incrementAndGet());
            var textureManager = Minecraft.getInstance().getTextureManager();
            Entry previous = CACHE.get(key);
            if (previous != null) {
                // the file changed: release the old GPU texture before
                // registering the replacement, otherwise every edit leaks
                textureManager.release(previous.id());
            }
            textureManager.register(id,
                    new DynamicTexture(() -> "OriginsX HUD: " + key, image));
            Entry entry = new Entry(id, mtime, image.getWidth(), image.getHeight());
            CACHE.put(key, entry);
            return entry;
        } catch (IOException e) {
            CACHE.remove(key);
            return null;
        }
    }

    /** Drops cached entries so changed configs reload from disk. */
    public static synchronized void invalidate() {
        var textureManager = Minecraft.getInstance().getTextureManager();
        CACHE.values().forEach(entry -> textureManager.release(entry.id()));
        CACHE.clear();
    }
}
