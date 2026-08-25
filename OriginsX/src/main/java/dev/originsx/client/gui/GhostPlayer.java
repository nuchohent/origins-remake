package dev.originsx.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A detached player used only for rendering. It is never added to the world,
 * so it never ticks: all animation state (walk animation, bob, rotation
 * interpolation) stays frozen, which makes the race preview perfectly stable
 * even while the real world keeps running behind the GUI.
 */
@OnlyIn(Dist.CLIENT)
public class GhostPlayer extends AbstractClientPlayer {

    private final PlayerSkin skin;

    public GhostPlayer(ClientLevel level, GameProfile profile, PlayerSkin skin) {
        super(level, profile);
        this.skin = skin;
        if (getAttribute(Attributes.MAX_HEALTH) != null) {
            setHealth(getMaxHealth());
        }
    }

    @Override
    public PlayerSkin getSkin() {
        return skin;
    }

    /**
     * A ghost is never ticked, so its player-model customisation entity data stays
     * at the default (empty) value. That makes {@link PlayerRenderer#setModelProperties}
     * disable the second skin layer (jacket, sleeves, pants, hat) in the preview.
     * We always report the parts as shown so the full skin renders.
     */
    @Override
    public boolean isModelPartShown(PlayerModelPart part) {
        return true;
    }

    /** Builds a ghost from the current local player so the preview matches the player's look. */
    public static GhostPlayer ofLocal(ClientLevel level) {
        Player local = Minecraft.getInstance().player;
        if (local instanceof AbstractClientPlayer abstractClientPlayer) {
            return new GhostPlayer(level, local.getGameProfile(), abstractClientPlayer.getSkin());
        }
        return new GhostPlayer(level, local.getGameProfile(),
                net.minecraft.client.resources.DefaultPlayerSkin.get(local.getGameProfile().id()));
    }
}
