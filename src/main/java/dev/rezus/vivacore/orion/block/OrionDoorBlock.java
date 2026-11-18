package dev.rezus.vivacore.orion.block;

import com.mojang.serialization.MapCodec;
import dev.rezus.vivacore.orion.OrionItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Дверь лаборатории ОРИОНа с латунным замком (тест 34, часть).
 * <ul>
 *   <li>Открывается только ПКМ с латунным ключом в руке; ключ не тратится.</li>
 *   <li>Закрывается обычным ПКМ — с ключом или без: закрыть за собой может любой, открыть — только с ключом.</li>
 *   <li>Рукой без ключа, редстоуном, ветровым зарядом и мобами не открывается: тип двери «не открывается рукой»
 *       (так жители и зомби не трогают её, как железную), а сигнал редстоуна игнорируется.</li>
 *   <li>Створка не поворачивается, а уезжает вверх (или вниз — конфиг orion.doorSlidesDown): статическая модель
 *       не рисуется, створку рисует рендерер блок-сущности нижней половины (client/OrionDoorRenderer) — плавно
 *       сжимает её к верху (низу) проёма за orion.doorSlideTicks тиков, поэтому она не вылезает за проём.
 *       Открытая дверь проходима; у кромки, куда уехала створка, остаётся тонкая полоска — по ней кликают,
 *       чтобы закрыть.</li>
 * </ul>
 */
public class OrionDoorBlock extends DoorBlock implements net.minecraft.world.level.block.EntityBlock {
    /** Тип замка: не открывается рукой и ветровым зарядом, звуки металла. Регистрируется по имени — нужен кодеку. */
    public static final BlockSetType ORION = BlockSetType.register(new BlockSetType("vivacore_orion",
            false, false, false, BlockSetType.PressurePlateSensitivity.MOBS, SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN,
            SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));

    public static final MapCodec<OrionDoorBlock> CODEC = simpleCodec(OrionDoorBlock::new);

    public OrionDoorBlock(Properties properties) {
        super(ORION, properties);
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return CODEC;
    }

    /** С ключом в руке: открыть (или закрыть). Без ключа — обычное ПКМ. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(OrionItems.BRASS_KEY.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        setOpen(player, level, state, pos, !isOpen(state));
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Без ключа: открытую дверь можно закрыть, закрытую — нет (сообщение-заглушка). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (isOpen(state)) {
            setOpen(player, level, state, pos, false);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("vivacore.orion.door.locked"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ---------------------------------------------------------------- сдвижная створка

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                ? new OrionDoorBlockEntity(pos, state) : null;
    }

    /** Статическую модель не рисуем — створку рисует рендерер блок-сущности. Модели остаются для частиц. */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED;
    }

    private static final net.minecraft.world.phys.shapes.VoxelShape TOP_STRIP = Block.box(0, 13, 0, 16, 16, 16);
    private static final net.minecraft.world.phys.shapes.VoxelShape BOTTOM_STRIP = Block.box(0, 0, 0, 16, 3, 16);

    /** Открытая: только полоска створки у кромки, куда она уехала (в той половине, где кромка). */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                                  BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        net.minecraft.world.phys.shapes.VoxelShape closed = super.getShape(state.setValue(OPEN, false), level, pos, context);
        if (!isOpen(state)) {
            return closed;
        }
        boolean down = dev.rezus.vivacore.VivaConfig.orionDoorSlidesDown();
        boolean lower = state.getValue(HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
        if (down != lower) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        return net.minecraft.world.phys.shapes.Shapes.join(closed, down ? BOTTOM_STRIP : TOP_STRIP,
                net.minecraft.world.phys.shapes.BooleanOp.AND);
    }

    /** Открытая дверь проходима целиком. */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                                           BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return isOpen(state) ? net.minecraft.world.phys.shapes.Shapes.empty()
                : super.getShape(state, level, pos, context);
    }

    /** Редстоун дверь не открывает и не закрывает. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        // намеренно ничего: сигнал редстоуна игнорируется
    }
}
