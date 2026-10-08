package dev.jiracipika.smallhours.entity;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.Tuning;
import dev.jiracipika.smallhours.director.NightDirector;
import dev.jiracipika.smallhours.dread.Dread;

public class PatientEntity extends Monster {
    private UUID targetUUID;
    private boolean observed;
    private int unobservedTicks;
    private boolean screamPlayed;
    private boolean dawnHandled;
    private boolean stealUsed;
    private int strikeCooldown;

    public PatientEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = Tuning.PATIENT_XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, Tuning.PATIENT_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, Tuning.PATIENT_MOVE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, Tuning.PATIENT_ATTACK_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, Tuning.PATIENT_FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    public void setTargetPlayer(UUID uuid) {
        this.targetUUID = uuid;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.strikeCooldown > 0) {
            this.strikeCooldown--;
        }
        if (this.level().isDay()) {
            if (!this.dawnHandled) {
                this.dawnHandled = true;
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SmallHours.SND_PATIENT_SCREAM,
                        SoundSource.HOSTILE, Tuning.PATIENT_SCREAM_VOLUME, 1.0F);
                ServerPlayer target = playerByUUID(level, this.targetUUID);
                if (target != null) {
                    Dread.setThreat(target, false);
                }
                this.discard();
            }
            return;
        }

        ServerPlayer target = resolveTarget(level);
        if (target == null) {
            this.getNavigation().stop();
            return;
        }

        if (this.tickCount % Tuning.PATIENT_OBSERVE_CHECK_TICKS == 0) {
            this.observed = computeObserved(level);
        }

        boolean marked = Dread.isMarked(target);
        float speedMod = this.observed
                ? (marked ? Tuning.PATIENT_CREEP_SPEED_MARKED : Tuning.PATIENT_CREEP_SPEED)
                : (marked ? Tuning.PATIENT_HUNT_SPEED_MARKED : Tuning.PATIENT_HUNT_SPEED);
        this.getNavigation().setSpeedModifier(speedMod);
        if (this.tickCount % Tuning.PATIENT_REPATH_TICKS == 0) {
            this.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), speedMod);
        }

        if (this.observed) {
            this.unobservedTicks = 0;
            faceToward(target);
        } else {
            this.unobservedTicks++;
            if (this.tickCount % Tuning.PATIENT_FOOTSTEP_TICKS == 0) {
                this.playSound(this.level().getBlockState(this.blockPosition().below()).getSoundType().getStepSound(),
                        Tuning.PATIENT_FOOTSTEP_VOLUME, Tuning.PATIENT_FOOTSTEP_PITCH);
            }
        }

        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= Tuning.PATIENT_STRIKE_DISTANCE && this.strikeCooldown == 0) {
            strike(target);
            this.strikeCooldown = Tuning.PATIENT_STRIKE_COOLDOWN_TICKS;
        }

        if (this.distanceToSqr(target) <= Tuning.PATIENT_PROXIMITY_RANGE * Tuning.PATIENT_PROXIMITY_RANGE
                && this.tickCount % Tuning.PATIENT_PROXIMITY_INTERVAL_TICKS == 0) {
            Dread.add(target, "patient_proximity", Tuning.PATIENT_PROXIMITY);
        }

        if (this.unobservedTicks >= Tuning.PATIENT_REGEN_UNOBSERVED_TICKS
                && this.tickCount % Tuning.PATIENT_REGEN_INTERVAL_TICKS == 0) {
            this.heal(Tuning.PATIENT_REGEN_AMOUNT);
        }
    }

    private ServerPlayer resolveTarget(ServerLevel level) {
        ServerPlayer current = playerByUUID(level, this.targetUUID);
        boolean invalid = current == null || !NightDirector.eligible(current)
                || this.distanceToSqr(current) > Tuning.OBSERVE_RANGE * Tuning.OBSERVE_RANGE;
        if (invalid || this.tickCount % Tuning.PATIENT_RETARGET_TICKS == 0) {
            ServerPlayer best = null;
            int bestDread = -1;
            for (ServerPlayer p : level.players()) {
                if (!NightDirector.eligible(p)) {
                    continue;
                }
                if (this.distanceToSqr(p) > Tuning.OBSERVE_RANGE * Tuning.OBSERVE_RANGE) {
                    continue;
                }
                int d = Dread.get(p);
                if (d > bestDread) {
                    bestDread = d;
                    best = p;
                }
            }
            if (best != null) {
                this.targetUUID = best.getUUID();
                current = best;
            } else {
                current = null;
            }
        }
        if (current != null && !this.screamPlayed) {
            this.screamPlayed = true;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SmallHours.SND_PATIENT_SCREAM,
                    SoundSource.HOSTILE, Tuning.PATIENT_SCREAM_VOLUME, 1.0F);
            Dread.setThreat(current, true);
        }
        return current;
    }

    private boolean computeObserved(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (!NightDirector.eligible(p)) {
                continue;
            }
            if (this.distanceToSqr(p) > Tuning.OBSERVE_RANGE * Tuning.OBSERVE_RANGE) {
                continue;
            }
            if (!p.hasLineOfSight(this)) {
                continue;
            }
            Vec3 dir = this.getEyePosition().subtract(p.getEyePosition());
            double len = dir.length();
            if (len < 1.0E-4D) {
                return true;
            }
            if (p.getViewVector(1.0F).dot(dir.scale(1.0D / len)) > Tuning.OBSERVE_COS) {
                return true;
            }
        }
        return false;
    }

    private void strike(ServerPlayer target) {
        if (!this.stealUsed && !target.getMainHandItem().isEmpty()) {
            ItemStack stolen = target.getMainHandItem().copy();
            target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.MAINHAND, stolen);
            target.hurt(this.damageSources().mobAttack(this), Tuning.PATIENT_STEAL_DAMAGE);
            Dread.add(target, "patient_steal", Tuning.PATIENT_STEAL);
            this.stealUsed = true;
            this.playSound(SoundEvents.WARDEN_ATTACK_IMPACT,
                    Tuning.PATIENT_STEAL_SOUND_VOLUME, Tuning.PATIENT_STEAL_SOUND_PITCH);
        } else {
            target.hurt(this.damageSources().mobAttack(this), Tuning.PATIENT_STRIKE_DAMAGE);
        }
    }

    private void faceToward(Entity target) {
        float targetYaw = (float) (Mth.atan2(target.getZ() - this.getZ(), target.getX() - this.getX()) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(NightDirector.approachAngle(this.getYRot(), targetYaw, Tuning.ENTITY_TURN_SPEED_DEG));
        this.yBodyRot = NightDirector.approachAngle(this.yBodyRot, targetYaw, Tuning.ENTITY_TURN_SPEED_DEG);
        this.yHeadRot = NightDirector.approachAngle(this.yHeadRot, targetYaw, Tuning.ENTITY_TURN_SPEED_DEG);
    }

    private ServerPlayer playerByUUID(ServerLevel level, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (ServerPlayer p : level.players()) {
            if (p.getUUID().equals(uuid)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        ItemStack held = this.getMainHandItem();
        if (!held.isEmpty()) {
            this.spawnAtLocation(held);
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        if (!this.level().isClientSide) {
            ServerPlayer target = this.level() instanceof ServerLevel level ? playerByUUID(level, this.targetUUID) : null;
            if (target != null) {
                Dread.setThreat(target, false);
            }
            if (source.getEntity() instanceof ServerPlayer killer) {
                Dread.add(killer, "patient_killed", Tuning.PATIENT_KILLED);
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // It leaves only at dawn or on death.
    }
}
