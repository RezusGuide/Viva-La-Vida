package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.gametest.TestPlayers;
import dev.rezus.vivacore.orion.Orion;
import dev.rezus.vivacore.orion.block.OrionBlocks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Оружие и три стороны (задача 7; тесты 24, 26). Каждый тест в своей пачке: машины воюют между собой,
 * а тесты одной пачки стоят рядом. Цель-манекен — разбойник (тег #minecraft:raiders) без ИИ и неуязвимый.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmyCombatGameTests {
    private static final String AREA = "wheels";

    private ArmyCombatGameTests() {}

    private static Pillager dummy(GameTestHelper h, BlockPos rel) {
        Pillager p = h.spawn(EntityType.PILLAGER, rel);
        p.setNoAi(true);
        p.setInvulnerable(true);
        p.setPersistenceRequired();
        return p;
    }

    private static OrionMachine machine(GameTestHelper h, MachineType t, BlockPos rel) {
        return ArmyEntities.type(t).spawn(h.getLevel(), h.absolutePos(rel), MobSpawnType.SPAWN_EGG);
    }

    private static List<MachineType> armed() {
        List<MachineType> out = new ArrayList<>();
        for (MachineType t : MachineType.values()) {
            if (t.weapon() != MachineType.Weapon.NONE && !t.infected) out.add(t);
        }
        return out;
    }

    /** Каждая вооружённая машина стреляет по цели в видимости и не стреляет сквозь непробиваемую стену. */
    @GameTestGenerator
    public static Collection<TestFunction> weapons() {
        List<TestFunction> tests = new ArrayList<>();
        for (MachineType t : armed()) {
            tests.add(new TestFunction("army_fire_" + t.id, "fires_" + t.id, VivaCore.MOD_ID + ":" + AREA, 260, 0, true,
                    h -> firesAtVisible(h, t)));
            tests.add(new TestFunction("army_wall_" + t.id, "wall_" + t.id, VivaCore.MOD_ID + ":" + AREA, 120, 0, true,
                    h -> noFireThroughWall(h, t)));
        }
        return tests;
    }

    private static void firesAtVisible(GameTestHelper h, MachineType t) {
        Pillager target = dummy(h, new BlockPos(9, 1, 7));
        OrionMachine m = machine(h, t, new BlockPos(2, t.movement == MachineType.Movement.FLYING ? 3 : 1, 7));
        h.succeedWhen(() -> {
            h.assertTrue(m.getTarget() == target, t.id + ": цель — разбойник, а не " + m.getTarget()
                    + "; видит=" + m.hasLineOfSight(target) + " дист=" + String.format("%.1f", m.distanceTo(target))
                    + " noAi=" + m.isNoAi() + " pos=" + m.blockPosition() + " target=" + target.blockPosition());
            h.assertTrue(m.combat().shotsFired() > 0, t.id + ": выстрелов " + m.combat().shotsFired()
                    + " дист=" + String.format("%.2f", m.distanceTo(target)) + " pos=" + m.position() + " цель=" + target.position()
                    + " bb=" + m.getBoundingBox() + " навигация=" + m.getNavigation().isInProgress());
            m.discard();
            target.discard();
        });
    }

    private static void noFireThroughWall(GameTestHelper h, MachineType t) {
        boolean was = ArmyRules.griefing(h.getLevel());
        h.getLevel().getGameRules().getRule(ArmyRules.GRIEFING).set(true, h.getLevel().getServer());
        for (int y = 0; y < 8; y++) {
            for (int z = 0; z < 14; z++) h.setBlock(new BlockPos(6, y, z), OrionBlocks.ORION_WALL.get());
        }
        Pillager target = dummy(h, new BlockPos(9, 1, 7));
        OrionMachine m = machine(h, t, new BlockPos(2, 1, 7));
        m.setTarget(target);
        h.assertTrue(!m.combat().canShoot(target, false) && !m.combat().canShoot(target, true),
                t.id + ": сквозь стену ОРИОНа (blast_proof) выстрела нет");
        if (t.movement == MachineType.Movement.FLYING) {
            // летающие могут подняться над стеной — достаточно проверки прямой видимости
            m.discard();
            target.discard();
            h.getLevel().getGameRules().getRule(ArmyRules.GRIEFING).set(was, h.getLevel().getServer());
            h.succeed();
            return;
        }
        h.runAfterDelay(80, () -> {
            h.assertTrue(m.combat().shotsFired() == 0, t.id + ": за стеной выстрелов " + m.combat().shotsFired());
            m.discard();
            target.discard();
            h.getLevel().getGameRules().getRule(ArmyRules.GRIEFING).set(was, h.getLevel().getServer());
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- правило разрушения

    private static OrionProjectile shoot(GameTestHelper h, OrionMachine owner, OrionProjectile.Kind kind, BlockPos fromRel, Vec3 dir) {
        OrionProjectile p = OrionProjectile.create(h.getLevel(), owner, kind, 5f, 2.5f);
        Vec3 from = Vec3.atCenterOf(h.absolutePos(fromRel));
        p.setPos(from);
        p.shoot(dir.x, dir.y, dir.z, 1.5f, 0f);
        h.getLevel().addFreshEntity(p);
        return p;
    }

    /** vivaArmyGriefing вкл: ракета и снаряд танка ломают блоки; выкл: взрывные не ломают. */
    @GameTest(template = AREA, batch = "army_griefing", timeoutTicks = 120)
    public static void explosivesFollowGriefingRule(GameTestHelper h) {
        var rule = h.getLevel().getGameRules().getRule(ArmyRules.GRIEFING);
        boolean was = rule.get();
        OrionMachine owner = machine(h, MachineType.TANK, new BlockPos(1, 1, 1));
        owner.setNoAi(true);
        for (int y = 1; y <= 3; y++) {
            for (int z = 2; z <= 12; z++) h.setBlock(new BlockPos(9, y, z), Blocks.STONE);
        }
        rule.set(true, h.getLevel().getServer());
        shoot(h, owner, OrionProjectile.Kind.ROCKET, new BlockPos(3, 2, 4), new Vec3(1, 0, 0));
        shoot(h, owner, OrionProjectile.Kind.SHELL, new BlockPos(3, 2, 10), new Vec3(1, 0, 0));
        h.runAfterDelay(30, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(9, 2, 4)).isAir(), "ракета сломала блок");
            h.assertTrue(h.getBlockState(new BlockPos(9, 2, 10)).isAir(), "снаряд танка сломал блок");
            h.setBlock(new BlockPos(9, 2, 7), Blocks.STONE);
            rule.set(false, h.getLevel().getServer());
            shoot(h, owner, OrionProjectile.Kind.ROCKET, new BlockPos(3, 2, 7), new Vec3(1, 0, 0));
        });
        h.runAfterDelay(70, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(9, 2, 7)).is(Blocks.STONE), "при выключенном правиле взрыв блоки не ломает");
            rule.set(was, h.getLevel().getServer());
            owner.discard();
            h.succeed();
        });
    }

    /** Сквозной снаряд проходит 2 лёгких блока (доски) и останавливается на blast_proof (стена ОРИОНа); блоки целы. */
    @GameTest(template = AREA, batch = "army_griefing2", timeoutTicks = 100)
    public static void piercingStopsOnBlastProof(GameTestHelper h) {
        OrionMachine owner = machine(h, MachineType.TANK, new BlockPos(1, 1, 1));
        owner.setNoAi(true);
        h.setBlock(new BlockPos(4, 2, 7), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(5, 2, 7), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(8, 2, 7), OrionBlocks.ORION_WALL.get());
        Pillager behind = dummy(h, new BlockPos(10, 1, 7));
        behind.setInvulnerable(false);
        float hp = behind.getHealth();
        OrionProjectile p = shoot(h, owner, OrionProjectile.Kind.PIERCING, new BlockPos(2, 2, 7), new Vec3(1, 0, 0));
        // второй — сквозь 2 доски в разбойника без стены
        h.setBlock(new BlockPos(4, 2, 3), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(5, 2, 3), Blocks.OAK_PLANKS);
        Pillager victim = dummy(h, new BlockPos(8, 1, 3));
        victim.setInvulnerable(false);
        float hpVictim = victim.getHealth();
        shoot(h, owner, OrionProjectile.Kind.PIERCING, new BlockPos(2, 2, 3), new Vec3(1, -0.08, 0));
        h.runAfterDelay(40, () -> {
            h.assertTrue(p.isRemoved(), "сквозной снаряд остановился");
            h.assertTrue(p.piercedBlocks().size() == 2, "пробил блоков: " + p.piercedBlocks().size());
            h.assertTrue(behind.getHealth() == hp, "за стеной ОРИОНа урона нет");
            h.assertTrue(victim.getHealth() < hpVictim, "за двумя досками разбойник получил урон");
            h.assertTrue(h.getBlockState(new BlockPos(4, 2, 7)).is(Blocks.OAK_PLANKS)
                    && h.getBlockState(new BlockPos(8, 2, 7)).is(OrionBlocks.ORION_WALL.get()), "блоки целы");
            owner.discard();
            behind.discard();
            victim.discard();
            h.succeed();
        });
    }

    /** При выключенном правиле танк стреляет сквозными, а не взрывными. */
    @GameTest(template = AREA, batch = "army_griefing3", timeoutTicks = 260)
    public static void tankUsesPiercingWhenGriefingOff(GameTestHelper h) {
        var rule = h.getLevel().getGameRules().getRule(ArmyRules.GRIEFING);
        boolean was = rule.get();
        rule.set(false, h.getLevel().getServer());
        Pillager target = dummy(h, new BlockPos(10, 1, 7));
        OrionMachine tank = machine(h, MachineType.TANK, new BlockPos(2, 1, 7));
        AtomicInteger explosive = new AtomicInteger();
        AtomicInteger piercing = new AtomicInteger();
        h.onEachTick(() -> {
            for (OrionProjectile p : h.getLevel().getEntitiesOfClass(OrionProjectile.class, new AABB(tank.blockPosition()).inflate(20))) {
                if (p.kind() == OrionProjectile.Kind.PIERCING) piercing.incrementAndGet();
                if (p.kind() == OrionProjectile.Kind.SHELL || p.kind() == OrionProjectile.Kind.ROCKET) explosive.incrementAndGet();
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(piercing.get() > 0, "танк выстрелил сквозным");
            h.assertTrue(explosive.get() == 0, "взрывных нет");
            rule.set(was, h.getLevel().getServer());
            tank.discard();
            target.discard();
        });
    }

    // ---------------------------------------------------------------- движение (задача 11, пункт 6)

    /**
     * Машины не стоят: без цели — патрулируют (уходят от точки появления), с целью дальше дистанции огня — подходят.
     * Площадка GameTest обнесена барьером, поэтому цель — внутри неё: неуязвимый разбойник без ИИ в ~11 блоках, а
     * дистанция огня на время теста — 3 блока. Танк (большой хитбокс) — тоже.
     */
    @GameTest(template = AREA, batch = "army_move", timeoutTicks = 400)
    public static void machinesPatrolAndApproach(GameTestHelper h) {
        double oldInf = VivaConfig.machineFireRange(MachineType.INFANTRY_LIGHT);
        double oldTank = VivaConfig.machineFireRange(MachineType.TANK);
        setFireRange(MachineType.INFANTRY_LIGHT, 3);
        setFireRange(MachineType.TANK, 3);
        Pillager far = dummy(h, new BlockPos(11, 1, 12));
        OrionMachine hunter = machine(h, MachineType.INFANTRY_LIGHT, new BlockPos(1, 1, 12));
        OrionMachine tank = machine(h, MachineType.TANK, new BlockPos(3, 1, 4));
        double startHunter = hunter.distanceTo(far);
        double startTank = tank.distanceTo(far);
        h.runAfterDelay(380, () -> {
            double hunterDist = hunter.distanceTo(far);
            double tankDist = tank.distanceTo(far);
            String info = "пехота " + String.format("%.1f → %.1f", startHunter, hunterDist)
                    + " (цель " + (hunter.getTarget() == far) + "); танк " + String.format("%.1f → %.1f", startTank, tankDist)
                    + " (цель " + (tank.getTarget() == far) + ")";
            dev.rezus.vivacore.VivaCore.LOGGER.info("Viva Core движение: {}", info);
            for (var e : h.getLevel().getEntitiesOfClass(OrionMachine.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40))) e.discard();
            far.discard();
            setFireRange(MachineType.INFANTRY_LIGHT, oldInf);
            setFireRange(MachineType.TANK, oldTank);
            h.assertTrue(hunterDist < startHunter - 4, "пехота подошла к цели: " + info);
            h.assertTrue(tankDist < startTank - 3, "танк подъехал к цели: " + info);
            h.succeed();
        });
    }

    /** Без целей машина патрулирует — уходит от точки появления (пехота и танк). */
    @GameTest(template = AREA, batch = "army_patrol", timeoutTicks = 400)
    public static void machinesPatrol(GameTestHelper h) {
        OrionMachine inf = machine(h, MachineType.INFANTRY_LIGHT, new BlockPos(9, 1, 3));
        OrionMachine tank = machine(h, MachineType.TANK, new BlockPos(4, 1, 9));
        Vec3 a = inf.position();
        Vec3 b = tank.position();
        double[] max = {0, 0};
        h.onEachTick(() -> {
            max[0] = Math.max(max[0], inf.position().distanceTo(a));
            max[1] = Math.max(max[1], tank.position().distanceTo(b));
        });
        h.runAfterDelay(380, () -> {
            inf.discard();
            tank.discard();
            dev.rezus.vivacore.VivaCore.LOGGER.info("Viva Core движение: патруль пехоты {}, танка {}", max[0], max[1]);
            h.assertTrue(max[0] > 1.5 && max[1] > 1.5, "патрулируют: пехота " + max[0] + ", танк " + max[1]);
            h.succeed();
        });
    }

    private static void setFireRange(MachineType t, double v) {
        VivaConfig.machineFireRangeValue(t).set(v);
    }

    // ---------------------------------------------------------------- размер (задача 11)

    /**
     * Задача 11: хитбокс и масштаб — из конфига (танк 4 × 4, мех высотой 5), ствол танка поднят по масштабу; танк
     * ломает перед собой листву и траву только при включённом vivaArmyGriefing, камень — никогда.
     */
    @GameTest(template = AREA, batch = "army_size")
    public static void bigMachinesSizeAndSoftBlocks(GameTestHelper h) {
        var rule = h.getLevel().getGameRules().getRule(ArmyRules.GRIEFING);
        boolean was = rule.get();
        OrionMachine tank = machine(h, MachineType.TANK, new BlockPos(3, 1, 7));
        OrionMachine mech = machine(h, MachineType.MECH_WALKER, new BlockPos(9, 1, 3));
        tank.setNoAi(true);
        mech.setNoAi(true);
        h.assertTrue(Math.abs(tank.getBbWidth() - VivaConfig.machineHitboxWidth(MachineType.TANK)) < 1e-3
                && Math.abs(mech.getBbHeight() - VivaConfig.machineHitboxHeight(MachineType.MECH_WALKER)) < 1e-3,
                "хитбокс из конфига: танк " + tank.getBbWidth() + ", мех " + mech.getBbHeight());
        h.assertTrue(tank.getBbWidth() >= 3.5 && mech.getBbHeight() >= 4.5, "танк и мех — большие");
        double muzzleUp = tank.combat().muzzle(0).y - tank.getY();
        h.assertTrue(Math.abs(muzzleUp - 18.8 / 16.0 * tank.modelScale()) < 1e-3, "ствол танка по масштабу: " + muzzleUp);
        h.assertTrue(tank.drivesDirect() && mech.drivesDirect()
                && !machine(h, MachineType.INFANTRY_LIGHT, new BlockPos(1, 1, 1)).drivesDirect(), "напрямую — только танк и мех");
        // танк смотрит на +X, перед ним листва, трава и камень
        tank.setYRot(-90f);
        BlockPos front = BlockPos.containing(tank.getBoundingBox().maxX + 0.5, tank.getY() + 0.5, tank.getZ());
        BlockPos rel = front.subtract(h.absolutePos(BlockPos.ZERO));
        h.setBlock(rel, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        h.setBlock(rel.above(), Blocks.STONE);
        h.setBlock(rel.north(), Blocks.SHORT_GRASS);
        h.setBlock(rel.north().below(), Blocks.GRASS_BLOCK);
        rule.set(false, h.getLevel().getServer());
        tank.breakSoftBlocksAhead();
        boolean keptWhenOff = h.getBlockState(rel).is(Blocks.OAK_LEAVES);
        rule.set(true, h.getLevel().getServer());
        tank.breakSoftBlocksAhead();
        boolean leavesGone = h.getBlockState(rel).isAir();
        boolean grassGone = h.getBlockState(rel.north()).isAir();
        boolean stoneKept = h.getBlockState(rel.above()).is(Blocks.STONE);
        rule.set(was, h.getLevel().getServer());
        for (var e : h.getLevel().getEntitiesOfClass(OrionMachine.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(20))) e.discard();
        h.assertTrue(keptWhenOff, "без разрушения листва цела");
        h.assertTrue(leavesGone && grassGone, "при разрушении листва и трава сломаны");
        h.assertTrue(stoneKept, "камень цел");
        h.succeed();
    }

    // ---------------------------------------------------------------- щит, лазер

    /** Щит штурмовика: урон спереди снижен, со спины — полный. */
    @GameTest(template = AREA, batch = "army_shield")
    public static void heavyShieldReducesFrontDamage(GameTestHelper h) {
        OrionMachine heavy = machine(h, MachineType.INFANTRY_HEAVY, new BlockPos(6, 1, 7));
        heavy.setNoAi(true);
        heavy.setYRot(0);
        heavy.setYBodyRot(0); // смотрит на юг (+Z)
        ArmorStand front = h.spawn(EntityType.ARMOR_STAND, new BlockPos(6, 1, 10));
        ArmorStand back = h.spawn(EntityType.ARMOR_STAND, new BlockPos(6, 1, 4));
        h.assertTrue(heavy.isHitFromFront(h.getLevel().damageSources().mobAttack(front)), "стойка спереди — «спереди»");
        h.assertTrue(!heavy.isHitFromFront(h.getLevel().damageSources().mobAttack(back)), "стойка сзади — «сзади»");
        float before = heavy.getHealth();
        heavy.hurt(h.getLevel().damageSources().mobAttack(front), 10f);
        float frontLoss = before - heavy.getHealth();
        heavy.invulnerableTime = 0;
        heavy.hurtTime = 0;
        float mid = heavy.getHealth();
        heavy.hurt(h.getLevel().damageSources().mobAttack(back), 10f);
        float backLoss = mid - heavy.getHealth();
        h.assertTrue(frontLoss > 0 && backLoss > 0 && frontLoss < backLoss * 0.6,
                "спереди " + frontLoss + ", сзади " + backLoss + " (множитель " + VivaConfig.heavyFrontMultiplier() + ")");
        heavy.discard();
        h.succeed();
    }

    /** Лазер снайпера появляется не меньше чем за laserTicks−1 тиков до выстрела. */
    @GameTest(template = AREA, batch = "army_laser", timeoutTicks = 300)
    public static void sniperLaserBeforeShot(GameTestHelper h) {
        Pillager target = dummy(h, new BlockPos(10, 1, 7));
        OrionMachine sniper = machine(h, MachineType.SNIPER, new BlockPos(2, 1, 7));
        AtomicInteger laserOnAt = new AtomicInteger(-1);
        AtomicInteger shotAt = new AtomicInteger(-1);
        AtomicInteger tick = new AtomicInteger();
        h.onEachTick(() -> {
            int t = tick.incrementAndGet();
            if (laserOnAt.get() < 0 && sniper.laserTarget() == target.getId()) laserOnAt.set(t);
            if (shotAt.get() < 0 && sniper.combat().shotsFired() > 0) shotAt.set(t);
        });
        h.succeedWhen(() -> {
            h.assertTrue(shotAt.get() > 0, "выстрел был");
            h.assertTrue(laserOnAt.get() > 0 && shotAt.get() - laserOnAt.get() >= VivaConfig.sniperLaserTicks() - 1,
                    "лазер за " + (shotAt.get() - laserOnAt.get()) + " тиков до выстрела");
            sniper.discard();
            target.discard();
        });
    }

    // ---------------------------------------------------------------- три стороны и заражение

    /** ОРИОН бьёт разбойника и заражённую машину; заражённые друг друга не трогают. */
    @GameTest(template = AREA, batch = "army_factions", timeoutTicks = 200)
    public static void factionsChooseTargets(GameTestHelper h) {
        OrionMachine orion = machine(h, MachineType.INFANTRY_LIGHT, new BlockPos(2, 1, 3));
        OrionMachine infectedA = machine(h, MachineType.INFANTRY_LIGHT_INFECTED, new BlockPos(9, 1, 11));
        OrionMachine infectedB = machine(h, MachineType.INFANTRY_HEAVY_INFECTED, new BlockPos(10, 1, 9));
        Pillager raider = dummy(h, new BlockPos(3, 1, 8));
        h.assertTrue(Factions.isTarget(orion, raider), "ОРИОН: разбойник — цель");
        h.assertTrue(Factions.isTarget(orion, infectedA), "ОРИОН: заражённая машина — цель");
        h.assertTrue(Factions.isTarget(infectedA, orion), "заражённая: машина ОРИОНа — цель");
        h.assertTrue(Factions.isTarget(infectedA, raider), "заражённая: разбойник — цель");
        h.assertTrue(!Factions.isTarget(infectedA, infectedB) && !Factions.isTarget(infectedB, infectedA), "заражённые друг друга не трогают");
        h.assertTrue(!Factions.isTarget(orion, machine(h, MachineType.INFANTRY_HEAVY, new BlockPos(1, 1, 1))), "ОРИОН своих не трогает");
        h.succeedWhen(() -> {
            h.assertTrue(orion.getTarget() != null, "ОРИОН выбрал цель");
            h.assertTrue(infectedA.getTarget() != infectedB && infectedB.getTarget() != infectedA, "заражённые не целятся друг в друга");
            for (var e : h.getLevel().getEntitiesOfClass(OrionMachine.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(20))) e.discard();
            raider.discard();
        });
    }

    /** Машину ОРИОНа убила заражённая машина → встаёт заражённая версия (шанс 1.0 в тесте). */
    @GameTest(template = AREA, batch = "army_infection")
    public static void killedByInfectedConverts(GameTestHelper h) {
        double old = VivaConfig.armyInfectionChance();
        VivaConfig.ARMY_INFECTION_CHANCE.set(1.0);
        OrionMachine victim = machine(h, MachineType.INFANTRY_LIGHT, new BlockPos(4, 1, 7));
        OrionMachine killer = machine(h, MachineType.MECH_WALKER_INFECTED, new BlockPos(9, 1, 7));
        killer.setNoAi(true);
        victim.die(h.getLevel().damageSources().mobAttack(killer));
        OrionMachine converted = victim.lastConversion;
        h.assertTrue(converted != null && converted.machine() == MachineType.INFANTRY_LIGHT_INFECTED,
                "встала заражённая версия: " + (converted == null ? null : converted.machine()));
        // танк заражённой версии не имеет — не превращается
        OrionMachine tank = machine(h, MachineType.TANK, new BlockPos(6, 1, 2));
        tank.die(h.getLevel().damageSources().mobAttack(killer));
        h.assertTrue(tank.lastConversion == null, "у танка заражённой версии нет");
        // убийство игроком/обычной машиной — без превращения
        OrionMachine other = machine(h, MachineType.INFANTRY_LIGHT, new BlockPos(2, 1, 10));
        other.die(h.getLevel().damageSources().mobAttack(machine(h, MachineType.SNIPER, new BlockPos(1, 1, 12))));
        h.assertTrue(other.lastConversion == null, "убила машина ОРИОНа — не превращается");
        VivaConfig.ARMY_INFECTION_CHANCE.set(old);
        for (var e : h.getLevel().getEntitiesOfClass(OrionMachine.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(20))) e.discard();
        h.succeed();
    }

    // ---------------------------------------------------------------- пещерная волна

    /** Игрок под землёй: волна только из машин, что помещаются в пещеру, на его высоте ±8; тяжёлые не приходят. */
    @GameTest(template = AREA, batch = "army_cave", timeoutTicks = 60)
    public static void caveWaveHasNoHeavy(GameTestHelper h) {
        var server = h.getLevel().getServer();
        boolean was = VivaConfig.armyEnabled();
        VivaConfig.ARMY_ENABLED.set(true);
        Orion.reset(server);
        // «пещера»: каменный пол и потолок над всей площадкой
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 14; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                h.setBlock(new BlockPos(x, 6, z), Blocks.STONE);
            }
        }
        ServerPlayer p = TestPlayers.player(h, new BlockPos(6, 1, 7));
        h.assertTrue(Waves.isUnderground(p), "игрок под землёй: canSeeSky=" + h.getLevel().canSeeSky(p.blockPosition().above())
                + " pos=" + p.blockPosition() + " над ним " + h.getLevel().getBlockState(p.blockPosition().above(5)));
        int heavyWave = Waves.spawnWave(List.of(p), 4);
        h.assertTrue(heavyWave == 0, "волна 4 (танк + мех) под землю не пришла: " + heavyWave);
        int lightWave = Waves.spawnWave(List.of(p), 2);
        h.assertTrue(lightWave > 0, "волна 2 (рой + пехота) пришла: " + lightWave);
        for (var m : h.getLevel().getEntitiesOfClass(OrionMachine.class, p.getBoundingBox().inflate(24))) {
            h.assertTrue(m.machine().fitsCave(), m.machine().id + " помещается в пещеру");
            h.assertTrue(Math.abs(m.getY() - p.getY()) <= 9, m.machine().id + " на высоте игрока ±8");
            m.discard();
        }
        VivaConfig.ARMY_ENABLED.set(was);
        h.succeed();
    }
}
