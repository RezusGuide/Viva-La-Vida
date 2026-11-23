package dev.rezus.vivacore.client;

import dev.rezus.vivacore.VivaRegistries;
import dev.rezus.vivacore.army.ArmyEntities;
import dev.rezus.vivacore.army.MachineType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Только клиент: рендереры сущностей Viva Core. */
public final class VivaClient {
    private VivaClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(VivaClient::onRegisterRenderers);
        modBus.addListener(ArmyParticleProviders::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Tracers::onClientTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Tracers::onRenderLevel);
        DevScreenshots.initIfEnabled();
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (MachineType mt : MachineType.values()) {
            if (mt == MachineType.SWARM_UNIT) {
                event.registerEntityRenderer(ArmyEntities.type(mt), MachineRenderer.Swarm::new);
            } else {
                event.registerEntityRenderer(ArmyEntities.type(mt), ctx -> new MachineRenderer(ctx, mt));
            }
        }
        event.registerEntityRenderer(ArmyEntities.PROJECTILE.get(), OrionProjectileRenderer::new);
        event.registerBlockEntityRenderer(VivaRegistries.ORION_DOOR_BE.get(), OrionDoorRenderer::create);
    }
}
