package dev.rezus.vivacore.orion;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.gametest.TestPlayers;
import dev.rezus.vivacore.orion.block.CodeTerminalBlock;
import dev.rezus.vivacore.orion.block.CryoCapsuleBlock;
import dev.rezus.vivacore.orion.block.OrionBlocks;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Блоки ОРИОНа, дверь с латунным замком, терминал кода, записка (тест 34 — часть).
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrionGameTests {
    private static final String AREA = "wheels";

    private OrionGameTests() {}

    private static BlockHitResult hit(BlockPos abs) {
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
    }

    /** Все блоки зарегистрированы, ломаются нужным инструментом и выпадают (двухблочные — один раз, из нижней половины). */
    @GameTest(template = AREA)
    public static void orionBlocksBreakAndDrop(GameTestHelper h) {
        for (Supplier<? extends Block> s : OrionBlocks.all()) {
            Block block = s.get();
            BlockState state = block.defaultBlockState();
            String id = block.builtInRegistryHolder().key().location().toString();
            boolean wood = block == OrionBlocks.SUPPLY_CRATE.get();
            if (wood) {
                h.assertTrue(state.is(BlockTags.MINEABLE_WITH_AXE), id + ": тег топора");
                h.assertTrue(!state.requiresCorrectToolForDrops(), id + ": выпадает и без инструмента");
            } else {
                h.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), id + ": тег кирки");
                h.assertTrue(state.requiresCorrectToolForDrops(), id + ": нужен инструмент");
                h.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state), id + ": каменная кирка подходит");
                h.assertTrue(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), id + ": деревянная — нет (как железо)");
                h.assertTrue(!ItemStack.EMPTY.isCorrectToolForDrops(state), id + ": рукой не выпадает");
            }
            BlockPos abs = h.absolutePos(new BlockPos(1, 1, 1));
            ItemStack tool = new ItemStack(wood ? Items.IRON_AXE : Items.IRON_PICKAXE);
            boolean twoHigh = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF);
            List<ItemStack> drops = Block.getDrops(state, h.getLevel(), abs, null, null, tool);
            h.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), id + ": выпадает сам: " + drops);
            if (twoHigh) {
                BlockState upper = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
                h.assertTrue(Block.getDrops(upper, h.getLevel(), abs, null, null, tool).isEmpty(), id + ": верхняя половина не выпадает");
            }
        }
        h.succeed();
    }

    /** Дверь: без ключа не открывается, с ключом открывается, закрывается без ключа, редстоун не открывает. */
    @GameTest(template = AREA)
    public static void orionDoorNeedsKey(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 1, 3);
        BlockState lower = OrionBlocks.ORION_DOOR.get().defaultBlockState();
        h.setBlock(rel, lower);
        h.setBlock(rel.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockPos abs = h.absolutePos(rel);
        ServerPlayer p = TestPlayers.player(h, rel.south());

        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), p, hit(abs));
        h.assertTrue(!h.getLevel().getBlockState(abs).getValue(DoorBlock.OPEN), "без ключа — закрыта");

        ItemStack key = new ItemStack(OrionItems.BRASS_KEY.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, key);
        h.getLevel().getBlockState(abs).useItemOn(key, h.getLevel(), p, InteractionHand.MAIN_HAND, hit(abs));
        h.assertTrue(h.getLevel().getBlockState(abs).getValue(DoorBlock.OPEN), "с ключом — открыта");
        h.assertTrue(p.getMainHandItem().is(OrionItems.BRASS_KEY.get()) && p.getMainHandItem().getCount() == 1, "ключ не тратится");
        // сдвижная створка: открытая дверь проходима целиком, полоска для клика — у кромки, куда уехала створка
        var ctx = net.minecraft.world.phys.shapes.CollisionContext.empty();
        h.assertTrue(h.getLevel().getBlockState(abs).getCollisionShape(h.getLevel(), abs, ctx).isEmpty()
                && h.getLevel().getBlockState(abs.above()).getCollisionShape(h.getLevel(), abs.above(), ctx).isEmpty(),
                "открытая дверь проходима");
        boolean down = VivaConfig.orionDoorSlidesDown();
        BlockPos strip = down ? abs : abs.above();
        h.assertTrue(!h.getLevel().getBlockState(strip).getShape(h.getLevel(), strip, ctx).isEmpty(), "полоска створки осталась для клика");

        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), p, hit(abs));
        h.assertTrue(!h.getLevel().getBlockState(abs).getValue(DoorBlock.OPEN), "закрыть можно и без ключа");

        h.setBlock(rel.east(), Blocks.REDSTONE_BLOCK);
        h.setBlock(rel.above().east(), Blocks.REDSTONE_BLOCK);
        h.assertTrue(!h.getLevel().getBlockState(abs).getValue(DoorBlock.OPEN), "редстоун не открывает");
        h.assertTrue(OrionBlocks.ORION_DOOR.get().type() == dev.rezus.vivacore.orion.block.OrionDoorBlock.ORION
                && !OrionBlocks.ORION_DOOR.get().type().canOpenByHand(), "тип двери не открывается рукой (мобы не открывают)");
        h.succeed();
    }

    /** Терминал: неверный код, пауза, верный код, повтор, далеко. */
    @GameTest(template = AREA)
    public static void orionTerminalCode(GameTestHelper h) {
        var server = h.getLevel().getServer();
        Orion.reset(server);
        BlockPos rel = new BlockPos(5, 1, 5);
        h.setBlock(rel, OrionBlocks.CODE_TERMINAL.get());
        BlockPos abs = h.absolutePos(rel);
        ServerPlayer near = TestPlayers.player(h, rel.south());
        long t = 1000;
        long cooldown = Math.round(VivaConfig.orionAttemptCooldownSeconds() * 20);
        String code = VivaConfig.orionAccessCode();

        h.assertTrue(Orion.submit(near, abs, "wrong", t) == Orion.Status.WRONG, "неверный код");
        h.assertTrue(!Orion.isProgramCancelled(server), "после неверного кода флага нет");
        h.assertTrue(Orion.submit(near, abs, code, t + 1) == Orion.Status.COOLDOWN, "сразу снова — пауза");
        h.assertTrue(Orion.submit(near, abs, code.toUpperCase(), t + cooldown) == Orion.Status.WRONG || code.equals(code.toUpperCase()),
                "регистр важен");
        h.assertTrue(Orion.submit(near, abs, " " + code, t + 2 * cooldown) == Orion.Status.WRONG, "пробелы важны");
        h.assertTrue(Orion.submit(near, abs, code, t + 3 * cooldown) == Orion.Status.ACCEPTED, "верный код принят");
        h.assertTrue(Orion.isProgramCancelled(server), "флаг мира поставлен");
        h.assertTrue(h.getLevel().getBlockState(abs).getValue(CodeTerminalBlock.UNLOCKED), "терминал unlocked=true");
        h.assertTrue(Orion.submit(near, abs, code, t + 10 * cooldown) == Orion.Status.ALREADY_CANCELLED, "повторно — уже отменено");

        Orion.reset(server);
        ServerPlayer far = TestPlayers.player(h, rel.south(40));
        h.assertTrue(Orion.submit(far, abs, code, t) == Orion.Status.TOO_FAR, "далеко — отказ");
        h.assertTrue(Orion.submit(near, abs.above(), code, t + 20 * cooldown) == Orion.Status.TOO_FAR, "не терминал — отказ");
        h.assertTrue(!Orion.isProgramCancelled(server), "после отказов флага нет");
        Orion.reset(server);
        h.succeed();
    }

    /** Записка с кодом выдаётся один раз. */
    @GameTest(template = "empty")
    public static void orionNoteOnce(GameTestHelper h) {
        ServerPlayer p = TestPlayers.player(h);
        boolean enabled = VivaConfig.orionGiveNote();
        h.assertTrue(Orion.giveNoteIfNeeded(p) == enabled, "первый вход — записка (если включено)");
        h.assertTrue(!Orion.giveNoteIfNeeded(p), "второй раз — нет");
        int notes = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(OrionItems.CODE_NOTE.get())) notes += s.getCount();
        h.assertTrue(notes == (enabled ? 1 : 0), "записок в инвентаре: " + notes);
        h.succeed();
    }

    /** Капсула занимает 2 блока; сломал любую половину — исчезает целиком, выпадает один предмет. */
    @GameTest(template = AREA)
    public static void orionCapsuleTwoBlocks(GameTestHelper h) {
        CryoCapsuleBlock block = OrionBlocks.CRYO_CAPSULE.get();
        for (boolean breakUpper : new boolean[] {true, false}) {
            BlockPos rel = breakUpper ? new BlockPos(2, 1, 2) : new BlockPos(7, 1, 2);
            BlockPos abs = h.absolutePos(rel);
            BlockState lower = block.defaultBlockState();
            h.setBlock(rel, lower);
            block.setPlacedBy(h.getLevel(), abs, lower, null, ItemStack.EMPTY);
            BlockState upper = h.getLevel().getBlockState(abs.above());
            h.assertTrue(upper.is(block) && upper.getValue(CryoCapsuleBlock.HALF) == DoubleBlockHalf.UPPER, "верхняя половина на месте");
            h.assertTrue(!upper.getShape(h.getLevel(), abs.above()).isEmpty(), "у верхней половины есть форма");

            h.getLevel().destroyBlock(breakUpper ? abs.above() : abs, true);
            h.assertTrue(h.getLevel().getBlockState(abs).isAir() && h.getLevel().getBlockState(abs.above()).isAir(),
                    "сломал " + (breakUpper ? "верх" : "низ") + " — исчезла целиком");
            int items = 0;
            for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2))) {
                if (e.getItem().is(block.asItem())) items += e.getItem().getCount();
                e.discard();
            }
            h.assertTrue(items == 1, "выпал ровно один предмет капсулы: " + items);
        }
        h.succeed();
    }
}
