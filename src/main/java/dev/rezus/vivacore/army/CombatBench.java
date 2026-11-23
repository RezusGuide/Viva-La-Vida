package dev.rezus.vivacore.army;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.gametest.TestPlayers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Только для разработки: замер нагрузки боя (тест 38, задача 9; повторяет замер задачи 7).
 * /viva devbench combat &lt;прогрев, с&gt; &lt;замер, с&gt; — в 600 блоках от спавна (вне защиты отсека) ставит 4 неуязвимых
 * разбойника без ИИ и вокруг них 20 стреляющих машин (6 лёгкой пехоты, 3 тяжёлой, 3 снайпера, 2 меха, 2 танка,
 * 2 вертолёта, 2 роя), бота-игрока в центре (ему уходят пакеты). Меряет время тика, считает выстрелы,
 * снаряды-сущности (всего и одновременно) и взрывы ОРИОНа, пишет итог в лог («Viva Core combat bench: …») и
 * останавливает сервер.
 */
public final class CombatBench {
    private CombatBench() {}

    private static final List<OrionMachine> MACHINES = new ArrayList<>();
    private static final List<Entity> EXTRA = new ArrayList<>();
    private static final List<Long> TIMES = new ArrayList<>();
    private static int phase; // 0 — нет, 1 — прогрев, 2 — замер
    private static int ticksLeft;
    private static int measureTicks;
    private static long tickStart;
    private static int projectilesSpawned;
    private static int projectilesLive;
    private static int projectilesPeak;
    private static int explosions;
    private static int shotsAtStart;
    private static int limitedAtStart;
    private static int skippedAtStart;
    private static int sentAtStart;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("combat")
                .then(Commands.argument("warmup", IntegerArgumentType.integer(0, 3600))
                        .then(Commands.argument("measure", IntegerArgumentType.integer(5, 3600)).executes(ctx -> {
                            start(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "warmup"),
                                    IntegerArgumentType.getInteger(ctx, "measure"), 1);
                            return 1;
                        }).then(Commands.argument("groups", IntegerArgumentType.integer(1, 10)).executes(ctx -> {
                            start(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "warmup"),
                                    IntegerArgumentType.getInteger(ctx, "measure"), IntegerArgumentType.getInteger(ctx, "groups"));
                            return 1;
                        }))));
    }

    private static int groundY(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return level.getChunk(bx >> 4, bz >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING, bx & 15, bz & 15) + 1;
    }

    /** groups — сколько групп «4 цели + 20 машин» ставить (через 64 блока по X; группа 1 — как в задаче 7). */
    private static void start(MinecraftServer server, int warmup, int measure, int groups) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(ArmyRules.GRIEFING).set(true, server);
        BlockPos spawn = level.getSharedSpawnPos();
        for (int g = 0; g < groups; g++) placeGroup(level, spawn.getX() + 600 + g * 64, spawn.getZ());
        ServerPlayer bot = TestPlayers.detached(level, new Vec3(spawn.getX() + 600.5, groundY(level, spawn.getX() + 600, spawn.getZ()) + 6,
                spawn.getZ() + 0.5), "bench_combat");
        bot.setGameMode(GameType.SPECTATOR);
        level.addNewPlayer(bot);
        EXTRA.add(bot);
        VivaCore.LOGGER.info("Viva Core combat bench: групп {}, машин {}, пули мгновенные: {}", groups, MACHINES.size(),
                dev.rezus.vivacore.VivaConfig.armyHitscanBullets());
        phase = 1;
        ticksLeft = warmup * 20;
        measureTicks = measure * 20;
    }

    private static void placeGroup(ServerLevel level, int cx, int cz) {
        for (int x = (cx - 48) >> 4; x <= (cx + 48) >> 4; x++) {
            for (int z = (cz - 48) >> 4; z <= (cz + 48) >> 4; z++) level.setChunkForced(x, z, true);
        }
        int[][] targets = {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
        for (int[] t : targets) {
            Pillager p = EntityType.PILLAGER.create(level);
            p.moveTo(cx + t[0] + 0.5, groundY(level, cx + t[0], cz + t[1]), cz + t[1] + 0.5);
            p.setNoAi(true);
            p.setInvulnerable(true);
            p.setPersistenceRequired();
            level.addFreshEntity(p);
            EXTRA.add(p);
        }
        MachineType[] kinds = {
                MachineType.INFANTRY_LIGHT, MachineType.INFANTRY_LIGHT, MachineType.INFANTRY_LIGHT,
                MachineType.INFANTRY_LIGHT, MachineType.INFANTRY_LIGHT, MachineType.INFANTRY_LIGHT,
                MachineType.INFANTRY_HEAVY, MachineType.INFANTRY_HEAVY, MachineType.INFANTRY_HEAVY,
                MachineType.SNIPER, MachineType.SNIPER, MachineType.SNIPER,
                MachineType.MECH_WALKER, MachineType.MECH_WALKER, MachineType.TANK, MachineType.TANK,
                MachineType.HELICOPTER, MachineType.HELICOPTER, MachineType.SWARM_UNIT, MachineType.SWARM_UNIT};
        for (int i = 0; i < kinds.length; i++) {
            double a = Math.PI * 2 * i / kinds.length;
            double r = 14 + (i % 3) * 3;
            double x = cx + Math.cos(a) * r;
            double z = cz + Math.sin(a) * r;
            boolean flying = kinds[i].movement == MachineType.Movement.FLYING;
            OrionMachine m = ArmyEntities.type(kinds[i]).create(level);
            m.moveTo(x, groundY(level, x, z) + (flying ? 4 : 0), z, (float) Math.toDegrees(a) + 90, 0);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.COMMAND, null);
            m.setPersistenceRequired();
            level.addFreshEntity(m);
            MACHINES.add(m);
        }
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (phase == 0 || event.getLevel().isClientSide() || !(event.getEntity() instanceof OrionProjectile)) return;
        projectilesLive++;
        projectilesPeak = Math.max(projectilesPeak, projectilesLive);
        if (phase == 2) projectilesSpawned++;
    }

    public static void onLeave(EntityLeaveLevelEvent event) {
        if (phase == 0 || event.getLevel().isClientSide() || !(event.getEntity() instanceof OrionProjectile)) return;
        projectilesLive = Math.max(0, projectilesLive - 1);
    }

    public static void onExplosion(ExplosionEvent.Start event) {
        if (phase == 2 && event.getExplosion().getDirectSourceEntity() instanceof OrionProjectile) explosions++;
    }

    public static void onTickPre(ServerTickEvent.Pre event) {
        if (phase == 2) tickStart = System.nanoTime();
    }

    private static int shots() {
        int s = 0;
        for (OrionMachine m : MACHINES) s += m.combat().shotsFired();
        return s;
    }

    public static void onTickPost(ServerTickEvent.Post event) {
        if (phase == 0) return;
        if (phase == 2) TIMES.add(System.nanoTime() - tickStart);
        if (--ticksLeft > 0) return;
        MinecraftServer server = event.getServer();
        if (phase == 1) {
            TIMES.clear();
            projectilesSpawned = 0;
            projectilesPeak = projectilesLive;
            explosions = 0;
            shotsAtStart = shots();
            Hitscan.STATS = true;
            Hitscan.nanos = 0;
            Hitscan.count = 0;
            OrionProjectile.tickNanos = 0;
            limitedAtStart = ArmyLimits.explosionsLimited;
            skippedAtStart = ArmyLimits.projectilesLimited;
            sentAtStart = ArmyNetwork.sent;
            phase = 2;
            ticksLeft = measureTicks;
            VivaCore.LOGGER.info("Viva Core combat bench: прогрев окончен, живых машин {}", MACHINES.stream().filter(Entity::isAlive).count());
            return;
        }
        long[] t = TIMES.stream().mapToLong(Long::longValue).sorted().toArray();
        double avg = Arrays.stream(t).average().orElse(0) / 1e6;
        long alive = MACHINES.stream().filter(Entity::isAlive).count();
        VivaCore.LOGGER.info("Viva Core combat bench: тиков {}, MSPT средний {} P50 {} P95 {} P99 {} макс {}; живых машин {}, "
                        + "выстрелов {}, снарядов-сущностей {} (одновременно до {}), взрывов ОРИОНа {} (из них урон без взрыва {}), "
                        + "пропущено снарядов по лимиту {}, пакетов трассеров {}, сущностей в мире {}",
                t.length, f(avg), f(t[t.length / 2] / 1e6), f(t[(int) (t.length * 0.95)] / 1e6), f(t[(int) (t.length * 0.99)] / 1e6),
                f(t[t.length - 1] / 1e6), alive, shots() - shotsAtStart, projectilesSpawned, projectilesPeak, explosions,
                ArmyLimits.explosionsLimited - limitedAtStart, ArmyLimits.projectilesLimited - skippedAtStart, ArmyNetwork.sent - sentAtStart,
                server.overworld().getEntities().getAll().spliterator().estimateSize());
        VivaCore.LOGGER.info("Viva Core combat bench: мгновенных выстрелов {} по {} мкс (всего {} мс); тики снарядов-сущностей всего {} мс",
                Hitscan.count, Hitscan.count == 0 ? "-" : f(Hitscan.nanos / 1e3 / Hitscan.count), f(Hitscan.nanos / 1e6),
                f(OrionProjectile.tickNanos / 1e6));
        Hitscan.STATS = false;
        for (OrionMachine m : MACHINES) m.discard();
        for (Entity e : EXTRA) {
            if (e instanceof ServerPlayer p) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else e.discard();
        }
        MACHINES.clear();
        EXTRA.clear();
        phase = 0;
        server.halt(false);
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
