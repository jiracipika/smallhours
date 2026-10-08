package dev.jiracipika.smallhours.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import dev.jiracipika.smallhours.SmallHours;
import dev.jiracipika.smallhours.Tuning;
import dev.jiracipika.smallhours.entity.UnderstudyEntity;

public class UnderstudyRenderer extends MobRenderer<UnderstudyEntity, HumanoidModel<UnderstudyEntity>> {
    public UnderstudyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(SmallHoursModelLayers.UNDERSTUDY)),
                Tuning.CLIENT_UNDERSTUDY_SHADOW);
    }

    @Override
    public ResourceLocation getTextureLocation(UnderstudyEntity entity) {
        return SmallHours.id("textures/entity/understudy.png");
    }

    @Override
    protected void scale(UnderstudyEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(Tuning.CLIENT_UNDERSTUDY_SCALE, Tuning.CLIENT_UNDERSTUDY_SCALE, Tuning.CLIENT_UNDERSTUDY_SCALE);
    }
}
