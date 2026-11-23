package dev.rezus.vivacore.structures;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

/**
 * Где стоят лаборатория и Нексус (тест 31). Чистая математика + проверка суши через источник биомов генератора —
 * без генерации чанков: шум биомов считается для любой точки мира мгновенно.
 * <p>
 * Угол — из сида мира. Лаборатория — на distance по углу от спавна, Нексус — в противоположную сторону. Если место
 * не подходит — ищем: сдвиг расстояния до ±distanceShift (шаг distanceStep) и угла до ±angleShift (шаг angleStep),
 * ближние сдвиги — первыми.
 * <p>
 * Место подходит (задача 9), если:
 * <ul>
 *   <li>суша по всей площади постройки и ещё landMargin вокруг — точки шума биомов по сетке gridStep (не океан, не
 *       река, не пляж);</li>
 *   <li>это не островок: связная суша вокруг (заливка по сетке 2 × gridStep) не меньше площади постройки × islandFactor.</li>
 * </ul>
 * Размер постройки — половина большей стороны шаблона (поворот заранее не важен).
 */
public final class SitePlanner {
    private SitePlanner() {}

    /** Проверка «суша ли тут» — в игре биомы генератора, в тестах — подставная. */
    @FunctionalInterface
    public interface LandTest {
        boolean isLand(int x, int z);
    }

    public record Params(double distance, double angleShiftDeg, double angleStepDeg, double distanceShift, double distanceStep,
                         int landMargin, int gridStep, double islandFactor) {}

    public record Plan(double angle, BlockPos lab, BlockPos nexus, boolean labOnLand, boolean nexusOnLand) {}

    /** Угол из сида, радианы [0, 2π). Соль — чтобы угол не совпадал с другими производными сида. */
    public static double angleFromSeed(long seed) {
        return RandomSource.create(seed ^ 0x5649564143L).nextDouble() * Math.PI * 2;
    }

    /** labHalf / nexusHalf — половина большей стороны шаблона, блоки. */
    public static Plan plan(long seed, BlockPos spawn, Params p, int labHalf, int nexusHalf, LandTest land) {
        double angle = angleFromSeed(seed);
        Spot lab = search(spawn, angle, p, labHalf, land);
        Spot nexus = search(spawn, angle + Math.PI, p, nexusHalf, land);
        return new Plan(angle, lab.pos, nexus.pos, lab.land, nexus.land);
    }

    private record Spot(BlockPos pos, boolean land) {}

    private static Spot search(BlockPos spawn, double angle, Params p, int half, LandTest land) {
        int dSteps = p.distanceStep() > 0 ? (int) Math.floor(p.distanceShift() / p.distanceStep()) : 0;
        int aSteps = p.angleStepDeg() > 0 ? (int) Math.floor(p.angleShiftDeg() / p.angleStepDeg()) : 0;
        // кольца по «величине сдвига»: сначала маленькие сдвиги и расстояния, и угла
        for (int ring = 0; ring <= Math.max(dSteps, aSteps); ring++) {
            for (int di = -Math.min(ring, dSteps); di <= Math.min(ring, dSteps); di++) {
                for (int ai = -Math.min(ring, aSteps); ai <= Math.min(ring, aSteps); ai++) {
                    if (Math.max(Math.abs(di), Math.abs(ai)) != ring) continue;
                    BlockPos at = point(spawn, angle + Math.toRadians(ai * p.angleStepDeg()), p.distance() + di * p.distanceStep());
                    if (fits(at, half, p, land)) return new Spot(at, true);
                }
            }
        }
        return new Spot(point(spawn, angle, p.distance()), false);
    }

    public static BlockPos point(BlockPos spawn, double angle, double distance) {
        return new BlockPos((int) Math.round(spawn.getX() + Math.cos(angle) * distance), 0,
                (int) Math.round(spawn.getZ() + Math.sin(angle) * distance));
    }

    /** Место подходит: суша по площади с отступом и не островок. */
    public static boolean fits(BlockPos c, int half, Params p, LandTest land) {
        return landArea(c, half + p.landMargin(), Math.max(1, p.gridStep()), land) && notIsland(c, half, p, land);
    }

    /** Суша во всех точках сетки квадрата ±r (края квадрата — тоже точки). Сначала углы и центр — вода там чаще. */
    static boolean landArea(BlockPos c, int r, int step, LandTest land) {
        int x = c.getX();
        int z = c.getZ();
        if (!land.isLand(x, z) || !land.isLand(x - r, z - r) || !land.isLand(x + r, z - r) || !land.isLand(x - r, z + r)
                || !land.isLand(x + r, z + r)) {
            return false;
        }
        for (int dx = -r; ; dx = Math.min(dx + step, r)) {
            for (int dz = -r; ; dz = Math.min(dz + step, r)) {
                if (!land.isLand(x + dx, z + dz)) return false;
                if (dz == r) break;
            }
            if (dx == r) break;
        }
        return true;
    }

    /**
     * Связная суша вокруг центра (заливка по клеткам 2 × gridStep, соседи по 4 сторонам) — не меньше площади постройки ×
     * islandFactor. Заливка останавливается, как только суши набралось достаточно.
     */
    static boolean notIsland(BlockPos c, int half, Params p, LandTest land) {
        int cell = Math.max(1, p.gridStep()) * 2;
        double area = (2.0 * half) * (2.0 * half) * p.islandFactor();
        long need = (long) Math.ceil(area / ((double) cell * cell));
        if (need <= 1) return true;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {0, 0});
        seen.add(0L);
        long count = 0;
        while (!queue.isEmpty()) {
            int[] q = queue.poll();
            if (!land.isLand(c.getX() + q[0] * cell, c.getZ() + q[1] * cell)) continue;
            if (++count >= need) return true;
            int[][] next = {{q[0] + 1, q[1]}, {q[0] - 1, q[1]}, {q[0], q[1] + 1}, {q[0], q[1] - 1}};
            for (int[] n : next) {
                long key = ((long) n[0] << 32) ^ (n[1] & 0xffffffffL);
                if (seen.add(key)) queue.add(n);
            }
        }
        return false;
    }

    /** Суша по биомам генератора мира (на уровне моря): не океан, не река, не пляж. */
    public static LandTest biomeLand(ServerLevel level) {
        int y = level.getSeaLevel();
        return (x, z) -> {
            Holder<Biome> b = level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2);
            return !b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_DEEP_OCEAN) && !b.is(BiomeTags.IS_RIVER) && !b.is(BiomeTags.IS_BEACH);
        };
    }
}
