package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.gametest.TestPlayers;
import dev.rezus.vivacore.orion.Orion;
import dev.rezus.vivacore.orion.OrionItems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Армия ОРИОНа (тесты 24 и 27): машины, рой, тревога, волны, лимиты, отмена программы.
 * Игрок в тестах — TestPlayers (вне мира): игрок в мире роняет тестовый сервер пакетами Create.
 * Тесты тревоги, волн и отмены — в отдельных пачках (batch): они меняют общие для сервера данные и конфиг.
 */
@GameTestHolder(VivaCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmyGameTests {
    private static final String AREA = "wheels";
    private static final int LIVE_TICKS = 200;

    private ArmyGameTests() {}

    /** Что гарантированно выпадает из каждой машины (шансы и количество — в таблицах лута). */
    private static Item guaranteedDrop(MachineType t) {
        return switch (t) {
            case SCOUT_DRONE -> OrionItems.OPTICAL_SENSOR.get();
            case INFANTRY_LIGHT, INFANTRY_HEAVY -> OrionItems.SERVO.get();
            case SNIPER -> OrionItems.COMBAT_CHIP.get();
            case SWARM_UNIT -> OrionItems.NANITE_SUSPENSION.get();
            case HELICOPTER -> OrionItems.TURBINE_MODULE.get();
            case MECH_WALKER -> OrionItems.POWER_ACTUATOR.get();
            case TANK -> OrionItems.ARMOR_PLATE.get();
            case CORE_GUARDIAN -> OrionItems.ORION_CORE.get();
            case SCOUT_DRONE_INFECTED, INFANTRY_LIGHT_INFECTED -> OrionItems.SERVO_INFECTED.get();
            case INFANTRY_HEAVY_INFECTED, MECH_WALKER_INFECTED -> OrionItems.ARMOR_PLATE_INFECTED.get();
        };
    }

    /** Каждая машина: появляется, живёт 200 тиков, не падает (летающие), при убийстве роняет деталь из таблицы. */
    @GameTestGenerator
    public static Collection<TestFunction> machinesLiveAndDrop() {
        List<TestFunction> tests = new ArrayList<>();
        for (MachineType t : MachineType.values()) {
            tests.add(new TestFunction("army_machine_" + t.id, "machine_" + t.id, VivaCore.MOD_ID + ":" + AREA,
                    LIVE_TICKS + 120, 0, true, h -> machineLives(h, t)));
        }
        return tests;
    }

    private static void machineLives(GameTestHelper h, MachineType t) {
        BlockPos rel = new BlockPos(6, t.movement == MachineType.Movement.FLYING ? 4 : 1, 7);
        OrionMachine m = ArmyEntities.type(t).spawn(h.getLevel(), h.absolutePos(rel), MobSpawnType.SPAWN_EGG);
        h.assertTrue(m != null, t.id + ": появился");
        h.assertTrue(m.getMaxHealth() == VivaConfig.machineHealth(t), t.id + ": здоровье из конфига " + m.getMaxHealth());
        double startY = m.getY();
        AtomicReference<List<Item>> dropped = new AtomicReference<>(List.of());
        h.startSequence()
                .thenExecuteFor(LIVE_TICKS, () -> {
                    h.assertTrue(m.isAlive() && !m.isRemoved(), t.id + ": жив");
                    h.assertTrue(m.getTarget() == null, t.id + ": не атакует (нет цели)");
                    if (t.movement == MachineType.Movement.FLYING) {
                        h.assertTrue(m.getY() > startY - VivaConfig.armyPatrolHeight() - 3, t.id + ": не падает, y=" + m.getY());
                    }
                })
                .thenExecute(m::kill)
                .thenExecuteAfter(t.deathTicks + 5, () -> {
                    List<Item> items = new ArrayList<>();
                    for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(m.blockPosition()).inflate(6))) {
                        items.add(e.getItem().getItem());
                        e.discard();
                    }
                    dropped.set(items);
                    h.assertTrue(m.isRemoved(), t.id + ": после анимации смерти удалён");
                    h.assertTrue(items.contains(guaranteedDrop(t)), t.id + ": дроп " + items);
                })
                .thenSucceed();
    }

    /** Рой — одна сущность на сервере (облако моделек рисует клиент). */
    @GameTest(template = AREA)
    public static void swarmIsOneEntity(GameTestHelper h) {
        OrionMachine swarm = ArmyEntities.type(MachineType.SWARM_UNIT).spawn(h.getLevel(), h.absolutePos(new BlockPos(6, 4, 7)), MobSpawnType.SPAWN_EGG);
        h.assertTrue(swarm != null, "рой появился");
        List<Entity> all = h.getLevel().getEntities((Entity) null, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(16),
                e -> !(e instanceof net.minecraft.world.entity.player.Player));
        h.assertTrue(all.size() == 1 && all.get(0) == swarm, "в области одна сущность: " + all.size());
        swarm.discard();
        h.succeed();
    }

    // ---------------------------------------------------------------- тревога

    private static boolean enableArmy() {
        boolean was = VivaConfig.armyEnabled();
        VivaConfig.ARMY_ENABLED.set(true);
        return was;
    }

    /** Тревога растёт от «дрон видит» и «убил машину», падает без игроков, сохраняется. */
    @GameTest(template = AREA, batch = "army_alarm")
    public static void alarmRisesAndDecays(GameTestHelper h) {
        var server = h.getLevel().getServer();
        boolean was = enableArmy();
        Orion.reset(server);
        BlockPos playerRel = new BlockPos(2, 1, 2);
        ServerPlayer p = TestPlayers.player(h, playerRel);
        BlockPos at = p.blockPosition();
        Alarm.set(h.getLevel(), at, 0);

        ScoutDrone drone = ArmyEntities.SCOUT_DRONE.get().spawn(h.getLevel(), h.absolutePos(new BlockPos(6, 3, 6)), MobSpawnType.SPAWN_EGG);
        h.assertTrue(drone != null && drone.checkSight(p), "дрон видит игрока");
        float afterSight = Alarm.get(h.getLevel(), at);
        h.assertTrue(Math.abs(afterSight - VivaConfig.armyAlarmPerSight()) < 1e-4, "тревога от взгляда дрона: " + afterSight);

        OrionMachine infantry = ArmyEntities.type(MachineType.INFANTRY_LIGHT).spawn(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 4)), MobSpawnType.SPAWN_EGG);
        infantry.die(h.getLevel().damageSources().playerAttack(p));
        float afterKill = Alarm.get(h.getLevel(), at);
        h.assertTrue(Math.abs(afterKill - afterSight - VivaConfig.armyAlarmPerKill()) < 1e-4, "тревога от убийства: " + afterKill);

        OrionMachine infected = ArmyEntities.type(MachineType.INFANTRY_LIGHT_INFECTED).spawn(h.getLevel(), h.absolutePos(new BlockPos(4, 1, 6)), MobSpawnType.SPAWN_EGG);
        infected.die(h.getLevel().damageSources().playerAttack(p));
        h.assertTrue(Alarm.get(h.getLevel(), at) == afterKill, "убийство заражённого тревогу не поднимает");

        // сохранение: сериализация SavedData и загрузка
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        AlarmData loaded = AlarmData.load(AlarmData.get(server).save(new CompoundTag(), reg), reg);
        h.assertTrue(loaded.get(Alarm.sector(h.getLevel(), at)) == afterKill, "тревога после сохранения/загрузки");

        // в участке есть игрок — не падает; пусто — падает на decayPerMinute за минуту
        Alarm.decay(server, java.util.Set.of(Alarm.sector(h.getLevel(), at)), 60);
        h.assertTrue(Alarm.get(h.getLevel(), at) == afterKill, "с игроком в участке тревога не падает");
        Alarm.decay(server, java.util.Set.of(), 60);
        float decayed = Alarm.get(h.getLevel(), at);
        h.assertTrue(Math.abs(decayed - Math.max(0, afterKill - VivaConfig.armyAlarmDecay())) < 1e-4, "спад без игроков: " + decayed);

        Alarm.set(h.getLevel(), at, 0);
        drone.discard();
        VivaConfig.ARMY_ENABLED.set(was);
        h.succeed();
    }

    // ---------------------------------------------------------------- волны и лимиты

    /** Волна только при пороге и после задержки; предупреждение раньше; лимиты не превышаются. */
    @GameTest(template = AREA, batch = "army_waves", timeoutTicks = 200)
    public static void wavesAndLimits(GameTestHelper h) {
        var server = h.getLevel().getServer();
        boolean was = enableArmy();
        double oldDistance = VivaConfig.armyWaveDistance();
        VivaConfig.ARMY_WAVE_DISTANCE.set(8.0); // в тестовом мире дальние чанки могут быть не загружены
        Orion.reset(server);
        ServerPlayer p = TestPlayers.player(h, new BlockPos(6, 1, 7));
        BlockPos at = p.blockPosition();
        Waves.reset();

        // ниже порога: сколько ни стой — волны нет
        Alarm.set(h.getLevel(), at, 0);
        for (int s = 0; s < VivaConfig.armyWaveDelay() * 2; s++) {
            h.assertTrue(Waves.tickSector(List.of(p), 20) == Waves.Event.NONE, "без тревоги волны нет");
        }
        // порог: предупреждение за warningSeconds, волна на delaySeconds; двое игроков в участке — одна волна
        Waves.reset();
        Alarm.set(h.getLevel(), at, VivaConfig.armyWaveThreshold());
        ServerPlayer p2 = TestPlayers.player(h, new BlockPos(7, 1, 8));
        h.assertTrue(Alarm.sector(h.getLevel(), p2.blockPosition()).equals(Alarm.sector(h.getLevel(), at)), "оба игрока в одном участке");
        int warnedAt = -1, spawnedAt = -1, waves = 0;
        for (int s = 1; s <= VivaConfig.armyWaveDelay(); s++) {
            Waves.Event e = Waves.tickSector(List.of(p, p2), 20);
            if (e == Waves.Event.WARNED) warnedAt = s;
            if (e == Waves.Event.SPAWNED || e == Waves.Event.BLOCKED) {
                spawnedAt = s;
                waves++;
            }
        }
        h.assertTrue(waves == 1, "одна волна на участок, а не на игрока: " + waves);
        h.assertTrue(warnedAt == VivaConfig.armyWaveDelay() - VivaConfig.armyWaveWarning(), "предупреждение на секунде " + warnedAt);
        h.assertTrue(spawnedAt == VivaConfig.armyWaveDelay(), "волна на секунде " + spawnedAt);
        AABB around = p.getBoundingBox().inflate(40);
        int wave1 = h.getLevel().getEntitiesOfClass(OrionMachine.class, around).size();
        int compositionSize = Waves.composition(VivaConfig.armyWaveThreshold(), h.getLevel().random).size();
        h.assertTrue(wave1 > 0 && wave1 <= compositionSize, "одна волна пришла (машин " + wave1 + ", в составе " + compositionSize + ")");

        // лимит тяжёлых: три волны уровня 4 (танк + мех) — тяжёлых не больше maxHeavyPerGroup
        for (int i = 0; i < 3; i++) Waves.spawnWave(p, 4);
        int heavy = Waves.countHeavyNear(h.getLevel(), p);
        h.assertTrue(heavy <= VivaConfig.armyMaxHeavy(), "тяжёлых " + heavy + " > " + VivaConfig.armyMaxHeavy());

        // общий лимит: ставим его на текущее число — новые не появляются
        int oldMax = VivaConfig.armyMaxMachines();
        VivaConfig.ARMY_MAX_MACHINES.set(Waves.countMachines(server));
        int before = Waves.countMachines(server);
        h.assertTrue(Waves.spawnWave(p, 2) == 0 && Waves.countMachines(server) == before, "общий лимит соблюдён");
        VivaConfig.ARMY_MAX_MACHINES.set(oldMax);

        // появились на земле, не в блоках, лицом к игроку
        for (OrionMachine m : h.getLevel().getEntitiesOfClass(OrionMachine.class, around)) {
            h.assertTrue(h.getLevel().noCollision(m), m.machine().id + " не в блоках");
            Vec3 toPlayer = p.position().subtract(m.position()).multiply(1, 0, 1).normalize(); // только по горизонтали
            Vec3 look = Vec3.directionFromRotation(0, m.getYRot());
            h.assertTrue(look.x * toPlayer.x + look.z * toPlayer.z > 0.7, m.machine().id + " лицом к игроку: yRot="
                    + m.getYRot() + " look=" + look + " toPlayer=" + toPlayer + " pos=" + m.position() + " tick=" + m.tickCount);
            m.discard();
        }
        Alarm.set(h.getLevel(), at, 0);
        Waves.reset();
        VivaConfig.ARMY_WAVE_DISTANCE.set(oldDistance);
        VivaConfig.ARMY_ENABLED.set(was);
        h.succeed();
    }

    // ---------------------------------------------------------------- отмена программы

    /**
     * После отмены программы (задача 11): волн нет, тревога не растёт, машины ОРИОНа отключаются в пределах
     * army.combat.shutdownSeconds — без дропа; заражённые остаются.
     */
    @GameTest(template = AREA, batch = "army_cancel", timeoutTicks = 200)
    public static void cancelledProgramShutsDown(GameTestHelper h) {
        var server = h.getLevel().getServer();
        boolean was = enableArmy();
        int oldWindow = VivaConfig.armyShutdownSeconds();
        VivaConfig.ARMY_SHUTDOWN_SECONDS.set(2);
        OrionMachine normal = ArmyEntities.type(MachineType.INFANTRY_LIGHT).spawn(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), MobSpawnType.SPAWN_EGG);
        OrionMachine tank = ArmyEntities.type(MachineType.TANK).spawn(h.getLevel(), h.absolutePos(new BlockPos(6, 1, 10)), MobSpawnType.SPAWN_EGG);
        OrionMachine infected = ArmyEntities.type(MachineType.INFANTRY_LIGHT_INFECTED).spawn(h.getLevel(), h.absolutePos(new BlockPos(9, 1, 3)), MobSpawnType.SPAWN_EGG);
        normal.setNoAi(true);
        tank.setNoAi(true);
        infected.setNoAi(true);
        ServerPlayer p = TestPlayers.player(h, new BlockPos(6, 1, 7));
        Orion.cancel(server, "test");
        Alarm.set(h.getLevel(), p.blockPosition(), 3);
        h.assertTrue(!Army.isActive(server), "армия неактивна после отмены");
        h.assertTrue(!Alarm.raise(h.getLevel(), p.blockPosition(), 1), "тревога не растёт");
        Waves.reset();
        for (int s = 0; s < VivaConfig.armyWaveDelay() + 5; s++) {
            h.assertTrue(Waves.tickSector(List.of(p), 20) == Waves.Event.NONE, "волн нет");
        }
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(24);
        h.runAfterDelay(110, () -> {
            boolean normalOff = normal.isShutDown() && (normal.isRemoved() || normal.isDeadOrDying());
            boolean tankOff = tank.isShutDown();
            boolean infectedAlive = infected.isAlive() && !infected.isShutDown();
            int drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).size();
            int orbs = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, area).size();
            infected.discard();
            Orion.reset(server);
            Alarm.set(h.getLevel(), p.blockPosition(), 0);
            VivaConfig.ARMY_SHUTDOWN_SECONDS.set(oldWindow);
            VivaConfig.ARMY_ENABLED.set(was);
            h.assertTrue(normalOff && tankOff, "машины ОРИОНа отключились: пехота " + normalOff + ", танк " + tankOff);
            h.assertTrue(infectedAlive, "заражённая осталась");
            h.assertTrue(drops == 0 && orbs == 0, "без дропа и опыта: предметов " + drops + ", опыта " + orbs);
            h.succeed();
        });
    }

    /** Карта ожидаемого дропа покрывает все машины (защита от забытой таблицы лута). */
    @GameTest(template = "empty")
    public static void dropTableCoversAll(GameTestHelper h) {
        Map<MachineType, Item> map = new java.util.EnumMap<>(MachineType.class);
        for (MachineType t : MachineType.values()) map.put(t, guaranteedDrop(t));
        h.assertTrue(map.size() == MachineType.values().length, "дроп для всех машин");
        h.succeed();
    }
}
