package dev.rezus.vivacore.protection;

import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.gametest.TestPlayers;
import dev.rezus.vivacore.structures.SiteType;
import dev.rezus.vivacore.structures.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 34, задача 9 — дыры в защите построек: раздатчик снаружи внутрь (жидкость, огонь), огонь и поджог в коробке,
 * жидкость, затекающая снаружи, разборка контрапции Create внутрь коробки.
 * Коробка — часть площадки (x 6…11) без отступа (protection.margin = 0 на время теста), снаружи — x 0…5.
 * Каждый тест — в своей пачке и в конце сбрасывает постройки и отступ.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectionHoleGameTests {
    private ProtectionHoleGameTests() {}

    private static final int EDGE = 6;

    private static int protect(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Structures.resetForTests(server);
        int margin = VivaConfig.protectionMargin();
        VivaConfig.PROTECTION_MARGIN.set(0);
        BoundingBox box = BoundingBox.fromCorners(h.absolutePos(new BlockPos(EDGE, 0, 0)), h.absolutePos(new BlockPos(11, 7, 13)));
        Structures.setPlacedForTests(server, SiteType.LAB, box, box.getCenter());
        // пол из камня под всей площадкой
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 14; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        return margin;
    }

    private static void unprotect(GameTestHelper h, int margin) {
        Structures.resetForTests(h.getLevel().getServer());
        VivaConfig.PROTECTION_MARGIN.set(margin);
    }

    private static void dispenser(GameTestHelper h, BlockPos rel, Direction facing, ItemStack item) {
        h.setBlock(rel, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing));
        if (h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof DispenserBlockEntity be) be.setItem(0, item);
        h.setBlock(rel.above(), Blocks.REDSTONE_BLOCK); // импульс: раздатчик срабатывает через 4 тика
    }

    /** Раздатчик снаружи, повёрнутый в коробку: вода и огниво — не срабатывают; такой же наружу — срабатывает. */
    @GameTest(template = "wheels", batch = "protect_dispenser", timeoutTicks = 100)
    public static void dispenserIntoBoxDenied(GameTestHelper h) {
        int margin = protect(h);
        BlockPos waterIn = new BlockPos(EDGE - 1, 1, 3);
        BlockPos fireIn = new BlockPos(EDGE - 1, 1, 7);
        BlockPos waterOut = new BlockPos(2, 1, 11);
        dispenser(h, waterIn, Direction.EAST, new ItemStack(Items.WATER_BUCKET));
        dispenser(h, fireIn, Direction.EAST, new ItemStack(Items.FLINT_AND_STEEL));
        dispenser(h, waterOut, Direction.WEST, new ItemStack(Items.WATER_BUCKET));
        h.runAfterDelay(20, () -> {
            boolean noWater = h.getLevel().getFluidState(h.absolutePos(waterIn.east())).isEmpty();
            boolean noFire = h.getBlockState(fireIn.east()).isAir();
            boolean controlWater = !h.getLevel().getFluidState(h.absolutePos(waterOut.west())).isEmpty();
            unprotect(h, margin);
            h.assertTrue(noWater, "раздатчик не вылил воду в коробку");
            h.assertTrue(noFire, "раздатчик не поджёг в коробке");
            h.assertTrue(controlWater, "контроль: раздатчик наружу воду вылил");
            h.succeed();
        });
    }

    /** Огонь в коробке не держится (молния, распространение, лава — любая установка огня), огниво игрока — отказ. */
    @GameTest(template = "wheels", batch = "protect_fire", timeoutTicks = 100)
    public static void fireInBoxDenied(GameTestHelper h) {
        int margin = protect(h);
        h.setBlock(new BlockPos(8, 1, 4), Blocks.FIRE);
        boolean setFireRemoved = h.getBlockState(new BlockPos(8, 1, 4)).isAir();
        h.setBlock(new BlockPos(2, 1, 4), Blocks.FIRE);
        boolean outsideStays = h.getBlockState(new BlockPos(2, 1, 4)).is(Blocks.FIRE);
        h.setBlock(new BlockPos(2, 1, 4), Blocks.AIR);
        // игрок с огнивом по полу коробки
        ServerPlayer p = TestPlayers.player(h, new BlockPos(8, 1, 7));
        p.setGameMode(GameType.SURVIVAL);
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        p.setItemInHand(InteractionHand.MAIN_HAND, flint);
        BlockPos floor = h.absolutePos(new BlockPos(8, 0, 9));
        p.gameMode.useItemOn(p, h.getLevel(), flint, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
        boolean flintDenied = h.getLevel().getBlockState(floor.above()).isAir() && flint.getDamageValue() == 0;
        // огненный заряд (как из раздатчика издалека) летит в пол коробки
        Vec3 from = Vec3.atCenterOf(h.absolutePos(new BlockPos(9, 4, 11)));
        SmallFireball ball = new SmallFireball(h.getLevel(), from.x, from.y, from.z, new Vec3(0, -1, 0));
        h.getLevel().addFreshEntity(ball);
        h.runAfterDelay(30, () -> {
            boolean noFire = true;
            for (BlockPos q : BlockPos.betweenClosed(h.absolutePos(new BlockPos(EDGE, 1, 0)), h.absolutePos(new BlockPos(11, 6, 13)))) {
                noFire &= !h.getLevel().getBlockState(q).is(Blocks.FIRE);
            }
            unprotect(h, margin);
            h.assertTrue(setFireRemoved, "огонь, поставленный в коробку, сразу убран");
            h.assertTrue(outsideStays, "контроль: вне коробки огонь стоит");
            h.assertTrue(flintDenied, "огниво игрока в коробке не поджигает и не тратится");
            h.assertTrue(noFire, "огненный заряд не оставил огня в коробке");
            h.succeed();
        });
    }

    /** Вода, вылитая у края снаружи, не затекает в коробку; наружу растекается как обычно. */
    @GameTest(template = "wheels", batch = "protect_flow", timeoutTicks = 100)
    public static void fluidFlowIntoBoxDenied(GameTestHelper h) {
        int margin = protect(h);
        h.setBlock(new BlockPos(EDGE - 1, 1, 7), Blocks.WATER);
        h.runAfterDelay(40, () -> {
            boolean inside = false;
            for (BlockPos q : BlockPos.betweenClosed(h.absolutePos(new BlockPos(EDGE, 1, 0)), h.absolutePos(new BlockPos(11, 6, 13)))) {
                inside |= !h.getLevel().getFluidState(q).isEmpty();
            }
            boolean outside = !h.getLevel().getFluidState(h.absolutePos(new BlockPos(EDGE - 3, 1, 7))).isEmpty();
            unprotect(h, margin);
            h.assertTrue(!inside, "вода не затекла в коробку");
            h.assertTrue(outside, "контроль: наружу вода растеклась");
            h.succeed();
        });
    }

    /**
     * Разборка контрапции Create: блок, который встаёт в коробку, выпадает предметом, а стоящий там блок постройки цел
     * (иначе Create ломает его destroyBlock и ставит свой). Вне коробки — ставится как обычно.
     */
    @GameTest(template = "wheels", batch = "protect_disassembly")
    public static void contraptionDisassemblyDenied(GameTestHelper h) {
        int margin = protect(h);
        BlockPos wall = new BlockPos(8, 1, 5);
        h.setBlock(wall, Blocks.STONE_BRICKS);
        BearingContraption c = new BearingContraption();
        c.getStorage().initialize(); // как после сборки (Contraption.assemble → storage.initialize)
        BlockPos local = BlockPos.ZERO;
        BlockPos localOut = new BlockPos(0, 0, 3);
        c.getBlocks().put(local, new StructureTemplate.StructureBlockInfo(local, Blocks.OAK_PLANKS.defaultBlockState(), null));
        c.getBlocks().put(localOut, new StructureTemplate.StructureBlockInfo(localOut, Blocks.OAK_PLANKS.defaultBlockState(), null));
        // сдвиг и поворот на 90°: local → место блока стены в коробке, localOut → на 3 блока к −X, за край коробки
        BlockPos offset = h.absolutePos(wall);
        StructureTransform t = new StructureTransform(offset, Direction.Axis.Y, net.minecraft.world.level.block.Rotation.CLOCKWISE_90,
                net.minecraft.world.level.block.Mirror.NONE);
        BlockPos outAbs = t.apply(localOut);
        c.addBlocksToWorld(h.getLevel(), t);
        boolean wallKept = h.getBlockState(wall).is(Blocks.STONE_BRICKS);
        boolean dropped = !h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(wall)).inflate(2),
                e -> e.getItem().is(Items.OAK_PLANKS)).isEmpty();
        boolean outsidePlaced = SiteProtection.isProtected(h.getLevel(), outAbs) || h.getLevel().getBlockState(outAbs).is(Blocks.OAK_PLANKS);
        boolean outsideReallyOutside = !SiteProtection.isProtected(h.getLevel(), outAbs);
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(wall)).inflate(4))) e.discard();
        unprotect(h, margin);
        h.assertTrue(wallKept, "блок постройки цел");
        h.assertTrue(dropped, "блок контрапции выпал предметом");
        h.assertTrue(outsideReallyOutside && outsidePlaced, "контроль: снаружи коробки блок контрапции встал (" + outAbs + ")");
        h.succeed();
    }
}
