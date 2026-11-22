package dev.rezus.vivacore.zones;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.army.Army;
import dev.rezus.vivacore.army.Factions;
import dev.rezus.vivacore.army.OrionMachine;
import dev.rezus.vivacore.army.Waves;
import dev.rezus.vivacore.stage.ServerStage;
import dev.rezus.vivacore.structures.Structures;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Зоны влияния вокруг ядер лаборатории и Нексуса (тест 32). Заметка «17 Три стороны конфликта», раздел «Зоны влияния»
 * (решено Батыром 16 ноября).
 * <p>
 * Доля стороны = 100% − 10% × (расстояние ÷ 1000) до 9 500 блоков, дальше 0. Ближе 1 000 блоков обычные мобы
 * не появляются вовсе. До 4-й стадии большие круги выключены: у ядер (ближе 1 000) — только охрана, мобы стороны
 * с шансом 30%. Обычный враждебный моб при естественном спавне пропускается с вероятностью доли стороны; вместо
 * пропущенного появляется моб стороны (общее число мобов не растёт: машины — того же класса MONSTER и сидят в том же
 * лимите мобов). У ядер до 4-й стадии: обычный пропущен всегда, моб стороны — с шансом 30%.
 * <p>
 * Цена: на событие спавна — две дистанции до ядер (координаты из кеша {@link Structures#cache}), без обхода мира.
 * Число машин для лимита — кеш, пересчитывается раз в 5 секунд.
 */
public final class InfluenceZones {
    private InfluenceZones() {}

    public enum Side { NONE, LAB, INFECTION }

    /** Решение для одной точки спавна. skip — шанс пропустить обычного моба; faction — шанс моба стороны. */
    public record Share(Side side, double distance, double skip, double faction) {
        public static final Share NONE = new Share(Side.NONE, Double.POSITIVE_INFINITY, 0, 0);
    }

    /** Доля стороны на расстоянии distance от её ядра при стадии stage. Чистая функция (тест 32). */
    public static double factionShare(double distance, int stage) {
        if (distance < VivaConfig.zonesCoreRadius()) {
            return stage >= VivaConfig.zonesFullStage() ? 1.0 : VivaConfig.zonesGuardChance();
        }
        if (stage < VivaConfig.zonesFullStage() || distance > VivaConfig.zonesRadius()) return 0;
        return Math.max(0, 1.0 - VivaConfig.zonesDropPer1000() * distance / 1000.0);
    }

    /** Шанс пропустить обычного моба: у ядра — всегда, в большом круге — равен доле стороны. */
    public static double skipChance(double distance, int stage) {
        if (distance < VivaConfig.zonesCoreRadius()) return 1.0;
        return factionShare(distance, stage);
    }

    /** Доли в точке (x, z): ближайшее активное ядро. Круги не пересекаются, но на всякий случай — ближайшее. */
    public static Share shareAt(MinecraftServer server, double x, double z) {
        if (!VivaConfig.zonesEnabled()) return Share.NONE;
        Structures.Cache c = Structures.cache(server);
        double dLab = c.labCore() != null && Army.isActive(server) ? dist(c.labCore(), x, z) : Double.POSITIVE_INFINITY;
        double dInf = c.nexusCore() != null && !c.nexusDestroyed() ? dist(c.nexusCore(), x, z) : Double.POSITIVE_INFINITY;
        if (dLab == Double.POSITIVE_INFINITY && dInf == Double.POSITIVE_INFINITY) return Share.NONE;
        Side side = dLab <= dInf ? Side.LAB : Side.INFECTION;
        double d = Math.min(dLab, dInf);
        int stage = ServerStage.get(server);
        double skip = skipChance(d, stage);
        if (skip <= 0) return Share.NONE;
        return new Share(side, d, skip, factionShare(d, stage));
    }

    private static double dist(BlockPos core, double x, double z) {
        double dx = core.getX() + 0.5 - x;
        double dz = core.getZ() + 0.5 - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public enum Outcome { NORMAL, SKIPPED, REPLACED }

    /**
     * Обычный враждебный моб хочет появиться естественным путём. Решение и, если надо, моб стороны вместо него.
     * Отдельно от события — для тестов.
     */
    public static Outcome decide(ServerLevel level, BlockPos pos, RandomSource random) {
        Share s = shareAt(level.getServer(), pos.getX() + 0.5, pos.getZ() + 0.5);
        if (s.side() == Side.NONE || random.nextDouble() >= s.skip()) return Outcome.NORMAL;
        // вместо пропущенного — моб стороны; условный шанс, чтобы итоговый был равен доле стороны
        double replace = s.skip() > 0 ? s.faction() / s.skip() : 0;
        if (random.nextDouble() >= replace) return Outcome.SKIPPED;
        boolean spawned = s.side() == Side.LAB ? LabZone.spawn(level, pos, s.distance(), random)
                : InfectionZone.spawn(level, pos, s.faction(), random);
        return spawned ? Outcome.REPLACED : Outcome.SKIPPED;
    }

    /** Событие NeoForge: проверка места естественного спавна. */
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || event.getSpawner() != null) return;
        Mob mob = event.getEntity();
        if (mob.getType().getCategory() != MobCategory.MONSTER || mob instanceof OrionMachine || Factions.isSporeMob(mob)) return;
        ServerLevel level = event.getLevel().getLevel();
        if (level.dimension() != Level.OVERWORLD) return;
        long t0 = System.nanoTime();
        Outcome o = decide(level, mob.blockPosition(), mob.getRandom());
        STATS.add(o, System.nanoTime() - t0);
        if (o != Outcome.NORMAL) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** Счётчики для замера нагрузки (dev-команда devbench): решений, пропущено, заменено, суммарное время. */
    public static final class Stats {
        public long decisions, skipped, replaced, nanos;

        synchronized void add(Outcome o, long ns) {
            decisions++;
            nanos += ns;
            if (o == Outcome.SKIPPED) skipped++;
            if (o == Outcome.REPLACED) replaced++;
        }

        public synchronized Stats snapshotAndReset() {
            Stats s = new Stats();
            s.decisions = decisions;
            s.skipped = skipped;
            s.replaced = replaced;
            s.nanos = nanos;
            decisions = skipped = replaced = nanos = 0;
            return s;
        }
    }

    public static final Stats STATS = new Stats();

    // ---------- кеш числа машин ----------

    private static volatile int machines;

    @Nullable
    private static MinecraftServer cachedFor;

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 100 == 13 || cachedFor != server) {
            cachedFor = server;
            machines = Waves.countMachines(server);
        }
    }

    /** Машин ОРИОНа на сервере (кеш, до 5 с давности) + сколько поставили зоны с прошлого пересчёта. */
    static int machineCount() {
        return machines;
    }

    static void countSpawned() {
        machines++;
    }

    /** Для тестов: пересчитать сразу. */
    public static void recount(MinecraftServer server) {
        cachedFor = server;
        machines = Waves.countMachines(server);
    }
}
