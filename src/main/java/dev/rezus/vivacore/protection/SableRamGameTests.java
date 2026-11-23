package dev.rezus.vivacore.protection;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.compat.SableCompat;
import dev.rezus.vivacore.orion.block.OrionBlocks;
import dev.rezus.vivacore.structures.SiteType;
import dev.rezus.vivacore.structures.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 34, задача 9 — таран кораблём Sable (необязательный, только с Sable в run/mods; итог — в лог
 * «Viva Core Sable таран: …», прогон не валит). Платформа 3×3 из досок разгоняется (~10 м/с) в стенку в коробке
 * постройки: листва (тег sable:fragile), каменный кирпич, стена ОРИОНа.
 * <p>
 * По коду Sable 2.0.6 таран ломает только блоки с свойством sable:fragile (тег #sable:fragile: листва, бамбук, арбуз,
 * тыква, кактус, лёд, лилия) — FragileBlockCallback.onHit → ServerLevel.destroyBlock, без событий; блоки-взрывчатка
 * взрываются (ExplosiveBlockCallback → взрыв проходит через ExplosionEvent.Detonate — его фильтрует защита).
 * В шаблонах построек хрупких блоков нет.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SableRamGameTests {
    private SableRamGameTests() {}

    @GameTest(template = "wheels", batch = "protect_sable_ram", timeoutTicks = 200, required = false)
    public static void sableRamIntoSite(GameTestHelper h) {
        if (!ModList.get().isLoaded("sable")) {
            h.succeed();
            return;
        }
        MinecraftServer server = h.getLevel().getServer();
        Structures.resetForTests(server);
        int margin = VivaConfig.protectionMargin();
        VivaConfig.PROTECTION_MARGIN.set(0);
        BoundingBox box = BoundingBox.fromCorners(h.absolutePos(new BlockPos(7, 0, 0)), h.absolutePos(new BlockPos(11, 7, 13)));
        Structures.setPlacedForTests(server, SiteType.LAB, box, box.getCenter());
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 14; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.OBSIDIAN);
        }
        Block[] wall = {Blocks.OAK_LEAVES, Blocks.STONE_BRICKS, OrionBlocks.ORION_WALL.get(), Blocks.OAK_LEAVES, Blocks.STONE_BRICKS};
        for (int i = 0; i < wall.length; i++) {
            for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(8, y, 5 + i), wall[i]);
        }
        Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 7)));
        var src = server.createCommandSourceStack().withPosition(at).withLevel(h.getLevel()).withPermission(4);
        server.getCommands().performPrefixedCommand(src, "sable spawn platform 1 minecraft:oak_planks");
        Vec3[] speed = new Vec3[1];
        BlockPos[] plot = new BlockPos[1];
        Vec3[] shipAt = new Vec3[2];
        h.runAfterDelay(5, () -> {
            var hit = h.getLevel().clip(new net.minecraft.world.level.ClipContext(at.add(0.2, 5, 0.2), at.add(0.2, -5, 0.2),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (h.getLevel().getBlockState(hit.getBlockPos()).is(Blocks.OAK_PLANKS)) plot[0] = hit.getBlockPos().immutable();
        });
        h.runAfterDelay(25, () -> {
            if (plot[0] != null) shipAt[0] = SableCompat.toWorld(h.getLevel(), Vec3.atCenterOf(plot[0]));
            server.getCommands().performPrefixedCommand(src, "sable physics impulse @l linear 45 0 0");
        });
        h.runAfterDelay(28, () -> {
            if (plot[0] != null) speed[0] = SableCompat.velocity(h.getLevel(), Vec3.atCenterOf(plot[0]));
        });
        h.runAfterDelay(80, () -> {
            int leaves = 0;
            int bricks = 0;
            int orion = 0;
            for (int i = 0; i < wall.length; i++) {
                for (int y = 1; y <= 3; y++) {
                    var s = h.getBlockState(new BlockPos(8, y, 5 + i));
                    if (wall[i] == Blocks.OAK_LEAVES && !s.is(Blocks.OAK_LEAVES)) leaves++;
                    if (wall[i] == Blocks.STONE_BRICKS && !s.is(Blocks.STONE_BRICKS)) bricks++;
                    if (wall[i] == OrionBlocks.ORION_WALL.get() && !s.is(OrionBlocks.ORION_WALL.get())) orion++;
                }
            }
            if (plot[0] != null) shipAt[1] = SableCompat.toWorld(h.getLevel(), Vec3.atCenterOf(plot[0]));
            VivaCore.LOGGER.info("Viva Core Sable таран: корабль найден {}, скорость после толчка {} блоков/с, был {} → стал {} (стенка у x={});"
                    + " сломано в коробке: листва {} из 6, кирпич {} из 6, стена ОРИОНа {} из 3", plot[0] != null, speed[0], shipAt[0], shipAt[1],
                    h.absolutePos(new BlockPos(8, 1, 5)).getX(), leaves, bricks, orion);
            server.getCommands().performPrefixedCommand(src, "sable remove @l");
            Structures.resetForTests(server);
            VivaConfig.PROTECTION_MARGIN.set(margin);
            h.assertTrue(bricks == 0 && orion == 0, "кирпич и стена ОРИОНа в коробке целы");
            h.succeed();
        });
    }
}
