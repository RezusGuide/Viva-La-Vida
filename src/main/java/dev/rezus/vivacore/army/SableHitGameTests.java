package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.compat.SableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тест 25: попадания по кораблям Sable. Необязательный (required = false): без Sable просто проходит,
 * с Sable в run/mods пишет в лог, что работает, а что нет («Viva Core Sable: …»).
 * <p>
 * Корабль — тестовая платформа Sable из досок (/sable spawn platform 5 — это 11×11), толкнутая импульсом. Он падает,
 * ложится на землю (немного наклонно) и едет — поэтому прицел строится от самого корабля: в начале теста лучом сверху
 * находится доска палубы (её позиция в участке корабля), а в момент выстрелов точки палубы переводятся в мир
 * ({@link SableCompat#toWorld}). Задача 9: пули — мгновенный выстрел; точка попадания в блок корабля — в мире
 * (искры и трассер видны там, где корабль), взрыв на палубе задевает сущности рядом.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SableHitGameTests {
    private SableHitGameTests() {}

    private static Pillager dummy(GameTestHelper h, BlockPos rel) {
        Pillager p = h.spawn(EntityType.PILLAGER, rel);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1000, 255, false, false));
        p.setPersistenceRequired();
        return p;
    }

    /** Центр доски палубы (dx, dz от найденной) в мировых координатах. */
    private static Vec3 deck(ServerLevel level, BlockPos plot, int dx, int dz) {
        return SableCompat.toWorld(level, Vec3.atCenterOf(plot.offset(dx, 0, dz)));
    }

    @GameTest(template = "wheels", batch = "army_sable", timeoutTicks = 240, required = false)
    public static void projectilesVsSableShip(GameTestHelper h) {
        if (!ModList.get().isLoaded("sable")) {
            h.succeed();
            return;
        }
        var server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        boolean wasGriefing = ArmyRules.griefing(level);
        level.getGameRules().getRule(ArmyRules.GRIEFING).set(false, server); // ракета не ломает палубу — только урон
        // пол площадки: без него корабль Sable в плоском мире GameTest иногда проваливается сквозь землю
        // (2 из 4 прогонов: палуба на y −68, ниже дна мира)
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 14; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.OBSIDIAN); // не pierceable
        }
        Vec3 spawnAt = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6, 3, 7)));
        var src = server.createCommandSourceStack().withPosition(spawnAt).withLevel(level).withPermission(4);
        server.getCommands().performPrefixedCommand(src, "sable spawn platform 5 minecraft:oak_planks");
        Pillager onDeck = dummy(h, new BlockPos(6, 5, 7));
        Pillager forRocket = dummy(h, new BlockPos(4, 5, 9));
        OrionMachine owner = ArmyEntities.type(MachineType.TANK).spawn(level, h.absolutePos(new BlockPos(0, 1, 0)), MobSpawnType.SPAWN_EGG);
        owner.setNoAi(true);
        BlockPos[] plot = new BlockPos[1];
        h.runAfterDelay(5, () -> {
            BlockHitResult hit = level.clip(new ClipContext(spawnAt.add(0.3, 6, 0.3), spawnAt.add(0.3, -6, 0.3),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (hit.getType() != HitResult.Type.MISS && level.getBlockState(hit.getBlockPos()).is(Blocks.OAK_PLANKS)) {
                plot[0] = hit.getBlockPos().immutable();
            }
            VivaCore.LOGGER.info("Viva Core Sable: доска палубы в участке корабля: {} (мировая точка удара {})", plot[0],
                    hit.getLocation());
        });
        float[] hp = new float[2];
        // корабль падает на пол и встаёт к ~40-му тику; на 50-м — толчок, выстрелы — на 53-м, пока он едет
        h.runAfterDelay(50, () -> {
            server.getCommands().performPrefixedCommand(src, "sable physics impulse @l linear 60 0 0");
            hp[0] = onDeck.getHealth();
        });
        Hitscan.Result[] bullets = new Hitscan.Result[2];
        OrionProjectile[] shots = new OrionProjectile[2];
        Vec3[] aim = new Vec3[3];
        Vec3[] start = new Vec3[1];
        boolean[] pillagerOnDeck = new boolean[1];
        Vec3[] velocity = new Vec3[1];
        Hitscan.Result[] pierceShot = new Hitscan.Result[1];
        h.runAfterDelay(53, () -> {
            if (plot[0] == null) return;
            velocity[0] = SableCompat.velocity(level, Vec3.atCenterOf(plot[0]));
            // оба разбойника — на палубу движущегося корабля (после толчка они могли съехать);
            // все выстрелы — сверху вниз: корабль лежит на полу наклонно и чуть «утопает» в нём (физика Sable),
            // горизонтальные лучи вдоль слоя палубы задевают пол
            Vec3 stand = deck(level, plot[0], 0, 0).add(0, 0.55, 0);
            onDeck.teleportTo(stand.x, stand.y, stand.z);
            Vec3 standRocket = deck(level, plot[0], 0, 3).add(0, 0.55, 0);
            forRocket.teleportTo(standRocket.x, standRocket.y, standRocket.z);
            hp[1] = forRocket.getHealth();
            pillagerOnDeck[0] = true;
            bullets[0] = Hitscan.fire(level, owner, onDeck.position().add(0, 5, 0), new Vec3(0, -1, 0), 20, 4f, null, ArmyNetwork.KIND_BULLET);
            // мгновенная пуля сверху вниз в доску палубы (2, −2): должна остановиться на её верху, точка — в мире
            aim[0] = deck(level, plot[0], -2, -2);
            bullets[1] = Hitscan.fire(level, owner, aim[0].add(0, 5, 0), new Vec3(0, -1, 0), 20, 4f, null, ArmyNetwork.KIND_BULLET);
            // сквозной мгновенный выстрел сверху в доску (2, −2): пробивает её (доска корабля, позиция в участке) и встаёт
            // на обсидиане пола мира под ней — продолжение луча после блока корабля идёт от мировой точки (та же
            // трассировка ShotTrace, что у сквозного снаряда-сущности)
            aim[1] = deck(level, plot[0], 2, -2);
            pierceShot[0] = Hitscan.fire(level, owner, aim[1].add(0, 4, 0), new Vec3(0, -1, 0), 10, 4f, new ShotTrace.Pierce(2),
                    ArmyNetwork.KIND_BULLET);
            // и сквозной снаряд-сущность туда же — только в лог: за 3–4 тика полёта корабль сдвигается/проседает
            Vec3 a = deck(level, plot[0], 2, 2).add(0, 4, 0);
            start[0] = a;
            shots[0] = fire(h, owner, OrionProjectile.Kind.PIERCING, a, a.add(0, -7, 0));
            // ракета сверху в доску (1, 2), второй разбойник стоит на палубе рядом
            aim[2] = deck(level, plot[0], 1, 3);
            shots[1] = fire(h, owner, OrionProjectile.Kind.ROCKET, aim[2].add(0, 4, 0), aim[2]);
        });
        h.runAfterDelay(110, () -> {
            boolean api = SableCompat.available();
            boolean found = plot[0] != null;
            boolean hitOnDeck = found && bullets[0].entity() == onDeck;
            Vec3 e = found ? bullets[1].end() : Vec3.ZERO;
            boolean worldPoint = found && e.subtract(aim[0]).horizontalDistance() < 0.7;
            boolean stoppedAtDeck = found && bullets[1].blocked() && Math.abs(e.y - (aim[0].y + 0.5)) < 0.7;
            // палуба выше пола? (иначе корабль «утонул» в полу — тогда выстрелы сверху честно бьют в пол)
            double floorTop = h.absolutePos(new BlockPos(0, 1, 0)).getY();
            // корабль лежит на полу: верх досок выше верха пола, но не выше чем на 3 блока (не провалился и не улетел)
            boolean deckAboveFloor = found && aim[0].y + 0.5 > floorTop + 0.05 && aim[1].y + 0.5 > floorTop + 0.05
                    && aim[0].y < floorTop + 3 && aim[1].y < floorTop + 3;
            boolean shipPlanks = found && pierceShot[0].pierced().size() == 1;
            if (found) {
                for (BlockPos pos : pierceShot[0].pierced()) shipPlanks &= level.getBlockState(pos).is(Blocks.OAK_PLANKS);
            }
            boolean pierceStop = found && pierceShot[0].blocked() && pierceShot[0].end().subtract(aim[1]).horizontalDistance() < 0.7
                    && pierceShot[0].end().y > floorTop - 0.6 && pierceShot[0].end().y < floorTop + 0.05; // в обсидиане пола или на нём
            boolean rocketOnShip = found && shots[1].lastBlockHit != null && level.getBlockState(shots[1].lastBlockHit).is(Blocks.OAK_PLANKS);
            boolean rocketHurt = found && (forRocket.getHealth() < hp[1] || forRocket.isDeadOrDying());
            VivaCore.LOGGER.info("Viva Core Sable: API перевода координат найдено: {}; палуба найдена: {}; скорость корабля в момент выстрелов {} блоков/с; палуба выше пола: {}",
                    api, found, velocity[0], deckAboveFloor);
            if (found) {
                VivaCore.LOGGER.info("Viva Core Sable: мгновенная пуля по разбойнику (на палубе: {}): {} (здоровье {} → {})",
                        pillagerOnDeck[0], hitOnDeck ? "попала" : "НЕ попала", hp[0], onDeck.getHealth());
                VivaCore.LOGGER.info("Viva Core Sable:   выстрел по разбойнику: {}; разбойник в {}", bullets[0], onDeck.position());
                VivaCore.LOGGER.info("Viva Core Sable: мгновенная пуля сверху в доску палубы: конец {}, доска в мире {} — {}, упёрлась в верх: {}",
                        e, aim[0], worldPoint ? "в мировых координатах" : "НЕ там (участок корабля?)", stoppedAtDeck);
                VivaCore.LOGGER.info("Viva Core Sable: сквозной мгновенный сверху: пробил {} (одна доска корабля: {}), конец {} (доска в мире {}, пол мира y={}) — {}",
                        pierceShot[0].pierced().size(), shipPlanks, pierceShot[0].end(), aim[1], floorTop, pierceStop ? "встал на полу" : "НЕТ");
                VivaCore.LOGGER.info("Viva Core Sable: сквозной снаряд-сущность сверху: пробил {}, упёрся в {} ({}), конец {}, старт {}",
                        shots[0].piercedBlocks().size(), shots[0].lastBlockHit,
                        shots[0].lastBlockHit == null ? "-" : level.getBlockState(shots[0].lastBlockHit), shots[0].position(), start[0]);
                VivaCore.LOGGER.info("Viva Core Sable: ракета в палубу: упала на доску корабля {}, разбойник на палубе рядом {} (здоровье {} → {})",
                        rocketOnShip, rocketHurt ? "ранен" : "НЕ ранен", hp[1], forRocket.getHealth());
            }
            onDeck.discard();
            forRocket.discard();
            owner.discard();
            server.getCommands().performPrefixedCommand(src, "sable remove @l");
            level.getGameRules().getRule(ArmyRules.GRIEFING).set(wasGriefing, server);
            h.assertTrue(api, "API Sable найдено");
            h.assertTrue(found, "доска палубы найдена лучом");
            if (deckAboveFloor) {
                h.assertTrue(hitOnDeck, "пуля попала в разбойника");
                h.assertTrue(worldPoint && stoppedAtDeck, "пуля остановилась на доске корабля, точка — в мире");
                h.assertTrue(shipPlanks && pierceStop, "сквозной пробил доску корабля и встал на полу мира");
                h.assertTrue(rocketOnShip && rocketHurt, "ракета взорвалась на палубе и ранила разбойника рядом");
            } else {
                VivaCore.LOGGER.warn("Viva Core Sable: корабль не лежит на полу (утонул в нём или провалился сквозь мир — физика Sable"
                        + " в плоском мире GameTest), проверки палубы пропущены");
            }
            h.succeed();
        });
    }

    private static OrionProjectile fire(GameTestHelper h, OrionMachine owner, OrionProjectile.Kind kind, Vec3 from, Vec3 to) {
        OrionProjectile p = OrionProjectile.create(h.getLevel(), owner, kind, 4f, 2f);
        p.setPos(from);
        Vec3 d = to.subtract(from);
        p.shoot(d.x, d.y, d.z, 1.2f, 0f);
        h.getLevel().addFreshEntity(p);
        return p;
    }
}
