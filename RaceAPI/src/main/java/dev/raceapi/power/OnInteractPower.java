package dev.raceapi.power;

import dev.raceapi.race.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Passive power that triggers an action when the player right-clicks a block or
 * entity. Highly configurable: trigger mode, target filters, item requirements,
 * cooldown, and multiple action types.
 */
public class OnInteractPower implements Power {

    private final Identifier id;
    private final int difficulty;

    // trigger
    private final String triggerMode;
    private final Identifier triggerItem;
    private final Identifier triggerEntity;
    private final Identifier triggerBlock;

    // action
    private final String action;
    private final Identifier effectId;
    private final int effectDuration;
    private final int effectAmplifier;
    private final float healAmount;
    private final float damageAmount;
    private final Identifier summonEntity;
    private final int summonCount;

    // global
    private final int cooldownTicks;
    private final Map<PlayerKey, Long> cooldownMap = new HashMap<>();

    public OnInteractPower(Identifier id, int difficulty,
                           String triggerMode, Identifier triggerItem, Identifier triggerEntity, Identifier triggerBlock,
                           String action, Identifier effectId, int effectDuration, int effectAmplifier,
                           float healAmount, float damageAmount,
                           Identifier summonEntity, int summonCount,
                           int cooldownTicks) {
        this.id = id;
        this.difficulty = difficulty;
        this.triggerMode = triggerMode;
        this.triggerItem = triggerItem;
        this.triggerEntity = triggerEntity;
        this.triggerBlock = triggerBlock;
        this.action = action;
        this.effectId = effectId;
        this.effectDuration = effectDuration;
        this.effectAmplifier = effectAmplifier;
        this.healAmount = healAmount;
        this.damageAmount = damageAmount;
        this.summonEntity = summonEntity;
        this.summonCount = summonCount;
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("power.raceapi.on_interact.name");
    }

    @Override
    public Component getDescription() {
        return Component.translatable("power.raceapi.on_interact.desc");
    }

    @Override
    public int getDifficulty() {
        return difficulty;
    }

    @Override
    public void onInteractBlock(ServerPlayer player, InteractionHand hand, BlockPos pos, BlockHitResult hitResult) {
        if (!matchesTrigger(player, hand, false, null)) {
            return;
        }
        if (triggerMode.equals("specific_block") && !matchesBlock(player, pos)) {
            return;
        }
        if (!checkCooldown(player)) {
            return;
        }
        if (performAction(player, null, pos)) {
            markCooldown(player);
        }
    }

    @Override
    public void onInteractEntity(ServerPlayer player, InteractionHand hand, Entity target) {
        if (!matchesTrigger(player, hand, true, target)) {
            return;
        }
        if (!checkCooldown(player)) {
            return;
        }
        if (performAction(player, target, null)) {
            markCooldown(player);
        }
    }

    private boolean matchesTrigger(ServerPlayer player, InteractionHand hand, boolean isEntity, Entity target) {
        return switch (triggerMode) {
            case "any" -> true;
            case "block" -> !isEntity;
            case "entity" -> isEntity;
            case "block_with_item" -> !isEntity && matchesItem(player, hand);
            case "entity_with_item" -> isEntity && matchesItem(player, hand);
            case "specific_entity" -> isEntity && matchesEntityType(target);
            case "specific_block" -> !isEntity;
            default -> true;
        };
    }

    private boolean matchesItem(ServerPlayer player, InteractionHand hand) {
        if (triggerItem == null) {
            return true;
        }
        ItemStack stack = hand == InteractionHand.MAIN_HAND
                ? player.getMainHandItem()
                : player.getOffhandItem();
        if (stack.isEmpty()) {
            return false;
        }
        var holder = BuiltInRegistries.ITEM.get(triggerItem);
        return holder.isPresent() && stack.is(holder.get().value());
    }

    private boolean matchesEntityType(Entity target) {
        if (triggerEntity == null || target == null) {
            return false;
        }
        Identifier targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        return triggerEntity.equals(targetId);
    }

    private boolean matchesBlock(ServerPlayer player, BlockPos pos) {
        if (triggerBlock == null || pos == null) {
            return false;
        }
        net.minecraft.world.level.Level level = player.level();
        if (level == null) {
            return true;
        }
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return triggerBlock.equals(blockId);
    }

    @Override
    public void onRemove(ServerPlayer player) {
        cooldownMap.remove(new PlayerKey(player.getUUID()));
    }

    private boolean checkCooldown(ServerPlayer player) {
        if (cooldownTicks <= 0) {
            return true;
        }
        long now = player.level().getGameTime();
        PlayerKey key = new PlayerKey(player.getUUID());
        Long lastUse = cooldownMap.get(key);
        return lastUse == null || now - lastUse >= cooldownTicks;
    }

    private void markCooldown(ServerPlayer player) {
        if (cooldownTicks <= 0) {
            return;
        }
        cooldownMap.put(new PlayerKey(player.getUUID()), player.level().getGameTime());
    }

    private boolean performAction(ServerPlayer player, Entity target, BlockPos blockPos) {
        switch (action) {
            case "effect" -> {
                return applyEffect(player);
            }
            case "heal" -> {
                if (healAmount > 0) {
                    player.heal(healAmount);
                    return true;
                }
                return false;
            }
            case "damage" -> {
                if (damageAmount > 0) {
                    player.hurt(player.damageSources().magic(), damageAmount);
                    return true;
                }
                return false;
            }
            case "damage_target" -> {
                if (damageAmount > 0 && target instanceof LivingEntity living) {
                    living.hurt(living.damageSources().magic(), damageAmount);
                    return true;
                }
                return false;
            }
            case "heal_target" -> {
                if (healAmount > 0 && target instanceof LivingEntity living) {
                    living.heal(healAmount);
                    return true;
                }
                return false;
            }
            case "summon" -> {
                return performSummon(player);
            }
            case "teleport" -> {
                return performTeleport(player, target, blockPos);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean applyEffect(ServerPlayer player) {
        if (effectId != null) {
            var holder = BuiltInRegistries.MOB_EFFECT.get(effectId);
            if (holder.isPresent()) {
                player.addEffect(new MobEffectInstance(
                        holder.get(), effectDuration * 20, effectAmplifier));
                return true;
            }
        }
        return false;
    }

    private boolean performSummon(ServerPlayer player) {
        if (summonEntity == null || player.level() == null) {
            return false;
        }
        var holder = BuiltInRegistries.ENTITY_TYPE.get(summonEntity);
        if (holder.isEmpty()) {
            return false;
        }
        EntityType<?> entityType = holder.get().value();
        for (int i = 0; i < summonCount; i++) {
            double x = player.getX() + (player.getRandom().nextDouble() - 0.5) * 4.0;
            double z = player.getZ() + (player.getRandom().nextDouble() - 0.5) * 4.0;
            BlockPos spawnPos = new BlockPos((int) Math.floor(x), (int) player.getY(), (int) Math.floor(z));
            entityType.spawn((net.minecraft.server.level.ServerLevel) player.level(),
                    spawnPos, EntitySpawnReason.MOB_SUMMONED);
        }
        return true;
    }

    private boolean performTeleport(ServerPlayer player, Entity target, BlockPos blockPos) {
        if (blockPos != null) {
            player.teleportTo(blockPos.getX() + 0.5, blockPos.getY() + 1.0, blockPos.getZ() + 0.5);
            return true;
        }
        if (target != null) {
            player.teleportTo(target.getX(), target.getY(), target.getZ());
            return true;
        }
        return false;
    }

    private record PlayerKey(java.util.UUID uuid) {
    }
}
