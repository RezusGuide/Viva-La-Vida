package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaCore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 25: попадания по кораблям Sable. Необязательный (required = false): без Sable просто проходит,
 * с Sable в run/mods пишет в лог, что работает, а что нет («Viva Core Sable: …»), и не валит прогон.
 * Корабль — тестовая платформа Sable из досок (/sable spawn platform), толкнутая импульсом; на палубе — разбойник.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SableHitGameTests {
    private SableHitGameTests() {}

    @GameTest(template = "wheels", batch = "army_sable", timeoutTicks = 200, required = false)
    public static void projectilesVsSableShip(GameTestHelper h) {
        if (!ModList.get().isLoaded("sable")) {
            h.succeed();
            return;
        }
        var server = h.getLevel().getServer();
        Vec3 deckPos = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 3, 7)));
        var src = server.createCommandSourceStack().withPosition(deckPos).withLevel(h.getLevel()).withPermission(4);
        server.getCommands().performPrefixedCommand(src, "sable spawn platform 5 minecraft:oak_planks");
        server.getCommands().performPrefixedCommand(src, "sable info @l");
        Pillager onDeck = h.spawn(EntityType.PILLAGER, new BlockPos(6, 5, 7));
        onDeck.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1000, 255, false, false));
        onDeck.setPersistenceRequired();
        OrionMachine owner = ArmyEntities.type(MachineType.TANK).spawn(h.getLevel(), h.absolutePos(new BlockPos(0, 1, 0)), MobSpawnType.SPAWN_EGG);
        owner.setNoAi(true);
        float[] hp = new float[1];
        OrionProjectile[] shots = new OrionProjectile[3];
        h.runAfterDelay(20, () -> {
            server.getCommands().performPrefixedCommand(src, "sable info @l");
            server.getCommands().performPrefixedCommand(src, "sable physics impulse @l linear 2 0 0");
            hp[0] = onDeck.getHealth();
            VivaCore.LOGGER.info("Viva Core Sable: разбойник на палубе y={}, палуба ожидалась у y={}", onDeck.getY(), deckPos.y);
        });
        double[] deckY = new double[1];
        double[] deckXAtShot = new double[1];
        h.runAfterDelay(30, () -> {
            Vec3 at = onDeck.position().add(0, 1, 0);
            deckY[0] = onDeck.getY() - 0.5; // середина слоя палубы под ногами разбойника
            deckXAtShot[0] = onDeck.getX();
            shots[0] = fire(h, owner, OrionProjectile.Kind.BULLET, at.add(-6, 0.5, 0), at);
            // горизонтально в слой палубы, мимо разбойника (z + 1): пуля должна остановиться у края палубы
            shots[1] = fire(h, owner, OrionProjectile.Kind.BULLET, new Vec3(at.x - 4, deckY[0], at.z + 1), new Vec3(at.x + 7, deckY[0], at.z + 1));
            // сквозной горизонтально в палубу из досок: должен пробить 2 доски и встать на третьей
            shots[2] = fire(h, owner, OrionProjectile.Kind.PIERCING, new Vec3(at.x - 4, deckY[0], at.z - 1), new Vec3(at.x + 7, deckY[0], at.z - 1));
        });
        h.runAfterDelay(70, () -> {
            boolean hitOnDeck = onDeck.getHealth() < hp[0];
            double stopX = shots[1].position().x - deckXAtShot[0]; // относительно палубы в момент выстрела
            // Sable возвращает точку попадания в блок корабля в координатах его участка (plot), а не мира:
            // далёкая точка остановки = пуля упёрлась в блок корабля. Иначе — у ближнего края палубы (полуширина 2.5).
            boolean plotSpace = Math.abs(stopX) > 1000;
            boolean stoppedAtDeck = plotSpace || (stopX > -3.5 && stopX < 0.5);
            VivaCore.LOGGER.info("Viva Core Sable: точка удара пули {}: {}", plotSpace ? "в координатах участка корабля" : "в мире",
                    shots[1].position());
            VivaCore.LOGGER.info("Viva Core Sable: пуля по разбойнику на движущемся корабле: {} (здоровье {} → {})",
                    hitOnDeck ? "попала" : "НЕ попала", hp[0], onDeck.getHealth());
            VivaCore.LOGGER.info("Viva Core Sable: пуля в борт палубы: остановилась в {} блоках от центра палубы — {}",
                    String.format("%.2f", stopX), stoppedAtDeck ? "остановилась о блоки корабля" : "ПРОЛЕТЕЛА сквозь блоки корабля");
            VivaCore.LOGGER.info("Viva Core Sable: сквозной в палубу: пробил блоков {} (ожидалось 2), удалён {}",
                    shots[2].piercedBlocks().size(), shots[2].isRemoved());
            for (BlockPos pos : shots[2].piercedBlocks()) {
                VivaCore.LOGGER.info("Viva Core Sable:   пробит {} в {} (площадка у {})", h.getLevel().getBlockState(pos).getBlock(),
                        pos, onDeck.blockPosition());
            }
            onDeck.discard();
            owner.discard();
            server.getCommands().performPrefixedCommand(src, "sable remove @l");
            h.assertTrue(hitOnDeck, "пуля попала в разбойника на корабле");
            h.assertTrue(stoppedAtDeck, "пуля остановилась о блоки корабля");
            h.assertTrue(shots[2].piercedBlocks().size() == 2, "сквозной пробил 2 блока корабля");
            h.succeed();
        });
    }

    private static OrionProjectile fire(GameTestHelper h, OrionMachine owner, OrionProjectile.Kind kind, Vec3 from, Vec3 to) {
        OrionProjectile p = OrionProjectile.create(h.getLevel(), owner, kind, 4f, 0f);
        p.setPos(from);
        Vec3 d = to.subtract(from);
        p.shoot(d.x, d.y, d.z, 1.2f, 0f);
        h.getLevel().addFreshEntity(p);
        return p;
    }
}
