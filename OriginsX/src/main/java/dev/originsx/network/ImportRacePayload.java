package dev.originsx.network;

import com.google.gson.JsonObject;
import dev.originsx.share.RaceShare;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.PacketFlow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Serverbound race import: an operator pastes a share string, the SERVER
 * validates and writes it into the world's {@code imported_races} datapack.
 * Clients can never write server files — this payload is the only sanctioned
 * path, gated behind the gamemasters permission.
 */
public record ImportRacePayload(String share) implements CustomPacketPayload {

    private static final int MAX_SHARE_LENGTH = 32768;

    public static final Type<ImportRacePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("originsx", "import_race"));

    public static final StreamCodec<FriendlyByteBuf, ImportRacePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(MAX_SHARE_LENGTH),
                    ImportRacePayload::share, ImportRacePayload::new);

    public static void handle(ImportRacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow() != PacketFlow.SERVERBOUND
                    || !(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.permissions().hasPermission(
                    net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
                player.sendSystemMessage(Component.translatable("originsx.share.no_permission")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            try {
                JsonObject raceJson = RaceShare.decode(payload.share());
                Identifier id = saveImportedRace(dev.raceapi.util.RaceUtils.serverLevel(player).getServer(), raceJson);
                player.sendSystemMessage(Component.translatable("originsx.share.done", id.toString())
                        .withStyle(ChatFormatting.GREEN));
                var server = dev.raceapi.util.RaceUtils.serverLevel(player).getServer();
                server.execute(() -> server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), "reload"));
            } catch (IllegalArgumentException bad) {
                player.sendSystemMessage(Component.translatable(bad.getMessage()).withStyle(ChatFormatting.RED));
            } catch (IOException io) {
                player.sendSystemMessage(Component.translatable("originsx.share.failed_io")
                        .withStyle(ChatFormatting.RED));
            }
        });
    }

    private static Identifier saveImportedRace(MinecraftServer server, JsonObject raceJson) throws IOException {
        Path packDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
                .resolve("imported_races");
        Path racesDir = packDir.resolve("data").resolve("imported")
                .resolve("raceapi").resolve("races");
        String canonical = raceJson.toString();
        if (Files.exists(racesDir)) {
            try (Stream<Path> stream = Files.walk(racesDir)) {
                var existing = stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .filter(path -> {
                            try {
                                return Files.readString(path, StandardCharsets.UTF_8).equals(canonical);
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .findFirst();
                if (existing.isPresent()) {
                    String fileName = existing.get().getFileName().toString();
                    Identifier dup = Identifier.tryParse("imported:"
                            + fileName.substring(0, fileName.length() - ".json".length()));
                    if (dup != null) {
                        return dup;
                    }
                }
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("imported",
                "imported_race_" + Long.toHexString(System.currentTimeMillis())
                        + "_" + Integer.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextInt(0x10000)));
        Path raceFile = racesDir.resolve(id.getPath() + ".json");
        Files.createDirectories(raceFile.getParent());
        Files.writeString(raceFile, canonical, StandardCharsets.UTF_8);
        Path meta = packDir.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, "{\n  \"pack\": {\n    \"pack_format\": 90,\n"
                    + "    \"min_format\": 82,\n    \"max_format\": 999,\n"
                    + "    \"description\": \"Imported races\"\n  }\n}\n", StandardCharsets.UTF_8);
        }
        return id;
    }

    @Override
    public Type<ImportRacePayload> type() {
        return TYPE;
    }
}
