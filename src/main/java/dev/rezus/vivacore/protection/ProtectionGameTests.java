package dev.rezus.vivacore.protection;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.gametest.TestPlayers;
import dev.rezus.vivacore.orion.Orion;
import dev.rezus.vivacore.orion.OrionItems;
import dev.rezus.vivacore.orion.block.OrionBlocks;
import dev.rezus.vivacore.structures.SiteType;
import dev.rezus.vivacore.structures.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 34: защита построек. В коробке (тестовая площадка, отмеченная как стоящая лаборатория): ломание, установка,
 * взрыв, жидкость, контрапция Create (сдвиг, дрель, BlockHelper), эндер-жемчуг и хорус — отказ; дверь с ключом и
 * терминал — работают. Каждый тест — в своей пачке (коробка с отступом не должна задевать чужие площадки) и в конце
 * сбрасывает постройки.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private ProtectionGameTests() {}

    /** Площадка теста — коробка лаборатории. */
    private static BoundingBox protect(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Structures.resetForTests(server);
        BlockPos a = h.absolutePos(new BlockPos(0, 0, 0));
        BlockPos b = h.absolutePos(new BlockPos(11, 7, 13));
        BoundingBox box = BoundingBox.fromCorners(a, b);
        Structures.setPlacedForTests(server, SiteType.LAB, box, box.getCenter());
        return box;
    }

    private static BlockHitResult hitTop(BlockPos abs) {
        return new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    }

    @GameTest(template = "wheels", batch = "protect_break")
    public static void breakAndPlaceDenied(GameTestHelper h) {
        protect(h);
        try {
            BlockPos rel = new BlockPos(3, 1, 3);
            h.setBlock(rel, Blocks.STONE);
            BlockPos abs = h.absolutePos(rel);
            ServerPlayer p = TestPlayers.player(h, new BlockPos(3, 1, 5));
            p.setGameMode(GameType.SURVIVAL);
            h.assertTrue(!p.gameMode.destroyBlock(abs) && h.getLevel().getBlockState(abs).is(Blocks.STONE), "ломать нельзя");
            // ящик припасов — можно (тег vivacore:protection_exempt)
            BlockPos crate = h.absolutePos(new BlockPos(5, 1, 3));
            h.getLevel().setBlock(crate, OrionBlocks.SUPPLY_CRATE.get().defaultBlockState(), 3);
            h.assertTrue(p.gameMode.destroyBlock(crate) && h.getLevel().getBlockState(crate).isAir(), "ящик припасов ломается");
            // установка
            ItemStack stone = new ItemStack(Items.STONE);
            p.setItemInHand(InteractionHand.MAIN_HAND, stone);
            stone.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hitTop(abs)));
            h.assertTrue(h.getLevel().getBlockState(abs.above()).isAir(), "ставить нельзя");
            // творческий режим без прав оператора — тоже нельзя
            p.setGameMode(GameType.CREATIVE);
            h.assertTrue(!p.gameMode.destroyBlock(abs) && h.getLevel().getBlockState(abs).is(Blocks.STONE), "творческий без оп — нельзя");
            // за коробкой (дальше отступа) — можно
            int far = VivaConfig.protectionMargin() + 3;
            BlockPos outside = h.absolutePos(new BlockPos(3, 1, 13 + far));
            h.getLevel().setBlock(outside, Blocks.STONE.defaultBlockState(), 3);
            p.setGameMode(GameType.SURVIVAL);
            h.assertTrue(!SiteProtection.isProtected(h.getLevel(), outside), "за отступом — не защищено");
            h.getLevel().setBlock(outside, Blocks.AIR.defaultBlockState(), 3);
        } finally {
            Structures.resetForTests(h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest(template = "wheels", batch = "protect_explosion")
    public static void explosionAndBucketDenied(GameTestHelper h) {
        protect(h);
        try {
            for (int x = 4; x <= 7; x++) {
                for (int z = 5; z <= 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
            Vec3 c = Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 2, 6)));
            h.getLevel().explode(null, c.x, c.y, c.z, 4f, Level.ExplosionInteraction.TNT);
            boolean all = true;
            for (int x = 4; x <= 7; x++) {
                for (int z = 5; z <= 8; z++) all &= h.getLevel().getBlockState(h.absolutePos(new BlockPos(x, 1, z))).is(Blocks.STONE);
            }
            h.assertTrue(all, "взрыв не ломает блоки в коробке");
            // ведро воды: игрок над камнем смотрит вниз
            ServerPlayer p = TestPlayers.player(h, new BlockPos(5, 2, 6));
            p.setGameMode(GameType.SURVIVAL);
            p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 6))));
            p.setXRot(90f);
            ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
            p.setItemInHand(InteractionHand.MAIN_HAND, bucket);
            BlockHitResult target = SiteProtection.bucketTarget(p);
            h.assertTrue(target.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK, "ведро смотрит в блок");
            p.gameMode.useItem(p, h.getLevel(), bucket, InteractionHand.MAIN_HAND);
            BlockPos water = target.getBlockPos().relative(target.getDirection());
            h.assertTrue(h.getLevel().getFluidState(water).isEmpty() && p.getMainHandItem().is(Items.WATER_BUCKET), "воду не вылить");
        } finally {
            Structures.resetForTests(h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest(template = "wheels", batch = "protect_create")
    public static void createContraptionsDenied(GameTestHelper h) {
        protect(h);
        try {
            BlockPos abs = h.absolutePos(new BlockPos(4, 1, 4));
            h.getLevel().setBlock(abs, Blocks.STONE.defaultBlockState(), 3);
            BlockState s = h.getLevel().getBlockState(abs);
            h.assertTrue(!BlockMovementChecks.isMovementAllowed(s, h.getLevel(), abs), "контрапция не сдвигает блок постройки");
            BlockPos far = abs.offset(0, 0, 200);
            h.assertTrue(BlockMovementChecks.isMovementAllowed(s, h.getLevel(), far), "вне постройки — сдвигается");
            h.assertTrue(!new BlockBreakingMovementBehaviour().canBreak(h.getLevel(), abs, s), "дрель/пила на контрапции: блок неразрушим");
            h.assertTrue(new BlockBreakingMovementBehaviour().canBreak(h.getLevel(), far, s), "вне постройки — разрушим");
            BlockHelper.destroyBlock(h.getLevel(), abs, 1f);
            h.assertTrue(h.getLevel().getBlockState(abs).is(Blocks.STONE), "BlockHelper.destroyBlock (дрель, пила) не ломает");
        } finally {
            Structures.resetForTests(h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest(template = "wheels", batch = "protect_teleport")
    public static void pearlAndChorusDenied(GameTestHelper h) {
        BoundingBox box = protect(h);
        try {
            ServerPlayer p = TestPlayers.player(h, new BlockPos(1, 1, 1));
            ThrownEnderpearl pearl = new ThrownEnderpearl(h.getLevel(), p);
            Vec3 inside = Vec3.atCenterOf(box.getCenter());
            BlockHitResult hit = new BlockHitResult(inside, Direction.UP, BlockPos.containing(inside), false);
            h.assertTrue(EventHooks.onEnderPearlLand(p, inside.x, inside.y, inside.z, pearl, 5f, hit).isCanceled(), "жемчуг внутрь — отказ");
            h.assertTrue(EventHooks.onChorusFruitTeleport(p, inside.x, inside.y, inside.z).isCanceled(), "хорус внутрь — отказ");
            Vec3 outside = inside.add(0, 0, 300);
            h.assertTrue(!EventHooks.onEnderPearlLand(p, outside.x, outside.y, outside.z, pearl, 5f, hit).isCanceled(), "снаружи — можно");
            h.assertTrue(!EventHooks.onChorusFruitTeleport(p, outside.x, outside.y, outside.z).isCanceled(), "хорус снаружи — можно");
            pearl.discard();
        } finally {
            Structures.resetForTests(h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest(template = "wheels", batch = "protect_use")
    public static void doorAndTerminalWork(GameTestHelper h) {
        protect(h);
        MinecraftServer server = h.getLevel().getServer();
        Orion.reset(server);
        try {
            BlockPos rel = new BlockPos(3, 1, 3);
            BlockState lower = OrionBlocks.ORION_DOOR.get().defaultBlockState();
            h.setBlock(rel, lower);
            h.setBlock(rel.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            BlockPos abs = h.absolutePos(rel);
            ServerPlayer p = TestPlayers.player(h, rel.south());
            p.setGameMode(GameType.SURVIVAL);
            ItemStack key = new ItemStack(OrionItems.BRASS_KEY.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, key);
            p.gameMode.useItemOn(p, h.getLevel(), key, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
            h.assertTrue(h.getLevel().getBlockState(abs).getValue(DoorBlock.OPEN), "дверь с ключом открывается в постройке");
            // терминал
            BlockPos trel = new BlockPos(7, 1, 3);
            h.setBlock(trel, OrionBlocks.CODE_TERMINAL.get());
            ServerPlayer near = TestPlayers.player(h, trel.south());
            h.assertTrue(Orion.submit(near, h.absolutePos(trel), VivaConfig.orionAccessCode(), 1_000_000L) == Orion.Status.ACCEPTED,
                    "терминал принимает код в постройке");
            // кнопка
            BlockPos brel = new BlockPos(9, 1, 3);
            h.setBlock(brel, Blocks.STONE_BUTTON);
            BlockPos babs = h.absolutePos(brel);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setPos(Vec3.atBottomCenterOf(babs.south()));
            p.gameMode.useItemOn(p, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(babs), Direction.UP, babs, false));
            h.assertTrue(h.getLevel().getBlockState(babs).getValue(net.minecraft.world.level.block.ButtonBlock.POWERED), "кнопка нажимается");
        } finally {
            Orion.reset(server);
            Structures.resetForTests(server);
        }
        h.succeed();
    }
}
