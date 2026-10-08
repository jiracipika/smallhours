package dev.jiracipika.smallhours.dread;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.LightLayer;

import dev.jiracipika.smallhours.Tuning;
import dev.jiracipika.smallhours.director.NightDirector;

public final class Dread {
    private Dread() {
    }

    public static int get(ServerPlayer player) {
        DreadState.PlayerDread st = state(player);
        return st == null ? 0 : Mth.clamp(st.dread, 0, Tuning.DREAD_MAX);
    }

    public static int tier(ServerPlayer player) {
        int d = get(player);
        return d < Tuning.TIER_1 ? 0 : d < Tuning.TIER_2 ? 1 : d < Tuning.TIER_3 ? 2 : 3;
    }

    public static boolean isMarked(ServerPlayer player) {
        DreadState.PlayerDread st = state(player);
        return st != null && st.maxEver >= Tuning.DREAD_MAX;
    }

    public static boolean isThreat(ServerPlayer player) {
        DreadState.PlayerDread st = state(player);
        return st != null && st.threat;
    }

    public static void setThreat(ServerPlayer player, boolean value) {
        DreadState.PlayerDread st = state(player);
        if (st != null && st.threat != value) {
            st.threat = value;
            markDirty(player);
        }
    }

    public static void add(ServerPlayer player, String source, int delta) {
        MinecraftServer server = player.getServer();
        DreadState.PlayerDread st = state(player);
        if (server == null || st == null) {
            return;
        }
        ensureNight(server, player, st);
        if (delta > 0) {
            int cap = Tuning.capFor(source);
            if (cap >= 0) {
                int used = st.accrual.getOrDefault(source, 0);
                if (used >= cap) {
                    return;
                }
                delta = Math.min(delta, cap - used);
            }
        }
        int old = st.dread;
        int now = Mth.clamp(old + delta, 0, Tuning.DREAD_MAX);
        if (now == old) {
            return;
        }
        st.dread = now;
        st.accrual.merge(source, delta, Integer::sum);
        if (now > st.maxEver) {
            st.maxEver = now;
        }
        st.marked = st.maxEver >= Tuning.DREAD_MAX;
        st.lastDreadEvent = System.currentTimeMillis();
        markDirty(player);
    }

    public static boolean tryConsumeBudget(ServerPlayer player, String key, int maxPerNight) {
        MinecraftServer server = player.getServer();
        DreadState.PlayerDread st = state(player);
        if (server == null || st == null || maxPerNight <= 0) {
            return false;
        }
        ensureNight(server, player, st);
        int used = st.budgets.getOrDefault(key, 0);
        if (used >= maxPerNight) {
            return false;
        }
        st.budgets.put(key, used + 1);
        markDirty(player);
        return true;
    }

    public static long timer(ServerPlayer player, String key) {
        DreadState.PlayerDread st = state(player);
        return st == null ? 0L : st.timers.getOrDefault(key, 0L);
    }

    public static void setTimer(ServerPlayer player, String key, long epochMs) {
        DreadState.PlayerDread st = state(player);
        if (st != null) {
            st.timers.put(key, epochMs);
            markDirty(player);
        }
    }

    public static void passiveTick(ServerPlayer player) {
        DreadState.PlayerDread st = state(player);
        if (st == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server != null) {
            ensureNight(server, player, st);
        }
        long now = System.currentTimeMillis();
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();
        boolean night = NightDirector.isNight(level);
        boolean skyVisible = level.canSeeSky(feet);
        int blockLight = level.getBrightness(LightLayer.BLOCK, feet);
        // Raw SKY layer does not dim at night; subtract skyDarken for an effective value.
        int effectiveSky = level.getBrightness(LightLayer.SKY, feet) - level.getSkyDarken();

        if (night && skyVisible && blockLight <= Tuning.DARKNESS_MAX_BLOCK_LIGHT) {
            interval(player, st, "darkness", now, Tuning.DARKNESS_INTERVAL_MS, 1);
        }
        if (!skyVisible && st.dread < Tuning.UNDERGROUND_MAX_DREAD) {
            interval(player, st, "underground", now, Tuning.UNDERGROUND_INTERVAL_MS, 1);
        }
        if (blockLight >= Tuning.LIT_ROOM_MIN_BLOCK_LIGHT && !skyVisible) {
            interval(player, st, "lit_room", now, Tuning.LIT_ROOM_INTERVAL_MS, -1);
        }
        if (effectiveSky >= Tuning.DAYLIGHT_MIN_SKY_LIGHT) {
            interval(player, st, "daylight", now, Tuning.DAYLIGHT_INTERVAL_MS, -1);
        }
        if (!level.getEntitiesOfClass(Cat.class, player.getBoundingBox().inflate(Tuning.CAT_RADIUS), Cat::isTame).isEmpty()) {
            interval(player, st, "cat", now, Tuning.CAT_INTERVAL_MS, -1);
        }

        if (now - st.lastDreadEvent >= Tuning.DECAY_QUIET_MS && now - st.lastDecay >= Tuning.DECAY_INTERVAL_MS) {
            int floor = st.maxEver < Tuning.DECAY_FLOOR_THRESHOLD ? 0 : Tuning.DECAY_FLOOR;
            if (st.dread > floor) {
                st.lastDecay = now;
                add(player, "decay", -1);
            }
        }
    }

    public static void onSlept(ServerPlayer player) {
        add(player, "sleep", Tuning.SLEEP_RELIEF);
    }

    public static void onDeath(ServerPlayer player) {
        DreadState.PlayerDread st = state(player);
        if (st == null) {
            return;
        }
        st.dread = Math.min(st.dread, Tuning.DEATH_DREAD_CAP);
        if (st.marked) {
            st.maxEver = Math.min(st.maxEver, Tuning.DEATH_MARKED_MAXEVER);
            st.marked = st.maxEver >= Tuning.DREAD_MAX;
        }
        st.lastDreadEvent = System.currentTimeMillis();
        markDirty(player);
    }

    private static DreadState.PlayerDread state(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : DreadState.get(server).player(player.getUUID());
    }

    private static void markDirty(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            DreadState.get(server).setDirty();
        }
    }

    // nightId = overworld day count while night; on change, reset budgets and accrual.
    private static void ensureNight(MinecraftServer server, ServerPlayer player, DreadState.PlayerDread st) {
        if (!NightDirector.isNight(server.overworld())) {
            return;
        }
        int id = (int) (server.overworld().getDayTime() / Tuning.DAY_LENGTH_TICKS);
        if (id != st.nightId) {
            st.nightId = id;
            st.accrual.clear();
            st.budgets.clear();
            markDirty(player);
        }
    }

    private static void interval(ServerPlayer player, DreadState.PlayerDread st, String key, long now, long gapMs, int delta) {
        if (now - st.timers.getOrDefault(key, 0L) >= gapMs) {
            st.timers.put(key, now);
            add(player, key, delta);
        }
    }
}
