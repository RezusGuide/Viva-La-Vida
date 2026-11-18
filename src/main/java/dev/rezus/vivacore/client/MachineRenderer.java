package dev.rezus.vivacore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.rezus.vivacore.VivaClientConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.army.MachineType;
import dev.rezus.vivacore.army.OrionMachine;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Рендерер машин ОРИОНа (GeckoLib): модель geo/entity/&lt;id&gt;.geo.json, анимации animations/entity/&lt;id&gt;.animation.json,
 * текстура textures/entity/&lt;id&gt;.png, свечение — AutoGlowingGeoLayer по маске &lt;id&gt;_glowmask.png.
 * Ванильный «переворот на бок» при смерти выключен — его заменяет анимация death модели.
 */
public class MachineRenderer extends GeoEntityRenderer<OrionMachine> {
    public MachineRenderer(EntityRendererProvider.Context context, MachineType type) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, type.id)));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = Math.min(type.width * 0.5f, 1.5f);
    }

    @Override
    protected float getDeathMaxRotation(OrionMachine animatable) {
        return 0f;
    }

    /**
     * Рой «Туманность»: на сервере одна сущность, клиент рисует вокруг неё N моделек swarm_unit
     * (N — клиентский конфиг army.swarmUnits). Каждая моделька кружит по своей орбите внутри хитбокса роя
     * и чуть шире; орбиты зависят от id сущности, поэтому у разных роёв облако разное.
     */
    public static class Swarm extends MachineRenderer {
        public Swarm(EntityRendererProvider.Context context) {
            super(context, MachineType.SWARM_UNIT);
            this.shadowRadius = 0.8f;
        }

        @Override
        public void render(OrionMachine entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            int n = VivaClientConfig.swarmUnits();
            float time = (entity.tickCount + partialTick) / 20f;
            long seed = entity.getId() * 341873128712L;
            for (int i = 0; i < n; i++) {
                double a = hash(seed, i, 1), b = hash(seed, i, 2), c = hash(seed, i, 3);
                float radius = 0.4f + (float) a * 1.4f;
                float speed = 0.6f + (float) b * 1.2f;
                float angle = (float) (c * Math.PI * 2) + time * speed;
                float y = 0.2f + (float) b * 1.3f + Mth.sin(time * 2f + i) * 0.15f;
                pose.pushPose();
                pose.translate(Mth.cos(angle) * radius, y, Mth.sin(angle) * radius);
                super.render(entity, entityYaw - angle * Mth.RAD_TO_DEG, partialTick, pose, buffers, light);
                pose.popPose();
            }
        }

        private static double hash(long seed, int i, int salt) {
            long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
            h ^= h >>> 33;
            h *= 0xff51afd7ed558ccdL;
            h ^= h >>> 33;
            return (h >>> 11) / (double) (1L << 53);
        }
    }
}
