package dev.jiracipika.smallhours.director;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.Tuning;
import dev.jiracipika.smallhours.config.Config;
import dev.jiracipika.smallhours.dread.Dread;
import dev.jiracipika.smallhours.entity.OmenEntity;
import dev.jiracipika.smallhours.entity.PatientEntity;
import dev.jiracipika.smallhours.entity.UnderstudyEntity;

public final class NightDirector {
    // Per-player transient sequence state; rebuilt lazily, cleared for logged-out players.
    private static final Map<UUID, AmbientState> STATES = new HashMap<>();

    private static final class AmbientState {
        boolean stepping;
        int stepsRemaining;
        int nextStepTick;
        int stillLoops;
        double lastX;
        double lastY;
        double lastZ;
        boolean knocking;
        int knocksRemaining;
        int nextKnockTick;
    }

    private NightDirector() {
    }

    public static void serverTick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % Tuning.DIRECTOR_LOOP_TICKS != 0 || !Config.enabled) {
            return;
        }
        List<ServerPlayer> players = server.overworld().players();
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer p : players) {
            present.add(p.getUUID());
        }
        STATES.keySet().retainAll(present);

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            if (!eligible(p)) {
                continue;
            }
            if (Dread.isThreat(p) && tick % Tuning.HEARTBEAT_INTERVAL_TICKS == 0) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.HOSTILE, Tuning.HEARTBEAT_VOLUME, Tuning.HEARTBEAT_PITCH);
            }
            if ((tick / Tuning.DIRECTOR_LOOP_TICKS + i) % (Tuning.PASSIVE_INTERVAL_TICKS / Tuning.DIRECTOR_LOOP_TICKS) == 0) {
                Dread.passiveTick(p);
            }
            AmbientState st = STATES.computeIfAbsent(p.getUUID(), k -> new AmbientState());
            tickAmbientSequences(p, tick, st);
            if (!Dread.isThreat(p) && p.hurtTime <= 0) {
                maybeAmbientEvent(p, tick, st);
            }
            trySpawns(server, p);
        }
    }

    public static boolean isNight(Level level) {
        long t = level.getDayTime() % Tuning.DAY_LENGTH_TICKS;
        return t > Tuning.NIGHT_START_TICK && t < Tuning.NIGHT_END_TICK;
    }

    public static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    public static ServerPlayer nearestEligible(Entity origin, ServerLevel level, double range) {
        ServerPlayer best = null;
        double bestDist = range * range;
        for (ServerPlayer p : level.players()) {
            if (!eligible(p)) {
                continue;
            }
            double d = origin.distanceToSqr(p);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    public static float approachAngle(float current, float target, float maxStepDegrees) {
        return current + Mth.clamp(Mth.wrapDegrees(target - current), -maxStepDegrees, maxStepDegrees);
    }

    private static void tickAmbientSequences(ServerPlayer p, int tick, AmbientState st) {
        ServerLevel level = (ServerLevel) p.level();
        if (st.stepping) {
            double dx = p.getX() - st.lastX;
            double dy = p.getY() - st.lastY;
            double dz = p.getZ() - st.lastZ;
            boolean still = dx * dx + dy * dy + dz * dz < Tuning.STEPS_STILL_MOVE_SQ;
            st.stillLoops = still ? st.stillLoops + 1 : 0;
            st.lastX = p.getX();
            st.lastY = p.getY();
            st.lastZ = p.getZ();
            if (st.stillLoops >= Tuning.STEPS_STILL_LOOPS) {
                st.stepping = false;
            } else if (tick >= st.nextStepTick) {
                Vec3 look = p.getViewVector(1.0F);
                double flat = Math.sqrt(look.x * look.x + look.z * look.z);
                if (flat < 1.0E-4D) {
                    st.stepping = false;
                } else {
                    RandomSource rng = level.random;
                    double bx = p.getX() - (look.x / flat) * Tuning.STEPS_DISTANCE
                            + (rng.nextDouble() * 2.0D - 1.0D) * Tuning.STEPS_JITTER;
                    double bz = p.getZ() - (look.z / flat) * Tuning.STEPS_DISTANCE
                            + (rng.nextDouble() * 2.0D - 1.0D) * Tuning.STEPS_JITTER;
                    var step = level.getBlockState(p.blockPosition().below()).getSoundType().getStepSound();
                    level.playSound(null, bx, p.getEyeY(), bz, step, SoundSource.HOSTILE,
                            Tuning.STEPS_VOLUME, Tuning.STEPS_PITCH);
                    st.stepsRemaining--;
                    st.nextStepTick = tick + Tuning.STEPS_SPACING_TICKS;
                    if (st.stepsRemaining <= 0) {
                        st.stepping = false;
                    }
                }
            }
        }
        if (st.knocking && tick >= st.nextKnockTick) {
            RandomSource rng = level.random;
            double angle = rng.nextDouble() * Math.PI * 2.0D;
            double radius = Tuning.KNOCKS_MIN_RADIUS + rng.nextDouble() * (Tuning.KNOCKS_MAX_RADIUS - Tuning.KNOCKS_MIN_RADIUS);
            int kx = Mth.floor(p.getX() + Math.cos(angle) * radius);
            int kz = Mth.floor(p.getZ() + Math.sin(angle) * radius);
            int ky = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, kx, kz);
            level.playSound(null, kx + 0.5D, ky, kz + 0.5D, SoundEvents.WOOD_HIT, SoundSource.HOSTILE,
                    Tuning.KNOCKS_VOLUME, 1.0F);
            st.knocksRemaining--;
            st.nextKnockTick = tick + Tuning.KNOCKS_SPACING_TICKS;
            if (st.knocksRemaining <= 0) {
                st.knocking = false;
            }
        }
    }

    private static void maybeAmbientEvent(ServerPlayer p, int tick, AmbientState st) {
        ServerLevel level = (ServerLevel) p.level();
        long now = System.currentTimeMillis();
        if (now - Dread.timer(p, "amb_any") < Tuning.AMBIENT_GLOBAL_GAP_MS) {
            return;
        }
        int tier = Dread.tier(p);
        RandomSource rng = level.random;

        if (tier >= 1 && isNight(level)
                && now - Dread.timer(p, "amb_steps") >= Tuning.STEPS_GAP_MS
                && Dread.tryConsumeBudget(p, "amb_steps", tier >= 2 ? Tuning.STEPS_BUDGET_TIER2 : Tuning.STEPS_BUDGET_BASE)) {
            Dread.setTimer(p, "amb_any", now);
            Dread.setTimer(p, "amb_steps", now);
            st.stepping = true;
            st.stepsRemaining = Tuning.STEPS_MIN_COUNT + rng.nextInt(Tuning.STEPS_COUNT_SPREAD);
            st.nextStepTick = tick + Tuning.STEPS_SPACING_TICKS;
            st.stillLoops = 0;
            st.lastX = p.getX();
            st.lastY = p.getY();
            st.lastZ = p.getZ();
            return;
        }

        if (now - Dread.timer(p, "amb_knocks") >= Tuning.KNOCKS_GAP_MS
                && Dread.tryConsumeBudget(p, "knocks", Tuning.KNOCKS_BUDGET)) {
            Dread.setTimer(p, "amb_any", now);
            Dread.setTimer(p, "amb_knocks", now);
            st.knocking = true;
            st.knocksRemaining = Tuning.KNOCKS_MIN_COUNT + rng.nextInt(Tuning.KNOCKS_COUNT_SPREAD);
            st.nextKnockTick = tick + Tuning.KNOCKS_SPACING_TICKS;
            return;
        }

        // Door creak: there IS a real door involved. You're roofed, it's night, and a
        // placed door within earshot creaks open at full vanilla volume. Nothing opens.
        if (tier >= 2 && isNight(level) && !level.canSeeSky(p.blockPosition())
                && now - Dread.timer(p, "amb_creak") >= Tuning.CREAK_GAP_MS) {
            BlockPos door = findDoorNear(level, p);
            if (door == null) {
                // Nothing in earshot — back off 15s so we don't rescan every loop.
                Dread.setTimer(p, "amb_creak", now - Tuning.CREAK_GAP_MS + 15_000L);
            } else if (Dread.tryConsumeBudget(p, "creak", Tuning.CREAK_BUDGET)) {
                Dread.setTimer(p, "amb_any", now);
                Dread.setTimer(p, "amb_creak", now);
                BlockState doorState = level.getBlockState(door);
                SoundEvent creak = doorState.is(Blocks.IRON_DOOR) ? SoundEvents.IRON_DOOR_OPEN
                        : SoundEvents.WOODEN_DOOR_OPEN;
                level.playSound(null, door.getX() + 0.5D, door.getY() + 0.5D, door.getZ() + 0.5D,
                        creak, SoundSource.BLOCKS, Tuning.CREAK_VOLUME, Tuning.CREAK_PITCH);
                return;
            }
        }

        if (tier >= 3
                && now - Dread.timer(p, "amb_whisper") >= Tuning.WHISPER_GAP_MS
                && Dread.tryConsumeBudget(p, "whisper", Tuning.WHISPER_BUDGET)) {
            Dread.setTimer(p, "amb_any", now);
            Dread.setTimer(p, "amb_whisper", now);
            Vec3 at = p.getEyePosition().subtract(p.getViewVector(1.0F).scale(Tuning.WHISPER_DISTANCE));
            level.playSound(null, at.x, at.y, at.z, SmallHours.SND_WHISPER, SoundSource.HOSTILE,
                    Tuning.WHISPER_VOLUME, 1.0F);
        }
    }

    private static void trySpawns(MinecraftServer server, ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) {
            return;
        }
        long now = System.currentTimeMillis();
        RandomSource rng = level.random;
        boolean night = isNight(level);

        // OMEN
        if (Config.omenEnabled && night
                && (!Config.skipFirstNight || server.overworld().getDayTime() >= Tuning.FIRST_NIGHT_END_DAYTIME)
                && level.getEntitiesOfClass(OmenEntity.class, p.getBoundingBox().inflate(Tuning.SPAWN_CLEAR_RADIUS)).isEmpty()) {
            long gap = Tuning.OMEN_GAP_MIN_MS
                    + (long) (rng.nextDouble() * (Tuning.OMEN_GAP_MAX_MS - Tuning.OMEN_GAP_MIN_MS));
            if (now - Dread.timer(p, "omen_attempt") >= gap) {
                Dread.setTimer(p, "omen_attempt", now);
                int tier = Dread.tier(p);
                int max = (tier == 0 ? (rng.nextBoolean() ? 1 : 0) : Math.min(tier, 3)) + (Dread.isMarked(p) ? 1 : 0);
                if (max > 0) {
                    BlockPos spot = findSpot(level, p, Tuning.OMEN_SPAWN_MIN_RADIUS, Tuning.OMEN_SPAWN_MAX_RADIUS,
                            Tuning.OMEN_SPAWN_MAX_DOT, Tuning.OMEN_SPAWN_MAX_LIGHT, true, 0, 0, Tuning.OMEN_SPAWN_ATTEMPTS);
                    if (spot != null && Dread.tryConsumeBudget(p, "omen", max) && SmallHours.OMEN != null) {
                        OmenEntity e = SmallHours.OMEN.get().create(level);
                        if (e != null) {
                            e.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, rng.nextFloat() * 360.0F, 0.0F);
                            level.addFreshEntity(e);
                        }
                    }
                }
            }
        }

        // UNDERSTUDY
        int tier = Dread.tier(p);
        boolean underground = p.getBlockY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                p.getBlockX(), p.getBlockZ()) - Tuning.UNDERSTUDY_UNDERGROUND_DEPTH;
        if (Config.understudyEnabled && tier >= 1 && (underground || night)
                && level.getEntitiesOfClass(UnderstudyEntity.class, p.getBoundingBox().inflate(Tuning.SPAWN_CLEAR_RADIUS)).isEmpty()) {
            long gap = Tuning.UNDERSTUDY_GAP_MIN_MS
                    + (long) (rng.nextDouble() * (Tuning.UNDERSTUDY_GAP_MAX_MS - Tuning.UNDERSTUDY_GAP_MIN_MS));
            if (now - Dread.timer(p, "understudy_attempt") >= gap) {
                Dread.setTimer(p, "understudy_attempt", now);
                BlockPos spot = findSpot(level, p, Tuning.UNDERSTUDY_SPAWN_MIN_RADIUS, Tuning.UNDERSTUDY_SPAWN_MAX_RADIUS,
                        Tuning.UNDERSTUDY_SPAWN_MAX_DOT, Tuning.UNDERSTUDY_SPAWN_MAX_LIGHT, false,
                        p.getBlockY() - Tuning.UNDERSTUDY_SPAWN_Y_DOWN, p.getBlockY() + Tuning.UNDERSTUDY_SPAWN_Y_UP,
                        Tuning.UNDERSTUDY_SPAWN_ATTEMPTS);
                if (spot != null && Dread.tryConsumeBudget(p, "understudy", Tuning.UNDERSTUDY_BUDGET) && SmallHours.UNDERSTUDY != null) {
                    UnderstudyEntity e = SmallHours.UNDERSTUDY.get().create(level);
                    if (e != null) {
                        e.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, rng.nextFloat() * 360.0F, 0.0F);
                        level.addFreshEntity(e);
                    }
                }
            }
        }

        // PATIENT
        if (Config.patientEnabled && Dread.get(p) >= Config.patientMinDread && night
                && level.getEntitiesOfClass(PatientEntity.class, p.getBoundingBox().inflate(Tuning.PATIENT_CLEAR_RADIUS)).isEmpty()) {
            long gap = Tuning.PATIENT_GAP_MIN_MS
                    + (long) (rng.nextDouble() * (Tuning.PATIENT_GAP_MAX_MS - Tuning.PATIENT_GAP_MIN_MS));
            if (now - Dread.timer(p, "patient_attempt") >= gap) {
                Dread.setTimer(p, "patient_attempt", now);
                BlockPos spot = findSpot(level, p, Tuning.PATIENT_SPAWN_MIN_RADIUS, Tuning.PATIENT_SPAWN_MAX_RADIUS,
                        Tuning.PATIENT_SPAWN_MAX_DOT, Tuning.PATIENT_SPAWN_MAX_LIGHT, true, 0, 0, Tuning.PATIENT_SPAWN_ATTEMPTS);
                if (spot != null && Dread.tryConsumeBudget(p, "patient", Dread.isMarked(p) ? 2 : 1) && SmallHours.PATIENT != null) {
                    PatientEntity e = SmallHours.PATIENT.get().create(level);
                    if (e != null) {
                        e.setTargetPlayer(p.getUUID());
                        e.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, rng.nextFloat() * 360.0F, 0.0F);
                        level.addFreshEntity(e);
                        Dread.setThreat(p, true);
                    }
                }
            }
        }
    }

    /** Nearest real placed door within CREAK_RANGE horizontally and ±4 vertically. */
    private static BlockPos findDoorNear(ServerLevel level, ServerPlayer p) {
        int range = Mth.floor(Tuning.CREAK_RANGE);
        BlockPos center = p.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(cursor).getBlock() instanceof DoorBlock) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos findSpot(ServerLevel level, ServerPlayer player, double minRadius, double maxRadius,
                                     double maxDot, int maxLight, boolean snapToHeightmap, int yMin, int yMax, int attempts) {
        RandomSource rng = level.random;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        for (int i = 0; i < attempts; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0D;
            double radius = minRadius + rng.nextDouble() * (maxRadius - minRadius);
            int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);
            int y = snapToHeightmap
                    ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                    : Mth.randomBetweenInclusive(rng, yMin, yMax);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBrightness(LightLayer.BLOCK, pos) > maxLight) {
                continue;
            }
            if (!level.getBlockState(pos.below()).blocksMotion()) {
                continue;
            }
            if (level.getBlockState(pos).blocksMotion() || level.getBlockState(pos.above()).blocksMotion()) {
                continue;
            }
            Vec3 spotEye = Vec3.atBottomCenterOf(pos).add(0.0D, 1.6D, 0.0D);
            BlockHitResult hit = level.clip(new ClipContext(eye, spotEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                continue;
            }
            Vec3 dir = spotEye.subtract(eye);
            double len = dir.length();
            if (len < 1.0E-4D) {
                continue;
            }
            if (look.dot(dir.scale(1.0D / len)) >= maxDot) {
                continue;
            }
            return pos;
        }
        return null;
    }
}
