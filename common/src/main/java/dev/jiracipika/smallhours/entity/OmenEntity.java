package dev.jiracipika.smallhours.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.Tuning;
import dev.jiracipika.smallhours.config.Config;
import dev.jiracipika.smallhours.director.NightDirector;
import dev.jiracipika.smallhours.dread.Dread;

public class OmenEntity extends Monster {
    private enum VanishReason { STARE, CLOSE, LIT, DWELL }

    private final Map<UUID, Integer> stareTicks = new HashMap<>();
    private final Map<UUID, Integer> lastStareTick = new HashMap<>();
    private final Set<UUID> stungPlayers = new HashSet<>();
    private int lastLightSample = -1;

    public OmenEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, Tuning.OMEN_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, Tuning.OMEN_MOVE_SPEED)
                .add(Attributes.FOLLOW_RANGE, Tuning.OMEN_FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer watch = NightDirector.nearestEligible(this, level, Tuning.OMEN_SIGHT_RANGE);
        if (watch != null) {
            faceToward(watch);
            double dx = watch.getX() - this.getX();
            double dz = watch.getZ() - this.getZ();
            if (Math.sqrt(dx * dx + dz * dz) <= Tuning.OMEN_CLOSE_DISTANCE) {
                vanish(VanishReason.CLOSE, watch);
                return;
            }
        }
        for (ServerPlayer p : level.players()) {
            if (!NightDirector.eligible(p)) {
                continue;
            }
            if (this.distanceToSqr(p) > Tuning.OMEN_STARE_RANGE * Tuning.OMEN_STARE_RANGE) {
                continue;
            }
            UUID id = p.getUUID();
            boolean staring = p.hasLineOfSight(this) && viewDot(p) > Tuning.OMEN_STARE_COS;
            int count = this.stareTicks.getOrDefault(id, 0);
            if (staring) {
                count++;
                this.lastStareTick.put(id, this.tickCount);
            } else {
                int since = this.tickCount - this.lastStareTick.getOrDefault(id, 0);
                count = Math.max(0, count - since / Tuning.OMEN_STARE_DECAY_TICKS);
            }
            this.stareTicks.put(id, count);
            if (count >= Tuning.OMEN_STARE_TICKS) {
                vanish(VanishReason.STARE, p);
                return;
            }
        }
        if (this.tickCount % Tuning.OMEN_LIGHT_SAMPLE_TICKS == 0) {
            int light = this.level().getBrightness(LightLayer.BLOCK, this.blockPosition());
            if (this.lastLightSample >= 0 && light - this.lastLightSample >= Tuning.OMEN_LIGHT_FLARE) {
                vanish(VanishReason.LIT, NightDirector.nearestEligible(this, level, Tuning.OMEN_SIGHT_RANGE));
                return;
            }
            this.lastLightSample = light;
        }
        if (this.tickCount > Tuning.OMEN_DWELL_TICKS) {
            vanish(VanishReason.DWELL, null);
        }
    }

    private double viewDot(Player p) {
        Vec3 dir = this.getEyePosition().subtract(p.getEyePosition());
        double len = dir.length();
        if (len < 1.0E-4D) {
            return 0.0D;
        }
        return p.getViewVector(1.0F).dot(dir.scale(1.0D / len));
    }

    private void faceToward(Entity target) {
        float targetYaw = (float) (Mth.atan2(target.getZ() - this.getZ(), target.getX() - this.getX()) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(NightDirector.approachAngle(this.getYRot(), targetYaw, Tuning.ENTITY_TURN_SPEED_DEG));
        this.yBodyRot = NightDirector.approachAngle(this.yBodyRot, targetYaw, Tuning.ENTITY_TURN_SPEED_DEG);
        this.yHeadRot = NightDirector.approachAngle(this.yHeadRot, targetYaw, Tuning.ENTITY_TURN_SPEED_DEG);
    }

    private void vanish(VanishReason reason, ServerPlayer player) {
        if (this.level().isClientSide) {
            this.discard();
            return;
        }
        if (player != null) {
            Dread.add(player, "omen_sighting",
                    reason == VanishReason.CLOSE ? Tuning.OMEN_SIGHTING_CLOSE : Tuning.OMEN_SIGHTING);
            SmallHours.IT_SAW_YOU_FIRST.trigger(player);
            // Witness rule: seeing it together earns the witness half credit.
            if (Config.witnessRule) {
                for (Player wp : this.level().players()) {
                    if (!(wp instanceof ServerPlayer w) || w == player || !NightDirector.eligible(w)
                            || this.distanceToSqr(w) > Tuning.OMEN_STARE_RANGE * Tuning.OMEN_STARE_RANGE
                            || !w.hasLineOfSight(this)) {
                        continue;
                    }
                    Dread.add(w, "omen_sighting_witness",
                            (reason == VanishReason.CLOSE ? Tuning.OMEN_SIGHTING_CLOSE : Tuning.OMEN_SIGHTING) / 2);
                }
            }
            if (this.stungPlayers.add(player.getUUID())) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SmallHours.SND_OMEN_STING,
                        SoundSource.HOSTILE, Tuning.OMEN_STING_VOLUME, 1.0F);
            }
        }
        // Step-back knock when outdoors; silent in caves. No particles, ever.
        if (this.level().canSeeSky(this.blockPosition())) {
            this.playSound(SoundEvents.WOOD_HIT, Tuning.OMEN_STEP_BACK_VOLUME, Tuning.OMEN_STEP_BACK_PITCH);
        }
        this.discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Only /kill may remove it.
        return source.is(DamageTypes.GENERIC_KILL) && super.hurt(source, amount);
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
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void checkDespawn() {
        // Lifetime is controlled by the dwell timer.
    }
}
