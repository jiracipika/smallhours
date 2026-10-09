package dev.jiracipika.smallhours;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;

import dev.jiracipika.smallhours.advancement.ItSawYouFirstTrigger;
import dev.jiracipika.smallhours.entity.OmenEntity;
import dev.jiracipika.smallhours.entity.PatientEntity;
import dev.jiracipika.smallhours.entity.UnderstudyEntity;

public final class SmallHours {
    public static final String MODID = "smallhours";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    // Assigned here; loaders register these exact instances.
    public static SoundEvent SND_OMEN_STING;
    public static SoundEvent SND_UNDERSTUDY_SHRIEK;
    public static SoundEvent SND_UNDERSTUDY_CHITTER;
    public static SoundEvent SND_PATIENT_SCREAM;
    public static SoundEvent SND_WHISPER;

    // Loader-registered singleton: the advancement trigger behind "It Saw You First".
    public static final ItSawYouFirstTrigger IT_SAW_YOU_FIRST = new ItSawYouFirstTrigger();

    // Damage type behind the Patient's kills; data/smallhours/damage_type/stopped_running.json.
    public static final ResourceKey<DamageType> STOPPED_RUNNING = ResourceKey.create(Registries.DAMAGE_TYPE, id("stopped_running"));

    // Holder slots assigned by loader init; null until then, always .get() at use time.
    public static Supplier<EntityType<OmenEntity>> OMEN;
    public static Supplier<EntityType<UnderstudyEntity>> UNDERSTUDY;
    public static Supplier<EntityType<PatientEntity>> PATIENT;

    static {
        SND_OMEN_STING = SoundEvent.createVariableRangeEvent(id("omen_sting"));
        SND_UNDERSTUDY_SHRIEK = SoundEvent.createVariableRangeEvent(id("understudy_shriek"));
        SND_UNDERSTUDY_CHITTER = SoundEvent.createVariableRangeEvent(id("understudy_chitter"));
        SND_PATIENT_SCREAM = SoundEvent.createVariableRangeEvent(id("patient_scream"));
        SND_WHISPER = SoundEvent.createVariableRangeEvent(id("whisper"));
    }

    private SmallHours() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
