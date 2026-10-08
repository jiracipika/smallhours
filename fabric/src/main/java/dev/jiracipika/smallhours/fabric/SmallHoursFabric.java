package dev.jiracipika.smallhours.fabric;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.director.NightDirector;
import dev.jiracipika.smallhours.dread.Dread;
import dev.jiracipika.smallhours.entity.OmenEntity;
import dev.jiracipika.smallhours.entity.PatientEntity;
import dev.jiracipika.smallhours.entity.UnderstudyEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public class SmallHoursFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EntityType<OmenEntity> omen = EntityType.Builder.of(OmenEntity::new, MobCategory.MONSTER)
                .sized(0.6F, 2.0F).clientTrackingRange(10).build("omen");
        Registry.register(BuiltInRegistries.ENTITY_TYPE, SmallHours.id("omen"), omen);
        SmallHours.OMEN = () -> omen;

        EntityType<UnderstudyEntity> understudy = EntityType.Builder.of(UnderstudyEntity::new, MobCategory.MONSTER)
                .sized(0.45F, 0.95F).clientTrackingRange(8).build("understudy");
        Registry.register(BuiltInRegistries.ENTITY_TYPE, SmallHours.id("understudy"), understudy);
        SmallHours.UNDERSTUDY = () -> understudy;

        EntityType<PatientEntity> patient = EntityType.Builder.of(PatientEntity::new, MobCategory.MONSTER)
                .sized(0.7F, 2.15F).clientTrackingRange(10).build("patient");
        Registry.register(BuiltInRegistries.ENTITY_TYPE, SmallHours.id("patient"), patient);
        SmallHours.PATIENT = () -> patient;

        registerSound("omen_sting", SmallHours.SND_OMEN_STING);
        registerSound("understudy_shriek", SmallHours.SND_UNDERSTUDY_SHRIEK);
        registerSound("understudy_chitter", SmallHours.SND_UNDERSTUDY_CHITTER);
        registerSound("patient_scream", SmallHours.SND_PATIENT_SCREAM);
        registerSound("whisper", SmallHours.SND_WHISPER);

        FabricDefaultAttributeRegistry.register(omen, OmenEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(understudy, UnderstudyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(patient, PatientEntity.createAttributes());

        Item omenEgg = new SpawnEggItem(omen, 0x1B1B26, 0xD8D4CC, new Item.Properties());
        Item understudyEgg = new SpawnEggItem(understudy, 0x3A3128, 0xCFC5A5, new Item.Properties());
        Item patientEgg = new SpawnEggItem(patient, 0x0D0D10, 0x6B6770, new Item.Properties());
        Registry.register(BuiltInRegistries.ITEM, SmallHours.id("omen_spawn_egg"), omenEgg);
        Registry.register(BuiltInRegistries.ITEM, SmallHours.id("understudy_spawn_egg"), understudyEgg);
        Registry.register(BuiltInRegistries.ITEM, SmallHours.id("patient_spawn_egg"), patientEgg);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> {
            entries.accept(omenEgg);
            entries.accept(understudyEgg);
            entries.accept(patientEgg);
        });

        ServerTickEvents.END_SERVER_TICK.register(NightDirector::serverTick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer sp) {
                Dread.onDeath(sp);
            }
        });
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayer sp) {
                Dread.onSlept(sp);
            }
        });

        SmallHours.LOG.info("Small Hours (fabric) initialized");
    }

    private static void registerSound(String path, SoundEvent sound) {
        Registry.register(BuiltInRegistries.SOUND_EVENT, SmallHours.id(path), sound);
    }
}
