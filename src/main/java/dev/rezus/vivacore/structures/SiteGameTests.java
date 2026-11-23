package dev.rezus.vivacore.structures;

import dev.rezus.vivacore.VivaConfig;

import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.army.OrionMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 31: две постройки в 10 000 блоков (угол из сида, противоположные точки, сдвиг на сушу), постройка ставится
 * один раз, метки заменяются; постройка встаёт, когда её чанк загрузился. Каждый тест — в своей пачке: они меняют
 * общее состояние построек сервера и в конце его сбрасывают.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SiteGameTests {
    private SiteGameTests() {}

    private static final SitePlanner.Params P = new SitePlanner.Params(10000, 20, 2, 1000, 100, 16, 8, 2.0);

    @GameTest(template = "empty", batch = "sites_plan")
    public static void planOppositeOnLand(GameTestHelper h) {
        BlockPos spawn = new BlockPos(120, 64, -45);
        SitePlanner.Plan a = SitePlanner.plan(12345L, spawn, P, 32, 20, (x, z) -> true);
        SitePlanner.Plan b = SitePlanner.plan(12345L, spawn, P, 32, 20, (x, z) -> true);
        SitePlanner.Plan c = SitePlanner.plan(98765L, spawn, P, 32, 20, (x, z) -> true);
        double dl = dist(spawn, a.lab());
        double dn = dist(spawn, a.nexus());
        h.assertTrue(Math.abs(dl - 10000) <= 1 && Math.abs(dn - 10000) <= 1, "обе точки в 10 000 от спавна: " + dl + ", " + dn);
        int sx = a.lab().getX() - spawn.getX() + a.nexus().getX() - spawn.getX();
        int sz = a.lab().getZ() - spawn.getZ() + a.nexus().getZ() - spawn.getZ();
        h.assertTrue(Math.abs(sx) <= 2 && Math.abs(sz) <= 2, "точки противоположны (сумма векторов " + sx + ", " + sz + ")");
        h.assertTrue(a.lab().equals(b.lab()) && a.nexus().equals(b.nexus()), "тот же сид — те же точки");
        h.assertTrue(Math.abs(a.angle() - c.angle()) > 1e-6, "другой сид — другой угол");
        h.assertTrue(a.labOnLand() && a.nexusOnLand(), "суша");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "sites_plan")
    public static void planOceanShiftsToLand(GameTestHelper h) {
        BlockPos spawn = BlockPos.ZERO;
        double angle = SitePlanner.angleFromSeed(777L);
        BlockPos direct = SitePlanner.point(spawn, angle, 10000);
        // океан — круг 450 блоков вокруг прямой точки лаборатории
        SitePlanner.LandTest land = (x, z) -> Math.hypot(x - direct.getX(), z - direct.getZ()) > 450;
        SitePlanner.Plan plan = SitePlanner.plan(777L, spawn, P, 32, 20, land);
        h.assertTrue(plan.labOnLand(), "лаборатория сдвинута на сушу");
        h.assertTrue(!plan.lab().equals(direct), "точка лаборатории сдвинута");
        h.assertTrue(land.isLand(plan.lab().getX(), plan.lab().getZ()), "новая точка — суша");
        double d = dist(spawn, plan.lab());
        h.assertTrue(d >= 9000 - 1 && d <= 11000 + 1, "расстояние в пределах ±1000: " + d);
        double a = Math.atan2(plan.lab().getZ(), plan.lab().getX());
        double da = Math.toDegrees(Math.abs(Math.atan2(Math.sin(a - angle), Math.cos(a - angle))));
        h.assertTrue(da <= 20.5, "угол в пределах ±20°: " + da);
        BlockPos nexusDirect = SitePlanner.point(spawn, angle + Math.PI, 10000);
        h.assertTrue(plan.nexus().equals(nexusDirect), "Нексус на суше — не сдвинут");
        // суши нет совсем — точка остаётся прямой, флаг «не суша»
        SitePlanner.Plan none = SitePlanner.plan(777L, spawn, P, 32, 20, (x, z) -> false);
        h.assertTrue(!none.labOnLand() && none.lab().equals(direct), "без суши — прямая точка и флаг");
        h.succeed();
    }

    /**
     * Задача 9: островок. В прямой точке Нексуса — остров 50×50 в океане (площадь постройки 40×40 он покрывает, но он
     * меньше площади × 2) — место отброшено; большая суша — дальше 300 блоков. Сетка суши видит и воду у края площади.
     */
    @GameTest(template = "empty", batch = "sites_plan")
    public static void planSkipsSmallIsland(GameTestHelper h) {
        BlockPos spawn = BlockPos.ZERO;
        SitePlanner.Params p = new SitePlanner.Params(10000, 20, 2, 1000, 100, 0, 8, 2.0);
        double angle = SitePlanner.angleFromSeed(4242L);
        BlockPos nexusDirect = SitePlanner.point(spawn, angle + Math.PI, 10000);
        SitePlanner.LandTest land = (x, z) -> {
            int dx = Math.abs(x - nexusDirect.getX());
            int dz = Math.abs(z - nexusDirect.getZ());
            return (dx <= 25 && dz <= 25) || Math.hypot(dx, dz) > 300;
        };
        h.assertTrue(SitePlanner.landArea(nexusDirect, 20, 8, land), "площадь постройки на островке — суша");
        h.assertTrue(!SitePlanner.fits(nexusDirect, 20, p, land), "островок меньше площади × 2 — не подходит");
        SitePlanner.Params noIsland = new SitePlanner.Params(10000, 20, 2, 1000, 100, 0, 8, 0.0);
        h.assertTrue(SitePlanner.fits(nexusDirect, 20, noIsland, land), "без проверки острова островок подошёл бы");
        SitePlanner.Plan plan = SitePlanner.plan(4242L, spawn, p, 32, 20, land);
        h.assertTrue(plan.nexusOnLand() && !plan.nexus().equals(nexusDirect), "Нексус сдвинут с островка: " + plan.nexus());
        h.assertTrue(Math.hypot(plan.nexus().getX() - nexusDirect.getX(), plan.nexus().getZ() - nexusDirect.getZ()) > 300 - 20,
                "новое место — на большой суше");
        // вода у края площади (а не в центре и не в 4 точках, как раньше) — тоже не подходит
        SitePlanner.LandTest corner = (x, z) -> !(x > nexusDirect.getX() + 15 && z > nexusDirect.getZ() + 15);
        h.assertTrue(!SitePlanner.landArea(nexusDirect, 20, 8, corner), "вода в углу площади видна");
        h.succeed();
    }

    /** Верх твёрдого блока в столбце (без растений), сверху вниз от maxY. */
    private static int top(ServerLevel level, int x, int z, int maxY) {
        for (int y = maxY; y > level.getMinBuildHeight(); y--) {
            var st = level.getBlockState(new BlockPos(x, y, z));
            if (!st.isAir() && !st.canBeReplaced() && st.blocksMotion()) return y;
        }
        return level.getMinBuildHeight();
    }

    /**
     * Задача 9: обрыв под постройкой и края. Плато высотой 10 с обрывом, который проходит под западной частью
     * площадки Нексуса: под постройкой не остаётся воздуха (столбы до земли: у земли — земля, выше — камень), а с запада
     * от коробки высота меняется не больше чем на 3 блока за шаг (без сведения краёв — обрыв в 10 блоков).
     */
    @GameTest(template = "empty", batch = "sites_foundation", timeoutTicks = 200)
    public static void foundationPillarsAndEdges(GameTestHelper h) {
        cliffSite(h, SiteType.NEXUS);
    }

    /**
     * То же с лабораторией: её край — стены, пол — на уровне земли постройки; к западу местность на 10 ниже — земля
     * снаружи должна сойти от уровня пола склоном (первый столбец за стеной — не дальше 3 от пола, шаг не больше 3).
     */
    @GameTest(template = "empty", batch = "sites_foundation_lab", timeoutTicks = 200)
    public static void labEdgesSlopeToTerrain(GameTestHelper h) {
        cliffSite(h, SiteType.LAB);
    }

    private static void cliffSite(GameTestHelper h, SiteType type) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = server.overworld();
        Structures.resetForTests(server);
        // мир GameTest сохраняется между прогонами — каждый раз новое место (иначе там уже плато и Нексус прошлого прогона)
        BlockPos c = new BlockPos(36000 + 512 * level.random.nextInt(400), 0, 30000 + 512 * level.random.nextInt(400));
        for (int cx = (c.getX() - 80) >> 4; cx <= (c.getX() + 80) >> 4; cx++) {
            for (int cz = (c.getZ() - 80) >> 4; cz <= (c.getZ() + 80) >> 4; cz++) level.getChunk(cx, cz);
        }
        int y0 = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()) - 1;
        int cliffX = c.getX() - 5;
        for (int x = c.getX() - 70; x <= c.getX() + 70; x++) {
            for (int z = c.getZ() - 70; z <= c.getZ() + 70; z++) {
                if (x < cliffX) continue;
                for (int y = y0 + 1; y <= y0 + 10; y++) {
                    level.setBlock(new BlockPos(x, y, z), y == y0 + 10 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        // задача 11: с востока за площадкой — гора на 35 блоков выше земли (выше зоны сведения краёв)
        int mountainX = c.getX() + (type == SiteType.LAB ? 36 : 24);
        for (int x = mountainX; x <= mountainX + 10; x++) {
            for (int z = c.getZ() - 20; z <= c.getZ() + 20; z++) {
                for (int y = y0 + 11; y <= y0 + 35; y++) level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        BoundingBox box = Structures.placeAt(level, type, c, Rotation.NONE);
        h.assertTrue(box != null, type.id + " поставлен");
        // нависаний (твёрдое над воздухом над землёй) в полосе вокруг коробки нет — сквозь землю не видно неба
        int overhangs = 0;
        int ring = VivaConfig.structuresEdgeBlend() + 2;
        for (int x = box.minX() - ring; x <= box.maxX() + ring; x++) {
            for (int z = box.minZ() - ring; z <= box.maxZ() + ring; z++) {
                if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) continue;
                boolean solidAbove = false;
                boolean gap = false;
                for (int y = y0 + 45; y > y0; y--) {
                    var st = level.getBlockState(new BlockPos(x, y, z));
                    boolean solid = !st.isAir() && st.blocksMotion();
                    if (solid && gap) {
                        overhangs++;
                        break;
                    }
                    if (solid) solidAbove = true;
                    else if (solidAbove && st.isAir()) gap = true;
                }
            }
        }
        int gaps = 0;
        int columns = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (level.getBlockState(new BlockPos(x, box.minY(), z)).isAir()) continue;
                columns++;
                for (int y = box.minY() - 1; y > y0; y--) {
                    if (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        gaps++;
                        break;
                    }
                }
            }
        }
        int px = box.minX() + 5;
        var nearGround = level.getBlockState(new BlockPos(px, y0 + 2, c.getZ()));
        int yHigh = box.minY() - 1; // сразу под низом коробки
        var higher = level.getBlockState(new BlockPos(px, yHigh, c.getZ()));
        var expectHigh = yHigh - y0 <= 3 ? Blocks.DIRT : Blocks.STONE;
        int maxStep = 0;
        int first = top(level, box.minX() - 1, c.getZ(), box.maxY() + 10);
        int prev = type == SiteType.LAB ? first : top(level, box.minX(), c.getZ(), box.maxY() + 10);
        StringBuilder row = new StringBuilder().append(prev);
        for (int x = type == SiteType.LAB ? box.minX() - 2 : box.minX() - 1; x >= box.minX() - VivaConfig.structuresEdgeBlend() - 3; x--) {
            int t = top(level, x, c.getZ(), box.maxY() + 10);
            maxStep = Math.max(maxStep, Math.abs(t - prev));
            row.append(' ').append(t);
            prev = t;
        }
        Structures.resetForTests(server);
        h.assertTrue(columns > 0 && gaps == 0, "под постройкой нет пустот: столбцов " + columns + ", с воздухом " + gaps + " (y0 " + y0
                + ", низ коробки " + box.minY() + ")");
        h.assertTrue(nearGround.is(Blocks.DIRT) && higher.is(expectHigh), "столб: у земли " + nearGround + ", под коробкой (y0+"
                + (yHigh - y0) + ") " + higher);
        h.assertTrue(maxStep <= 3, "к западу от коробки шаг высоты не больше 3: " + row);
        h.assertTrue(overhangs == 0, "вокруг коробки нет нависающей земли (дыр с видом на небо): столбцов " + overhangs);
        if (type == SiteType.LAB) {
            // пол лаборатории: уровень земли постройки = верх плато (y0 + 10) — земля у стены не ниже пола больше чем на 3
            h.assertTrue(first >= y0 + 10 - 3, "у западной стены земля поднята к полу: " + first + " при плато " + (y0 + 10) + ", ряд " + row);
            h.assertTrue(prev <= y0 + 1, "дальше полосы — местность (" + y0 + "): " + prev);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "sites_place_lab", timeoutTicks = 200)
    public static void labPlacedOnceMarkersReplaced(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = server.overworld();
        Structures.resetForTests(server);
        BlockPos center = new BlockPos(30000, 0, 30000);
        BoundingBox box = Structures.placeAt(level, SiteType.LAB, center, Rotation.NONE);
        h.assertTrue(box != null, "лаборатория поставлена");
        h.assertTrue(box.getXSpan() == 64 && box.getZSpan() == 64 && box.getYSpan() == 24, "размер по шаблону: " + box);
        AABB area = AABB.of(box);
        int machines = level.getEntitiesOfClass(OrionMachine.class, area).size();
        h.assertTrue(machines == 7, "страж ядра + 6 охранников на метках, есть " + machines);
        h.assertTrue(noMarkers(level, box), "меток structure_block в коробке не осталось");
        BlockPos core = Structures.position(server, SiteType.LAB);
        h.assertTrue(core != null && box.isInside(core), "ядро — метка внутри коробки: " + core);
        h.assertTrue(level.getBlockState(core.offset(0, 0, 3)).is(dev.rezus.vivacore.orion.block.OrionBlocks.CODE_TERMINAL.get()),
                "терминал кода у ядра");
        // второй раз не ставится: та же коробка, машин не прибавилось
        BoundingBox again = Structures.placeAt(level, SiteType.LAB, center.offset(500, 0, 0), Rotation.NONE);
        h.assertTrue(box.equals(again), "второй раз — та же коробка");
        h.assertTrue(level.getEntitiesOfClass(OrionMachine.class, area).size() == machines, "второй раз машины не ставятся");
        level.getEntitiesOfClass(OrionMachine.class, area).forEach(m -> m.discard());
        Structures.resetForTests(server);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "sites_place_crash", timeoutTicks = 200)
    public static void crashMovesWorldSpawn(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = server.overworld();
        Structures.resetForTests(server);
        BlockPos oldSpawn = level.getSharedSpawnPos();
        int oldRadius = level.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS);
        BoundingBox box = Structures.placeAt(level, SiteType.CRASH, new BlockPos(31000, 0, 30000), Rotation.CLOCKWISE_90);
        h.assertTrue(box != null && noMarkers(level, box), "отсек поставлен, метки убраны");
        BlockPos spawn = Structures.position(server, SiteType.CRASH);
        h.assertTrue(spawn != null && box.isInside(spawn), "метка спавна внутри отсека: " + spawn);
        h.assertTrue(level.getSharedSpawnPos().equals(spawn), "спавн мира перенесён к метке");
        h.assertTrue(level.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) == 0, "spawnRadius = 0");
        h.assertTrue(level.getBlockState(spawn).isAir() && level.getBlockState(spawn.above()).isAir(), "на спавне — место для игрока");
        int capsules = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(p).is(dev.rezus.vivacore.orion.block.OrionBlocks.CRYO_CAPSULE.get())) capsules++;
        }
        h.assertTrue(capsules == 24, "12 криокапсул (24 половинки), есть " + capsules);
        level.setDefaultSpawnPos(oldSpawn, 0f);
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(oldRadius, server);
        Structures.resetForTests(server);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "sites_place_nexus", timeoutTicks = 200)
    public static void nexusCoreMarker(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = server.overworld();
        Structures.resetForTests(server);
        BoundingBox box = Structures.placeAt(level, SiteType.NEXUS, new BlockPos(32000, 0, 30000), Rotation.COUNTERCLOCKWISE_90);
        h.assertTrue(box != null && noMarkers(level, box), "Нексус поставлен, метки убраны");
        BlockPos core = Structures.position(server, SiteType.NEXUS);
        h.assertTrue(core != null && box.isInside(core) && level.getBlockState(core).isAir(), "ядро — метка в центре кратера: " + core);
        h.assertTrue(Math.abs(core.getX() - 32000) <= 2 && Math.abs(core.getZ() - 30000) <= 2, "ядро в центре");
        Structures.resetForTests(server);
        h.succeed();
    }

    /** Постройка у воды: озеро вокруг — вода не остаётся в коробке и не затекает потом (найдено на скриншоте у озера). */
    @GameTest(template = "empty", batch = "sites_water", timeoutTicks = 200)
    public static void waterDoesNotFlood(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = server.overworld();
        Structures.resetForTests(server);
        BlockPos c = new BlockPos(33000, 0, 30000);
        for (int cx = (c.getX() - 32) >> 4; cx <= (c.getX() + 32) >> 4; cx++) {
            for (int cz = (c.getZ() - 32) >> 4; cz <= (c.getZ() + 32) >> 4; cz++) level.getChunk(cx, cz);
        }
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, c.getX(), c.getZ());
        // озеро глубиной 2 на 65×65 вокруг точки
        for (BlockPos p : BlockPos.betweenClosed(c.getX() - 32, top, c.getZ() - 32, c.getX() + 32, top + 1, c.getZ() + 32)) {
            level.setBlock(p, Blocks.WATER.defaultBlockState(), 2);
        }
        BoundingBox box = Structures.placeAt(level, SiteType.NEXUS, c, Rotation.NONE);
        h.assertTrue(box != null, "Нексус поставлен");
        h.runAfterDelay(60, () -> {
            int inside = 0;
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (!level.getFluidState(p).isEmpty()) inside++;
            }
            Structures.resetForTests(server);
            h.assertTrue(inside == 0, "через 3 с воды в коробке нет, есть блоков с водой: " + inside);
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "sites_autoplace", timeoutTicks = 200)
    public static void placedWhenChunkLoads(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = server.overworld();
        Structures.resetForTests(server);
        BlockPos center = new BlockPos(41000, 0, -41000);
        Structures.setPlanned(server, SiteType.NEXUS, center);
        h.assertTrue(!level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4), "чанк ещё не загружен");
        h.assertTrue(Structures.tickPlacement(server) == 0 && !Structures.isPlaced(server, SiteType.NEXUS), "без чанка — не ставится");
        h.assertTrue(!level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4), "проверка не грузит чанк");
        level.getChunk(center.getX() >> 4, center.getZ() >> 4); // «игрок подлетел»
        h.assertTrue(Structures.tickPlacement(server) == 1 && Structures.isPlaced(server, SiteType.NEXUS), "чанк загрузился — поставлена");
        h.assertTrue(Structures.tickPlacement(server) == 0, "второй раз — не ставится");
        Structures.resetForTests(server);
        h.succeed();
    }

    static boolean noMarkers(ServerLevel level, BoundingBox box) {
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(p).is(Blocks.STRUCTURE_BLOCK)) return false;
        }
        return true;
    }

    private static double dist(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }
}
