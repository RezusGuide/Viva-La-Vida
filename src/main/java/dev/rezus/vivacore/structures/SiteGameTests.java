package dev.rezus.vivacore.structures;

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

    private static final SitePlanner.Params P = new SitePlanner.Params(10000, 20, 2, 1000, 100, 40);

    @GameTest(template = "empty", batch = "sites_plan")
    public static void planOppositeOnLand(GameTestHelper h) {
        BlockPos spawn = new BlockPos(120, 64, -45);
        SitePlanner.Plan a = SitePlanner.plan(12345L, spawn, P, (x, z) -> true);
        SitePlanner.Plan b = SitePlanner.plan(12345L, spawn, P, (x, z) -> true);
        SitePlanner.Plan c = SitePlanner.plan(98765L, spawn, P, (x, z) -> true);
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
        SitePlanner.Plan plan = SitePlanner.plan(777L, spawn, P, land);
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
        SitePlanner.Plan none = SitePlanner.plan(777L, spawn, P, (x, z) -> false);
        h.assertTrue(!none.labOnLand() && none.lab().equals(direct), "без суши — прямая точка и флаг");
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
