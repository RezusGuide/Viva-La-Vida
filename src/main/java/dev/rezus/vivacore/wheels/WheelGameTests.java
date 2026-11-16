package dev.rezus.vivacore.wheels;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 17 из «09 Тесты»: лимит водяных колёс в одной сети Create.
 * Вода — пузырьковые столбы над песком душ: Create считает их потоком (WaterWheelBlockEntity#getFlowVectorAtPosition),
 * их можно поставить через setBlock, и они не растекаются.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WheelGameTests {
    private static final String AREA = "wheels";
    private static final int TIMEOUT = 400;

    private WheelGameTests() {}

    // ------------------------------------------------------------------ тесты

    /** 4 малых колеса на одном валу: работают ровно 3; лишнее стабильно; убрали работающее — снова 3. */
    @GameTest(template = AREA, timeoutTicks = TIMEOUT)
    public static void fourOnOneShaft(GameTestHelper h) {
        int x = 4, y = 3;
        List<BlockPos> wheels = List.of(new BlockPos(x, y, 1), new BlockPos(x, y, 3), new BlockPos(x, y, 5), new BlockPos(x, y, 7));
        for (BlockPos p : wheels) smallWheel(h, p);
        shaft(h, new BlockPos(x, y, 2));
        shaft(h, new BlockPos(x, y, 4));
        shaft(h, new BlockPos(x, y, 6));

        AtomicReference<Set<BlockPos>> limitedBefore = new AtomicReference<>();
        AtomicReference<BlockPos> removed = new AtomicReference<>();
        List<BlockPos> remaining = new ArrayList<>();

        h.startSequence()
                .thenWaitUntil(() -> {
                    assertOneNetwork(h, wheels);
                    assertLimitedCount(h, wheels, 1);
                    assertCapacity(h, wheels);
                })
                .thenExecute(() -> limitedBefore.set(limited(h, wheels)))
                // 60 тиков: набор лишних не меняется (не мигает)
                .thenExecuteFor(60, () -> {
                    if (!limited(h, wheels).equals(limitedBefore.get()))
                        throw new GameTestAssertException("набор лишних колёс изменился без причины");
                    assertCapacity(h, wheels);
                })
                .thenExecute(() -> {
                    // лишнее колесо: скорость генерации 0, но крутится как вал от сети; флаг сохраняется в NBT
                    BlockPos lim = limitedBefore.get().iterator().next();
                    WaterWheelBlockEntity w = wheel(h, lim);
                    h.assertTrue(w.getGeneratedSpeed() == 0, "лишнее колесо не генерирует");
                    h.assertTrue(Math.abs(w.getSpeed()) == 8, "лишнее колесо крутится от сети как вал: " + w.getSpeed());
                    CompoundTag tag = w.saveWithoutMetadata(h.getLevel().registryAccess());
                    h.assertTrue(tag.getBoolean("vivacore:limited") && tag.contains("vivacore:order"),
                            "флаг и порядок сохраняются в NBT: " + tag);
                    // убираем работающее колесо с края вала (чтобы вал не разделился)
                    BlockPos end = !limitedBefore.get().contains(wheels.get(0)) ? wheels.get(0) : wheels.get(3);
                    removed.set(end);
                    h.setBlock(end, Blocks.AIR);
                    wheels.stream().filter(p -> !p.equals(end)).forEach(remaining::add);
                })
                .thenWaitUntil(() -> {
                    assertOneNetwork(h, remaining);
                    assertLimitedCount(h, remaining, 0);
                    assertCapacity(h, remaining);
                    h.assertTrue(working(h, remaining) == 3, "после удаления снова работают 3");
                })
                .thenSucceed();
    }

    /** Два отдельных вала по 2 колеса — работают все 4. */
    @GameTest(template = AREA, timeoutTicks = TIMEOUT)
    public static void twoSeparateShafts(GameTestHelper h) {
        List<BlockPos> a = List.of(new BlockPos(2, 3, 1), new BlockPos(2, 3, 3));
        List<BlockPos> b = List.of(new BlockPos(7, 3, 1), new BlockPos(7, 3, 3));
        for (BlockPos p : a) smallWheel(h, p);
        for (BlockPos p : b) smallWheel(h, p);
        shaft(h, new BlockPos(2, 3, 2));
        shaft(h, new BlockPos(7, 3, 2));

        h.succeedWhen(() -> {
            assertOneNetwork(h, a);
            assertOneNetwork(h, b);
            h.assertTrue(!wheel(h, a.get(0)).network.equals(wheel(h, b.get(0)).network), "у валов разные сети");
            List<BlockPos> all = new ArrayList<>(a);
            all.addAll(b);
            assertLimitedCount(h, all, 0);
            assertCapacity(h, a);
            assertCapacity(h, b);
        });
    }

    /** Малые и большое колесо в одной сети считаются вместе: 3 малых + 1 большое → одно лишнее. */
    @GameTest(template = AREA, timeoutTicks = TIMEOUT)
    public static void smallAndLargeCountTogether(GameTestHelper h) {
        BlockPos large = new BlockPos(3, 3, 1);
        largeWheel(h, large);
        shaft(h, new BlockPos(3, 3, 2));
        List<BlockPos> wheels = new ArrayList<>(List.of(large));
        for (int z : new int[] {3, 5, 7}) {
            BlockPos p = new BlockPos(3, 3, z);
            smallWheel(h, p);
            wheels.add(p);
        }
        shaft(h, new BlockPos(3, 3, 4));
        shaft(h, new BlockPos(3, 3, 6));

        h.succeedWhen(() -> {
            h.assertTrue(wheel(h, large) instanceof LargeWaterWheelBlockEntity, "большое колесо на месте");
            assertOneNetwork(h, wheels);
            assertLimitedCount(h, wheels, 1);
            assertCapacity(h, wheels);
        });
    }

    /** Чистая логика подсчёта. */
    @GameTest(template = "empty")
    public static void countingLogic(GameTestHelper h) {
        h.assertTrue(WheelLimit.working(4, 3) == 3, "4 при лимите 3 → 3");
        h.assertTrue(WheelLimit.working(2, 3) == 2, "2 при лимите 3 → 2");
        h.assertTrue(WheelLimit.working(5, 0) == 0, "лимит 0 → 0");
        h.assertTrue(VivaConfig.wheelsMaxPerNetwork() == 3 && VivaConfig.wheelsEnabled(), "конфиг по умолчанию: 3, включено");
        h.succeed();
    }

    // ------------------------------------------------------------------ постройка

    private static void smallWheel(GameTestHelper h, BlockPos p) {
        h.setBlock(p.offset(1, -1, 0), Blocks.SOUL_SAND);
        h.setBlock(p.offset(1, 0, 0), Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
        h.setBlock(p, AllBlocks.WATER_WHEEL.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
    }

    private static void largeWheel(GameTestHelper h, BlockPos p) {
        h.setBlock(p.offset(2, -1, 0), Blocks.SOUL_SAND);
        h.setBlock(p.offset(2, 0, 0), Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false));
        h.setBlock(p, AllBlocks.LARGE_WATER_WHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z));
    }

    private static void shaft(GameTestHelper h, BlockPos p) {
        h.setBlock(p, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z));
    }

    // ------------------------------------------------------------------ проверки

    private static WaterWheelBlockEntity wheel(GameTestHelper h, BlockPos p) {
        if (!(h.getBlockEntity(p) instanceof WaterWheelBlockEntity w))
            throw new GameTestAssertException("нет водяного колеса в " + p);
        return w;
    }

    private static boolean isLimited(WaterWheelBlockEntity w) {
        return ((VivaWaterWheel) w).vivacore$isLimited();
    }

    private static Set<BlockPos> limited(GameTestHelper h, List<BlockPos> wheels) {
        return wheels.stream().filter(p -> isLimited(wheel(h, p))).collect(Collectors.toSet());
    }

    private static int working(GameTestHelper h, List<BlockPos> wheels) {
        return (int) wheels.stream().filter(p -> wheel(h, p).getGeneratedSpeed() != 0).count();
    }

    private static void assertOneNetwork(GameTestHelper h, List<BlockPos> wheels) {
        Set<Long> ids = new HashSet<>();
        for (BlockPos p : wheels) {
            WaterWheelBlockEntity w = wheel(h, p);
            if (w.flowScore == 0) throw new GameTestAssertException("у колеса " + p + " нет потока воды");
            if (!w.hasNetwork()) throw new GameTestAssertException("колесо " + p + " не в сети");
            ids.add(w.network);
        }
        if (ids.size() != 1) throw new GameTestAssertException("колёса в разных сетях: " + ids);
    }

    private static void assertLimitedCount(GameTestHelper h, List<BlockPos> wheels, int expected) {
        Set<BlockPos> lim = limited(h, wheels);
        if (lim.size() != expected)
            throw new GameTestAssertException("лишних колёс " + lim.size() + ", ожидалось " + expected + ": " + lim);
        int work = working(h, wheels);
        if (work != wheels.size() - expected)
            throw new GameTestAssertException("работает " + work + ", ожидалось " + (wheels.size() - expected));
    }

    /** Мощность сети = сумма мощностей только работающих колёс (лишние дают 0). */
    private static void assertCapacity(GameTestHelper h, List<BlockPos> wheels) {
        double expected = 0;
        for (BlockPos p : wheels) {
            WaterWheelBlockEntity w = wheel(h, p);
            if (isLimited(w)) continue;
            double perRpm = BlockStressValues.getCapacity(w.getBlockState().getBlock());
            int size = w instanceof LargeWaterWheelBlockEntity ? 2 : 1;
            expected += perRpm * 8.0 / size;
        }
        KineticNetwork net = wheel(h, wheels.get(0)).getOrCreateNetwork();
        float actual = net.calculateCapacity();
        if (Math.abs(actual - expected) > 0.01)
            throw new GameTestAssertException("мощность сети " + actual + ", ожидалось " + expected);
    }
}
