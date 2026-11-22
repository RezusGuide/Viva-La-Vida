package dev.rezus.vivacore.zones;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.army.Waves;
import dev.rezus.vivacore.gametest.TestPlayers;
import dev.rezus.vivacore.stage.ServerStage;
import dev.rezus.vivacore.structures.SiteType;
import dev.rezus.vivacore.structures.Structures;
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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Только для разработки (не регистрируется в обычной сборке): замер нагрузки зон влияния (тест 32, нагрузка).
 * /viva devbench &lt;прогрев, с&gt; &lt;замер, с&gt; — ставит 5 ботов (ServerPlayer с «глухим» соединением, творческий режим,
 * добавлены в мир — вокруг них грузятся чанки и идёт естественный спавн): у ядра лаборатории (100 — чанк центра в прорисовке: постройка встаёт сама), в 3 000 и 7 000
 * от неё, у ядра Нексуса (100) и на спавне. Стадия 4, армия включена. После прогрева меряет время тика с зонами,
 * потом без зон, пишет итоги в лог («Viva Core bench: …») и останавливает сервер.
 */
public final class DevBench {
    private DevBench() {}

    private static final List<ServerPlayer> BOTS = new ArrayList<>();
    private static int phase; // 0 — нет, 1 — прогрев, 2 — с зонами, 3 — без зон
    private static int ticksLeft;
    private static int measureTicks;
    private static long tickStart;
    private static final List<Long> TIMES = new ArrayList<>();

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("devbench").requires(s -> s.hasPermission(4))
                .then(Commands.argument("warmup", IntegerArgumentType.integer(0, 3600))
                        .then(Commands.argument("measure", IntegerArgumentType.integer(5, 3600)).executes(ctx -> {
                            start(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "warmup"),
                                    IntegerArgumentType.getInteger(ctx, "measure"));
                            ctx.getSource().sendSuccess(() -> Component.literal("Viva Core bench: старт"), false);
                            return 1;
                        })));
    }

    private static void start(MinecraftServer server, int warmup, int measure) {
        ServerLevel level = server.overworld();
        Structures.ensurePlanned(server);
        ServerStage.set(server, 4);
        VivaConfig.ARMY_ENABLED.set(true);
        VivaConfig.ZONES_ENABLED.set(true);
        // ночь навсегда: монстры спавнятся на поверхности
        level.setDayTime(18000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
        var spawning = level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
        VivaCore.LOGGER.info("Viva Core bench: doMobSpawning был {}, сложность {}", spawning.get(), level.getDifficulty());
        spawning.set(true, server);
        BlockPos lab = Structures.position(server, SiteType.LAB);
        BlockPos nexus = Structures.position(server, SiteType.NEXUS);
        BlockPos spawn = level.getSharedSpawnPos();
        // направление «от лаборатории к спавну»: точки 3 000 и 7 000 — по линии между ядрами
        double dx = spawn.getX() - lab.getX();
        double dz = spawn.getZ() - lab.getZ();
        double len = Math.max(1, Math.hypot(dx, dz));
        BlockPos[] points = {
                lab.offset(100, 0, 0),
                new BlockPos((int) (lab.getX() + dx / len * 3000), 0, (int) (lab.getZ() + dz / len * 3000)),
                new BlockPos((int) (lab.getX() + dx / len * 7000), 0, (int) (lab.getZ() + dz / len * 7000)),
                nexus.offset(100, 0, 0),
                spawn};
        String[] names = {"bench_lab100", "bench_lab3000", "bench_lab7000", "bench_nexus100", "bench_spawn"};
        for (int i = 0; i < points.length; i++) {
            BlockPos p = points[i];
            int y = level.getChunk(p.getX() >> 4, p.getZ() >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX() & 15, p.getZ() & 15) + 1;
            ServerPlayer bot = TestPlayers.detached(level, new Vec3(p.getX() + 0.5, y, p.getZ() + 0.5), names[i]);
            bot.setGameMode(GameType.CREATIVE);
            level.addNewPlayer(bot);
            BOTS.add(bot);
            VivaCore.LOGGER.info("Viva Core bench: бот {} в {} {} {}", names[i], p.getX(), y, p.getZ());
        }
        phase = 1;
        ticksLeft = warmup * 20;
        measureTicks = measure * 20;
    }

    public static void onTickPre(ServerTickEvent.Pre event) {
        if (phase >= 2) tickStart = System.nanoTime();
    }

    public static void onTickPost(ServerTickEvent.Post event) {
        if (phase == 0) return;
        MinecraftServer server = event.getServer();
        if (phase >= 2) TIMES.add(System.nanoTime() - tickStart);
        if (--ticksLeft > 0) return;
        if (phase == 1) {
            report(server, "прогрев окончен");
            InfluenceZones.STATS.snapshotAndReset();
            TIMES.clear();
            phase = 2;
            ticksLeft = measureTicks;
        } else if (phase == 2) {
            report(server, "С ЗОНАМИ");
            VivaConfig.ZONES_ENABLED.set(false);
            TIMES.clear();
            phase = 3;
            ticksLeft = measureTicks;
        } else {
            report(server, "БЕЗ ЗОН");
            VivaConfig.ZONES_ENABLED.set(true);
            for (ServerPlayer bot : BOTS) bot.serverLevel().removePlayerImmediately(bot, Entity.RemovalReason.DISCARDED);
            BOTS.clear();
            phase = 0;
            server.halt(false);
        }
    }

    private static void report(MinecraftServer server, String what) {
        long[] t = TIMES.stream().mapToLong(Long::longValue).sorted().toArray();
        InfluenceZones.Stats s = InfluenceZones.STATS.snapshotAndReset();
        int monsters = 0;
        for (Entity e : server.overworld().getAllEntities()) {
            if (e.getType().getCategory() == MobCategory.MONSTER) monsters++;
        }
        if (t.length == 0) {
            VivaCore.LOGGER.info("Viva Core bench: {} — монстров {}, машин {}, чанков {}", what, monsters, Waves.countMachines(server),
                    server.overworld().getChunkSource().getLoadedChunksCount());
            return;
        }
        double avg = Arrays.stream(t).average().orElse(0) / 1e6;
        VivaCore.LOGGER.info("Viva Core bench: {} — тиков {}, MSPT средний {} P50 {} P95 {} P99 {} макс {}; монстров {}, машин ОРИОНа {}, "
                        + "чанков {}; решений зон {}, пропущено {}, заменено {}, время решений всего {} мс (на решение {} мкс)",
                what, t.length, f(avg), f(t[t.length / 2] / 1e6), f(t[(int) (t.length * 0.95)] / 1e6), f(t[(int) (t.length * 0.99)] / 1e6),
                f(t[t.length - 1] / 1e6), monsters, Waves.countMachines(server), server.overworld().getChunkSource().getLoadedChunksCount(),
                s.decisions, s.skipped, s.replaced, f(s.nanos / 1e6), s.decisions == 0 ? "-" : f(s.nanos / 1e3 / s.decisions));
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
