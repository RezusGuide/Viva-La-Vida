package dev.rezus.vivacore.client;

import dev.rezus.vivacore.army.ArmyParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Частицы армии: вспышка выстрела (3 кадра, ярко, 3 тика), искры (3 кадра, падают), обломки машины
 * (болт, кабель, плита, жёлтый осколок — случайный спрайт, падают и катятся).
 */
public final class ArmyParticleProviders {
    private ArmyParticleProviders() {}

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ArmyParticles.MUZZLE_FLASH.get(), sprites -> (t, l, x, y, z, dx, dy, dz) ->
                new VivaParticle(l, x, y, z, 0, 0, 0, sprites, 4, 0f, (float) dev.rezus.vivacore.VivaClientConfig.muzzleFlashSize(), true, true)
                        .hideNearCamera());
        event.registerSpriteSet(ArmyParticles.SPARK.get(), sprites -> (t, l, x, y, z, dx, dy, dz) ->
                new VivaParticle(l, x, y, z, dx, dy, dz, sprites, 8 + l.random.nextInt(6), 0.6f, 0.08f, true, true));
        event.registerSpriteSet(ArmyParticles.DEBRIS.get(), sprites -> (t, l, x, y, z, dx, dy, dz) ->
                new VivaParticle(l, x, y, z, dx, dy + 0.15, dz, sprites, 30 + l.random.nextInt(20), 1.0f, 0.12f, false, false));
    }

    /** Простая частица со спрайтом: animated — кадры по возрасту, иначе случайный спрайт из набора. */
    static final class VivaParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final boolean animated;
        private final boolean bright;

        VivaParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
                     int lifetime, float gravity, float size, boolean animated, boolean bright) {
            super(level, x, y, z, dx, dy, dz);
            this.sprites = sprites;
            this.animated = animated;
            this.bright = bright;
            this.lifetime = lifetime;
            this.gravity = gravity;
            this.quadSize = size;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.hasPhysics = !animated;
            if (animated) setSpriteFromAge(sprites);
            else pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (animated && !removed) setSpriteFromAge(sprites);
        }

        /** Задача 11: вспышка у самой камеры (машина стреляет вплотную к игроку) не рисуется — не заслоняет экран. */
        private boolean hideNear;

        VivaParticle hideNearCamera() {
            hideNear = true;
            return this;
        }

        @Override
        public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTicks) {
            if (hideNear) {
                double near = dev.rezus.vivacore.VivaClientConfig.hideNearCamera() + 0.5;
                if (camera.getPosition().distanceToSqr(x, y, z) < near * near) return;
            }
            super.render(buffer, camera, partialTicks);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return bright ? 0xF000F0 : super.getLightColor(partialTick);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

}
