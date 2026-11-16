package dev.rezus.vivacore.client;

import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.VivaRegistries;
import dev.rezus.vivacore.army.ScoutDrone;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Только клиент: рендереры сущностей Viva Core. */
public final class VivaClient {
    private VivaClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(VivaClient::onRegisterRenderers);
        DevScreenshots.initIfEnabled();
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VivaRegistries.SCOUT_DRONE.get(), ScoutDroneRenderer::new);
    }

    /**
     * Дрон-разведчик: модель, анимации и текстура GeckoLib по путям
     * geo/entity/scout_drone.geo.json, animations/entity/scout_drone.animation.json, textures/entity/scout_drone.png;
     * свечение — AutoGlowingGeoLayer по маске textures/entity/scout_drone_glowmask.png.
     */
    public static final class ScoutDroneRenderer extends GeoEntityRenderer<ScoutDrone> {
        public ScoutDroneRenderer(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(
                    ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "scout_drone")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            this.shadowRadius = 0.5f;
        }
    }
}
