package dev.rezus.vivacore.structures;

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
 * Угол — из сида мира. Лаборатория — на distance по углу от спавна, Нексус — в противоположную сторону. Если точка
 * в океане или реке — ищем сушу: сдвиг расстояния до ±distanceShift (шаг distanceStep) и угла до ±angleShift
 * (шаг angleStep), ближние сдвиги — первыми. Суша = центр и 4 точки на landRadius вокруг — не океан и не река.
 */
public final class SitePlanner {
    private SitePlanner() {}

    /** Проверка «суша ли тут» — в игре биомы генератора, в тестах — подставная. */
    @FunctionalInterface
    public interface LandTest {
        boolean isLand(int x, int z);
    }

    public record Params(double distance, double angleShiftDeg, double angleStepDeg, double distanceShift, double distanceStep,
                         int landRadius) {}

    public record Plan(double angle, BlockPos lab, BlockPos nexus, boolean labOnLand, boolean nexusOnLand) {}

    /** Угол из сида, радианы [0, 2π). Соль — чтобы угол не совпадал с другими производными сида. */
    public static double angleFromSeed(long seed) {
        return RandomSource.create(seed ^ 0x5649564143L).nextDouble() * Math.PI * 2;
    }

    public static Plan plan(long seed, BlockPos spawn, Params p, LandTest land) {
        double angle = angleFromSeed(seed);
        Spot lab = search(spawn, angle, p, land);
        Spot nexus = search(spawn, angle + Math.PI, p, land);
        return new Plan(angle, lab.pos, nexus.pos, lab.land, nexus.land);
    }

    private record Spot(BlockPos pos, boolean land) {}

    private static Spot search(BlockPos spawn, double angle, Params p, LandTest land) {
        int dSteps = p.distanceStep() > 0 ? (int) Math.floor(p.distanceShift() / p.distanceStep()) : 0;
        int aSteps = p.angleStepDeg() > 0 ? (int) Math.floor(p.angleShiftDeg() / p.angleStepDeg()) : 0;
        // кольца по «величине сдвига»: сначала маленькие сдвиги и расстояния, и угла
        for (int ring = 0; ring <= Math.max(dSteps, aSteps); ring++) {
            for (int di = -Math.min(ring, dSteps); di <= Math.min(ring, dSteps); di++) {
                for (int ai = -Math.min(ring, aSteps); ai <= Math.min(ring, aSteps); ai++) {
                    if (Math.max(Math.abs(di), Math.abs(ai)) != ring) continue;
                    BlockPos at = point(spawn, angle + Math.toRadians(ai * p.angleStepDeg()), p.distance() + di * p.distanceStep());
                    if (isLand(at, p.landRadius(), land)) return new Spot(at, true);
                }
            }
        }
        return new Spot(point(spawn, angle, p.distance()), false);
    }

    public static BlockPos point(BlockPos spawn, double angle, double distance) {
        return new BlockPos((int) Math.round(spawn.getX() + Math.cos(angle) * distance), 0,
                (int) Math.round(spawn.getZ() + Math.sin(angle) * distance));
    }

    private static boolean isLand(BlockPos c, int r, LandTest land) {
        return land.isLand(c.getX(), c.getZ()) && land.isLand(c.getX() + r, c.getZ()) && land.isLand(c.getX() - r, c.getZ())
                && land.isLand(c.getX(), c.getZ() + r) && land.isLand(c.getX(), c.getZ() - r);
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
