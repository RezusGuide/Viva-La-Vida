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
     * Сдвиг модели вверх (мех, страж ядра — геометрия ниже ног) и лазер снайпера: красный луч от ствола к цели,
     * пока снайпер целится (laserTarget синхронизируется с сервера).
     */
    @Override
    public void render(OrionMachine entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float scale = entity.modelScale();
        float offset = entity.machine().modelOffsetPx() / 16f * scale;
        this.shadowRadius = Math.min(entity.getBbWidth() * 0.5f, 3f);
        pose.pushPose();
        pose.translate(0, offset, 0);
        pose.scale(scale, scale, scale); // задача 11: масштаб модели из конфига army.<id>.scale
        super.render(entity, entityYaw, partialTick, pose, buffers, light);
        pose.popPose();
        int laser = entity.laserTarget();
        if (laser >= 0) {
            net.minecraft.world.entity.Entity target = entity.level().getEntity(laser);
            if (target != null) renderLaser(entity, target, partialTick, pose, buffers);
        }
    }

    /** Пока снайпер целится, рисуем его всегда: луч может пересекать экран, даже если сам снайпер за кадром. */
    @Override
    public boolean shouldRender(OrionMachine entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return entity.laserTarget() >= 0 || super.shouldRender(entity, frustum, x, y, z);
    }

    /** Отрисовка модели без сдвига и лазера (для моделек роя). */
    protected void renderUnshifted(OrionMachine entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /**
     * Лазер снайпера: лента от ствола к цели, всегда повёрнутая к камере (иначе вдоль луча она видна ребром),
     * и яркая точка-излучатель у ствола — её видно и тому, в кого целятся (он смотрит на луч в торец).
     */
    private static void renderLaser(OrionMachine shooter, net.minecraft.world.entity.Entity target, float partialTick,
                                    PoseStack pose, MultiBufferSource buffers) {
        net.minecraft.world.phys.Vec3 origin = shooter.getPosition(partialTick);
        net.minecraft.world.phys.Vec3 muzzle = shooter.combat().muzzleAt(partialTick).subtract(origin);
        net.minecraft.world.phys.Vec3 end = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.6, 0).subtract(origin);
        net.minecraft.world.phys.Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera()
                .getPosition().subtract(origin);
        var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
        var m = pose.last().pose();
        net.minecraft.world.phys.Vec3 beam = end.subtract(muzzle);
        net.minecraft.world.phys.Vec3 side = beam.cross(camera.subtract(muzzle));
        if (side.lengthSqr() < 1e-8) side = new net.minecraft.world.phys.Vec3(0, 1, 0);
        side = side.normalize().scale(0.035);
        quad(vc, m, muzzle.subtract(side), muzzle.add(side), end.add(side), end.subtract(side), 255, 30, 30, 220);
        // точка-излучатель у ствола: квадрат 0.18, повёрнутый к камере
        net.minecraft.world.phys.Vec3 toCam = camera.subtract(muzzle).normalize();
        net.minecraft.world.phys.Vec3 up = Math.abs(toCam.y) > 0.95 ? new net.minecraft.world.phys.Vec3(1, 0, 0) : new net.minecraft.world.phys.Vec3(0, 1, 0);
        net.minecraft.world.phys.Vec3 r = toCam.cross(up).normalize().scale(0.09);
        net.minecraft.world.phys.Vec3 u = r.cross(toCam).normalize().scale(0.09);
        quad(vc, m, muzzle.subtract(r).subtract(u), muzzle.add(r).subtract(u), muzzle.add(r).add(u), muzzle.subtract(r).add(u), 255, 60, 60, 255);
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m,
                             net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, net.minecraft.world.phys.Vec3 c,
                             net.minecraft.world.phys.Vec3 d, int r, int g, int bl, int alpha) {
        vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alpha);
        vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alpha);
        vc.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, alpha);
        vc.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(r, g, bl, alpha);
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
            renderCloud(entity, entityYaw, partialTick, pose, buffers, light);
        }

        private void renderCloud(OrionMachine entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
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
                renderUnit(entity, entityYaw - angle * Mth.RAD_TO_DEG, partialTick, pose, buffers, light);
                pose.popPose();
            }
        }

        private void renderUnit(OrionMachine entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            super.renderUnshifted(entity, yaw, partialTick, pose, buffers, light);
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
