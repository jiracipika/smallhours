package dev.jiracipika.smallhours.neoforge;

import java.util.function.Supplier;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.director.NightDirector;
import dev.jiracipika.smallhours.dread.Dread;
import dev.jiracipika.smallhours.entity.OmenEntity;
import dev.jiracipika.smallhours.entity.PatientEntity;
import dev.jiracipika.smallhours.entity.UnderstudyEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(SmallHours.MODID)
public final class SmallHoursNeoForge {

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, SmallHours.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, SmallHours.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SmallHours.MODID);

    public static final Supplier<EntityType<OmenEntity>> OMEN = ENTITIES.register("omen",
        () -> EntityType.Builder.of(OmenEntity::new, MobCategory.MONSTER).sized(0.6F, 2.0F).clientTrackingRange(10).build("omen"));
    public static final Supplier<EntityType<UnderstudyEntity>> UNDERSTUDY = ENTITIES.register("understudy",
        () -> EntityType.Builder.of(UnderstudyEntity::new, MobCategory.MONSTER).sized(0.45F, 0.95F).clientTrackingRange(8).build("understudy"));
    public static final Supplier<EntityType<PatientEntity>> PATIENT = ENTITIES.register("patient",
        () -> EntityType.Builder.of(PatientEntity::new, MobCategory.MONSTER).sized(0.7F, 2.15F).clientTrackingRange(10).build("patient"));

    // 21.1 has no SpawnEggItem(Supplier, ...) ctor; DeferredSpawnEggItem is NeoForge's lazy supplier-based equivalent.
    public static final DeferredItem<SpawnEggItem> OMEN_SPAWN_EGG = ITEMS.registerItem("omen_spawn_egg",
        props -> new DeferredSpawnEggItem(SmallHours.OMEN, 0x1B1B26, 0xD8D4CC, props), new Item.Properties());
    public static final DeferredItem<SpawnEggItem> UNDERSTUDY_SPAWN_EGG = ITEMS.registerItem("understudy_spawn_egg",
        props -> new DeferredSpawnEggItem(SmallHours.UNDERSTUDY, 0x3A3128, 0xCFC5A5, props), new Item.Properties());
    public static final DeferredItem<SpawnEggItem> PATIENT_SPAWN_EGG = ITEMS.registerItem("patient_spawn_egg",
        props -> new DeferredSpawnEggItem(SmallHours.PATIENT, 0x0D0D10, 0x6B6770, props), new Item.Properties());

    static {
        SOUNDS.register("omen_sting", () -> SmallHours.SND_OMEN_STING);
        SOUNDS.register("understudy_shriek", () -> SmallHours.SND_UNDERSTUDY_SHRIEK);
        SOUNDS.register("understudy_chitter", () -> SmallHours.SND_UNDERSTUDY_CHITTER);
        SOUNDS.register("patient_scream", () -> SmallHours.SND_PATIENT_SCREAM);
        SOUNDS.register("whisper", () -> SmallHours.SND_WHISPER);
    }

    public SmallHoursNeoForge(IEventBus modEventBus) {
        SmallHours.OMEN = OMEN::get;
        SmallHours.UNDERSTUDY = UNDERSTUDY::get;
        SmallHours.PATIENT = PATIENT::get;
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    @EventBusSubscriber(modid = SmallHours.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBusEvents {

        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void onEntityAttributes(EntityAttributeCreationEvent event) {
            event.put(SmallHours.OMEN.get(), OmenEntity.createAttributes().build());
            event.put(SmallHours.UNDERSTUDY.get(), UnderstudyEntity.createAttributes().build());
            event.put(SmallHours.PATIENT.get(), PatientEntity.createAttributes().build());
        }

        @SubscribeEvent
        public static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                event.accept(OMEN_SPAWN_EGG);
                event.accept(UNDERSTUDY_SPAWN_EGG);
                event.accept(PATIENT_SPAWN_EGG);
            }
        }
    }

    @EventBusSubscriber(modid = SmallHours.MODID)
    public static final class GameEvents {

        private GameEvents() {
        }

        @SubscribeEvent
        public static void onServerTickPost(ServerTickEvent.Post event) {
            NightDirector.serverTick(event.getServer());
        }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                Dread.onDeath(player);
            }
        }

        @SubscribeEvent
        public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                Dread.onSlept(player);
            }
        }
    }
}
