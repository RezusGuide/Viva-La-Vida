package dev.rezus.vivacore.orion.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Блок с поворотом по сторонам света и формой по модели (а не полный куб).
 * Форма задаётся для facing=north (как модель в Blockbench); для остальных сторон поворачивается так же,
 * как blockstate поворачивает модель (y = 90/180/270).
 * <p>
 * wallMounted — вешается на стену (светильник, кабели): задняя сторона модели (+Z) прижимается к блоку,
 * по которому кликнули. Иначе — передом к игроку, как печь.
 */
public class FacingShapedBlock extends HorizontalDirectionalBlock {
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final boolean wallMounted;
    private final MapCodec<? extends FacingShapedBlock> codec;

    public FacingShapedBlock(Properties properties, VoxelShape northShape, boolean wallMounted) {
        super(properties);
        this.wallMounted = wallMounted;
        VoxelShape shape = northShape;
        for (Direction dir : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            shapes.put(dir, shape);
            shape = rotateClockwise(shape);
        }
        this.codec = simpleCodec(p -> new FacingShapedBlock(p, northShape, wallMounted));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Поворот формы на 90° по часовой (вид сверху): north → east. Точка (x, z) → (1 − z, x). */
    public static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                out[0] = Shapes.or(out[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
        return out[0];
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing;
        if (wallMounted && context.getClickedFace().getAxis().isHorizontal()) {
            facing = context.getClickedFace();
        } else {
            facing = context.getHorizontalDirection().getOpposite();
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    public VoxelShape shapeFor(Direction facing) {
        return shapes.get(facing);
    }
}
