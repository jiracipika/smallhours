package dev.jiracipika.smallhours.neoforge;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.client.OmenRenderer;
import dev.jiracipika.smallhours.client.PatientRenderer;
import dev.jiracipika.smallhours.client.SmallHoursModelLayers;
import dev.jiracipika.smallhours.client.UnderstudyRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = SmallHours.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SmallHoursNeoForgeClient {

    private SmallHoursNeoForgeClient() {
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SmallHoursModelLayers.OMEN, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64));
        event.registerLayerDefinition(SmallHoursModelLayers.UNDERSTUDY, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64));
        event.registerLayerDefinition(SmallHoursModelLayers.PATIENT, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EntityRenderers.register(SmallHours.OMEN.get(), OmenRenderer::new);
        EntityRenderers.register(SmallHours.UNDERSTUDY.get(), UnderstudyRenderer::new);
        EntityRenderers.register(SmallHours.PATIENT.get(), PatientRenderer::new);
    }
}
