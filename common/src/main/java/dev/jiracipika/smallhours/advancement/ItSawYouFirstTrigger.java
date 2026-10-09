package dev.jiracipika.smallhours.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * "It Saw You First" — fired whenever the Omen vanishes with a witness. Advancements
 * are one-shot, so re-sightings are free. The instance is a singleton on SmallHours,
 * registered into TRIGGER_TYPES by each loader under smallhours:it_saw_you_first.
 */
public final class ItSawYouFirstTrigger extends SimpleCriterionTrigger<ItSawYouFirstTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        super.trigger(player, instance -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        ContextAwarePredicate.CODEC.optionalFieldOf("player")
                                .forGetter(TriggerInstance::player))
                        .apply(instance, TriggerInstance::new));
    }
}
