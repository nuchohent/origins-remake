package dev.originsx.skilltree.net;

import dev.originsx.skilltree.SkillTreeMod;
import dev.originsx.skilltree.progress.ProgressData;
import dev.originsx.skilltree.tree.SkillTree;
import dev.originsx.skilltree.tree.TreeManager;
import dev.raceapi.player.RaceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server -> client snapshot of the player's skill tree state: the raw tree
 * JSON of the player's race (empty strings when there is no race or no tree)
 * plus the player's unlocked tiers.
 */
public record SyncSkillStatePayload(String raceId, String treeJson, String tiersData) implements CustomPacketPayload {

    private static final int MAX_TREE_JSON_LENGTH = 800_000;

    public static final Type<SyncSkillStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SkillTreeMod.MOD_ID, "sync_state"));

    public static final StreamCodec<FriendlyByteBuf, SyncSkillStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SyncSkillStatePayload::raceId,
                    ByteBufCodecs.STRING_UTF8, SyncSkillStatePayload::treeJson,
                    ByteBufCodecs.STRING_UTF8, SyncSkillStatePayload::tiersData,
                    SyncSkillStatePayload::new);

    public static void send(ServerPlayer player) {
        var race = RaceManager.getRace(player);
        SkillTree tree = race == null ? null : TreeManager.treeFor(race.getId());
        String raceId = race == null ? "" : race.getId().toString();
        String treeJson = tree == null ? "" : tree.raw();
        if (treeJson.length() > MAX_TREE_JSON_LENGTH) {
            dev.raceapi.data.ParseErrors.error(
                    "Skill tree state for " + raceId + " too large to sync (" + treeJson.length()
                            + " chars) — sending an empty tree");
            treeJson = "";
        }
        StringBuilder tiers = new StringBuilder();
        if (tree != null) {
            var progress = ProgressData.get(player.level()).tiersFor(player.getUUID());
            // send tiers keyed by bare node id — that is what the client looks up
            for (var node : tree.nodes()) {
                Integer value = progress.get(TreeManager.nodeKey(tree, node));
                if (value != null) {
                    tiers.append(node.id()).append('=').append(value).append('\n');
                }
            }
        }
        PacketDistributor.sendToPlayer(player,
                new SyncSkillStatePayload(raceId, treeJson, tiers.toString()));
    }

    public static void handle(SyncSkillStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                dev.originsx.skilltree.client.SkillTreeClientState.apply(payload));
    }

    @Override
    public Type<SyncSkillStatePayload> type() {
        return TYPE;
    }
}