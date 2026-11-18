package dev.rezus.vivacore.orion.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Формы блоков по моделям агента моделей (facing=north). Посчитаны из элементов моделей:
 * вокселизация с шагом 2 px и слияние в коробки (скрипт был в run/tmp-task5/shapes.py, не в репозитории);
 * капсула — вручную по крупным элементам (основание, корпус, крышка), чтобы в неё нельзя было зайти.
 */
final class OrionShapes {
    private OrionShapes() {}

    static final VoxelShape CABLE_BUNDLE = Shapes.or(Block.box(8, 0, 14, 10, 16, 16),
            Block.box(2, 2, 14, 8, 4, 16),
            Block.box(10, 2, 14, 12, 4, 16),
            Block.box(2, 4, 14, 4, 6, 16),
            Block.box(2, 10, 14, 8, 12, 16),
            Block.box(10, 10, 14, 12, 12, 16));

    static final VoxelShape DEAD_CONSOLE = Shapes.or(Block.box(2, 0, 2, 14, 2, 16),
            Block.box(0, 0, 4, 2, 10, 14),
            Block.box(14, 0, 4, 16, 10, 14),
            Block.box(2, 2, 4, 14, 8, 16),
            Block.box(0, 8, 2, 16, 10, 4),
            Block.box(2, 8, 4, 14, 10, 14),
            Block.box(0, 10, 6, 2, 12, 14),
            Block.box(14, 10, 6, 16, 12, 14),
            Block.box(2, 10, 10, 14, 16, 14),
            Block.box(6, 10, 14, 12, 12, 16),
            Block.box(2, 12, 2, 14, 14, 10),
            Block.box(0, 12, 10, 2, 16, 14),
            Block.box(14, 12, 10, 16, 16, 14),
            Block.box(6, 12, 14, 10, 14, 16));

    static final VoxelShape EMERGENCY_LIGHT = Shapes.or(Block.box(6, 4, 14, 10, 12, 16),
            Block.box(6, 6, 12, 10, 12, 14),
            Block.box(4, 6, 14, 6, 10, 16),
            Block.box(10, 6, 14, 12, 10, 16));

    /** Модель терминала выше блока (до 19.5 px) — форма обрезана по 16 px. */
    static final VoxelShape CODE_TERMINAL = Shapes.or(Block.box(2, 0, 4, 14, 4, 14),
            Block.box(4, 4, 6, 12, 16, 14),
            Block.box(2, 6, 2, 14, 8, 6),
            Block.box(4, 8, 2, 8, 10, 6),
            Block.box(2, 8, 4, 4, 16, 14),
            Block.box(8, 8, 4, 14, 16, 6),
            Block.box(12, 8, 6, 14, 16, 14),
            Block.box(10, 8, 14, 12, 14, 16),
            Block.box(4, 10, 4, 8, 16, 6),
            Block.box(0, 10, 6, 2, 16, 14),
            Block.box(14, 10, 6, 16, 16, 14),
            Block.box(4, 10, 14, 10, 14, 16),
            Block.box(0, 14, 4, 2, 16, 6),
            Block.box(14, 14, 4, 16, 16, 6));

    static final VoxelShape IMPLANT_DEVICE = Shapes.or(Block.box(0, 0, 0, 16, 6, 16),
            Block.box(2, 6, 10, 12, 12, 12),
            Block.box(4, 6, 12, 16, 10, 14),
            Block.box(4, 6, 14, 12, 8, 16),
            Block.box(4, 8, 8, 12, 14, 10),
            Block.box(12, 8, 10, 14, 10, 12),
            Block.box(6, 8, 14, 10, 16, 16),
            Block.box(12, 8, 14, 14, 10, 16),
            Block.box(4, 10, 12, 14, 12, 14),
            Block.box(6, 12, 2, 10, 16, 4),
            Block.box(4, 12, 10, 12, 16, 14),
            Block.box(4, 12, 14, 6, 16, 16),
            Block.box(10, 12, 14, 12, 16, 16),
            Block.box(6, 14, 4, 10, 16, 6),
            Block.box(6, 14, 8, 10, 16, 10));

    /** Капсула, нижняя половина: основание 16×2 и корпус (x 1–15, z 1–16). */
    static final VoxelShape CRYO_LOWER = Shapes.or(Block.box(0, 0, 0, 16, 2, 16),
            Block.box(1, 2, 1, 15, 16, 16));

    /** Капсула, верхняя половина: корпус до 13 px и крышка до 15 px (модель — до y = 31 из 32). */
    static final VoxelShape CRYO_UPPER = Shapes.or(Block.box(1, 0, 1, 15, 13, 16),
            Block.box(1, 13, 2, 15, 15, 15));
}
