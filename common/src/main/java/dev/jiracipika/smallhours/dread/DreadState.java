package dev.jiracipika.smallhours.dread;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

public class DreadState extends SavedData {
    private static final String DATA_NAME = "smallhours_dread";

    private final Map<UUID, PlayerDread> players = new HashMap<>();

    public static class PlayerDread {
        public int dread;
        public int maxEver;
        public boolean marked;
        public int nightId = -1;
        public final Map<String, Integer> accrual = new HashMap<>();
        public final Map<String, Integer> budgets = new HashMap<>();
        public final Map<String, Long> timers = new HashMap<>();
        public boolean threat;
        public long lastDreadEvent;
        public long lastDecay;
        // What the Patient took from this player and still holds at dawn — it
        // remembers, across nights, if you're marked.
        public ItemStack stolenCache = ItemStack.EMPTY;
    }

    public static DreadState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(DreadState::new, DreadState::load, null), DATA_NAME);
    }

    public PlayerDread player(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerDread());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerDread> entry : players.entrySet()) {
            PlayerDread d = entry.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("uuid", entry.getKey());
            t.putInt("dread", d.dread);
            t.putInt("maxEver", d.maxEver);
            t.putBoolean("marked", d.marked);
            t.putInt("nightId", d.nightId);
            t.put("accrual", writeIntMap(d.accrual));
            t.put("budgets", writeIntMap(d.budgets));
            t.put("timers", writeLongMap(d.timers));
            t.putBoolean("threat", d.threat);
            t.putLong("lastDreadEvent", d.lastDreadEvent);
            t.putLong("lastDecay", d.lastDecay);
            if (!d.stolenCache.isEmpty()) {
                t.put("stolen", d.stolenCache.save(registries));
            }
            list.add(t);
        }
        tag.put("players", list);
        return tag;
    }

    public static DreadState load(CompoundTag tag, HolderLookup.Provider registries) {
        DreadState state = new DreadState();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("uuid")) {
                continue;
            }
            PlayerDread d = new PlayerDread();
            d.dread = t.getInt("dread");
            d.maxEver = t.getInt("maxEver");
            d.marked = t.getBoolean("marked");
            d.nightId = t.getInt("nightId");
            readIntMap(t.getList("accrual", Tag.TAG_COMPOUND), d.accrual);
            readIntMap(t.getList("budgets", Tag.TAG_COMPOUND), d.budgets);
            readLongMap(t.getList("timers", Tag.TAG_COMPOUND), d.timers);
            d.threat = t.getBoolean("threat");
            d.lastDreadEvent = t.getLong("lastDreadEvent");
            d.lastDecay = t.getLong("lastDecay");
            if (t.contains("stolen")) {
                d.stolenCache = ItemStack.parse(registries, t.get("stolen")).orElse(ItemStack.EMPTY);
            }
            state.players.put(t.getUUID("uuid"), d);
        }
        return state;
    }

    private static ListTag writeIntMap(Map<String, Integer> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("k", e.getKey());
            t.putInt("v", e.getValue());
            list.add(t);
        }
        return list;
    }

    private static ListTag writeLongMap(Map<String, Long> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> e : map.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("k", e.getKey());
            t.putLong("v", e.getValue());
            list.add(t);
        }
        return list;
    }

    private static void readIntMap(ListTag list, Map<String, Integer> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            into.put(t.getString("k"), t.getInt("v"));
        }
    }

    private static void readLongMap(ListTag list, Map<String, Long> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            into.put(t.getString("k"), t.getLong("v"));
        }
    }
}
