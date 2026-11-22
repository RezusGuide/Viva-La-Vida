package dev.rezus.vivacore.structures;

import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.orion.block.OrionBlocks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Генератор черновиков трёх построек (тест 31, часть А). Только для разработки: команда
 * /viva постройки собрать &lt;crash|lab|nexus&gt; в dev-мире ставит блоки над игроком (на высоте 200), сохраняет
 * StructureTemplate в data/vivacore/structure/&lt;file&gt;.nbt (папка — свойство vivacore.structureOut, по умолчанию
 * ../src/main/resources/... относительно run/) и убирает блоки.
 * <p>
 * Только ванильные блоки и блоки Viva Core. Всё, что не постройка, — structure_void: при размещении там остаётся
 * местность. Метки — structure_block в режиме DATA (см. {@link Structures}). Случайности — с фиксированным сидом,
 * чтобы файл собирался одинаково.
 */
public final class StructureGenerator {
    private StructureGenerator() {}

    public static Vec3i size(SiteType t) {
        return switch (t) {
            case CRASH -> new Vec3i(30, 14, 20);
            case LAB -> new Vec3i(64, 24, 64);
            case NEXUS -> new Vec3i(40, 12, 40);
        };
    }

    public static String generate(ServerLevel level, SiteType t, BlockPos near) {
        Vec3i size = size(t);
        BlockPos origin = new BlockPos(near.getX() + 8, 200, near.getZ() + 8);
        B b = new B(level, origin, size, 0x5649564100L + t.ordinal());
        b.fill(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1, Blocks.STRUCTURE_VOID.defaultBlockState());
        switch (t) {
            case CRASH -> crash(b);
            case LAB -> lab(b);
            case NEXUS -> nexus(b);
        }
        StructureTemplate tpl = new StructureTemplate();
        tpl.fillFromWorld(level, origin, size, false, Blocks.STRUCTURE_VOID);
        tpl.setAuthor("Viva Core (черновик)");
        CompoundTag tag = tpl.save(new CompoundTag());
        Path dir = Path.of(System.getProperty("vivacore.structureOut", "../src/main/resources/data/vivacore/structure"));
        Path out = dir.resolve(t.file + ".nbt");
        try {
            Files.createDirectories(dir);
            NbtIo.writeCompressed(tag, out);
        } catch (IOException e) {
            VivaCore.LOGGER.error("Viva Core dev: не записал {}", out, e);
            return "ОШИБКА: " + e.getMessage();
        }
        b.fill(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1, Blocks.AIR.defaultBlockState());
        VivaCore.LOGGER.info("Viva Core dev: шаблон {} {}×{}×{} → {}", t.file, size.getX(), size.getY(), size.getZ(), out.toAbsolutePath());
        return out.toAbsolutePath().normalize().toString();
    }

    // ------------------------------------------------------------------ жилой отсек 30×14×20

    private static void crash(B b) {
        double cz = 9.5;
        double ry = 5.8;
        double rz = 9.3;
        for (int x = 0; x < 30; x++) {
            double cy = 6.0 - (x - 15) * 0.07; // нос (+x) зарылся в землю
            for (int y = 0; y < 14; y++) {
                for (int z = 0; z < 20; z++) {
                    double v = sq((z - cz) / rz) + sq((y - cy) / ry);
                    double vin = sq((z - cz) / (rz - 1.1)) + sq((y - cy) / (ry - 1.1));
                    if (v > 1) continue;
                    boolean cap = x == 0 || x == 29;
                    if (vin > 1 || cap) {
                        if (x == 29 && b.r.nextFloat() < 0.45 && y >= 3) b.air(x, y, z); // оторванный край
                        else b.set(x, y, z, hull(b, x >= 12 && x <= 19 ? 0.5f : 0.15f));
                    } else if (y <= 2) {
                        b.set(x, y, z, OrionBlocks.HULL_PANEL.get().defaultBlockState()); // пол
                    } else {
                        b.air(x, y, z);
                    }
                }
            }
        }
        // мелкие дыры в обшивке
        for (int x = 1; x < 29; x++) {
            for (int y = 5; y < 14; y++) {
                for (int z = 0; z < 20; z++) {
                    if (isHull(b, x, y, z) && b.r.nextFloat() < 0.05f) b.air(x, y, z);
                }
            }
        }
        // дыры в крыше: над спавном (небо видно — спавн игрока встаёт на пол) и ещё две
        roofHole(b, 15, 9.5, 2.3);
        roofHole(b, 6, 7, 1.5);
        roofHole(b, 24, 12, 1.6);
        // разлом-выход в борту (+z), на уровне земли
        for (int x = 13; x <= 18; x++) {
            for (int y = 3; y <= 6 + (x == 15 || x == 16 ? 1 : 0); y++) {
                for (int z = 12; z < 20; z++) {
                    if (isHull(b, x, y, z)) b.air(x, y, z);
                }
            }
        }
        // обломки у разлома снаружи
        for (int i = 0; i < 7; i++) {
            int x = 11 + b.r.nextInt(9);
            int z = 17 + b.r.nextInt(3);
            if (b.isVoid(x, 3, z)) b.set(x, 3, z, hull(b, 0.7f));
        }
        // криокапсулы: два ряда по 6, лицом к проходу
        int[] xs = {4, 6, 8, 10, 21, 23};
        for (int x : xs) {
            b.capsule(x, 3, 4, Direction.SOUTH);
            b.capsule(x, 3, 15, Direction.NORTH);
        }
        // кабели по полу вдоль прохода
        for (int x = 2; x <= 27; x++) {
            if (b.r.nextFloat() < 0.55f && b.isAir(x, 3, 7)) b.facing(x, 3, 7, OrionBlocks.CABLE_BUNDLE.get().defaultBlockState(), Direction.EAST);
            if (b.r.nextFloat() < 0.45f && b.isAir(x, 3, 12)) b.facing(x, 3, 12, OrionBlocks.CABLE_BUNDLE.get().defaultBlockState(), Direction.EAST);
        }
        // аварийный свет между капсулами
        for (int x : new int[] {5, 9, 22}) {
            b.facing(x, 3, 4, OrionBlocks.EMERGENCY_LIGHT.get().defaultBlockState(), Direction.SOUTH);
            b.facing(x, 3, 15, OrionBlocks.EMERGENCY_LIGHT_RED.get().defaultBlockState(), Direction.NORTH);
        }
        b.facing(14, 3, 13, OrionBlocks.EMERGENCY_LIGHT_RED.get().defaultBlockState(), Direction.NORTH);
        b.facing(17, 3, 13, OrionBlocks.EMERGENCY_LIGHT_RED.get().defaultBlockState(), Direction.NORTH);
        // разбитые консоли в носу
        for (int z = 6; z <= 13; z++) {
            if (b.isAir(26, 3, z)) {
                b.facing(26, 3, z, b.r.nextFloat() < 0.25f ? OrionBlocks.HULL_PANEL_BURNT.get().defaultBlockState()
                        : OrionBlocks.DEAD_CONSOLE.get().defaultBlockState(), Direction.WEST);
            }
        }
        // ящики припасов у кормы
        for (int z : new int[] {5, 6, 7, 12, 13, 14}) {
            if (b.isAir(2, 3, z)) b.set(2, 3, z, OrionBlocks.SUPPLY_CRATE.get().defaultBlockState());
        }
        if (b.isAir(2, 4, 6)) b.set(2, 4, 6, OrionBlocks.SUPPLY_CRATE.get().defaultBlockState());
        if (b.isAir(3, 3, 13)) b.set(3, 3, 13, OrionBlocks.SUPPLY_CRATE.get().defaultBlockState());
        b.set(18, 3, 6, OrionBlocks.SUPPLY_CRATE.get().defaultBlockState());
        b.set(18, 4, 6, OrionBlocks.SUPPLY_CRATE.get().defaultBlockState());
        b.set(19, 3, 6, OrionBlocks.SUPPLY_CRATE.get().defaultBlockState());
        // метки
        b.marker(0, 3, 0, Structures.M_GROUND);
        b.marker(15, 3, 9, Structures.M_PLAYER_SPAWN);
    }

    private static void roofHole(B b, int cx, double cz, double r) {
        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 20; z++) {
                if (sq(x - cx) + sq(z - cz) > r * r) continue;
                for (int y = 8; y < 14; y++) {
                    if (isHull(b, x, y, z)) b.air(x, y, z);
                }
            }
        }
    }

    private static boolean isHull(B b, int x, int y, int z) {
        BlockState s = b.get(x, y, z);
        return s.is(OrionBlocks.HULL_PANEL.get()) || s.is(OrionBlocks.HULL_PANEL_BURNT.get()) || s.is(OrionBlocks.HULL_PANEL_DENTED.get())
                || s.is(OrionBlocks.HULL_VENT.get());
    }

    /** Обшивка трёх видов вперемешку + решётки; burnt — доля горелых/мятых (у разлома больше). */
    private static BlockState hull(B b, float burnt) {
        float f = b.r.nextFloat();
        if (f < burnt * 0.5f) return OrionBlocks.HULL_PANEL_BURNT.get().defaultBlockState();
        if (f < burnt) return OrionBlocks.HULL_PANEL_DENTED.get().defaultBlockState();
        if (f < burnt + 0.08f) return OrionBlocks.HULL_VENT.get().defaultBlockState();
        if (f < burnt + 0.2f) return OrionBlocks.HULL_PANEL_DENTED.get().defaultBlockState();
        return OrionBlocks.HULL_PANEL.get().defaultBlockState();
    }

    // ------------------------------------------------------------------ лаборатория 64×24×64

    private static void lab(B b) {
        BlockState wall = OrionBlocks.ORION_WALL.get().defaultBlockState();
        BlockState hazard = OrionBlocks.ORION_WALL_HAZARD.get().defaultBlockState();
        BlockState floor = OrionBlocks.ORION_FLOOR.get().defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        // фундамент и пол
        b.fill(0, 0, 0, 63, 2, 63, wall);
        b.fill(0, 3, 0, 63, 3, 63, floor);
        // внешний корпус
        b.fill(0, 4, 0, 63, 23, 63, wall);
        b.fill(1, 4, 1, 62, 22, 62, Blocks.AIR.defaultBlockState());
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                boolean edge = x == 0 || x == 63 || z == 0 || z == 63;
                if (edge) {
                    b.set(x, 4, z, hazard);
                    b.set(x, 13, z, hazard);
                }
                if (b.r.nextFloat() < 0.04f) b.set(x, 23, z, OrionBlocks.HULL_PANEL_BURNT.get().defaultBlockState());
            }
        }
        // окна цеха (южная стена)
        for (int x = 3; x <= 60; x++) {
            if ((x / 3) % 2 == 0) b.fill(x, 15, 0, x, 17, 0, Blocks.GRAY_STAINED_GLASS.defaultBlockState());
        }
        // вход в цех: ворота 6×6 в рамке
        b.fill(28, 4, 0, 35, 10, 0, hazard);
        b.fill(29, 4, 0, 34, 9, 0, Blocks.AIR.defaultBlockState());

        // --- цех: z 1..18, до крыши
        int[] px = {12, 24, 39, 51};
        for (int x : px) b.fill(x, 4, 9, x + 1, 22, 10, wall);
        for (int x = 3; x <= 60; x += 6) {
            for (int z = 3; z <= 17; z += 6) b.set(x, 23, z, lamp);
        }
        // производственные линии: столы, консоли, ящики, клетки, цепи
        for (int x = 3; x <= 21; x++) {
            b.set(x, 4, 4, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            if (x % 3 == 0) b.facing(x, 5, 4, OrionBlocks.DEAD_CONSOLE.get().defaultBlockState(), Direction.SOUTH);
            if (b.r.nextFloat() < 0.6f) b.facing(x, 4, 5, OrionBlocks.CABLE_BUNDLE.get().defaultBlockState(), Direction.EAST);
        }
        for (int x = 42; x <= 60; x++) {
            b.set(x, 4, 14, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            if (x % 3 == 0) b.facing(x, 5, 14, OrionBlocks.DEAD_CONSOLE.get().defaultBlockState(), Direction.NORTH);
            if (b.r.nextFloat() < 0.6f) b.facing(x, 4, 13, OrionBlocks.CABLE_BUNDLE.get().defaultBlockState(), Direction.EAST);
        }
        for (int[] c : new int[][] {{4, 15}, {5, 15}, {4, 16}, {58, 3}, {59, 3}, {59, 4}, {20, 15}, {21, 15}, {44, 4}}) {
            b.set(c[0], 4, c[1], OrionBlocks.SUPPLY_CRATE.get().defaultBlockState());
            if (b.r.nextBoolean()) b.set(c[0], 5, c[1], OrionBlocks.SUPPLY_CRATE.get().defaultBlockState());
        }
        // клетки сборки машин
        cage(b, 15, 13);
        cage(b, 45, 4);
        // цепи с потолка
        for (int[] c : new int[][] {{18, 7}, {30, 6}, {36, 12}, {48, 9}, {8, 12}}) {
            b.fill(c[0], 17, c[1], c[0], 22, c[1], Blocks.CHAIN.defaultBlockState());
        }
        // аварийный свет на полу цеха
        for (int x = 6; x <= 58; x += 13) {
            b.facing(x, 4, 1, OrionBlocks.EMERGENCY_LIGHT.get().defaultBlockState(), Direction.SOUTH);
            b.facing(x, 4, 18, OrionBlocks.EMERGENCY_LIGHT_RED.get().defaultBlockState(), Direction.NORTH);
        }

        // --- северная часть: сплошной массив, в нём вырезаны коридоры и комнаты
        b.fill(1, 4, 19, 62, 22, 62, wall);
        for (int x = 1; x <= 62; x++) b.set(x, 13, 19, hazard); // полоса на стене цеха
        // свет цеха: ряды ламп в южной и северной стенах и на колоннах
        for (int x = 4; x <= 59; x += 5) {
            if (x < 28 || x > 35) b.set(x, 9, 0, lamp);
            b.set(x, 9, 19, lamp);
        }
        for (int x : px) {
            b.set(x, 12, 8, lamp);
            b.set(x + 1, 12, 11, lamp);
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        // проходы из цеха
        b.fill(31, 4, 19, 32, 6, 19, air);
        b.fill(8, 4, 19, 9, 6, 19, air);
        b.fill(54, 4, 19, 55, 6, 19, air);
        // кольцевой коридор вокруг комнаты ядра
        b.fill(20, 4, 20, 43, 6, 22, air);
        b.fill(20, 4, 41, 43, 6, 43, air);
        b.fill(20, 4, 20, 22, 6, 43, air);
        b.fill(41, 4, 20, 43, 6, 43, air);
        // западный и восточный коридоры, задний коридор, северный отросток
        b.fill(8, 4, 20, 9, 6, 60, air);
        b.fill(54, 4, 20, 55, 6, 60, air);
        b.fill(8, 4, 59, 55, 6, 60, air);
        b.fill(31, 4, 44, 32, 6, 58, air);
        // поперечные коридоры к кольцу
        b.fill(10, 4, 31, 19, 6, 32, air);
        b.fill(44, 4, 31, 53, 6, 32, air);
        // комнаты
        room(b, 2, 22, 6, 30, 7, 26);
        room(b, 2, 35, 6, 45, 7, 40);
        room(b, 2, 48, 6, 57, 7, 52);
        room(b, 57, 22, 61, 30, 56, 26);
        room(b, 57, 35, 61, 45, 56, 40);
        room(b, 57, 48, 61, 57, 56, 52);
        room(b, 11, 22, 18, 29, 10, 25);
        room(b, 11, 35, 18, 42, 10, 38);
        room(b, 45, 22, 52, 29, 53, 25);
        room(b, 45, 35, 52, 42, 53, 38);
        room(b, 11, 46, 29, 56, 30, 51);
        room(b, 34, 46, 52, 56, 33, 51);
        // свет в коридорах
        for (int z = 22; z <= 58; z += 6) {
            b.facing(8, 4, z, OrionBlocks.EMERGENCY_LIGHT.get().defaultBlockState(), Direction.EAST);
            b.facing(55, 4, z, OrionBlocks.EMERGENCY_LIGHT_RED.get().defaultBlockState(), Direction.WEST);
        }
        for (int x = 21; x <= 42; x += 7) {
            b.facing(x, 4, 20, OrionBlocks.EMERGENCY_LIGHT_RED.get().defaultBlockState(), Direction.SOUTH);
            b.facing(x, 4, 43, OrionBlocks.EMERGENCY_LIGHT.get().defaultBlockState(), Direction.NORTH);
            b.set(x, 7, 21, lamp);
            b.set(x, 7, 42, lamp);
        }
        for (int z = 24; z <= 40; z += 8) {
            b.set(21, 7, z, lamp);
            b.set(42, 7, z, lamp);
        }

        // --- комната ядра: x 23..40, z 23..40 (стены — полоса опасности), внутри 24..39, высота 4..11
        b.fill(23, 4, 23, 40, 12, 40, hazard);
        b.fill(24, 4, 24, 39, 11, 39, air);
        for (int x = 26; x <= 38; x += 4) {
            for (int z = 26; z <= 38; z += 4) b.set(x, 12, z, lamp);
        }
        // дверь с латунным замком в южной стене (со стороны кольца)
        b.door(31, 4, 23, Direction.SOUTH);
        // терминал кода напротив двери
        b.facing(31, 4, 37, OrionBlocks.CODE_TERMINAL.get().defaultBlockState(), Direction.NORTH);
        // кабели от терминала, консоли вдоль стен, красный свет в углах
        for (int z = 25; z <= 36; z++) {
            if (z % 2 == 0) b.facing(30, 4, z, OrionBlocks.CABLE_BUNDLE.get().defaultBlockState(), Direction.NORTH);
        }
        for (int z = 26; z <= 37; z += 2) {
            b.facing(24, 4, z, OrionBlocks.DEAD_CONSOLE.get().defaultBlockState(), Direction.EAST);
            b.facing(39, 4, z, OrionBlocks.DEAD_CONSOLE.get().defaultBlockState(), Direction.WEST);
        }
        for (int[] c : new int[][] {{24, 24}, {39, 24}, {24, 39}, {39, 39}}) {
            b.facing(c[0], 4, c[1], OrionBlocks.EMERGENCY_LIGHT_RED.get().defaultBlockState(), c[1] == 24 ? Direction.SOUTH : Direction.NORTH);
        }

        // --- метки
        b.marker(5, 4, 5, Structures.M_GROUND);
        b.marker(31, 4, 34, Structures.M_CORE);
        b.marker(35, 4, 30, Structures.M_GUARDIAN);
        b.marker(17, 4, 7, Structures.M_GUARD);
        b.marker(46, 4, 7, Structures.M_GUARD);
        b.marker(31, 4, 14, Structures.M_GUARD);
        b.marker(21, 4, 31, Structures.M_GUARD);
        b.marker(42, 4, 31, Structures.M_GUARD);
        b.marker(31, 4, 52, Structures.M_GUARD);
    }

    /** Комната x1..x2, z1..z2 (высота 4..9) с проёмом шириной 2 в стене x = doorX на z = doorZ. */
    private static void room(B b, int x1, int z1, int x2, int z2, int doorX, int doorZ) {
        b.fill(x1, 4, z1, x2, 9, z2, Blocks.AIR.defaultBlockState());
        b.fill(doorX, 4, doorZ, doorX, 6, doorZ + 1, Blocks.AIR.defaultBlockState());
        furnish(b, x1, z1, x2, z2);
    }

    private static void furnish(B b, int x1, int z1, int x2, int z2) {
        for (int x = x1 + 1; x < x2; x += 3) b.set(x, 10, (z1 + z2) / 2, Blocks.SEA_LANTERN.defaultBlockState());
        for (int x = x1; x <= x2; x++) {
            if (b.r.nextFloat() < 0.5f) b.facing(x, 4, z1, OrionBlocks.DEAD_CONSOLE.get().defaultBlockState(), Direction.SOUTH);
            if (b.r.nextFloat() < 0.3f) b.set(x, 4, z2, OrionBlocks.SUPPLY_CRATE.get().defaultBlockState());
        }
        for (int z = z1 + 1; z < z2; z++) {
            if (b.r.nextFloat() < 0.35f) b.facing((x1 + x2) / 2, 4, z, OrionBlocks.CABLE_BUNDLE.get().defaultBlockState(), Direction.NORTH);
        }
        if (b.r.nextFloat() < 0.6f) b.facing(x2, 4, z2, OrionBlocks.EMERGENCY_LIGHT.get().defaultBlockState(), Direction.NORTH);
    }

    /** Клетка сборки машины 3×3 из прутьев с консолью внутри. */
    private static void cage(B b, int x, int z) {
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                boolean edge = dx != 1 || dz != 1;
                if (edge) b.fill(x + dx, 4, z + dz, x + dx, 7, z + dz, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        b.fill(x, 8, z, x + 2, 8, z + 2, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        b.set(x + 1, 4, z + 1, OrionBlocks.DEAD_CONSOLE.get().defaultBlockState());
    }

    // ------------------------------------------------------------------ место падения капсулы 40×12×40

    private static void nexus(B b) {
        double c = 19.5;
        double rBowl = 17;
        double rRim = 19.5;
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                double r = Math.sqrt(sq(x - c) + sq(z - c));
                if (r <= rBowl) {
                    int bottom = 8 - (int) Math.round(6 * (1 - sq(r / rBowl)));
                    for (int y = 0; y < bottom; y++) b.set(x, y, z, y == bottom - 1 ? burnt(b) : deep(b));
                    for (int y = bottom; y < 12; y++) b.air(x, y, z);
                    if (b.get(x, bottom - 1, z).is(Blocks.SOUL_SOIL) && b.r.nextFloat() < 0.12f) b.set(x, bottom, z, Blocks.SOUL_FIRE.defaultBlockState());
                } else if (r <= rRim) {
                    int top = r < 18.6 ? 9 : 8;
                    for (int y = 8; y <= top; y++) b.set(x, y, z, b.r.nextFloat() < 0.6f ? burnt(b) : Blocks.GRAVEL.defaultBlockState());
                    for (int y = top + 1; y < 12; y++) b.air(x, y, z);
                }
            }
        }
        // обломки капсулы в центре: наклонный цилиндр r 2.6, длина 9, часть в грунте
        for (int x = 14; x <= 25; x++) {
            double ax = x - c;
            if (Math.abs(ax) > 4.6) continue;
            double cy = 3.6 + ax * 0.35;
            for (int y = 0; y < 12; y++) {
                for (int z = 15; z <= 24; z++) {
                    double rr = Math.sqrt(sq(y - cy) + sq(z - c));
                    if (rr <= 2.6 && rr > 1.5) {
                        if (b.r.nextFloat() < 0.22f && y > 3) b.air(x, y, z);
                        else b.set(x, y, z, b.r.nextFloat() < 0.6f ? OrionBlocks.HULL_PANEL_BURNT.get().defaultBlockState()
                                : OrionBlocks.HULL_PANEL_DENTED.get().defaultBlockState());
                    } else if (rr <= 1.5 && y >= 3) {
                        b.air(x, y, z);
                    }
                }
            }
        }
        // осколки по склонам и на валу
        for (int i = 0; i < 34; i++) {
            double a = b.r.nextDouble() * Math.PI * 2;
            double r = 5 + b.r.nextDouble() * 13.5;
            int x = (int) Math.round(c + Math.cos(a) * r);
            int z = (int) Math.round(c + Math.sin(a) * r);
            if (x < 0 || z < 0 || x >= 40 || z >= 40) continue;
            int y = topAir(b, x, z);
            if (y < 0) continue;
            b.set(x, y, z, b.r.nextBoolean() ? OrionBlocks.HULL_PANEL_BURNT.get().defaultBlockState() : OrionBlocks.HULL_PANEL_DENTED.get().defaultBlockState());
            if (b.r.nextFloat() < 0.3f && y + 1 < 12) b.set(x, y + 1, z, OrionBlocks.HULL_PANEL_BURNT.get().defaultBlockState());
        }
        b.marker(0, 8, 0, Structures.M_GROUND);
        b.marker(19, 3, 19, Structures.M_CORE);
    }

    /** Нижний свободный (воздух) блок над грунтом в столбце, -1 — нет. */
    private static int topAir(B b, int x, int z) {
        for (int y = 1; y < 12; y++) {
            if (b.isAir(x, y, z) && !b.isAir(x, y - 1, z) && !b.isVoid(x, y - 1, z)) return y;
        }
        return -1;
    }

    private static BlockState burnt(B b) {
        float f = b.r.nextFloat();
        if (f < 0.35f) return Blocks.COARSE_DIRT.defaultBlockState();
        if (f < 0.55f) return Blocks.BLACKSTONE.defaultBlockState();
        if (f < 0.70f) return Blocks.BASALT.defaultBlockState();
        if (f < 0.85f) return Blocks.SOUL_SOIL.defaultBlockState();
        if (f < 0.92f) return Blocks.MAGMA_BLOCK.defaultBlockState();
        return Blocks.TUFF.defaultBlockState();
    }

    private static BlockState deep(B b) {
        float f = b.r.nextFloat();
        if (f < 0.5f) return Blocks.STONE.defaultBlockState();
        if (f < 0.75f) return Blocks.TUFF.defaultBlockState();
        if (f < 0.9f) return Blocks.BLACKSTONE.defaultBlockState();
        return Blocks.COARSE_DIRT.defaultBlockState();
    }

    private static double sq(double v) {
        return v * v;
    }

    // ------------------------------------------------------------------ помощник: блоки в относительных координатах

    private static final class B {
        final ServerLevel level;
        final BlockPos origin;
        final Vec3i size;
        final RandomSource r;
        static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

        B(ServerLevel level, BlockPos origin, Vec3i size, long seed) {
            this.level = level;
            this.origin = origin;
            this.size = size;
            this.r = RandomSource.create(seed);
        }

        boolean in(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < size.getX() && y < size.getY() && z < size.getZ();
        }

        void set(int x, int y, int z, BlockState s) {
            if (in(x, y, z)) level.setBlock(origin.offset(x, y, z), s, FLAGS);
        }

        void air(int x, int y, int z) {
            set(x, y, z, Blocks.AIR.defaultBlockState());
        }

        BlockState get(int x, int y, int z) {
            return in(x, y, z) ? level.getBlockState(origin.offset(x, y, z)) : Blocks.STRUCTURE_VOID.defaultBlockState();
        }

        boolean isAir(int x, int y, int z) {
            return get(x, y, z).isAir();
        }

        boolean isVoid(int x, int y, int z) {
            return get(x, y, z).is(Blocks.STRUCTURE_VOID);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
                }
            }
        }

        void facing(int x, int y, int z, BlockState s, Direction d) {
            set(x, y, z, s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.setValue(HorizontalDirectionalBlock.FACING, d) : s);
        }

        void capsule(int x, int y, int z, Direction d) {
            BlockState s = OrionBlocks.CRYO_CAPSULE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d);
            set(x, y, z, s.setValue(dev.rezus.vivacore.orion.block.CryoCapsuleBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, y + 1, z, s.setValue(dev.rezus.vivacore.orion.block.CryoCapsuleBlock.HALF, DoubleBlockHalf.UPPER));
        }

        void door(int x, int y, int z, Direction d) {
            BlockState s = OrionBlocks.ORION_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, d)
                    .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
            set(x, y, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, y + 1, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }

        void marker(int x, int y, int z, String name) {
            BlockPos p = origin.offset(x, y, z);
            level.setBlock(p, Blocks.STRUCTURE_BLOCK.defaultBlockState().setValue(StructureBlock.MODE, StructureMode.DATA), FLAGS);
            if (level.getBlockEntity(p) instanceof StructureBlockEntity be) {
                be.setMode(StructureMode.DATA);
                be.setMetaData(name);
                be.setChanged();
            }
        }
    }
}
