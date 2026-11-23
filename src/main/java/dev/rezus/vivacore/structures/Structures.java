package dev.rezus.vivacore.structures;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.army.ArmyEntities;
import dev.rezus.vivacore.army.MachineType;
import dev.rezus.vivacore.army.OrionMachine;
import dev.rezus.vivacore.orion.Orion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Размещение трёх построек (тест 31). Заметка «17 Три стороны конфликта», раздел «Карта».
 * <p>
 * Как не генерировать далёкие чанки: при первом запуске считается только план — угол из сида и проверка суши по шуму
 * биомов генератора (чанки не грузятся). Жилой отсек ставится сразу у спавна (чанки спавна и так загружены).
 * Лаборатория и Нексус ждут: раз в секунду проверяем, загружен ли чанк центра постройки (а он грузится, только когда
 * игрок подлетел на дальность прорисовки). Как только загружен — догружаем недостающие чанки коробки (обычно они уже
 * загружены: коробка 64 блока, это 4×4 чанка рядом с игроком) и ставим постройку. Флаг «поставлена» в SavedData —
 * второй раз не ставится.
 * <p>
 * Шаблон и метки: код знает только имя файла ({@link SiteType#file}) и метки — structure_block в режиме DATA с именем
 * в поле metadata. Метки не ставятся в мир (BlockIgnoreProcessor), их позиции читаются из шаблона:
 * <ul>
 *   <li>{@code vivacore:ground} — эта высота встаёт на уровень земли (нижние слои уходят в грунт);</li>
 *   <li>{@code vivacore:core} — ядро: центр зоны влияния (лаборатория, Нексус; у Нексуса здесь позже встанет Infnexus);</li>
 *   <li>{@code vivacore:player_spawn} — спавн мира (жилой отсек);</li>
 *   <li>{@code vivacore:guardian} — страж ядра; {@code vivacore:guard} — охрана (типы — конфиг).</li>
 * </ul>
 */
public final class Structures {
    private Structures() {}

    public static final String M_GROUND = "vivacore:ground";
    public static final String M_CORE = "vivacore:core";
    public static final String M_PLAYER_SPAWN = "vivacore:player_spawn";
    public static final String M_GUARDIAN = "vivacore:guardian";
    public static final String M_GUARD = "vivacore:guard";

    // ---------- кеш для зон и защиты: без чтения SavedData на каждое событие ----------

    /** Ядра и коробки поставленных построек. Пересобирается при изменении SiteData. */
    public record Cache(@Nullable BlockPos labCore, @Nullable BlockPos nexusCore, boolean nexusDestroyed,
                        List<Protected> boxes) {}

    public record Protected(SiteType type, BoundingBox box) {}

    private static volatile Cache cache;

    static void invalidateCache() {
        cache = null;
    }

    public static Cache cache(MinecraftServer server) {
        Cache c = cache;
        if (c == null) {
            SiteData d = SiteData.get(server);
            List<Protected> boxes = new ArrayList<>();
            for (SiteType t : SiteType.values()) {
                SiteData.Entry e = d.entry(t);
                if (e.placed() && e.box() != null) boxes.add(new Protected(t, e.box()));
            }
            c = new Cache(position(server, SiteType.LAB), position(server, SiteType.NEXUS), d.nexusDestroyed(), List.copyOf(boxes));
            cache = c;
        }
        return c;
    }

    /** Новый мир / остановка сервера — кеш от старого мира не переживает. */
    public static void onServerStopped() {
        cache = null;
    }

    // ---------- API ----------

    /**
     * Координаты постройки для квестов и зон: ядро поставленной постройки (метка core; у отсека — спавн),
     * иначе плановый центр (y = 0 — высота ещё неизвестна). null — плана нет.
     */
    @Nullable
    public static BlockPos position(MinecraftServer server, SiteType type) {
        SiteData.Entry e = SiteData.get(server).entry(type);
        if (e.placed()) {
            if (type == SiteType.CRASH && e.spawn() != null) return e.spawn();
            if (e.core() != null) return e.core();
            if (e.box() != null) return e.box().getCenter();
        }
        return e.planned();
    }

    public static boolean isPlaced(MinecraftServer server, SiteType type) {
        return SiteData.get(server).entry(type).placed();
    }

    @Nullable
    public static BoundingBox box(MinecraftServer server, SiteType type) {
        return SiteData.get(server).entry(type).box();
    }

    public static boolean nexusDestroyed(MinecraftServer server) {
        return SiteData.get(server).nexusDestroyed();
    }

    public static void setNexusDestroyed(MinecraftServer server, boolean destroyed) {
        SiteData.get(server).setNexusDestroyed(destroyed);
    }

    /** Для тестов: задать плановый центр постройки. */
    public static void setPlanned(MinecraftServer server, SiteType type, BlockPos center) {
        SiteData.get(server).setPlanned(type, new BlockPos(center.getX(), 0, center.getZ()));
    }

    /** План: угол из сида, точки на суше. Считается один раз на мир. */
    public static void ensurePlanned(MinecraftServer server) {
        SiteData d = SiteData.get(server);
        if (d.planned()) return;
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        long t0 = System.nanoTime();
        SitePlanner.Plan plan = SitePlanner.plan(level.getSeed(), spawn, params(), templateHalf(level, SiteType.LAB),
                templateHalf(level, SiteType.NEXUS), SitePlanner.biomeLand(level));
        VivaCore.LOGGER.info("Viva Core: поиск места построек — {} мс", (System.nanoTime() - t0) / 1_000_000);
        if (d.entry(SiteType.CRASH).planned() == null) d.setPlanned(SiteType.CRASH, new BlockPos(spawn.getX(), 0, spawn.getZ()));
        d.setPlan(plan.angle(), plan.lab(), plan.nexus());
        VivaCore.LOGGER.info("Viva Core: постройки — угол {}°, лаборатория {} (суша {}), Нексус {} (суша {})",
                String.format("%.1f", Math.toDegrees(plan.angle())), plan.lab().toShortString(), plan.labOnLand(),
                plan.nexus().toShortString(), plan.nexusOnLand());
    }

    public static SitePlanner.Params params() {
        return new SitePlanner.Params(VivaConfig.structuresDistance(), VivaConfig.structuresAngleShift(), VivaConfig.structuresAngleStep(),
                VivaConfig.structuresDistanceShift(), VivaConfig.structuresDistanceStep(), VivaConfig.structuresLandRadius(),
                VivaConfig.structuresLandGrid(), VivaConfig.structuresIslandFactor());
    }

    /** Половина большей стороны шаблона постройки (для поиска места); нет шаблона — 0. */
    static int templateHalf(ServerLevel level, SiteType type) {
        return level.getStructureManager().get(type.template())
                .map(t -> Math.max(t.getSize().getX(), t.getSize().getZ()) / 2).orElse(0);
    }

    private static boolean auto(MinecraftServer server) {
        return VivaConfig.structuresAutoPlace() && !(server instanceof GameTestServer);
    }

    /** Первый запуск мира: план и жилой отсек у спавна (чанки спавна уже загружены). */
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        cache = null;
        if (!auto(server)) return;
        ensurePlanned(server);
        if (!isPlaced(server, SiteType.CRASH)) {
            place(server, SiteType.CRASH);
        }
    }

    /** Раз в секунду: поставить лабораторию / Нексус, если чанк их центра загрузился. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7 || !auto(server)) return;
        tickPlacement(server);
    }

    /**
     * Проверка «область загрузилась»: по одной проверке hasChunk на постройку (чанк центра). Чанки не грузит.
     * @return сколько построек поставлено
     */
    public static int tickPlacement(MinecraftServer server) {
        SiteData d = SiteData.get(server);
        ServerLevel level = server.overworld();
        int placed = 0;
        for (SiteType t : new SiteType[] {SiteType.LAB, SiteType.NEXUS}) {
            SiteData.Entry e = d.entry(t);
            if (e.placed() || e.planned() == null) continue;
            if (level.getChunkSource().hasChunk(e.planned().getX() >> 4, e.planned().getZ() >> 4)) {
                VivaCore.LOGGER.info("Viva Core: область постройки {} загрузилась — ставлю", t.id);
                if (place(server, t) != null) placed++;
            }
        }
        return placed;
    }

    /** Поставить постройку в её плановой точке (если плана нет — у спавна). @return коробка или null */
    @Nullable
    public static BoundingBox place(MinecraftServer server, SiteType type) {
        ensurePlanned(server);
        BlockPos at = SiteData.get(server).entry(type).planned();
        if (at == null) at = server.overworld().getSharedSpawnPos();
        return placeAt(server.overworld(), type, at, rotationFor(server.overworld().getSeed(), type));
    }

    /** Поставить постройку центром в точке center (x, z) и записать в SavedData. Второй раз не ставит. */
    @Nullable
    public static BoundingBox placeAt(ServerLevel level, SiteType type, BlockPos center, Rotation rotation) {
        SiteData d = SiteData.get(level.getServer());
        if (d.entry(type).placed()) return d.entry(type).box();
        Placed p = build(level, type, center, rotation);
        if (p == null) return null;
        d.setPlanned(type, new BlockPos(center.getX(), 0, center.getZ()));
        d.setPlaced(type, p.box, p.core, p.spawn);
        if (type == SiteType.CRASH && p.spawn != null && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            level.setDefaultSpawnPos(p.spawn, 0f);
            if (VivaConfig.structuresCrashSpawnRadiusZero()) {
                level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
            }
        }
        VivaCore.LOGGER.info("Viva Core: постройка {} поставлена у {} (коробка {} {} {} — {} {} {}), ядро {}, спавн {}", type.id,
                center.toShortString(), p.box.minX(), p.box.minY(), p.box.minZ(), p.box.maxX(), p.box.maxY(), p.box.maxZ(),
                p.core, p.spawn);
        return p.box;
    }

    /** Для тестов и оператора: забыть, что постройка стоит (блоки не трогает) — можно поставить снова. */
    public static void forget(MinecraftServer server, SiteType type) {
        SiteData.get(server).forget(type);
    }

    /** Для тестов защиты: считать постройку стоящей в коробке box (без блоков). */
    public static void setPlacedForTests(MinecraftServer server, SiteType type, BoundingBox box, @Nullable BlockPos core) {
        SiteData.get(server).setPlaced(type, box, core, null);
    }

    /** Для тестов: забыть все постройки, план и флаг Нексуса. */
    public static void resetForTests(MinecraftServer server) {
        SiteData.get(server).resetAll();
    }

    public static Rotation rotationFor(long seed, SiteType type) {
        return Rotation.values()[RandomSource.create(seed + 31L * (type.ordinal() + 1)).nextInt(4)];
    }

    private record Placed(BoundingBox box, @Nullable BlockPos core, @Nullable BlockPos spawn) {}

    /** Сейчас ставится шаблон постройки (только поток сервера). */
    private static boolean building;

    public static boolean isBuilding() {
        return building;
    }

    @Nullable
    private static Placed build(ServerLevel level, SiteType type, BlockPos center, Rotation rotation) {
        StructureTemplate template = level.getStructureManager().get(type.template()).orElse(null);
        if (template == null || template.getSize().getX() == 0) {
            VivaCore.LOGGER.error("Viva Core: нет шаблона {} — постройка {} не поставлена", type.template(), type.id);
            return null;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        BoundingBox b0 = template.getBoundingBox(settings, BlockPos.ZERO);
        int groundY = 0;
        for (var info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.STRUCTURE_BLOCK)) {
            if (M_GROUND.equals(marker(info))) groundY = info.pos().getY();
        }
        int ox = center.getX() - (b0.minX() + b0.maxX()) / 2;
        int oz = center.getZ() - (b0.minZ() + b0.maxZ()) / 2;
        BoundingBox foot = b0.moved(ox, 0, oz);
        loadChunks(level, foot);
        int surface = surface(level, foot);
        BlockPos origin = new BlockPos(ox, surface - groundY, oz);
        BoundingBox box = template.getBoundingBox(settings, origin);

        java.util.List<BlockPos> terrainFluids = fluidsIn(level, box);
        building = true; // огонь шаблона (огонь душ Нексуса) не убирается защитой
        try {
            template.placeInWorld(level, origin, origin, settings, level.random, Block.UPDATE_CLIENTS);
        } finally {
            building = false;
        }
        removeLeftoverMarkers(level, box);
        drainTerrainFluids(level, terrainFluids, surface);
        sealFluidRing(level, box);
        foundation(level, box);
        clearAbove(level, box);
        blendEdges(level, box, surface);

        BlockPos core = null;
        BlockPos spawn = null;
        List<? extends String> guards = VivaConfig.structuresGuardTypes();
        int guardIndex = 0;
        for (var info : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
            String m = marker(info);
            BlockPos pos = info.pos();
            if (m == null || M_GROUND.equals(m)) continue;
            switch (m) {
                case M_CORE -> core = pos;
                case M_PLAYER_SPAWN -> spawn = pos;
                case M_GUARDIAN -> spawnMachine(level, VivaConfig.structuresGuardianType(), pos);
                case M_GUARD -> {
                    if (!guards.isEmpty()) spawnMachine(level, guards.get(guardIndex++ % guards.size()), pos);
                }
                default -> VivaCore.LOGGER.warn("Viva Core: неизвестная метка «{}» в шаблоне {} ({})", m, type.template(), pos);
            }
        }
        if (core == null && type.hasCore()) core = box.getCenter();
        return new Placed(box, core, spawn);
    }

    /** Имя метки: structure_block в режиме DATA, поле metadata. */
    @Nullable
    static String marker(StructureTemplate.StructureBlockInfo info) {
        if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) return null;
        String m = info.nbt().getString("metadata").trim();
        return m.isEmpty() ? null : m;
    }

    private static void loadChunks(ServerLevel level, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** Уровень земли под коробкой: медиана 9 точек карты высот (без листвы). */
    private static int surface(ServerLevel level, BoundingBox foot) {
        int[] ys = new int[9];
        int i = 0;
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                int x = foot.minX() + (foot.maxX() - foot.minX()) * a / 2;
                int z = foot.minZ() + (foot.maxZ() - foot.minZ()) * b / 2;
                ys[i++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            }
        }
        Arrays.sort(ys);
        return Mth.clamp(ys[4], level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 1);
    }

    /** На случай чужого шаблона с метками не в режиме DATA: structure_block в коробке не остаётся. */
    private static void removeLeftoverMarkers(ServerLevel level, BoundingBox box) {
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(p).is(Blocks.STRUCTURE_BLOCK)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Жидкость местности в коробке до постройки (озеро, река, болото) — позиции чистых блоков жидкости. */
    private static java.util.List<BlockPos> fluidsIn(ServerLevel level, BoundingBox box) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(p).getBlock() instanceof LiquidBlock) out.add(p.immutable());
        }
        return out;
    }

    /**
     * Вода местности, оставшаяся в коробке там, где у шаблона пусто (structure_void), иначе зальёт постройку:
     * ниже уровня земли — засыпка, выше — воздух. Вода, которую поставил сам шаблон, не трогается, если только местность
     * не была водой ровно в той же точке.
     */
    static void drainTerrainFluids(ServerLevel level, java.util.List<BlockPos> terrainFluids, int surface) {
        BlockState fill = foundationBlock();
        for (BlockPos p : terrainFluids) {
            if (!(level.getBlockState(p).getBlock() instanceof LiquidBlock)) continue;
            level.setBlock(p, p.getY() < surface ? fill : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Кольцо в один блок вокруг коробки (по высоте коробки): жидкость → засыпка, чтобы снаружи не затекало внутрь. */
    static void sealFluidRing(ServerLevel level, BoundingBox box) {
        BlockState fill = foundationBlock();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
                for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                    boolean ring = x == box.minX() - 1 || x == box.maxX() + 1 || z == box.minZ() - 1 || z == box.maxZ() + 1;
                    if (!ring) continue;
                    p.set(x, y, z);
                    if (level.getBlockState(p).getBlock() instanceof LiquidBlock) level.setBlock(p, fill, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * Фундамент (задача 9): под нижним слоем постройки пустоты (обрыв, склон, пещера, вода) засыпаются столбами до
     * земли, не глубже structures.foundationMaxDepth. Блоки — по местности под столбом ({@link #pillarBlocks}):
     * у самой земли (3 блока) — то, что под ней (земля под травой, песчаник под песком), выше — камень этой местности.
     */
    private static void foundation(ServerLevel level, BoundingBox box) {
        int depth = VivaConfig.structuresFoundationDepth();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                p.set(x, box.minY(), z);
                BlockState bottom = level.getBlockState(p);
                if (bottom.isAir() || !bottom.getFluidState().isEmpty()) continue;
                int floor = Math.max(box.minY() - depth, level.getMinBuildHeight() + 1);
                int ground = floor - 1;
                BlockState groundState = foundationBlock();
                for (int y = box.minY() - 1; y >= floor; y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (solidGround(s)) {
                        ground = y;
                        groundState = s;
                        break;
                    }
                }
                BlockState[] fill = pillarBlocks(groundState);
                for (int y = box.minY() - 1; y > ground; y--) {
                    p.set(x, y, z);
                    level.setBlock(p, y - ground <= 3 ? fill[0] : fill[1], Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Земля, на которую можно опереть столб: твёрдый блок без жидкости, не листва, не растение, не бревно. */
    static boolean solidGround(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)
                && s.blocksMotion();
    }

    /**
     * Блоки столба по земле под ним: [у земли, выше]. Трава/земля → земля, выше камень; песок → песчаник (красный —
     * красный); камень, глубинный сланец, терракота и прочие природные камни — тот же блок; иначе — structures.foundationBlock.
     */
    static BlockState[] pillarBlocks(BlockState ground) {
        BlockState stone = foundationBlock();
        if (ground.is(Blocks.RED_SAND)) return new BlockState[] {Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState()};
        if (ground.is(BlockTags.SAND)) return new BlockState[] {Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()};
        if (ground.is(BlockTags.DIRT) || ground.is(Blocks.SNOW_BLOCK)) return new BlockState[] {Blocks.DIRT.defaultBlockState(), stone};
        if (ground.is(Blocks.GRAVEL)) return new BlockState[] {Blocks.ANDESITE.defaultBlockState(), stone};
        if (ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(BlockTags.TERRACOTTA) || ground.is(Blocks.SANDSTONE)
                || ground.is(Blocks.RED_SANDSTONE) || ground.is(Blocks.CALCITE)) {
            BlockState same = ground.getBlock().defaultBlockState();
            return new BlockState[] {same, same};
        }
        return new BlockState[] {stone, stone};
    }

    /**
     * Края (задача 9): квадратная плоская площадка вокруг постройки сводится к местности. В полосе structures.edgeBlend
     * снаружи коробки верх земли плавно (smoothstep) идёт от уровня земли постройки к высоте местности, склон не круче
     * structures.edgeSlope на блок от края, с неровностью до ±0,4 (шум по координатам — край не прямой). Внутри коробки на
     * structures.edgeInner блоков природная земля (не блоки постройки) тоже чуть сдвигается к местности.
     * Шаблон не перестраивается: меняются только столбцы природной земли.
     */
    static void blendEdges(ServerLevel level, BoundingBox box, int surface) {
        int w = VivaConfig.structuresEdgeBlend();
        if (w <= 0) return;
        int slope = VivaConfig.structuresEdgeSlope();
        int inner = VivaConfig.structuresEdgeInner();
        int base = surface - 1; // верх земли постройки (метка vivacore:ground — уровень, на котором стоят)
        long seed = level.getSeed() ^ 0x45444745L;
        for (int x = box.minX() - w; x <= box.maxX() + w; x++) {
            for (int z = box.minZ() - w; z <= box.maxZ() + w; z++) {
                int dx = Math.max(Math.max(box.minX() - x, x - box.maxX()), 0);
                int dz = Math.max(Math.max(box.minZ() - z, z - box.maxZ()), 0);
                double d = Math.sqrt(dx * dx + dz * dz);
                boolean outside = dx > 0 || dz > 0;
                if (outside && d > w) continue;
                if (!outside) {
                    int din = Math.min(Math.min(x - box.minX(), box.maxX() - x), Math.min(z - box.minZ(), box.maxZ() - z));
                    if (din >= inner) continue;
                }
                int top = groundTop(level, x, z, base + w * slope + 8);
                if (top == Integer.MIN_VALUE) continue; // вода, дерево — не трогаем
                int target;
                if (outside) {
                    // уровень у края: верх природной земли в ближайшем столбце коробки (у шаблона там пусто — местность,
                    // как у краёв Нексуса и отсека), иначе (край — блоки постройки: стены лаборатории) — уровень земли
                    int edgeTop = groundTop(level, Mth.clamp(x, box.minX(), box.maxX()), Mth.clamp(z, box.minZ(), box.maxZ()),
                            box.maxY() + 1);
                    int b0 = edgeTop == Integer.MIN_VALUE ? base : edgeTop;
                    double t = d / (w + 1.0);
                    double s = t * t * (3 - 2 * t);
                    // неровность до ±0,4: с крутизной smoothstep (~2,1 на блок при обрыве 10) шаг после округления ≤ 3
                    double h = b0 + (top - b0) * s + noise(seed, x, z) * 1.6 * t * (1 - t);
                    int lim = (int) Math.ceil(d * slope);
                    target = Mth.clamp((int) Math.round(h), b0 - lim, b0 + lim);
                } else {
                    // внутренняя кайма: природная земля постройки на 1 блок к местности за полосой (по нормали к краю),
                    // не во всех столбцах — край неровный
                    if (Math.abs(noise(seed + 7, x, z)) < 0.35) continue;
                    int fromMinX = x - box.minX(), fromMaxX = box.maxX() - x, fromMinZ = z - box.minZ(), fromMaxZ = box.maxZ() - z;
                    int m = Math.min(Math.min(fromMinX, fromMaxX), Math.min(fromMinZ, fromMaxZ));
                    int ex = m == fromMinX ? box.minX() - w - 1 : m == fromMaxX ? box.maxX() + w + 1 : x;
                    int ez = m == fromMinX || m == fromMaxX ? z : m == fromMinZ ? box.minZ() - w - 1 : box.maxZ() + w + 1;
                    int far = groundTop(level, ex, ez, base + w * slope + 8);
                    if (far == Integer.MIN_VALUE || Math.abs(far - base) < 2) continue;
                    target = top + Integer.signum(far - base);
                }
                setGroundTop(level, x, z, top, target);
            }
        }
    }

    /** Шум [−1, 1] по координатам (без состояния; соседние столбцы — разные). */
    private static double noise(long seed, int x, int z) {
        long h = seed + x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h ^= h >>> 29;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    /**
     * Верх природной земли в столбце (ищется сверху от maxY): первый блок {@link #solidGround}, природный
     * ({@link #naturalGround}). Над ним вода или бревно, или уже в maxY твёрдо (гора выше зоны) — Integer.MIN_VALUE
     * (такой столбец не трогаем).
     */
    private static int groundTop(ServerLevel level, int x, int z, int maxY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, Math.min(maxY, level.getMaxBuildHeight() - 1), z);
        // местность выше зоны поиска (склон горы) — не трогаем: срез оставил бы в склоне щель с нависанием
        if (solidGround(level.getBlockState(p))) return Integer.MIN_VALUE;
        for (; p.getY() > level.getMinBuildHeight(); p.move(0, -1, 0)) {
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (!s.getFluidState().isEmpty() || s.is(BlockTags.LOGS)) return Integer.MIN_VALUE;
            if (s.is(BlockTags.LEAVES) || s.canBeReplaced() || !s.blocksMotion()) continue;
            return naturalGround(s) ? p.getY() : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /** Природная земля (её можно срезать и насыпать): трава, земля, песок, гравий, снег, природный камень, терракота. */
    static boolean naturalGround(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CALCITE) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.SOUL_SOIL)
                || s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT);
    }

    /** Поднять или срезать верх земли столбца с top до target: верхний блок — как был (трава, песок), под ним — {@link #pillarBlocks}. */
    private static void setGroundTop(ServerLevel level, int x, int z, int top, int target) {
        if (target == top) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top, z);
        BlockState surfaceBlock = level.getBlockState(p);
        BlockState[] fill = pillarBlocks(surfaceBlock);
        BlockState under = fill[0];
        if (target > top) {
            for (int y = top; y < target; y++) level.setBlock(p.setY(y), under, Block.UPDATE_CLIENTS);
            level.setBlock(p.setY(target), surfaceBlock, Block.UPDATE_CLIENTS);
            // трава и цветы, что стояли на старом верху, — на новый
            BlockState plant = level.getBlockState(p.setY(target + 1));
            if (!plant.isAir() && plant.canBeReplaced()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        } else {
            // срезать: всё выше target (земля и растения над ней), верх — прежний верхний блок
            for (int y = top + 1; y >= target + 1; y--) {
                BlockState s = level.getBlockState(p.setY(y));
                if (y == top + 1 && !(s.isAir() || s.canBeReplaced())) continue;
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            level.setBlock(p.setY(target), surfaceBlock, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Нависающий грунт: на склоне горы над постройкой остаётся скала (верх шаблона — воздух, выше — камень).
     * Где верхний слой коробки — воздух, всё твёрдое выше убирается, не выше конфига.
     */
    private static void clearAbove(ServerLevel level, BoundingBox box) {
        int depth = VivaConfig.structuresClearAbove();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                p.set(x, box.maxY(), z);
                if (!level.getBlockState(p).isAir()) continue;
                int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), box.maxY() + depth);
                for (int y = box.maxY() + 1; y <= top; y++) {
                    p.set(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static BlockState foundationBlock() {
        ResourceLocation id = ResourceLocation.tryParse(VivaConfig.structuresFoundationBlock());
        Block b = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return (b == null ? Blocks.STONE : b).defaultBlockState();
    }

    /** Машина на метке: стоит на посту (persistent). После отмены программы ОРИОНа машины ОРИОНа не ставятся. */
    private static void spawnMachine(ServerLevel level, String id, BlockPos pos) {
        MachineType mt = MachineType.byId(id);
        if (mt == null) {
            VivaCore.LOGGER.warn("Viva Core: неизвестная машина «{}» для метки охраны", id);
            return;
        }
        if (Orion.isProgramCancelled(level.getServer()) && !mt.infected) return;
        EntityType<? extends OrionMachine> et = ArmyEntities.type(mt);
        OrionMachine m = et.create(level);
        if (m == null) return;
        m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        m.setPersistenceRequired();
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        level.addFreshEntity(m);
    }
}
