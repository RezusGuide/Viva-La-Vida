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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
        SitePlanner.Plan plan = SitePlanner.plan(level.getSeed(), spawn, params(), SitePlanner.biomeLand(level));
        if (d.entry(SiteType.CRASH).planned() == null) d.setPlanned(SiteType.CRASH, new BlockPos(spawn.getX(), 0, spawn.getZ()));
        d.setPlan(plan.angle(), plan.lab(), plan.nexus());
        VivaCore.LOGGER.info("Viva Core: постройки — угол {}°, лаборатория {} (суша {}), Нексус {} (суша {})",
                String.format("%.1f", Math.toDegrees(plan.angle())), plan.lab().toShortString(), plan.labOnLand(),
                plan.nexus().toShortString(), plan.nexusOnLand());
    }

    public static SitePlanner.Params params() {
        return new SitePlanner.Params(VivaConfig.structuresDistance(), VivaConfig.structuresAngleShift(), VivaConfig.structuresAngleStep(),
                VivaConfig.structuresDistanceShift(), VivaConfig.structuresDistanceStep(), VivaConfig.structuresLandRadius());
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

        template.placeInWorld(level, origin, origin, settings, level.random, Block.UPDATE_CLIENTS);
        removeLeftoverMarkers(level, box);
        foundation(level, box);
        clearAbove(level, box);

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

    /** Фундамент: под нижним слоем постройки пустоты (обрыв, пещера, вода) засыпаются до грунта, не глубже конфига. */
    private static void foundation(ServerLevel level, BoundingBox box) {
        BlockState fill = foundationBlock();
        int depth = VivaConfig.structuresFoundationDepth();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                p.set(x, box.minY(), z);
                BlockState bottom = level.getBlockState(p);
                if (bottom.isAir() || !bottom.getFluidState().isEmpty()) continue;
                for (int y = box.minY() - 1; y >= box.minY() - depth && y > level.getMinBuildHeight(); y--) {
                    p.set(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
                    level.setBlock(p, fill, Block.UPDATE_CLIENTS);
                }
            }
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
