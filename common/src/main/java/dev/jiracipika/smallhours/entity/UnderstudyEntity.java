package dev.jiracipika.smallhours.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.Tuning;
import dev.jiracipika.smallhours.director.NightDirector;
import dev.jiracipika.smallhours.dread.Dread;

public class UnderstudyEntity extends Monster {
    private static final List<Holder<SoundEvent>> LURE_SOUNDS = List.of(
            Holder.direct(SoundEvents.CHEST_CLOSE), Holder.direct(SoundEvents.WOODEN_DOOR_OPEN),
            Holder.direct(SoundEvents.GENERIC_EAT), Holder.direct(SoundEvents.PLAYER_HURT));

    private enum State { LURE, CAUGHT, POUNCE, FLEE }

    private UUID targetUUID;
    private State state = State.LURE;
    private int cycleCount;
    private int nextLureAt = -1;
    private int pendingRepeatAt = -1;
    private Holder<SoundEvent> pendingRepeatSound;
    private int caughtTicks;
    private int fleeTicks;
    private int lightTicks;
    private int dayTicks;
    private boolean pounceUsed;
    private boolean pounceArmed;

    public UnderstudyEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = Tuning.UNDERSTUDY_XP;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, Tuning.UNDERSTUDY_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, Tuning.UNDERSTUDY_MOVE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, Tuning.UNDERSTUDY_ATTACK_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, Tuning.UNDERSTUDY_FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new CaughtGoal());
        this.goalSelector.addGoal(2, new LureGoal());
        this.goalSelector.addGoal(3, new PounceGoal());
        this.goalSelector.addGoal(4, new FleeGoal());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer near = nearestEligible(Tuning.UNDERSTUDY_TARGET_SCAN_RANGE);
        if (near != null) {
            this.targetUUID = near.getUUID();
        }
        if (this.level().getBrightness(LightLayer.BLOCK, this.blockPosition()) >= Tuning.UNDERSTUDY_FLEE_LIGHT) {
            if (++this.lightTicks >= Tuning.UNDERSTUDY_LIGHT_TICKS) {
                this.discard();
                return;
            }
        } else {
            this.lightTicks = 0;
        }
        if (!NightDirector.isNight(this.level()) && this.level().canSeeSky(this.blockPosition())) {
            if (++this.dayTicks >= Tuning.UNDERSTUDY_DAY_TICKS) {
                this.discard();
                return;
            }
        } else {
            this.dayTicks = 0;
        }
        if (this.fleeTicks > 0) {
            this.fleeTicks--;
        }
    }

    private ServerPlayer nearestEligible(double range) {
        if (!(this.level() instanceof ServerLevel level)) {
            return null;
        }
        return NightDirector.nearestEligible(this, level, range);
    }

    // Isolated private-sound helper: everything player-secret goes through this one call.
    private static void playPrivate(ServerPlayer player, Holder<SoundEvent> sound, BlockPos pos, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.HOSTILE,
                (int) (pos.getX() * 8.0D), (int) (pos.getY() * 8.0D), (int) (pos.getZ() * 8.0D),
                volume, pitch, player.getRandom().nextLong()));
    }

    private void reposition() {
        for (int tries = 0; tries < 6; tries++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double dist = Tuning.UNDERSTUDY_REPOSITION_MIN
                    + this.random.nextDouble() * (Tuning.UNDERSTUDY_REPOSITION_MAX - Tuning.UNDERSTUDY_REPOSITION_MIN);
            BlockPos candidate = new BlockPos(
                    Mth.floor(this.getX() + Math.cos(angle) * dist),
                    this.blockPosition().getY(),
                    Mth.floor(this.getZ() + Math.sin(angle) * dist));
            if (this.level().getBrightness(LightLayer.BLOCK, candidate) <= Tuning.UNDERSTUDY_LURE_MAX_LIGHT
                    && !this.level().getBlockState(candidate).blocksMotion()
                    && !this.level().getBlockState(candidate.above()).blocksMotion()
                    && this.level().getBlockState(candidate.below()).blocksMotion()) {
                this.getNavigation().moveTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D, 1.0D);
                return;
            }
        }
    }

    private void vanish() {
        ServerPlayer hearer = nearestEligible(Tuning.UNDERSTUDY_HEAR_RANGE);
        if (hearer != null) {
            playPrivate(hearer, Holder.direct(SmallHours.SND_UNDERSTUDY_CHITTER), this.blockPosition(),
                    Tuning.UNDERSTUDY_CHITTER_FLEE_VOLUME, 1.0F);
        }
        this.discard();
    }

    private class LureGoal extends Goal {
        LureGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (UnderstudyEntity.this.fleeTicks > 0 || UnderstudyEntity.this.pounceUsed) {
                return false;
            }
            ServerPlayer t = UnderstudyEntity.this.nearestEligible(Tuning.UNDERSTUDY_LURE_MAX_DIST);
            if (t == null) {
                return false;
            }
            double dist = UnderstudyEntity.this.distanceTo(t);
            if (dist < Tuning.UNDERSTUDY_LURE_MIN_DIST || dist > Tuning.UNDERSTUDY_LURE_MAX_DIST) {
                return false;
            }
            return UnderstudyEntity.this.level().getBrightness(LightLayer.BLOCK, UnderstudyEntity.this.blockPosition())
                    <= Tuning.UNDERSTUDY_LURE_MAX_LIGHT;
        }

        @Override
        public void start() {
            UnderstudyEntity.this.state = State.LURE;
            if (UnderstudyEntity.this.nextLureAt < 0) {
                UnderstudyEntity.this.nextLureAt = UnderstudyEntity.this.tickCount + Tuning.UNDERSTUDY_LURE_FIRST_DELAY_TICKS;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            UnderstudyEntity u = UnderstudyEntity.this;
            ServerPlayer t = u.nearestEligible(Tuning.UNDERSTUDY_LURE_MAX_DIST);
            if (t == null) {
                return;
            }
            if (u.pendingRepeatAt >= 0) {
                if (u.tickCount >= u.pendingRepeatAt && u.pendingRepeatSound != null) {
                    playPrivate(t, u.pendingRepeatSound, u.blockPosition(),
                            Tuning.UNDERSTUDY_LURE_VOLUME, Tuning.UNDERSTUDY_LURE_PITCH);
                    u.pendingRepeatAt = -1;
                }
                return;
            }
            if (u.tickCount >= u.nextLureAt) {
                RandomSource rng = u.random;
                Holder<SoundEvent> sound = LURE_SOUNDS.get(rng.nextInt(LURE_SOUNDS.size()));
                BlockPos at = u.blockPosition().offset(
                        rng.nextInt(Tuning.UNDERSTUDY_LURE_OFFSET * 2 + 1) - Tuning.UNDERSTUDY_LURE_OFFSET, 0,
                        rng.nextInt(Tuning.UNDERSTUDY_LURE_OFFSET * 2 + 1) - Tuning.UNDERSTUDY_LURE_OFFSET);
                playPrivate(t, sound, at, Tuning.UNDERSTUDY_LURE_VOLUME, Tuning.UNDERSTUDY_LURE_PITCH);
                u.pendingRepeatSound = sound;
                u.pendingRepeatAt = u.tickCount + Mth.randomBetweenInclusive(rng,
                        Tuning.UNDERSTUDY_REPEAT_MIN_TICKS, Tuning.UNDERSTUDY_REPEAT_MAX_TICKS);
                u.nextLureAt = u.pendingRepeatAt + Mth.randomBetweenInclusive(rng,
                        Tuning.UNDERSTUDY_NEXT_LURE_MIN_TICKS, Tuning.UNDERSTUDY_NEXT_LURE_MAX_TICKS);
                u.cycleCount++;
                if (rng.nextFloat() < Tuning.UNDERSTUDY_REPOSITION_CHANCE) {
                    u.reposition();
                }
                if (u.cycleCount >= Tuning.UNDERSTUDY_LURE_CYCLES) {
                    u.fleeTicks = Tuning.UNDERSTUDY_FLEE_TICKS;
                    Dread.add(t, "understudy_failed", Tuning.UNDERSTUDY_FAILED);
                }
            }
        }
    }

    private class CaughtGoal extends Goal {
        private ServerPlayer watcher;

        CaughtGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (UnderstudyEntity.this.fleeTicks > 0 || UnderstudyEntity.this.pounceUsed) {
                return false;
            }
            this.watcher = findWatcher();
            return this.watcher != null;
        }

        @Override
        public void start() {
            UnderstudyEntity.this.state = State.CAUGHT;
            UnderstudyEntity.this.getNavigation().stop();
            UnderstudyEntity.this.caughtTicks = Tuning.UNDERSTUDY_CAUGHT_TICKS;
            if (this.watcher != null) {
                playPrivate(this.watcher, Holder.direct(SmallHours.SND_UNDERSTUDY_CHITTER),
                        UnderstudyEntity.this.blockPosition(), Tuning.UNDERSTUDY_CAUGHT_VOLUME, 1.0F);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            UnderstudyEntity.this.getNavigation().stop();
            UnderstudyEntity.this.caughtTicks--;
        }

        @Override
        public boolean canContinueToUse() {
            return UnderstudyEntity.this.caughtTicks > 0 && this.watcher != null && this.watcher.isAlive();
        }

        @Override
        public void stop() {
            ServerPlayer w = this.watcher;
            this.watcher = null;
            if (w == null || !w.isAlive()) {
                UnderstudyEntity.this.fleeTicks = Tuning.UNDERSTUDY_FLEE_TICKS;
                return;
            }
            boolean closeEnough = UnderstudyEntity.this.distanceToSqr(w)
                    <= Tuning.UNDERSTUDY_CAUGHT_POUNCE_RANGE * Tuning.UNDERSTUDY_CAUGHT_POUNCE_RANGE;
            boolean darkEnough = UnderstudyEntity.this.level().getBrightness(LightLayer.BLOCK, UnderstudyEntity.this.blockPosition())
                    <= Tuning.UNDERSTUDY_POUNCE_MAX_LIGHT;
            if (closeEnough && darkEnough) {
                UnderstudyEntity.this.pounceArmed = true;
            } else {
                UnderstudyEntity.this.fleeTicks = Tuning.UNDERSTUDY_FLEE_TICKS;
            }
        }

        private ServerPlayer findWatcher() {
            if (!(UnderstudyEntity.this.level() instanceof ServerLevel level)) {
                return null;
            }
            for (ServerPlayer p : level.players()) {
                if (!NightDirector.eligible(p)) {
                    continue;
                }
                if (UnderstudyEntity.this.distanceToSqr(p) > Tuning.UNDERSTUDY_CAUGHT_RANGE * Tuning.UNDERSTUDY_CAUGHT_RANGE) {
                    continue;
                }
                if (!p.hasLineOfSight(UnderstudyEntity.this)) {
                    continue;
                }
                Vec3 dir = UnderstudyEntity.this.getEyePosition().subtract(p.getEyePosition());
                double len = dir.length();
                if (len < 1.0E-4D) {
                    continue;
                }
                if (p.getViewVector(1.0F).dot(dir.scale(1.0D / len)) > Tuning.UNDERSTUDY_CAUGHT_COS) {
                    return p;
                }
            }
            return null;
        }
    }

    private class PounceGoal extends Goal {
        PounceGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            UnderstudyEntity u = UnderstudyEntity.this;
            if (u.pounceUsed || u.fleeTicks > 0) {
                return false;
            }
            if (u.level().getBrightness(LightLayer.BLOCK, u.blockPosition()) > Tuning.UNDERSTUDY_POUNCE_MAX_LIGHT) {
                return false;
            }
            return u.pounceArmed || u.nearestEligible(Tuning.UNDERSTUDY_POUNCE_RANGE) != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            UnderstudyEntity u = UnderstudyEntity.this;
            u.pounceUsed = true;
            u.pounceArmed = false;
            u.state = State.POUNCE;
            ServerPlayer t = u.nearestEligible(Tuning.UNDERSTUDY_CAUGHT_POUNCE_RANGE);
            if (t == null) {
                u.fleeTicks = Tuning.UNDERSTUDY_FLEE_TICKS;
                return;
            }
            u.getNavigation().stop();
            double dx = t.getX() - u.getX();
            double dz = t.getZ() - u.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            double nx = len < 1.0E-4D ? 0.0D : dx / len;
            double nz = len < 1.0E-4D ? 0.0D : dz / len;
            u.setDeltaMovement(nx * Tuning.UNDERSTUDY_POUNCE_HORIZONTAL, Tuning.UNDERSTUDY_POUNCE_VERTICAL,
                    nz * Tuning.UNDERSTUDY_POUNCE_HORIZONTAL);
            u.hasImpulse = true;
            t.hurt(u.damageSources().mobAttack(u), Tuning.UNDERSTUDY_POUNCE_DAMAGE);
            u.level().playSound(null, u.getX(), u.getY(), u.getZ(), SmallHours.SND_UNDERSTUDY_SHRIEK,
                    SoundSource.HOSTILE, Tuning.UNDERSTUDY_SHRIEK_VOLUME, 1.0F);
            Dread.add(t, "understudy_pounce", Tuning.UNDERSTUDY_POUNCE);
            u.fleeTicks = Tuning.UNDERSTUDY_FLEE_TICKS;
        }
    }

    private class FleeGoal extends Goal {
        FleeGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return UnderstudyEntity.this.fleeTicks > 0;
        }

        @Override
        public void start() {
            UnderstudyEntity.this.state = State.FLEE;
            navigateAway();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (UnderstudyEntity.this.fleeTicks <= 0
                    || UnderstudyEntity.this.level().getBrightness(LightLayer.BLOCK, UnderstudyEntity.this.blockPosition())
                            >= Tuning.UNDERSTUDY_FLEE_LIGHT) {
                UnderstudyEntity.this.vanish();
                return;
            }
            if (UnderstudyEntity.this.getNavigation().isDone()) {
                navigateAway();
            }
        }

        private void navigateAway() {
            ServerPlayer p = UnderstudyEntity.this.nearestEligible(Tuning.UNDERSTUDY_CAUGHT_RANGE);
            if (p == null) {
                UnderstudyEntity.this.getNavigation().stop();
                return;
            }
            double dx = UnderstudyEntity.this.getX() - p.getX();
            double dz = UnderstudyEntity.this.getZ() - p.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-4D) {
                return;
            }
            double fx = UnderstudyEntity.this.getX() + (dx / len) * Tuning.UNDERSTUDY_FLEE_DISTANCE;
            double fz = UnderstudyEntity.this.getZ() + (dz / len) * Tuning.UNDERSTUDY_FLEE_DISTANCE;
            UnderstudyEntity.this.getNavigation().moveTo(fx, UnderstudyEntity.this.getY(), fz, Tuning.UNDERSTUDY_FLEE_SPEED);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide) {
            this.fleeTicks = Tuning.UNDERSTUDY_FLEE_TICKS;
            ServerPlayer to = source.getEntity() instanceof ServerPlayer sp ? sp : nearestEligible(Tuning.UNDERSTUDY_HEAR_RANGE);
            if (to != null) {
                playPrivate(to, Holder.direct(SmallHours.SND_UNDERSTUDY_CHITTER), this.blockPosition(),
                        Tuning.UNDERSTUDY_CAUGHT_VOLUME, 1.0F);
            }
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide && source.getEntity() instanceof ServerPlayer killer) {
            Dread.add(killer, "understudy_killed", Tuning.UNDERSTUDY_KILLED);
        }
        super.die(source);
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }
}
