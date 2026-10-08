package dev.jiracipika.smallhours.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.Tuning;
import dev.jiracipika.smallhours.entity.OmenEntity;

public class OmenRenderer extends MobRenderer<OmenEntity, HumanoidModel<OmenEntity>> {
    public OmenRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(SmallHoursModelLayers.OMEN)), Tuning.CLIENT_OMEN_SHADOW);
    }

    @Override
    public ResourceLocation getTextureLocation(OmenEntity entity) {
        return SmallHours.id("textures/entity/omen.png");
    }

    @Override
    protected void scale(OmenEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(Tuning.CLIENT_OMEN_SCALE, Tuning.CLIENT_OMEN_SCALE, Tuning.CLIENT_OMEN_SCALE);
    }
}
