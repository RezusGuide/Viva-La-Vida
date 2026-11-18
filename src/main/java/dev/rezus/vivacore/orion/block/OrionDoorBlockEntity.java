package dev.rezus.vivacore.orion.block;

import dev.rezus.vivacore.VivaRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-сущность нижней половины двери ОРИОНа — только для плавной анимации сдвига на клиенте.
 * Ничего не хранит на диске; поля ниже меняет рендерер (client/OrionDoorRenderer).
 */
public class OrionDoorBlockEntity extends BlockEntity {
    /** Последнее увиденное рендерером состояние «открыта» (null — ещё не рисовали). */
    public Boolean lastOpen;
    /** Игровой тик (с долей кадра), когда рендерер заметил смену состояния. */
    public double changeTick = Double.NEGATIVE_INFINITY;

    public OrionDoorBlockEntity(BlockPos pos, BlockState state) {
        super(VivaRegistries.ORION_DOOR_BE.get(), pos, state);
    }
}
