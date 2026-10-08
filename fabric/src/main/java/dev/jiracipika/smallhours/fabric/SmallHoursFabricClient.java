package dev.jiracipika.smallhours.fabric;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.client.OmenRenderer;
import dev.jiracipika.smallhours.client.PatientRenderer;
import dev.jiracipika.smallhours.client.SmallHoursModelLayers;
import dev.jiracipika.smallhours.client.UnderstudyRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

@Environment(EnvType.CLIENT)
public class SmallHoursFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(SmallHoursModelLayers.OMEN,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64));
        EntityModelLayerRegistry.registerModelLayer(SmallHoursModelLayers.UNDERSTUDY,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64));
        EntityModelLayerRegistry.registerModelLayer(SmallHoursModelLayers.PATIENT,
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64));

        EntityRendererRegistry.register(SmallHours.OMEN.get(), OmenRenderer::new);
        EntityRendererRegistry.register(SmallHours.UNDERSTUDY.get(), UnderstudyRenderer::new);
        EntityRendererRegistry.register(SmallHours.PATIENT.get(), PatientRenderer::new);
    }
}
