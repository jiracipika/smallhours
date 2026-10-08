package dev.jiracipika.smallhours.client;

import net.minecraft.client.model.geom.ModelLayerLocation;

import dev.jiracipika.smallhours.SmallHours;

public final class SmallHoursModelLayers {
    public static final ModelLayerLocation OMEN = new ModelLayerLocation(SmallHours.id("omen"), "main");
    public static final ModelLayerLocation UNDERSTUDY = new ModelLayerLocation(SmallHours.id("understudy"), "main");
    public static final ModelLayerLocation PATIENT = new ModelLayerLocation(SmallHours.id("patient"), "main");

    private SmallHoursModelLayers() {
    }
}
