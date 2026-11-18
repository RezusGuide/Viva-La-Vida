package dev.rezus.vivacore.orion.block;

import com.mojang.serialization.MapCodec;
import dev.rezus.vivacore.VivaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Аварийный светильник обломков корабля (белый и красный). Уровень света — конфиг orion.emergencyLightLevel.
 * Свет в 1.21.1 кешируется в BlockState при регистрации (конфига ещё нет), поэтому статический уровень в свойствах
 * — значение по умолчанию, а настоящий берётся из конфига через позиционный getLightEmission (расширение NeoForge,
 * его читает движок света). Изменение конфига применится к уже стоящим светильникам после обновления блока/чанка.
 */
public class EmergencyLightBlock extends FacingShapedBlock {
    private final MapCodec<EmergencyLightBlock> codec = simpleCodec(EmergencyLightBlock::new);

    public EmergencyLightBlock(Properties properties) {
        super(properties, OrionShapes.EMERGENCY_LIGHT, true);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return VivaConfig.emergencyLightLevel();
    }
}
