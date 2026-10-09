package dev.jiracipika.smallhours.entity;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.Tuning;
import dev.jiracipika.smallhours.director.NightDirector;
import dev.jiracipika.smallhours.dread.Dread;

public class PatientEntity extends Monster {
    private enum Phase { LURK, HUNT }

    private static final EntityDataAccessor<Boolean> DATA_EYES_OPEN =
            SynchedEntityData.defineId(PatientEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID targetUUID;
    private boolean observed;
    private int unobservedTicks;
    private boolean dawnHandled;
    private boolean stealUsed;
    private int strikeCooldown;
    private Phase phase = Phase.LURK;
    private int lurkUntil = -1;

    public PatientEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = Tuning.PATIENT_XP;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EYES_OPEN, false);
    }

    /** Everyone can see its eyes are open. Only its marked prey knows why. */
    public boolean isEyesOpen() {
        return this.entityData.get(DATA_EYES_OPEN);
    }

    /** Its kills don't read as combat: the death line is "X stopped running." */
    private DamageSource stoppedRunning() {
        return new DamageSource(this.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SmallHours.STOPPED_RUNNING), this, this);
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
        this.entityData.set(DATA_EYES_OPEN, marked);

        if (this.phase == Phase.LURK) {
            tickLurk(level, target);
            return;
        }

        float speedMod = speedFor(marked);
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
            tryClosingStep(level, target);
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

    /** Observed = creep, unobserved = hunt; marked and past-midnight both escalate. */
    private float speedFor(boolean marked) {
        boolean midnight = this.level().getDayTime() % Tuning.DAY_LENGTH_TICKS >= Tuning.PATIENT_MIDNIGHT_TICK;
        if (this.observed) {
            if (marked) {
                return midnight ? Tuning.PATIENT_CREEP_SPEED_MARKED_RAMP : Tuning.PATIENT_CREEP_SPEED_MARKED;
            }
            return midnight ? Tuning.PATIENT_CREEP_SPEED_RAMP : Tuning.PATIENT_CREEP_SPEED;
        }
        if (marked) {
            return midnight ? Tuning.PATIENT_HUNT_SPEED_MARKED_RAMP : Tuning.PATIENT_HUNT_SPEED_MARKED;
        }
        return midnight ? Tuning.PATIENT_HUNT_SPEED_RAMP : Tuning.PATIENT_HUNT_SPEED;
    }

    /** Silent distance-keeping: it circles at the edge of your awareness and waits. */
    private void tickLurk(ServerLevel level, ServerPlayer target) {
        boolean midnight = level.getDayTime() % Tuning.DAY_LENGTH_TICKS >= Tuning.PATIENT_MIDNIGHT_TICK;
        if (this.tickCount >= this.lurkUntil || midnight
                || this.distanceTo(target) <= Tuning.PATIENT_LURK_COMMIT_RANGE) {
            enterHunt(target);
            return;
        }
        if (this.getNavigation().isDone()) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double radius = Tuning.PATIENT_LURK_RADIUS_MIN
                    + this.random.nextDouble() * (Tuning.PATIENT_LURK_RADIUS_MAX - Tuning.PATIENT_LURK_RADIUS_MIN);
            double x = target.getX() + Math.cos(angle) * radius;
            double z = target.getZ() + Math.sin(angle) * radius;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            this.getNavigation().moveTo(x, y, z, Tuning.PATIENT_LURK_SPEED);
        }
        faceToward(target);
    }

    private void enterHunt(ServerPlayer target) {
        this.phase = Phase.HUNT;
        // The scream is the hunt's starting gun — not before, never after.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SmallHours.SND_PATIENT_SCREAM,
                SoundSource.HOSTILE, Tuning.PATIENT_SCREAM_VOLUME, 1.0F);
        Dread.setThreat(target, true);
    }

    /**
     * You're not looking, it's still far, and physics has opinions: it may take one
     * ground-true, light-shy, view-cone-dodging step that simply closes distance.
     * A single heavy footstep marks the arrival. Nothing else does.
     */
    private void tryClosingStep(ServerLevel level, ServerPlayer target) {
        if (this.tickCount % Tuning.PATIENT_CLOSING_INTERVAL_TICKS != 0
                || this.random.nextFloat() >= Tuning.PATIENT_CLOSING_CHANCE
                || this.distanceTo(target) < Tuning.PATIENT_CLOSING_MIN_DISTANCE) {
            return;
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            return;
        }
        double step = Tuning.PATIENT_CLOSING_STEP_MIN
                + this.random.nextDouble() * (Tuning.PATIENT_CLOSING_STEP_MAX - Tuning.PATIENT_CLOSING_STEP_MIN);
        double jitter = (this.random.nextDouble() - 0.5D) * Math.PI / 4.0D;
        double cos = Math.cos(jitter);
        double sin = Math.sin(jitter);
        double ux = dx / len;
        double uz = dz / len;
        int bx = Mth.floor(this.getX() + (ux * cos - uz * sin) * step);
        int bz = Mth.floor(this.getZ() + (ux * sin + uz * cos) * step);
        int baseY = this.blockPosition().getY();
        for (int dy : new int[] {0, -1, 1, -2, 2}) {
            BlockPos at = new BlockPos(bx, baseY + dy, bz);
            if (!validClosingSpot(level, at)) {
                continue;
            }
            double newX = at.getX() + 0.5D;
            double newZ = at.getZ() + 0.5D;
            if (target.distanceToSqr(newX, at.getY(), newZ) >= this.distanceToSqr(target)) {
                continue; // must actually close distance
            }
            if (inAnyViewCone(level, at)) {
                continue; // never into a cone — it is not seen moving, ever
            }
            this.teleportTo(newX, at.getY(), newZ);
            this.getNavigation().stop();
            this.playSound(level.getBlockState(at.below()).getSoundType().getStepSound(),
                    Tuning.PATIENT_FOOTSTEP_VOLUME, Tuning.PATIENT_FOOTSTEP_PITCH);
            return;
        }
    }

    private boolean validClosingSpot(ServerLevel level, BlockPos at) {
        if (level.getBrightness(LightLayer.BLOCK, at) > Tuning.PATIENT_CLOSING_MAX_LIGHT) {
            return false;
        }
        return level.getBlockState(at.below()).blocksMotion()
                && !level.getBlockState(at).blocksMotion()
                && !level.getBlockState(at.above()).blocksMotion();
    }

    private boolean inAnyViewCone(ServerLevel level, BlockPos at) {
        Vec3 spotEye = Vec3.atBottomCenterOf(at).add(0.0D, 1.6D, 0.0D);
        for (ServerPlayer p : level.players()) {
            if (!NightDirector.eligible(p)) {
                continue;
            }
            Vec3 dir = spotEye.subtract(p.getEyePosition());
            double len = dir.length();
            if (len < 1.0E-4D) {
                return true; // on top of someone — assume seen
            }
            if (p.getViewVector(1.0F).dot(dir.scale(1.0D / len)) > Tuning.OBSERVE_COS) {
                return true;
            }
        }
        return false;
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
        if (current != null && this.phase == Phase.LURK && this.lurkUntil < 0) {
            this.lurkUntil = this.tickCount + Mth.randomBetweenInclusive(this.random,
                    Tuning.PATIENT_LURK_MIN_TICKS, Tuning.PATIENT_LURK_MAX_TICKS);
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
            target.hurt(stoppedRunning(), Tuning.PATIENT_STEAL_DAMAGE);
            Dread.add(target, "patient_steal", Tuning.PATIENT_STEAL);
            this.stealUsed = true;
            this.playSound(SoundEvents.WARDEN_ATTACK_IMPACT,
                    Tuning.PATIENT_STEAL_SOUND_VOLUME, Tuning.PATIENT_STEAL_SOUND_PITCH);
        } else {
            target.hurt(stoppedRunning(), Tuning.PATIENT_STRIKE_DAMAGE);
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("smallhours_phase", this.phase.name());
        tag.putInt("smallhours_lurk_until", this.lurkUntil);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // No tag = fresh spawn (keep the LURK initializer); a bad tag = pre-v0.4
        // save, and those patients were hunt-committed.
        if (tag.contains("smallhours_phase")) {
            try {
                this.phase = Phase.valueOf(tag.getString("smallhours_phase"));
            } catch (IllegalArgumentException ignored) {
                this.phase = Phase.HUNT;
            }
        }
        if (tag.contains("smallhours_lurk_until")) {
            this.lurkUntil = tag.getInt("smallhours_lurk_until");
        }
        if (this.phase == Phase.LURK && this.lurkUntil < 0) {
            this.lurkUntil = this.tickCount + Tuning.PATIENT_LURK_MAX_TICKS;
        }
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
