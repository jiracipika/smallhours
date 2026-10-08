package dev.jiracipika.smallhours.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.Tuning;
import dev.jiracipika.smallhours.entity.PatientEntity;

public class PatientRenderer extends MobRenderer<PatientEntity, HumanoidModel<PatientEntity>> {
    public PatientRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(SmallHoursModelLayers.PATIENT)), Tuning.CLIENT_PATIENT_SHADOW);
    }

    @Override
    public ResourceLocation getTextureLocation(PatientEntity entity) {
        return SmallHours.id("textures/entity/patient.png");
    }

    @Override
    protected void scale(PatientEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(Tuning.CLIENT_PATIENT_SCALE, Tuning.CLIENT_PATIENT_SCALE, Tuning.CLIENT_PATIENT_SCALE);
    }
}
