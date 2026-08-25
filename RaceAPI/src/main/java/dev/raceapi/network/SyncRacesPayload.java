package dev.raceapi.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.raceapi.data.RaceDataLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Server -&gt; client sync of ALL registered race definitions.
 * <p>
 * Races are loaded server-side from datapacks; on a dedicated server the
 * client's registry would otherwise stay empty and the selection screen would
 * show nothing. The payload carries a gzip+base64 JSON array of
 * {@code [{id, ...raceJson}]}; the client re-parses it into its own registry.
 * Sent on login and after every datapack reload.
 */
public record SyncRacesPayload(String data) implements CustomPacketPayload {

    private static final int MAX_DATA_LENGTH = 900_000;

    public static final Type<SyncRacesPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("raceapi", "sync_races"));

    public static final StreamCodec<FriendlyByteBuf, SyncRacesPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SyncRacesPayload::data, SyncRacesPayload::new);

    /** Sends the currently loaded race definitions to one player. */
    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, build());
    }

    /** Sends the currently loaded race definitions to every connected player. */
    public static void broadcast() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        var payload = build();
        for (var player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static SyncRacesPayload build() {
        try {
            JsonArray array = new JsonArray();
            for (var entry : RaceDataLoader.lastLoaded().entrySet()) {
                JsonObject wrapper = new JsonObject();
                wrapper.addProperty("id", entry.getKey().toString());
                wrapper.add("race", entry.getValue());
                array.add(wrapper);
            }
            String data = compress(array.toString());
            if (data.length() > MAX_DATA_LENGTH) {
                dev.raceapi.data.ParseErrors.error(
                        "Race sync payload too large (" + data.length() + " chars) — too many/too big races");
                return new SyncRacesPayload("");
            }
            return new SyncRacesPayload(data);
        } catch (IOException e) {
            return new SyncRacesPayload("");
        }
    }

    public static void handle(SyncRacesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.data().isEmpty()) {
                return;
            }
            try {
                Map<Identifier, JsonObject> races = new HashMap<>();
                JsonArray array = JsonParser.parseString(decompress(payload.data())).getAsJsonArray();
                for (var element : array) {
                    JsonObject wrapper = element.getAsJsonObject();
                    Identifier id = Identifier.tryParse(wrapper.get("id").getAsString());
                    if (id != null) {
                        races.put(id, wrapper.getAsJsonObject("race"));
                    }
                }
                RaceDataLoader.applyClientRaces(races);
            } catch (Exception e) {
                // a broken sync must never crash the client
            }
        });
    }

    private static String compress(String json) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static String decompress(String data) throws IOException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        try (GZIPInputStream gzip = new GZIPInputStream(bytes)) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public Type<SyncRacesPayload> type() {
        return TYPE;
    }
}
