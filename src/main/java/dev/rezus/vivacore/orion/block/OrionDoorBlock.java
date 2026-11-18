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
 * </ul>
 */
public class OrionDoorBlock extends DoorBlock {
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

    /** Редстоун дверь не открывает и не закрывает. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        // намеренно ничего: сигнал редстоуна игнорируется
    }
}
