package dev.rezus.vivacore.zhiva.device;

import com.mojang.serialization.MapCodec;
import dev.rezus.vivacore.orion.block.FacingShapedBlock;
import dev.rezus.vivacore.orion.block.OrionBlocks;
import dev.rezus.vivacore.zhiva.net.ZhivaNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * «Продвинутое устройство имплантирования» (тест 36). ПКМ → сервер отправляет игроку экран-терминал
 * с его состоянием Живы. Модель — от агента моделей (с поворотом и формой по модели).
 */
public class ImplantDeviceBlock extends FacingShapedBlock {
    private final MapCodec<ImplantDeviceBlock> codec = simpleCodec(ImplantDeviceBlock::new);

    public ImplantDeviceBlock(Properties properties) {
        super(properties, OrionBlocks.implantDeviceShape(), false);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            ZhivaNetwork.openTerminal(serverPlayer, pos, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
