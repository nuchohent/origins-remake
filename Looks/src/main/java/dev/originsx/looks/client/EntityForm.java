package dev.originsx.looks.client;

import dev.originsx.looks.LooksMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side full transformation: when the locally selected race is bound to
 * an entity model ({@code model_entity} in the race JSON), the local player's
 * body is hidden and a detached "ghost" entity of that type is rendered at the
 * player's position, following its movement and rotation.
 * <p>
 * Client-only for now: this player sees themselves as the mob; other players on
 * the server still see a normal player (server-side sync is a later step).
 */
@EventBusSubscriber(modid = LooksMod.MOD_ID, value = Dist.CLIENT)
public final class EntityForm {

    /**
     * Marker placed on the local player's avatar render state by the state
     * modifier when transformed; the {@code RenderPlayerEvent.Pre} listener
     * reads it and cancels the body render.
     */
    public static final ContextKey<Boolean> HIDE_BODY = new ContextKey<>(LooksMod.id("hide_body"));

    /** Safety: entity id base so the ghost never collides with real entity ids. */
    private static final java.util.concurrent.atomic.AtomicInteger ENTITY_ID_COUNTER =
            new java.util.concurrent.atomic.AtomicInteger(9_000_000);

    @Nullable
    private static LivingEntity ghost;

    /** Entity type of the form the local player currently is, or null. */
    @Nullable
    private static EntityType<?> currentType;

    /** Visual height (blocks) of the current form; used to place cosmetics on the mob. */
    private static float currentHeight;

    /**
     * Natural eye height (blocks) of the current form's head. Cached separately
     * from {@link #ghost} because first person discards the ghost (so its body
     * doesn't block the tiny view) yet the camera still needs this value.
     */
    private static float currentEyeHeight;

    /** Entity types we have already attached the crown layer to. */
    private static final java.util.Set<EntityType<?>> crownedTypes =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private EntityForm() {
    }

    public static void init(IEventBus modBus) {
        // sync in Post (after the mob's own tick) so our final position/rotation
        // win before render; also in Pre so the mob's animation logic sees the
        // player's movement and picks the right walk/swim animation state
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EntityForm::onClientTickPre);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EntityForm::onClientTickPost);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EntityForm::onRenderPlayerPre);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EntityForm::onRenderHand);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(EntityForm::onLoggingOut);
    }

    private static void onClientTickPre(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        update();
    }

    private static void onClientTickPost(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        update();
    }

    /** Whether this player should currently render as an entity form. */
    public static boolean isTransformed(AbstractClientPlayer player) {
        return formOf(player) != null;
    }

    /** Entity type of the form the local player currently is, or null if not transformed. */
    @Nullable
    public static EntityType<?> currentType() {
        return currentType;
    }

    /** Visual height (blocks) of the current form. */
    public static float currentHeight() {
        return currentHeight;
    }

    /** Natural eye height (blocks) of the current form's head; 0 when not transformed. */
    public static float currentEyeHeight() {
        return currentEyeHeight;
    }

    /**
     * The mob's eye height to use as the interaction/aim origin for the given
     * entity while it is the transformed local player, or {@code -1} when this
     * entity should keep its normal eye height. Used by the client mixins so the
     * block-pick ray and the camera both sit at the mob's head.
     */
    public static float formEyeHeightFor(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof AbstractClientPlayer p
                && entity == Minecraft.getInstance().player
                && isTransformed(p)) {
            float e = currentEyeHeight();
            return e > 0.0F ? e : -1.0F;
        }
        return -1.0F;
    }

    /** The entity form the local player should currently be, or null. */
    @Nullable
    private static Identifier formOf(AbstractClientPlayer player) {
        if (player == null || player.level() == null) {
            return null;
        }
        Identifier raceId = dev.raceapi.client.SelectedRaceClient.getOrNull();
        if (raceId == null) {
            return null;
        }
        var race = dev.raceapi.race.RaceRegistry.getOrNull(raceId);
        Identifier form = race == null ? null : LooksClient.modelEntityFor(race);
        if (form == null) {
            return null;
        }
        var holder = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(form);
        return holder.isPresent() ? form : null;
    }

    private static void update() {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || mc.level == null || !(mc.level instanceof ClientLevel level)) {
            return;
        }
        Identifier form = formOf(player);
        if (form == null) {
            discardGhost();
            currentEyeHeight = 0f;
            return;
        }
        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .get(form).get().value();
        // In first person the camera sits at the mob's (tiny) eye height, i.e.
        // inside the model — like vanilla hides the player's own head, we hide
        // the ghost too so it can't block the view (it reappears in third person).
        // We still set the static per-type eye height so the camera adapts even if
        // the ghost was never created (e.g. the player is already in first person).
        if (mc.options.getCameraType().isFirstPerson()) {
            currentEyeHeight = type.getDimensions().eyeHeight();
            discardGhost();
            return;
        }
        if (ghost == null || ghost.isRemoved() || ghost.getType() != type) {
            discardGhost();
            ghost = createGhost(level, type);
            if (ghost == null) {
                return;
            }
            currentType = type;
            currentHeight = ghost.getDimensions(ghost.getPose()).height();
            currentEyeHeight = ghost.getEyeHeight();
            registerCrownLayer(ghost);
        }
        if (ghost != null && !ghost.isRemoved()) {
            syncGhost(player);
        }
    }

    private static void syncGhost(AbstractClientPlayer player) {
        if (ghost == null) {
            return;
        }
        // Exact alignment: place the ghost at the player's feet, matching both the
        // current and the previous frame so the render interpolation is zero.
        ghost.setPos(player.getX(), player.getY(), player.getZ());
        // CRITICAL: Do NOT copy xo/yo/zo from player! If we do, ghost's own
        // calculateEntityAnimation() will compute distance = length(x-xo, y-yo, z-zo)
        // and overwrite our manually synced walkAnimation. Instead, keep ghost's
        // xo/yo/zo equal to its current position so distance is always 0.
        ghost.xo = ghost.getX();
        ghost.yo = ghost.getY();
        ghost.zo = ghost.getZ();
        // copy visual rotation (body + head) so the mob looks where the player looks
        ghost.setYRot(player.getYRot());
        ghost.yRotO = player.yRotO;
        ghost.setYBodyRot(player.yBodyRot);
        ghost.yBodyRotO = player.yBodyRotO;
        ghost.setYHeadRot(player.yHeadRot);
        ghost.yHeadRotO = player.yHeadRotO;
        ghost.setXRot(player.getXRot());
        ghost.xRotO = player.xRotO;
        // freeze physics but keep the mob's own animation alive
        ghost.setNoGravity(true);
        ghost.setDeltaMovement(0.0, 0.0, 0.0);
        // drive the walk animation from the player's movement so it animates in sync
        // Use Mixin Accessor (Identity mod approach) instead of reflection
        var ghostAnim = (dev.originsx.looks.mixin.WalkAnimationStateAccessor) (Object) ghost.walkAnimation;
        var playerAnim = (dev.originsx.looks.mixin.WalkAnimationStateAccessor) (Object) player.walkAnimation;
        ghostAnim.setSpeedOld(playerAnim.getSpeedOld());
        ghostAnim.setSpeed(playerAnim.getSpeed());
        ghostAnim.setPosition(playerAnim.getPosition());
    }

    @Nullable
    private static LivingEntity createGhost(ClientLevel level, EntityType<?> type) {
        try {
            Entity spawned = type.create(level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, true));
            if (spawned == null) {
                return null;
            }
            spawned.setId(ENTITY_ID_COUNTER.getAndIncrement());
            if (!(spawned instanceof LivingEntity living)) {
                return null;
            }
            // Freeze the ghost completely so it stays a motionless mannequin:
            // - NoAI stops all autonomous client-side movement/animation.
            // - NoGravity keeps it from falling; we drive position every tick.
            living.setNoGravity(true);
            if (living instanceof net.minecraft.world.entity.Mob mob) {
                mob.setNoAi(true);
            }
            level.addEntity(living);
            spawnMorphParticles(level, living.getX(), living.getY(), living.getZ());
            playMorphSound(level, living);
            return living;
        } catch (Exception e) {
            LooksMod.LOGGER.warn("Failed to create entity-form ghost {}", type, e);
            return null;
        }
    }

    private static void discardGhost() {
        if (ghost != null) {
            if (!ghost.isRemoved()) {
                spawnMorphParticles(ghost.level(), ghost.getX(), ghost.getY(), ghost.getZ());
                ghost.discard();
            }
            ghost = null;
        }
        currentType = null;
        currentHeight = 0f;
    }

    private static void spawnMorphParticles(net.minecraft.world.level.Level level, double x, double y, double z) {
        if (!(level instanceof ClientLevel cl)) return;
        var rnd = new java.util.Random();
        for (int i = 0; i < 20; i++) {
            double dx = rnd.nextGaussian() * 0.4;
            double dy = rnd.nextDouble() * 0.6;
            double dz = rnd.nextGaussian() * 0.4;
            cl.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, x, y, z, dx, dy, dz);
        }
    }

    private static void playMorphSound(net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity entity) {
        if (!(level instanceof ClientLevel cl)) return;
        cl.playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_BURP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.0f, false);
    }

    /**
     * Attaches {@link EntityCrownLayer} to the ghost's renderer once per entity
     * type so cosmetics of all slots render on the mob. Restricted to the ghost
     * in the layer itself by position + type matching the local player.
     */
    static void registerCrownLayer(LivingEntity living) {
        try {
            EntityType<?> type = living.getType();
            if (crownedTypes.contains(type)) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.renderer.entity.EntityRenderer<? super net.minecraft.world.entity.Entity, ?>
                    renderer = mc.getEntityRenderDispatcher().getRenderer(living);
            if (!(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> ler)) {
                return;
            }
            @SuppressWarnings("unchecked")
            net.minecraft.client.renderer.entity.LivingEntityRenderer<LivingEntity, LivingEntityRenderState, net.minecraft.client.model.EntityModel<? super LivingEntityRenderState>> lerCast =
                    (net.minecraft.client.renderer.entity.LivingEntityRenderer<LivingEntity, LivingEntityRenderState, net.minecraft.client.model.EntityModel<? super LivingEntityRenderState>>) ler;
            lerCast.addLayer(new EntityCrownLayer<>(lerCast, type));
            crownedTypes.add(type);
            LooksMod.LOGGER.info("[Looks] attached crown layer to renderer of {}", type);
        } catch (Exception e) {
            LooksMod.LOGGER.warn("Failed to attach crown layer to {} renderer", living.getType(), e);
        }
    }

    private static void onRenderPlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre event) {
        if (Boolean.TRUE.equals(event.getRenderState().getRenderData(HIDE_BODY))) {
            event.setCanceled(true);
        }
    }

    /**
     * First person: while morphed, hide the vanilla hand/held items entirely so
     * the player sees nothing of their body in front view (like vanilla hides it).
     */
    private static void onRenderHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player instanceof net.minecraft.client.player.AbstractClientPlayer p
                && isTransformed(p)) {
            event.setCanceled(true);
        }
    }

    private static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        discardGhost();
    }
}
