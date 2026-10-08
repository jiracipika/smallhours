package dev.jiracipika.smallhours.mimic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import dev.jiracipika.smallhours.Tuning;

/**
 * Per-player ring buffer of sounds the player personally triggered: block
 * breaks, placements, doors, chests. The Understudy draws its lures from here,
 * so what calls to you in the dark is the noise you made earlier tonight.
 * Server thread only.
 */
public final class SoundMimicry {
    private record Entry(SoundEvent sound, long tick) {
    }

    private static final Map<UUID, Deque<Entry>> RECENT = new HashMap<>();

    private SoundMimicry() {
    }

    public static void recordUse(ServerPlayer player, BlockState state, ItemStack held) {
        SoundEvent sound = classifyUse(state, held);
        if (sound != null) {
            record(player, sound);
        }
    }

    public static void recordBreak(ServerPlayer player, BlockState state) {
        record(player, state.getSoundType().getBreakSound());
    }

    /** Freshest-first, deduplicated, window-filtered pool for the target. */
    public static List<SoundEvent> pool(ServerPlayer player) {
        long now = player.level().getGameTime();
        Deque<Entry> deque = RECENT.get(player.getUUID());
        if (deque == null) {
            return List.of();
        }
        deque.removeIf(entry -> now - entry.tick() > Tuning.MIMICRY_WINDOW_TICKS);
        List<SoundEvent> out = new ArrayList<>();
        Set<SoundEvent> seen = new HashSet<>();
        for (var it = deque.descendingIterator(); it.hasNext() && out.size() < Tuning.MIMICRY_MAX_PER_PLAYER;) {
            SoundEvent sound = it.next().sound();
            if (seen.add(sound)) {
                out.add(sound);
            }
        }
        return out;
    }

    private static void record(ServerPlayer player, SoundEvent sound) {
        Deque<Entry> deque = RECENT.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
        deque.addLast(new Entry(sound, player.level().getGameTime()));
        while (deque.size() > Tuning.MIMICRY_MAX_PER_PLAYER * 4) {
            deque.removeFirst();
        }
    }

    private static SoundEvent classifyUse(BlockState state, ItemStack held) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock) {
            return SoundEvents.WOODEN_DOOR_OPEN;
        }
        if (block instanceof ChestBlock || block instanceof EnderChestBlock
                || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
            return SoundEvents.CHEST_OPEN;
        }
        if (held.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState().getSoundType().getPlaceSound();
        }
        return null;
    }
}
