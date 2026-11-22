package dev.rezus.vivacore.zones;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.army.OrionMachine;
import dev.rezus.vivacore.orion.Orion;
import dev.rezus.vivacore.stage.ServerStage;
import dev.rezus.vivacore.structures.SiteType;
import dev.rezus.vivacore.structures.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 32: зоны влияния. Доли в контрольных точках (0, 1 000, 5 000, 9 000, 9 600 блоков от ядра), до 4-й стадии —
 * только охрана 30% у ядра; после отмены программы машины в зоне лаборатории не появляются; после «Нексус уничтожен»
 * зона заражения пуста. Тесты, меняющие общее состояние (ядра, стадия, флаги), — в своих пачках и всё сбрасывают.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZoneGameTests {
    private ZoneGameTests() {}

    /** Пояса состава по умолчанию (zones.labComposition). */
    private static final java.util.Set<String> NEAR = java.util.Set.of("infantry_heavy", "mech_walker", "sniper", "tank");
    private static final java.util.Set<String> FAR = java.util.Set.of("scout_drone", "infantry_light", "swarm_unit");

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    @GameTest(template = "empty", batch = "zones_math")
    public static void sharesAtControlPoints(GameTestHelper h) {
        int full = 4;
        h.assertTrue(near(InfluenceZones.factionShare(0, full), 1.0), "у ядра — 100%");
        h.assertTrue(near(InfluenceZones.factionShare(1000, full), 0.9), "1 000 — 90%");
        h.assertTrue(near(InfluenceZones.factionShare(5000, full), 0.5), "5 000 — 50%");
        h.assertTrue(near(InfluenceZones.factionShare(9000, full), 0.1), "9 000 — 10%");
        h.assertTrue(near(InfluenceZones.factionShare(9500, full), 0.05), "9 500 — 5%");
        h.assertTrue(near(InfluenceZones.factionShare(9600, full), 0.0), "9 600 — 0");
        h.assertTrue(near(InfluenceZones.skipChance(500, full), 1.0) && near(InfluenceZones.skipChance(0, full), 1.0), "ближе 1 000 обычных нет");
        h.assertTrue(near(InfluenceZones.skipChance(5000, full), 0.5), "обычные пропускаются с долей стороны");
        // до 4-й стадии: только охрана у ядра
        for (int stage = 1; stage <= 3; stage++) {
            h.assertTrue(near(InfluenceZones.factionShare(0, stage), 0.3) && near(InfluenceZones.factionShare(999, stage), 0.3),
                    "стадия " + stage + ": у ядра охрана 30%");
            h.assertTrue(near(InfluenceZones.skipChance(500, stage), 1.0), "стадия " + stage + ": у ядра обычных нет");
            h.assertTrue(near(InfluenceZones.factionShare(1000, stage), 0) && near(InfluenceZones.factionShare(5000, stage), 0)
                    && near(InfluenceZones.skipChance(5000, stage), 0), "стадия " + stage + ": большие круги выключены");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "zones_sides")
    public static void nearestCoreAndFlags(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        boolean armyWas = VivaConfig.armyEnabled();
        VivaConfig.ARMY_ENABLED.set(true); // army.enabled по умолчанию false — без него зоны лаборатории нет
        Structures.resetForTests(server);
        Orion.reset(server);
        Structures.setPlanned(server, SiteType.LAB, new BlockPos(100000, 0, 0));
        Structures.setPlanned(server, SiteType.NEXUS, new BlockPos(-100000, 0, 0));
        ServerStage.set(server, 4);
        try {
            InfluenceZones.Share lab = InfluenceZones.shareAt(server, 105000.5, 0.5);
            h.assertTrue(lab.side() == InfluenceZones.Side.LAB && Math.abs(lab.faction() - 0.5) < 1e-6, "5 000 от лаборатории — 50%: " + lab);
            InfluenceZones.Share inf = InfluenceZones.shareAt(server, -100000.5, 9000.5);
            h.assertTrue(inf.side() == InfluenceZones.Side.INFECTION && Math.abs(inf.faction() - 0.1) < 1e-3, "9 000 от Нексуса — 10%: " + inf);
            h.assertTrue(InfluenceZones.shareAt(server, 0.5, 0.5).side() == InfluenceZones.Side.NONE, "между ядрами (спавн) — нейтрально");
            ServerStage.set(server, 3);
            h.assertTrue(InfluenceZones.shareAt(server, 105000.5, 0.5).side() == InfluenceZones.Side.NONE, "стадия 3: большой круг выключен");
            InfluenceZones.Share guard = InfluenceZones.shareAt(server, 100500.5, 0.5);
            h.assertTrue(guard.side() == InfluenceZones.Side.LAB && Math.abs(guard.faction() - 0.3) < 1e-6 && guard.skip() == 1.0,
                    "стадия 3: охрана у ядра 30%: " + guard);
            ServerStage.set(server, 4);
            Structures.setNexusDestroyed(server, true);
            h.assertTrue(InfluenceZones.shareAt(server, -100000.5, 9000.5).side() == InfluenceZones.Side.NONE, "Нексус уничтожен — зоны нет");
            Orion.cancel(server, "test");
            h.assertTrue(InfluenceZones.shareAt(server, 105000.5, 0.5).side() == InfluenceZones.Side.NONE, "программа отменена — зоны нет");
        } finally {
            VivaConfig.ARMY_ENABLED.set(armyWas);
            Orion.reset(server);
            ServerStage.set(server, 1);
            Structures.resetForTests(server);
        }
        h.succeed();
    }

    @GameTest(template = "wheels", batch = "zones_lab_spawn", timeoutTicks = 100)
    public static void labZoneSpawnsMachinesUntilCancelled(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        boolean armyWas = VivaConfig.armyEnabled();
        VivaConfig.ARMY_ENABLED.set(true); // army.enabled по умолчанию false — без него зоны лаборатории нет
        Structures.resetForTests(server);
        Orion.reset(server);
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 7));
        Structures.setPlanned(server, SiteType.LAB, pos.offset(300, 0, 0)); // ядро в 300 блоках: доля 100%
        ServerStage.set(server, 4);
        InfluenceZones.recount(server);
        RandomSource r = RandomSource.create(1);
        AABB area = new AABB(pos).inflate(8);
        try {
            InfluenceZones.Outcome o = InfluenceZones.decide(h.getLevel(), pos, r);
            h.assertTrue(o == InfluenceZones.Outcome.REPLACED, "у ядра — машина вместо обычного моба: " + o);
            var made = h.getLevel().getEntitiesOfClass(OrionMachine.class, area);
            h.assertTrue(made.size() == 1 && !made.get(0).machine().infected, "появилась машина ОРИОНа");
            h.assertTrue(NEAR.contains(made.get(0).machine().id), "у ядра — состав ближнего пояса: " + made.get(0).machine().id);
            made.forEach(m -> m.discard());
            Orion.cancel(server, "test");
            for (int i = 0; i < 50; i++) {
                h.assertTrue(InfluenceZones.decide(h.getLevel(), pos, r) == InfluenceZones.Outcome.NORMAL, "после отмены — обычные мобы");
            }
            h.assertTrue(h.getLevel().getEntitiesOfClass(OrionMachine.class, area).isEmpty(), "после отмены машин нет");
            // стадия 1, 5 000 блоков от ядра — круг выключен, обычные мобы не трогаются
            Orion.reset(server);
            ServerStage.set(server, 1);
            Structures.setPlanned(server, SiteType.LAB, pos.offset(5000, 0, 0));
            for (int i = 0; i < 50; i++) {
                h.assertTrue(InfluenceZones.decide(h.getLevel(), pos, r) == InfluenceZones.Outcome.NORMAL, "стадия 1 вдали — обычные");
            }
        } finally {
            VivaConfig.ARMY_ENABLED.set(armyWas);
            h.getLevel().getEntitiesOfClass(OrionMachine.class, area).forEach(m -> m.discard());
            Orion.reset(server);
            ServerStage.set(server, 1);
            Structures.resetForTests(server);
        }
        h.succeed();
    }

    @GameTest(template = "wheels", batch = "zones_infection_spawn", timeoutTicks = 100)
    public static void infectionZoneStopsAfterNexus(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Structures.resetForTests(server);
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 7));
        Structures.setPlanned(server, SiteType.NEXUS, pos.offset(0, 0, 200));
        ServerStage.set(server, 4);
        InfluenceZones.recount(server);
        RandomSource r = RandomSource.create(2);
        AABB area = new AABB(pos).inflate(8);
        try {
            h.assertTrue(InfluenceZones.decide(h.getLevel(), pos, r) == InfluenceZones.Outcome.REPLACED, "у Нексуса — моб заражения");
            var made = h.getLevel().getEntitiesOfClass(OrionMachine.class, area);
            h.assertTrue(made.size() == 1 && made.get(0).machine().infected, "заглушка до Spore — заражённая машина");
            made.forEach(m -> m.discard());
            Structures.setNexusDestroyed(server, true);
            for (int i = 0; i < 50; i++) {
                h.assertTrue(InfluenceZones.decide(h.getLevel(), pos, r) == InfluenceZones.Outcome.NORMAL, "Нексус уничтожен — обычные");
            }
            h.assertTrue(h.getLevel().getEntitiesOfClass(OrionMachine.class, area).isEmpty(), "зона заражения пуста");
        } finally {
            h.getLevel().getEntitiesOfClass(OrionMachine.class, area).forEach(m -> m.discard());
            ServerStage.set(server, 1);
            Structures.resetForTests(server);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "zones_math")
    public static void labCompositionByDistance(GameTestHelper h) {
        RandomSource r = RandomSource.create(3);
        for (int i = 0; i < 40; i++) {
            var near = LabZone.pick(500, r);
            var far = LabZone.pick(8000, r);
            h.assertTrue(near != null && NEAR.contains(near.id), "ближе к ядру — тяжёлые: " + near);
            h.assertTrue(far != null && FAR.contains(far.id), "дальше — дроны и пехота: " + far);
        }
        h.succeed();
    }
}
