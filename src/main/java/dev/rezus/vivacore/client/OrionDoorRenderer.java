package dev.rezus.vivacore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.orion.block.OrionDoorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Створка двери ОРИОНа: уезжает вверх (или вниз), а не поворачивается.
 * Рисуются обычные модели закрытой двери (нижняя и верхняя половины), сдвинутые целиком на 2 × «насколько открыта»
 * блока (задача 11: раньше створка сжималась по высоте и выглядела как бумага). Открытая створка стоит в двух
 * блоках над проёмом: в непрозрачной стене (стена ОРИОНа в лаборатории) её не видно, без стены сверху она висит
 * над проходом.
 */
public class OrionDoorRenderer implements BlockEntityRenderer<OrionDoorBlockEntity> {
    private final BlockRenderDispatcher blocks;

    public OrionDoorRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(OrionDoorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof DoorBlock) || be.getLevel() == null) {
            return;
        }
        boolean open = state.getValue(DoorBlock.OPEN);
        double now = be.getLevel().getGameTime() + partialTick;
        if (be.lastOpen == null) {
            be.lastOpen = open; // первый кадр — сразу в конечном положении, без анимации
        } else if (be.lastOpen != open) {
            be.lastOpen = open;
            be.changeTick = now;
        }
        float t = (float) Mth.clamp((now - be.changeTick) / VivaConfig.orionDoorSlideTicks(), 0.0, 1.0);
        float opened = open ? t : 1 - t;
        boolean down = VivaConfig.orionDoorSlidesDown();
        pose.pushPose();
        pose.translate(0, (down ? -2f : 2f) * opened, 0);

        BlockState closed = state.setValue(DoorBlock.OPEN, false);
        VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
        renderHalf(pose, consumer, closed.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), light, overlay);
        pose.translate(0, 1, 0);
        renderHalf(pose, consumer, closed.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), light, overlay);
        pose.popPose();
    }

    private void renderHalf(PoseStack pose, VertexConsumer consumer, BlockState state, int light, int overlay) {
        blocks.getModelRenderer().renderModel(pose.last(), consumer, state, blocks.getBlockModel(state),
                1f, 1f, 1f, light, overlay);
    }

    /** Створка — 2 блока от нижней половины и ещё до 2 блоков вверх или вниз, куда она уезжает. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(OrionDoorBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0, 3, 0).expandTowards(0, -2, 0);
    }

    /** Создаёт рендерер (для регистрации). */
    public static BlockEntityRenderer<OrionDoorBlockEntity> create(BlockEntityRendererProvider.Context ctx) {
        return new OrionDoorRenderer(ctx);
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
