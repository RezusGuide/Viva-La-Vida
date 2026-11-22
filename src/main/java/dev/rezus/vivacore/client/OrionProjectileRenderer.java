package dev.rezus.vivacore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.army.OrionProjectile;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Снаряды ОРИОНа — плоские спрайты агента моделей, всегда повёрнутые к камере и светящиеся:
 * bullet, bullet_piercing, shell_explosive (снаряд танка и ракета).
 */
public class OrionProjectileRenderer extends EntityRenderer<OrionProjectile> {
    private static final ResourceLocation BULLET = tex("bullet");
    private static final ResourceLocation PIERCING = tex("bullet_piercing");
    private static final ResourceLocation SHELL = tex("shell_explosive");

    public OrionProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "textures/entity/projectiles/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(OrionProjectile entity) {
        return switch (entity.kind()) {
            case BULLET -> BULLET;
            case PIERCING -> PIERCING;
            case SHELL, ROCKET -> SHELL;
        };
    }

    @Override
    public void render(OrionProjectile entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float size = switch (entity.kind()) {
            case BULLET -> 0.35f;
            case PIERCING -> 0.45f;
            case SHELL, ROCKET -> 0.6f;
        };
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(size, size, size);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        Matrix4f m = pose.last().pose();
        int full = LightTexture.FULL_BRIGHT;
        vertex(vc, pose, m, -0.5f, -0.5f, 0, 1, full);
        vertex(vc, pose, m, 0.5f, -0.5f, 1, 1, full);
        vertex(vc, pose, m, 0.5f, 0.5f, 1, 0, full);
        vertex(vc, pose, m, -0.5f, 0.5f, 0, 0, full);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, Matrix4f m, float x, float y, float u, float v, int light) {
        vc.addVertex(m, x, y, 0).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose.last(), 0, 1, 0);
    }
}
