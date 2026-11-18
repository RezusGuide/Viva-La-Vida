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
 * Рисуются обычные модели закрытой двери (нижняя и верхняя половины), сжатые по высоте к кромке, куда уезжает
 * створка: видимая доля = 1 − «насколько открыта». Так створка не выходит за проём.
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
        float visible = 1 - opened;
        if (visible <= 0.001f) {
            return;
        }
        boolean down = VivaConfig.orionDoorSlidesDown();
        pose.pushPose();
        float anchor = down ? 0f : 2f;
        pose.translate(0, anchor, 0);
        pose.scale(1, visible, 1);
        pose.translate(0, -anchor, 0);

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

    /** Створка рисуется на 2 блока вверх от нижней половины. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(OrionDoorBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0, 1, 0);
    }

    /** Создаёт рендерер (для регистрации). */
    public static BlockEntityRenderer<OrionDoorBlockEntity> create(BlockEntityRendererProvider.Context ctx) {
        return new OrionDoorRenderer(ctx);
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
