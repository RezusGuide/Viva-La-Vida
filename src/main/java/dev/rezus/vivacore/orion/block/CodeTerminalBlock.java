package dev.rezus.vivacore.orion.block;

import com.mojang.serialization.MapCodec;
import dev.rezus.vivacore.orion.net.OrionNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Терминал кода лаборатории ОРИОНа (заметка «17 Три стороны конфликта»). ПКМ → экран ввода кода.
 * unlocked=true — на этом терминале ввели верный код (модель code_terminal_open).
 */
public class CodeTerminalBlock extends FacingShapedBlock {
    public static final BooleanProperty UNLOCKED = BooleanProperty.create("unlocked");
    private final MapCodec<CodeTerminalBlock> codec = simpleCodec(CodeTerminalBlock::new);

    public CodeTerminalBlock(Properties properties) {
        super(properties, OrionShapes.CODE_TERMINAL, false);
        registerDefaultState(defaultBlockState().setValue(UNLOCKED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(UNLOCKED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            OrionNetwork.openTerminal(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
