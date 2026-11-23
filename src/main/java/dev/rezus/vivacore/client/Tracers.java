package dev.rezus.vivacore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rezus.vivacore.VivaClientConfig;
import dev.rezus.vivacore.army.ArmyNetwork;
import dev.rezus.vivacore.army.ArmyParticles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Только клиент: трассеры мгновенных выстрелов ОРИОНа (задача 9, тест 38). Сервер шлёт «выстрел из A в B»
 * ({@link ArmyNetwork.Tracer}); здесь — вспышка у ствола сразу, светящаяся черта, которая летит от A к B со скоростью
 * army.tracerSpeed (клиентский конфиг), и искры в B, когда черта долетела (если выстрел во что-то попал).
 */
public final class Tracers {
    private Tracers() {}

    private static final int MAX = 512;
    private static final List<Tracer> ACTIVE = new ArrayList<>();

    private static final class Tracer {
        final Vec3 from;
        final Vec3 dir;
        final double dist;
        final byte kind;
        final byte impact;
        int age;
        boolean sparked;

        Tracer(Vec3 from, Vec3 to, byte kind, byte impact) {
            this.from = from;
            Vec3 d = to.subtract(from);
            this.dist = d.length();
            this.dir = dist < 1e-6 ? new Vec3(0, 0, 1) : d.scale(1 / dist);
            this.kind = kind;
            this.impact = impact;
        }
    }

    public static void add(ArmyNetwork.Tracer p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 from = p.from();
        level.addParticle(ArmyParticles.MUZZLE_FLASH.get(), from.x, from.y, from.z, 0, 0, 0);
        if (ACTIVE.size() >= MAX) ACTIVE.remove(0);
        ACTIVE.add(new Tracer(from, p.to(), p.kind(), p.impact()));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        double speed = VivaClientConfig.tracerSpeed();
        double length = VivaClientConfig.tracerLength();
        RandomSource random = level.random;
        Iterator<Tracer> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Tracer t = it.next();
            t.age++;
            double traveled = t.age * speed;
            if (!t.sparked && traveled >= t.dist) {
                t.sparked = true;
                if (t.impact != ArmyNetwork.IMPACT_NONE) {
                    Vec3 end = t.from.add(t.dir.scale(t.dist));
                    int count = t.impact == ArmyNetwork.IMPACT_BLOCK ? 5 : 3;
                    for (int i = 0; i < count; i++) {
                        level.addParticle(ArmyParticles.SPARK.get(),
                                end.x + random.nextGaussian() * 0.1, end.y + random.nextGaussian() * 0.1, end.z + random.nextGaussian() * 0.1,
                                random.nextGaussian() * 0.05, random.nextGaussian() * 0.05, random.nextGaussian() * 0.05);
                    }
                }
            }
            if (traveled >= t.dist + length) it.remove();
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double speed = VivaClientConfig.tracerSpeed();
        double length = VivaClientConfig.tracerLength();
        double width = VivaClientConfig.tracerWidth();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        for (Tracer t : ACTIVE) {
            double traveled = (t.age + partial) * speed;
            double head = Math.min(traveled, t.dist);
            double tail = Math.max(0, Math.min(traveled - length, t.dist));
            if (head - tail < 1e-3) continue;
            Vec3 a = t.from.add(t.dir.scale(tail)).subtract(cam);
            Vec3 b = t.from.add(t.dir.scale(head)).subtract(cam);
            // задача 11: у камеры (выстрел в самого игрока или мимо лица) черту не рисуем — иначе она во весь экран
            double near = VivaClientConfig.hideNearCamera();
            if (near > 0 && (a.lengthSqr() < near * near || b.lengthSqr() < near * near)) continue;
            // лента, повёрнутая к камере (камера в начале координат)
            Vec3 side = b.subtract(a).cross(a.add(b).scale(-0.5));
            if (side.lengthSqr() < 1e-10) continue;
            side = side.normalize().scale(width);
            int r = 255;
            int g = t.kind == ArmyNetwork.KIND_SNIPER ? 70 : 215;
            int bl = t.kind == ArmyNetwork.KIND_SNIPER ? 60 : 120;
            vertex(vc, m, a.subtract(side), r, g, bl, 0);
            vertex(vc, m, a.add(side), r, g, bl, 0);
            vertex(vc, m, b.add(side), r, g, bl, 230);
            vertex(vc, m, b.subtract(side), r, g, bl, 230);
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    /** Для dev-проверки: сколько трассеров сейчас летит. */
    public static int active() {
        return ACTIVE.size();
    }
}
