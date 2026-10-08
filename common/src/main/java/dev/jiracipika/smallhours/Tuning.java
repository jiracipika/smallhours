package dev.jiracipika.smallhours;

public final class Tuning {
    private Tuning() {
    }

    // ------------------------------------------------------------------
    // Day/night
    // ------------------------------------------------------------------
    public static final long DAY_LENGTH_TICKS = 24000L;
    public static final long NIGHT_START_TICK = 12542L;
    public static final long NIGHT_END_TICK = 23459L;

    // ------------------------------------------------------------------
    // Dread scale
    // ------------------------------------------------------------------
    public static final int DREAD_MAX = 100;
    public static final int TIER_1 = 25;
    public static final int TIER_2 = 50;
    public static final int TIER_3 = 75;
    public static final int DEATH_DREAD_CAP = 20;
    public static final int DEATH_MARKED_MAXEVER = 85;

    // ------------------------------------------------------------------
    // Passive decay
    // ------------------------------------------------------------------
    public static final long DECAY_QUIET_MS = 60_000L;
    public static final long DECAY_INTERVAL_MS = 120_000L;
    public static final int DECAY_FLOOR_THRESHOLD = 25;
    public static final int DECAY_FLOOR = 5;

    // ------------------------------------------------------------------
    // Passive sources / sinks (per-night accrual caps)
    // ------------------------------------------------------------------
    public static final long DARKNESS_INTERVAL_MS = 60_000L;
    public static final int DARKNESS_CAP = 5;
    public static final int DARKNESS_MAX_BLOCK_LIGHT = 3;

    public static final long UNDERGROUND_INTERVAL_MS = 90_000L;
    public static final int UNDERGROUND_CAP = 4;
    public static final int UNDERGROUND_MAX_DREAD = 50;

    public static final long LIT_ROOM_INTERVAL_MS = 30_000L;
    public static final int LIT_ROOM_CAP = 6;
    public static final int LIT_ROOM_MIN_BLOCK_LIGHT = 9;

    public static final long DAYLIGHT_INTERVAL_MS = 45_000L;
    public static final int DAYLIGHT_CAP = 8;
    public static final int DAYLIGHT_MIN_SKY_LIGHT = 12;

    public static final long CAT_INTERVAL_MS = 60_000L;
    public static final int CAT_CAP = 8;
    public static final double CAT_RADIUS = 12.0D;

    // ------------------------------------------------------------------
    // Dread event deltas
    // ------------------------------------------------------------------
    public static final int SLEEP_RELIEF = -10;
    public static final int OMEN_SIGHTING = 6;
    public static final int OMEN_SIGHTING_CLOSE = 4;
    public static final int UNDERSTUDY_FAILED = 2;
    public static final int UNDERSTUDY_POUNCE = 8;
    public static final int UNDERSTUDY_KILLED = -12;
    public static final int PATIENT_STEAL = 8;
    public static final int PATIENT_PROXIMITY = 1;
    public static final int PATIENT_KILLED = 12;

    /** Per-night accrual cap per source; -1 = uncapped. Negative-delta sources bypass caps. */
    public static int capFor(String source) {
        return switch (source) {
            case "darkness" -> DARKNESS_CAP;
            case "underground" -> UNDERGROUND_CAP;
            case "lit_room" -> LIT_ROOM_CAP;
            case "daylight" -> DAYLIGHT_CAP;
            case "cat" -> CAT_CAP;
            default -> -1;
        };
    }

    // ------------------------------------------------------------------
    // Shared entity behavior
    // ------------------------------------------------------------------
    public static final float ENTITY_TURN_SPEED_DEG = 6.0F;

    // ------------------------------------------------------------------
    // Omen
    // ------------------------------------------------------------------
    public static final double OMEN_MAX_HEALTH = 20.0D;
    public static final double OMEN_MOVE_SPEED = 0.0D;
    public static final double OMEN_FOLLOW_RANGE = 64.0D;

    public static final double OMEN_SIGHT_RANGE = 64.0D;
    public static final double OMEN_STARE_RANGE = 60.0D;
    public static final double OMEN_STARE_COS = 0.96D;
    public static final int OMEN_STARE_TICKS = 50;
    public static final int OMEN_STARE_DECAY_TICKS = 20;
    public static final double OMEN_CLOSE_DISTANCE = 15.0D;
    public static final int OMEN_LIGHT_SAMPLE_TICKS = 20;
    public static final int OMEN_LIGHT_FLARE = 4;
    public static final int OMEN_DWELL_TICKS = 500;

    public static final float OMEN_STING_VOLUME = 0.35F;
    public static final float OMEN_STEP_BACK_VOLUME = 0.3F;
    public static final float OMEN_STEP_BACK_PITCH = 0.7F;

    // ------------------------------------------------------------------
    // Understudy
    // ------------------------------------------------------------------
    public static final double UNDERSTUDY_MAX_HEALTH = 12.0D;
    public static final double UNDERSTUDY_MOVE_SPEED = 0.30D;
    public static final double UNDERSTUDY_ATTACK_DAMAGE = 3.0D;
    public static final double UNDERSTUDY_FOLLOW_RANGE = 32.0D;
    public static final int UNDERSTUDY_XP = 5;

    public static final double UNDERSTUDY_LURE_MIN_DIST = 12.0D;
    public static final double UNDERSTUDY_LURE_MAX_DIST = 28.0D;
    public static final int UNDERSTUDY_LURE_MAX_LIGHT = 3;
    public static final int UNDERSTUDY_LURE_FIRST_DELAY_TICKS = 20;
    public static final float UNDERSTUDY_LURE_VOLUME = 0.7F;
    public static final float UNDERSTUDY_LURE_PITCH = 1.0F;
    public static final int UNDERSTUDY_LURE_OFFSET = 2;
    public static final int UNDERSTUDY_REPEAT_MIN_TICKS = 24;
    public static final int UNDERSTUDY_REPEAT_MAX_TICKS = 36;
    public static final int UNDERSTUDY_NEXT_LURE_MIN_TICKS = 10;
    public static final int UNDERSTUDY_NEXT_LURE_MAX_TICKS = 20;
    public static final float UNDERSTUDY_REPOSITION_CHANCE = 0.5F;
    public static final double UNDERSTUDY_REPOSITION_MIN = 6.0D;
    public static final double UNDERSTUDY_REPOSITION_MAX = 10.0D;
    public static final int UNDERSTUDY_LURE_CYCLES = 3;

    public static final double UNDERSTUDY_CAUGHT_RANGE = 24.0D;
    public static final double UNDERSTUDY_CAUGHT_COS = 0.60D;
    public static final int UNDERSTUDY_CAUGHT_TICKS = 50;
    public static final float UNDERSTUDY_CAUGHT_VOLUME = 0.2F;
    public static final double UNDERSTUDY_CAUGHT_POUNCE_RANGE = 5.0D;

    public static final double UNDERSTUDY_POUNCE_RANGE = 2.5D;
    public static final int UNDERSTUDY_POUNCE_MAX_LIGHT = 8;
    public static final float UNDERSTUDY_POUNCE_DAMAGE = 3.0F;
    public static final double UNDERSTUDY_POUNCE_HORIZONTAL = 0.5D;
    public static final double UNDERSTUDY_POUNCE_VERTICAL = 0.42D;
    public static final float UNDERSTUDY_SHRIEK_VOLUME = 1.0F;

    public static final int UNDERSTUDY_FLEE_TICKS = 80;
    public static final double UNDERSTUDY_FLEE_DISTANCE = 16.0D;
    public static final double UNDERSTUDY_FLEE_SPEED = 1.35D;
    public static final int UNDERSTUDY_FLEE_LIGHT = 9;
    public static final int UNDERSTUDY_LIGHT_TICKS = 200;
    public static final int UNDERSTUDY_DAY_TICKS = 100;
    public static final float UNDERSTUDY_CHITTER_FLEE_VOLUME = 0.3F;
    public static final double UNDERSTUDY_HEAR_RANGE = 24.0D;
    public static final double UNDERSTUDY_TARGET_SCAN_RANGE = 32.0D;

    // ------------------------------------------------------------------
    // Patient
    // ------------------------------------------------------------------
    public static final double PATIENT_MAX_HEALTH = 100.0D;
    public static final double PATIENT_MOVE_SPEED = 0.30D;
    public static final double PATIENT_ATTACK_DAMAGE = 8.0D;
    public static final double PATIENT_FOLLOW_RANGE = 48.0D;
    public static final int PATIENT_XP = 20;

    public static final double OBSERVE_RANGE = 48.0D;
    public static final double OBSERVE_COS = 0.6428D;
    public static final int PATIENT_RETARGET_TICKS = 40;
    public static final int PATIENT_OBSERVE_CHECK_TICKS = 2;
    public static final int PATIENT_REPATH_TICKS = 10;

    public static final float PATIENT_CREEP_SPEED = 0.27F;
    public static final float PATIENT_CREEP_SPEED_MARKED = 0.4F;
    public static final float PATIENT_HUNT_SPEED = 1.5F;
    public static final float PATIENT_HUNT_SPEED_MARKED = 1.8F;

    public static final int PATIENT_FOOTSTEP_TICKS = 8;
    public static final float PATIENT_FOOTSTEP_VOLUME = 0.8F;
    public static final float PATIENT_FOOTSTEP_PITCH = 0.55F;

    public static final double PATIENT_STRIKE_DISTANCE = 2.5D;
    public static final int PATIENT_STRIKE_COOLDOWN_TICKS = 30;
    public static final float PATIENT_STEAL_DAMAGE = 2.0F;
    public static final float PATIENT_STRIKE_DAMAGE = 8.0F;
    public static final float PATIENT_STEAL_SOUND_VOLUME = 1.2F;
    public static final float PATIENT_STEAL_SOUND_PITCH = 0.7F;

    public static final double PATIENT_PROXIMITY_RANGE = 24.0D;
    public static final int PATIENT_PROXIMITY_INTERVAL_TICKS = 200;

    public static final int PATIENT_REGEN_UNOBSERVED_TICKS = 100;
    public static final int PATIENT_REGEN_INTERVAL_TICKS = 40;
    public static final float PATIENT_REGEN_AMOUNT = 1.0F;

    public static final float PATIENT_SCREAM_VOLUME = 1.0F;

    // ------------------------------------------------------------------
    // Night director
    // ------------------------------------------------------------------
    public static final int DIRECTOR_LOOP_TICKS = 10;
    public static final int PASSIVE_INTERVAL_TICKS = 100;
    public static final int HEARTBEAT_INTERVAL_TICKS = 30;
    public static final float HEARTBEAT_VOLUME = 0.3F;
    public static final float HEARTBEAT_PITCH = 1.0F;

    public static final long AMBIENT_GLOBAL_GAP_MS = 90_000L;

    public static final long STEPS_GAP_MS = 360_000L;
    public static final int STEPS_BUDGET_TIER2 = 5;
    public static final int STEPS_BUDGET_BASE = 3;
    public static final int STEPS_MIN_COUNT = 4;
    public static final int STEPS_COUNT_SPREAD = 3;
    public static final int STEPS_SPACING_TICKS = 20;
    public static final double STEPS_DISTANCE = 7.0D;
    public static final double STEPS_JITTER = 1.0D;
    public static final float STEPS_VOLUME = 0.35F;
    public static final float STEPS_PITCH = 0.9F;
    public static final double STEPS_STILL_MOVE_SQ = 0.01D;
    public static final int STEPS_STILL_LOOPS = 2;

    public static final long KNOCKS_GAP_MS = 480_000L;
    public static final int KNOCKS_BUDGET = 2;
    public static final int KNOCKS_MIN_COUNT = 2;
    public static final int KNOCKS_COUNT_SPREAD = 2;
    public static final int KNOCKS_SPACING_TICKS = 20;
    public static final float KNOCKS_VOLUME = 0.3F;
    public static final double KNOCKS_MIN_RADIUS = 24.0D;
    public static final double KNOCKS_MAX_RADIUS = 40.0D;

    public static final long WHISPER_GAP_MS = 720_000L;
    public static final int WHISPER_BUDGET = 2;
    public static final float WHISPER_VOLUME = 0.3F;
    public static final double WHISPER_DISTANCE = 2.5D;

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------
    public static final long FIRST_NIGHT_END_DAYTIME = 37000L;
    public static final double SPAWN_CLEAR_RADIUS = 64.0D;
    public static final double PATIENT_CLEAR_RADIUS = 96.0D;

    public static final long OMEN_GAP_MIN_MS = 60_000L;
    public static final long OMEN_GAP_MAX_MS = 120_000L;
    public static final int OMEN_SPAWN_ATTEMPTS = 8;
    public static final double OMEN_SPAWN_MIN_RADIUS = 30.0D;
    public static final double OMEN_SPAWN_MAX_RADIUS = 60.0D;
    public static final int OMEN_SPAWN_MAX_LIGHT = 6;
    public static final double OMEN_SPAWN_MAX_DOT = 0.7D;

    public static final long UNDERSTUDY_GAP_MIN_MS = 45_000L;
    public static final long UNDERSTUDY_GAP_MAX_MS = 90_000L;
    public static final int UNDERSTUDY_BUDGET = 2;
    public static final int UNDERSTUDY_SPAWN_ATTEMPTS = 6;
    public static final double UNDERSTUDY_SPAWN_MIN_RADIUS = 24.0D;
    public static final double UNDERSTUDY_SPAWN_MAX_RADIUS = 40.0D;
    public static final int UNDERSTUDY_SPAWN_MAX_LIGHT = 3;
    public static final double UNDERSTUDY_SPAWN_MAX_DOT = 0.55D;
    public static final int UNDERSTUDY_SPAWN_Y_DOWN = 12;
    public static final int UNDERSTUDY_SPAWN_Y_UP = 6;
    public static final int UNDERSTUDY_UNDERGROUND_DEPTH = 10;

    public static final long PATIENT_GAP_MIN_MS = 30_000L;
    public static final long PATIENT_GAP_MAX_MS = 60_000L;
    public static final int PATIENT_MIN_DREAD = 60;
    public static final int PATIENT_SPAWN_ATTEMPTS = 8;
    public static final double PATIENT_SPAWN_MIN_RADIUS = 32.0D;
    public static final double PATIENT_SPAWN_MAX_RADIUS = 48.0D;
    public static final int PATIENT_SPAWN_MAX_LIGHT = 4;
    public static final double PATIENT_SPAWN_MAX_DOT = 0.55D;

    // ------------------------------------------------------------------
    // v0.2 — mimicry + cat ritual
    // ------------------------------------------------------------------
    public static final int MIMICRY_WINDOW_TICKS = 6000;
    public static final int MIMICRY_MAX_PER_PLAYER = 8;
    public static final float MIMICRY_MIMIC_CHANCE = 0.66F;
    public static final double RITUAL_RADIUS = 8.0D;
    public static final int RITUAL_RELIEF = -10;

    // ------------------------------------------------------------------
    // Client rendering
    // ------------------------------------------------------------------
    public static final float CLIENT_OMEN_SCALE = 1.12F;
    public static final float CLIENT_OMEN_SHADOW = 0.4F;
    public static final float CLIENT_UNDERSTUDY_SCALE = 0.55F;
    public static final float CLIENT_UNDERSTUDY_SHADOW = 0.25F;
    public static final float CLIENT_PATIENT_SCALE = 1.17F;
    public static final float CLIENT_PATIENT_SHADOW = 0.55F;
}
