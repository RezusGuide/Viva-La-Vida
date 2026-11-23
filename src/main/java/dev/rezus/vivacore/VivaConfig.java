package dev.rezus.vivacore;

import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Серверный конфиг Viva Core. Все числа будущих функций добавляются сюда, а не в код.
 * Каждый раздел соответствует модулю (пакету) мода.
 */
public final class VivaConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- home: испытание «Приятный дом» (заметка «03 Этап 1 — Земля») ---
    public static final ModConfigSpec.IntValue HOME_MAX_MEMBERS;
    public static final ModConfigSpec.IntValue HOME_COMMAND_PERMISSION;

    static {
        BUILDER.comment("Испытание «Приятный дом» (Этап 1, стадия 2)").push("home");
        HOME_MAX_MEMBERS = BUILDER
                .comment("Сколько игроков может жить в одном доме")
                .defineInRange("maxMembers", 2, 1, 16);
        HOME_COMMAND_PERMISSION = BUILDER
                .comment("Уровень прав для /viva home (2 = оператор)")
                .defineInRange("commandPermissionLevel", 2, 0, 4);
        BUILDER.pop();
    }

    // --- wheels: лимит водяных колёс Create (заметка «13 Мир, спавнер, колёса», тест 17) ---
    public static final ModConfigSpec.BooleanValue WHEELS_ENABLED;
    public static final ModConfigSpec.IntValue WHEELS_MAX_PER_NETWORK;
    public static final ModConfigSpec.IntValue WHEELS_CHECK_INTERVAL;

    static {
        BUILDER.comment("Лимит водяных колёс Create в одной кинетической сети").push("wheels");
        WHEELS_ENABLED = BUILDER
                .comment("Включить лимит")
                .define("enabled", true);
        WHEELS_MAX_PER_NETWORK = BUILDER
                .comment("Сколько водяных колёс (малых и больших, каждое как одно) работает в одной сети")
                .defineInRange("maxPerNetwork", 3, 0, 1024);
        WHEELS_CHECK_INTERVAL = BUILDER
                .comment("Как часто (в тиках) каждое колесо перепроверяет лимит")
                .defineInRange("checkIntervalTicks", 10, 1, 1200);
        BUILDER.pop();
    }

    // --- zhiva: Жива и твист с имплантом (заметка «02 Сюжет и Жива», тест 36) ---
    public static final ModConfigSpec.IntValue ZHIVA_PHRASE_COOLDOWN;
    public static final ModConfigSpec.IntValue ZHIVA_LOW_HEALTH_PERCENT;
    public static final ModConfigSpec.IntValue ZHIVA_HEALTH_CHECK_TICKS;
    public static final ModConfigSpec.DoubleValue ZHIVA_DEVICE_REACH;

    static {
        BUILDER.comment("Жива: голос и устройство имплантирования").push("zhiva");
        ZHIVA_PHRASE_COOLDOWN = BUILDER
                .comment("Одна и та же фраза Живы не чаще, чем раз в столько секунд")
                .defineInRange("phraseCooldownSeconds", 60, 0, 86400);
        ZHIVA_LOW_HEALTH_PERCENT = BUILDER
                .comment("Здоровье ниже стольких процентов → фраза про опасность")
                .defineInRange("lowHealthPercent", 30, 0, 100);
        ZHIVA_HEALTH_CHECK_TICKS = BUILDER
                .comment("Как часто (в тиках) проверять здоровье игрока")
                .defineInRange("healthCheckTicks", 20, 1, 1200);
        ZHIVA_DEVICE_REACH = BUILDER
                .comment("Запас дальности (в блоках) сверх обычной дальности руки для действий в устройстве")
                .defineInRange("deviceReachBuffer", 1.0, 0.0, 16.0);
        BUILDER.pop();
    }

    // --- army: армия ОРИОНа (заметка «16 Враги-армия», тесты 24 и 27) ---
    public static final ModConfigSpec.BooleanValue ARMY_ENABLED;
    public static final ModConfigSpec.IntValue ARMY_PATROL_RADIUS;
    public static final ModConfigSpec.IntValue ARMY_PATROL_HEIGHT;
    public static final ModConfigSpec.IntValue ARMY_SECTOR_SIZE;
    public static final ModConfigSpec.DoubleValue ARMY_SIGHT_RANGE;
    public static final ModConfigSpec.DoubleValue ARMY_ALARM_PER_SIGHT;
    public static final ModConfigSpec.DoubleValue ARMY_ALARM_PER_KILL;
    public static final ModConfigSpec.DoubleValue ARMY_ALARM_DECAY;
    public static final ModConfigSpec.IntValue ARMY_WAVE_THRESHOLD;
    public static final ModConfigSpec.IntValue ARMY_WAVE_DELAY;
    public static final ModConfigSpec.IntValue ARMY_WAVE_WARNING;
    public static final ModConfigSpec.DoubleValue ARMY_WAVE_DISTANCE;
    public static final ModConfigSpec.IntValue ARMY_MAX_MACHINES;
    public static final ModConfigSpec.IntValue ARMY_MAX_HEAVY;
    public static final ModConfigSpec.DoubleValue ARMY_HEAVY_GROUP_RADIUS;
    public static final ModConfigSpec.IntValue ARMY_HEAVY_DESPAWN;
    @SuppressWarnings("unchecked")
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>>[] ARMY_WAVES = new ModConfigSpec.ConfigValue[5];

    /** Числа одной машины: army.<id>.* (оружие — задача 7: урон, перезарядка, очередь, разброс, скорость снаряда). */
    public record Machine(ModConfigSpec.DoubleValue health, ModConfigSpec.DoubleValue speed,
                          ModConfigSpec.DoubleValue armor, ModConfigSpec.DoubleValue fireRange,
                          ModConfigSpec.DoubleValue detectRange,
                          ModConfigSpec.DoubleValue damage, ModConfigSpec.IntValue cooldown,
                          ModConfigSpec.IntValue burst, ModConfigSpec.IntValue burstGap,
                          ModConfigSpec.DoubleValue spread, ModConfigSpec.DoubleValue projectileSpeed) {}

    // особые параметры оружия
    public static final ModConfigSpec.DoubleValue HELI_ROCKET_DAMAGE;
    public static final ModConfigSpec.IntValue HELI_ROCKET_COOLDOWN;
    public static final ModConfigSpec.DoubleValue HELI_ROCKET_POWER;
    public static final ModConfigSpec.DoubleValue HELI_ROCKET_SPEED;
    public static final ModConfigSpec.DoubleValue TANK_SHELL_POWER;
    public static final ModConfigSpec.DoubleValue HEAVY_FRONT_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HEAVY_SHIELD_ANGLE;
    public static final ModConfigSpec.IntValue SNIPER_LASER_TICKS;
    public static final ModConfigSpec.DoubleValue SWARM_AURA_RADIUS;
    // разрушение, цели, заражение
    public static final ModConfigSpec.IntValue ARMY_PIERCE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ARMY_TARGETS_ORION;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ARMY_TARGETS_INFECTED;
    public static final ModConfigSpec.DoubleValue ARMY_INFECTION_CHANCE;
    public static final ModConfigSpec.BooleanValue ARMY_HITSCAN_BULLETS;
    public static final ModConfigSpec.DoubleValue ARMY_HITSCAN_RANGE_FACTOR;
    public static final ModConfigSpec.DoubleValue ARMY_TRACER_SEND_RANGE;
    public static final ModConfigSpec.IntValue ARMY_MAX_PROJECTILES;
    public static final ModConfigSpec.IntValue ARMY_MAX_EXPLOSIONS;

    private static final java.util.Map<dev.rezus.vivacore.army.MachineType, Machine> MACHINES =
            new java.util.EnumMap<>(dev.rezus.vivacore.army.MachineType.class);
    private static final ModConfigSpec.DoubleValue[] heliRocket = new ModConfigSpec.DoubleValue[3];
    private static final ModConfigSpec.IntValue[] heliRocketCd = new ModConfigSpec.IntValue[1];
    private static final ModConfigSpec.DoubleValue[] tankPower = new ModConfigSpec.DoubleValue[1];
    private static final ModConfigSpec.DoubleValue[] heavyShield = new ModConfigSpec.DoubleValue[2];
    private static final ModConfigSpec.IntValue[] sniperLaser = new ModConfigSpec.IntValue[1];
    private static final ModConfigSpec.DoubleValue[] swarmRadius = new ModConfigSpec.DoubleValue[1];

    /** Значения оружия по умолчанию: урон, перезарядка, очередь, пауза, разброс, скорость. Баланс — в конфиге. */
    private static double weaponDefault(dev.rezus.vivacore.army.MachineType t, int i) {
        double[] v = switch (t.weapon()) {
            case RIFLE -> new double[] {3, 40, 3, 4, 3.0, 3.0};
            case CANNON -> new double[] {7, 50, 1, 1, 2.0, 2.5};
            case SNIPER -> new double[] {14, 100, 1, 1, 0.3, 5.0};
            case HELICOPTER -> new double[] {3, 30, 5, 2, 4.0, 3.5};
            case MECH -> new double[] {6, 40, 2, 5, 2.0, 3.0};
            case TANK -> new double[] {16, 100, 1, 1, 1.0, 2.2};
            case SWARM -> new double[] {2, 10, 1, 1, 0.0, 1.0};
            case NONE -> new double[] {0, 100, 1, 1, 0.0, 1.0};
        };
        return v[i];
    }

    static {
        BUILDER.comment("Армия ОРИОНа").push("army");
        ARMY_ENABLED = BUILDER
                .comment("Армия включена: тревога растёт и приходят волны (выключено до стадий; /viva армия вкл|выкл)")
                .define("enabled", false);
        ARMY_PATROL_RADIUS = BUILDER
                .comment("Радиус патруля вокруг точки появления, блоки")
                .defineInRange("patrolRadius", 8, 1, 64);
        ARMY_PATROL_HEIGHT = BUILDER
                .comment("Летающие машины держатся на столько блоков над землёй (± половина)")
                .defineInRange("patrolHeight", 4, 0, 32);

        BUILDER.comment("Тревога участков").push("alarm");
        ARMY_SECTOR_SIZE = BUILDER
                .comment("Размер участка тревоги, блоки (квадрат)")
                .defineInRange("sectorSize", 256, 16, 4096);
        ARMY_SIGHT_RANGE = BUILDER
                .comment("Дальность, на которой дрон-разведчик видит игрока (прямая видимость), блоки")
                .defineInRange("droneSightRange", 24.0, 1.0, 128.0);
        ARMY_ALARM_PER_SIGHT = BUILDER
                .comment("Сколько тревоги добавляет дрон за каждую секунду, пока видит игрока")
                .defineInRange("perDroneSightSecond", 0.05, 0.0, 4.0);
        ARMY_ALARM_PER_KILL = BUILDER
                .comment("Сколько тревоги добавляет убийство машины ОРИОНа игроком")
                .defineInRange("perKill", 0.5, 0.0, 4.0);
        ARMY_ALARM_DECAY = BUILDER
                .comment("На сколько тревога падает за минуту, пока в участке нет игроков")
                .defineInRange("decayPerMinute", 0.5, 0.0, 4.0);
        BUILDER.pop();

        BUILDER.comment("Волны подкрепления").push("waves");
        ARMY_WAVE_THRESHOLD = BUILDER
                .comment("С какого уровня тревоги (1–4) приходят волны")
                .defineInRange("threshold", 1, 1, 4);
        ARMY_WAVE_DELAY = BUILDER
                .comment("Сколько секунд игрок должен пробыть в участке, чтобы пришла волна")
                .defineInRange("delaySeconds", 60, 5, 3600);
        ARMY_WAVE_WARNING = BUILDER
                .comment("За сколько секунд до волны — звук и сообщение")
                .defineInRange("warningSeconds", 10, 0, 600);
        ARMY_WAVE_DISTANCE = BUILDER
                .comment("На каком расстоянии от игрока появляется волна (край видимости), блоки")
                .defineInRange("spawnDistance", 28.0, 8.0, 96.0);
        String[][] waves = {null,
                {"scout_drone*2"},
                {"swarm_unit", "infantry_light*2"},
                {"helicopter|mech_walker", "infantry_light"},
                {"tank", "mech_walker"}};
        for (int level = 1; level <= 4; level++) {
            ARMY_WAVES[level] = BUILDER
                    .comment("Состав волны при тревоге " + level + ": id машины, «*N» — сколько, «a|b» — одна из")
                    .defineListAllowEmpty("level" + level, java.util.List.of(waves[level]), () -> "scout_drone", o -> o instanceof String);
        }
        BUILDER.pop();

        BUILDER.comment("Лимиты").push("limits");
        ARMY_MAX_MACHINES = BUILDER
                .comment("Всего машин ОРИОНа на сервере, не больше")
                .defineInRange("maxMachines", 40, 0, 1000);
        ARMY_MAX_HEAVY = BUILDER
                .comment("Тяжёлых машин (мех, танк, вертолёт) на группу игроков, не больше")
                .defineInRange("maxHeavyPerGroup", 2, 0, 50);
        ARMY_HEAVY_GROUP_RADIUS = BUILDER
                .comment("Радиус «группы игроков» для лимита тяжёлых, блоки")
                .defineInRange("heavyGroupRadius", 128.0, 16.0, 1024.0);
        ARMY_HEAVY_DESPAWN = BUILDER
                .comment("Тяжёлые без игроков в радиусе 64 блоков исчезают через столько секунд")
                .defineInRange("heavyDespawnSeconds", 120, 10, 3600);
        BUILDER.pop();

        BUILDER.comment("Правило vivaArmyGriefing (игровое правило, по умолчанию true): при выключенном вместо взрывных — сквозные снаряды")
                .push("griefing");
        ARMY_PIERCE_BLOCKS = BUILDER
                .comment("Сколько лёгких блоков (тег vivacore:pierceable) пробивает сквозной снаряд")
                .defineInRange("pierceBlocks", 2, 0, 16);
        BUILDER.pop();

        BUILDER.comment("Кто с кем воюет: «@player» — игроки (не творческий, не наблюдатель), «@orion» — машины ОРИОНа,",
                "«@infected» — заражённые машины, «#тег» — тег сущностей, «мод:*» — все сущности мода, «мод:id» — одна сущность").push("targets");
        ARMY_TARGETS_ORION = BUILDER
                .comment("Цели машин ОРИОНа")
                .defineListAllowEmpty("orion", java.util.List.of("@player", "#minecraft:raiders", "spore:*", "@infected"),
                        () -> "@player", o -> o instanceof String);
        ARMY_TARGETS_INFECTED = BUILDER
                .comment("Цели заражённых машин (мобов Spore не трогают)")
                .defineListAllowEmpty("infected", java.util.List.of("@player", "@orion", "#minecraft:raiders"),
                        () -> "@player", o -> o instanceof String);
        BUILDER.pop();

        BUILDER.comment("Заражение захватывает армию (тест 26)").push("infection");
        ARMY_INFECTION_CHANCE = BUILDER
                .comment("Шанс, что машина ОРИОНа, убитая мобом Spore или заражённой машиной, встанет заражённой")
                .defineInRange("convertChance", 0.5, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.comment("Экономия в бою (тест 38, задача 9): пули — мгновенный выстрел без сущности, лимиты снарядов и взрывов")
                .push("combat");
        ARMY_HITSCAN_BULLETS = BUILDER
                .comment("Пули — мгновенный выстрел (true) или, как в задаче 7, снаряды-сущности (false; для сравнения нагрузки)")
                .define("hitscanBullets", true);
        ARMY_HITSCAN_RANGE_FACTOR = BUILDER
                .comment("Дальность мгновенного выстрела = дальность огня машины (army.<id>.fireRange) × это")
                .defineInRange("hitscanRangeFactor", 1.5, 1.0, 4.0);
        ARMY_TRACER_SEND_RANGE = BUILDER
                .comment("Пакет трассера (рисунок выстрела) шлётся игрокам не дальше этого от начала или конца выстрела, блоки")
                .defineInRange("tracerSendRange", 128.0, 16.0, 512.0);
        ARMY_MAX_PROJECTILES = BUILDER
                .comment("Не больше стольких снарядов-сущностей ОРИОНа (ракеты, снаряды танка, сквозные) одновременно в участке",
                        "army.alarm.sectorSize; сверх лимита машина пропускает выстрел")
                .defineInRange("maxProjectilesPerSector", 16, 0, 1000);
        ARMY_MAX_EXPLOSIONS = BUILDER
                .comment("Не больше стольких взрывов ОРИОНа за тик на сервере; лишние — без взрыва, только урон рядом")
                .defineInRange("maxExplosionsPerTick", 4, 0, 100);
        BUILDER.pop();

        for (dev.rezus.vivacore.army.MachineType t : dev.rezus.vivacore.army.MachineType.values()) {
            BUILDER.push(t.id);
            MACHINES.put(t, new Machine(
                    BUILDER.comment("Здоровье (полусердца)").defineInRange("maxHealth", t.defaultHealth, 1.0, 4096.0),
                    BUILDER.comment("Скорость (наземные — movement_speed, летающие — flying_speed)").defineInRange("speed", t.defaultSpeed, 0.0, 4.0),
                    BUILDER.comment("Броня (атрибут armor)").defineInRange("armor", t.defaultArmor, 0.0, 30.0),
                    BUILDER.comment("Дистанция «огня»: на неё машина подходит к игроку, блоки").defineInRange("fireRange", t.defaultFireRange, 0.0, 128.0),
                    BUILDER.comment("Дальность обнаружения игрока, блоки").defineInRange("detectRange", t.defaultDetectRange, 0.0, 128.0),
                    BUILDER.comment("Урон выстрела (у роя — урон касания раз в 10 тиков)").defineInRange("damage", weaponDefault(t, 0), 0.0, 1000.0),
                    BUILDER.comment("Перезарядка между очередями, тики").defineInRange("cooldownTicks", (int) weaponDefault(t, 1), 1, 1200),
                    BUILDER.comment("Выстрелов в очереди").defineInRange("burst", (int) weaponDefault(t, 2), 1, 50),
                    BUILDER.comment("Пауза между выстрелами очереди, тики").defineInRange("burstGapTicks", (int) weaponDefault(t, 3), 1, 100),
                    BUILDER.comment("Разброс, градусы").defineInRange("spreadDegrees", weaponDefault(t, 4), 0.0, 45.0),
                    BUILDER.comment("Скорость снаряда, блоки за тик").defineInRange("projectileSpeed", weaponDefault(t, 5), 0.1, 10.0)));
            if (t == dev.rezus.vivacore.army.MachineType.HELICOPTER) {
                BUILDER.comment("Ракеты (взрывные; при выключенном vivaArmyGriefing — сквозные)").push("rockets");
                heliRocket[0] = BUILDER.defineInRange("damage", 10.0, 0.0, 1000.0);
                heliRocketCd[0] = BUILDER.comment("тики").defineInRange("cooldownTicks", 120, 1, 2400);
                heliRocket[1] = BUILDER.comment("сила взрыва (TNT = 4)").defineInRange("explosionPower", 2.0, 0.0, 8.0);
                heliRocket[2] = BUILDER.defineInRange("speed", 1.6, 0.1, 10.0);
                BUILDER.pop();
            }
            if (t == dev.rezus.vivacore.army.MachineType.TANK) {
                tankPower[0] = BUILDER.comment("Сила взрыва снаряда (TNT = 4)").defineInRange("explosionPower", 3.0, 0.0, 8.0);
            }
            if (t == dev.rezus.vivacore.army.MachineType.INFANTRY_HEAVY) {
                heavyShield[0] = BUILDER.comment("Щит: множитель урона спереди (со спины — полный)").defineInRange("frontDamageMultiplier", 0.3, 0.0, 1.0);
                heavyShield[1] = BUILDER.comment("Щит: полуширина сектора «спереди», градусы").defineInRange("shieldHalfAngle", 60.0, 0.0, 180.0);
            }
            if (t == dev.rezus.vivacore.army.MachineType.SNIPER) {
                sniperLaser[0] = BUILDER.comment("За сколько тиков до выстрела виден красный луч на цель").defineInRange("laserTicks", 30, 1, 200);
            }
            if (t == dev.rezus.vivacore.army.MachineType.SWARM_UNIT) {
                swarmRadius[0] = BUILDER.comment("Насколько дальше хитбокса роя бьют наниты, блоки").defineInRange("auraRadius", 0.5, 0.0, 8.0);
            }
            BUILDER.pop();
        }
        HELI_ROCKET_DAMAGE = heliRocket[0];
        HELI_ROCKET_POWER = heliRocket[1];
        HELI_ROCKET_SPEED = heliRocket[2];
        HELI_ROCKET_COOLDOWN = heliRocketCd[0];
        TANK_SHELL_POWER = tankPower[0];
        HEAVY_FRONT_MULTIPLIER = heavyShield[0];
        HEAVY_SHIELD_ANGLE = heavyShield[1];
        SNIPER_LASER_TICKS = sniperLaser[0];
        SWARM_AURA_RADIUS = swarmRadius[0];
        BUILDER.pop();
    }

    // --- orion: лаборатория ОРИОНа и обломки корабля (заметка «17 Три стороны конфликта», тест 34) ---
    public static final ModConfigSpec.ConfigValue<String> ORION_ACCESS_CODE;
    public static final ModConfigSpec.DoubleValue ORION_ATTEMPT_COOLDOWN;
    public static final ModConfigSpec.BooleanValue ORION_GIVE_NOTE;
    public static final ModConfigSpec.DoubleValue ORION_TERMINAL_REACH;
    public static final ModConfigSpec.IntValue ORION_EMERGENCY_LIGHT;
    public static final ModConfigSpec.IntValue ORION_DOOR_SLIDE_TICKS;
    public static final ModConfigSpec.BooleanValue ORION_DOOR_SLIDES_DOWN;

    static {
        BUILDER.comment("Лаборатория ОРИОНа").push("orion");
        ORION_ACCESS_CODE = BUILDER
                .comment("Код доступа в терминале лаборатории. Сравнивается строго: регистр и пробелы важны")
                .define("accessCode", "alpha-9G483#67");
        ORION_ATTEMPT_COOLDOWN = BUILDER
                .comment("Пауза между попытками ввода кода, секунды")
                .defineInRange("attemptCooldownSeconds", 1.0, 0.0, 3600.0);
        ORION_GIVE_NOTE = BUILDER
                .comment("Выдавать записку с кодом каждому игроку при первом входе")
                .define("giveNoteOnFirstJoin", true);
        ORION_TERMINAL_REACH = BUILDER
                .comment("Запас дальности (в блоках) сверх обычной дальности руки для ввода кода")
                .defineInRange("terminalReachBuffer", 1.0, 0.0, 16.0);
        ORION_EMERGENCY_LIGHT = BUILDER
                .comment("Уровень света аварийных светильников (0–15)")
                .defineInRange("emergencyLightLevel", 12, 0, 15);
        ORION_DOOR_SLIDE_TICKS = BUILDER
                .comment("За сколько тиков створка двери ОРИОНа уезжает или возвращается")
                .defineInRange("doorSlideTicks", 10, 1, 200);
        ORION_DOOR_SLIDES_DOWN = BUILDER
                .comment("Створка уезжает вниз (true) или вверх (false)")
                .define("doorSlidesDown", false);
        BUILDER.pop();
    }

    // --- structures, zones, protection: постройки, зоны влияния, защита (заметка «17 Три стороны конфликта», тесты 31, 32, 34) ---
    public static final ModConfigSpec.BooleanValue STRUCTURES_AUTO_PLACE;
    public static final ModConfigSpec.DoubleValue STRUCTURES_DISTANCE;
    public static final ModConfigSpec.DoubleValue STRUCTURES_ANGLE_SHIFT;
    public static final ModConfigSpec.DoubleValue STRUCTURES_ANGLE_STEP;
    public static final ModConfigSpec.DoubleValue STRUCTURES_DISTANCE_SHIFT;
    public static final ModConfigSpec.DoubleValue STRUCTURES_DISTANCE_STEP;
    public static final ModConfigSpec.IntValue STRUCTURES_LAND_RADIUS;
    public static final ModConfigSpec.IntValue STRUCTURES_LAND_GRID;
    public static final ModConfigSpec.DoubleValue STRUCTURES_ISLAND_FACTOR;
    public static final ModConfigSpec.IntValue STRUCTURES_EDGE_BLEND;
    public static final ModConfigSpec.IntValue STRUCTURES_EDGE_SLOPE;
    public static final ModConfigSpec.IntValue STRUCTURES_EDGE_INNER;
    public static final ModConfigSpec.IntValue STRUCTURES_FOUNDATION_DEPTH;
    public static final ModConfigSpec.ConfigValue<String> STRUCTURES_FOUNDATION_BLOCK;
    public static final ModConfigSpec.IntValue STRUCTURES_CLEAR_ABOVE;
    public static final ModConfigSpec.BooleanValue STRUCTURES_CRASH_SPAWN_RADIUS_ZERO;
    public static final ModConfigSpec.ConfigValue<String> STRUCTURES_GUARDIAN;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> STRUCTURES_GUARDS;

    public static final ModConfigSpec.BooleanValue ZONES_ENABLED;
    public static final ModConfigSpec.DoubleValue ZONES_CORE_RADIUS;
    public static final ModConfigSpec.DoubleValue ZONES_RADIUS;
    public static final ModConfigSpec.DoubleValue ZONES_DROP_PER_1000;
    public static final ModConfigSpec.DoubleValue ZONES_GUARD_CHANCE;
    public static final ModConfigSpec.IntValue ZONES_FULL_STAGE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ZONES_LAB_COMPOSITION;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ZONES_INFECTION_STUB;

    public static final ModConfigSpec.BooleanValue PROTECTION_ENABLED;
    public static final ModConfigSpec.IntValue PROTECTION_MARGIN;
    public static final ModConfigSpec.IntValue PROTECTION_OP_LEVEL;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> PROTECTION_STRUCTURES;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> PROTECTION_NO_TELEPORT;

    static {
        BUILDER.comment("Три постройки: жилой отсек у спавна, лаборатория ОРИОНа, место падения капсулы (тест 31)").push("structures");
        STRUCTURES_AUTO_PLACE = BUILDER.comment("Ставить постройки самому: отсек — при первом запуске мира, лабораторию и Нексус — когда",
                "их область впервые загрузилась. false — только командой /viva постройки поставить").define("autoPlace", true);
        STRUCTURES_DISTANCE = BUILDER.comment("Расстояние от спавна до лаборатории и до Нексуса, блоки").defineInRange("distance", 10000.0, 0.0, 1.0e6);
        STRUCTURES_ANGLE_SHIFT = BUILDER.comment("Поиск суши: сдвиг угла до ±, градусы").defineInRange("searchAngleDegrees", 20.0, 0.0, 180.0);
        STRUCTURES_ANGLE_STEP = BUILDER.comment("Поиск суши: шаг угла, градусы").defineInRange("searchAngleStepDegrees", 2.0, 0.1, 90.0);
        STRUCTURES_DISTANCE_SHIFT = BUILDER.comment("Поиск суши: сдвиг расстояния до ±, блоки").defineInRange("searchDistance", 1000.0, 0.0, 1.0e5);
        STRUCTURES_DISTANCE_STEP = BUILDER.comment("Поиск суши: шаг расстояния, блоки").defineInRange("searchDistanceStep", 100.0, 1.0, 1.0e5);
        STRUCTURES_LAND_RADIUS = BUILDER.comment("Поиск суши (задача 9): суша нужна по всей площади постройки и ещё на столько блоков вокруг",
                "(не океан, не река, не пляж)").defineInRange("landMargin", 16, 0, 512);
        STRUCTURES_LAND_GRID = BUILDER.comment("Поиск суши: шаг сетки точек шума биомов по площади, блоки (меньше — точнее и дольше)")
                .defineInRange("landGridStep", 8, 1, 64);
        STRUCTURES_ISLAND_FACTOR = BUILDER.comment("Поиск суши: остров (связная суша вокруг точки) должен быть не меньше площади постройки × это")
                .defineInRange("islandFactor", 2.0, 0.0, 100.0);
        STRUCTURES_FOUNDATION_DEPTH = BUILDER.comment("Насколько глубоко ставить столбы фундамента под постройкой до земли (обрыв, склон), блоки")
                .defineInRange("foundationMaxDepth", 64, 0, 256);
        STRUCTURES_EDGE_BLEND = BUILDER.comment("Края (задача 9): ширина полосы вокруг постройки, где земля плавно сводится к местности, блоки")
                .defineInRange("edgeBlend", 6, 0, 32);
        STRUCTURES_EDGE_SLOPE = BUILDER.comment("Края: склон не круче стольких блоков высоты на блок от края").defineInRange("edgeSlope", 2, 1, 8);
        STRUCTURES_EDGE_INNER = BUILDER.comment("Края: на столько блоков внутрь постройки природная земля (не постройка) неровно сдвигается к местности")
                .defineInRange("edgeInner", 2, 0, 8);
        STRUCTURES_FOUNDATION_BLOCK = BUILDER.comment("Чем засыпать пустоты под постройкой").define("foundationBlock", "minecraft:stone");
        STRUCTURES_CLEAR_ABOVE = BUILDER.comment("Над постройкой (где её верх — воздух) убрать нависающий грунт на столько блоков вверх").defineInRange("clearAboveDepth", 48, 0, 256);
        STRUCTURES_CRASH_SPAWN_RADIUS_ZERO = BUILDER.comment("После жилого отсека поставить правило spawnRadius = 0 (иначе новичок появляется",
                "в случайной точке в 10 блоках — на крыше или снаружи отсека)").define("crashSpawnRadiusZero", true);
        STRUCTURES_GUARDIAN = BUILDER.comment("Машина на метке vivacore:guardian (страж ядра)").define("guardianType", "core_guardian");
        STRUCTURES_GUARDS = BUILDER.comment("Машины на метках vivacore:guard — по кругу").defineListAllowEmpty("guardTypes",
                java.util.List.of("infantry_heavy", "infantry_light", "sniper"), () -> "", o -> o instanceof String);
        BUILDER.pop();

        BUILDER.comment("Зоны влияния вокруг ядер (тест 32): доля стороны = 100% − dropPer1000 × (расстояние ÷ 1000)").push("zones");
        ZONES_ENABLED = BUILDER.comment("Включены ли зоны").define("enabled", true);
        ZONES_CORE_RADIUS = BUILDER.comment("Ближе этого к ядру обычные мобы не появляются, блоки").defineInRange("coreRadius", 1000.0, 0.0, 1.0e5);
        ZONES_RADIUS = BUILDER.comment("Радиус большого круга, блоки (дальше — доля 0)").defineInRange("radius", 9500.0, 0.0, 1.0e6);
        ZONES_DROP_PER_1000 = BUILDER.comment("На сколько падает доля за 1000 блоков (0.1 = 10%)").defineInRange("dropPer1000", 0.1, 0.0, 1.0);
        ZONES_GUARD_CHANCE = BUILDER.comment("До полной стадии: шанс моба стороны у ядра (охрана)").defineInRange("guardChance", 0.3, 0.0, 1.0);
        ZONES_FULL_STAGE = BUILDER.comment("С какой стадии сервера включаются большие круги (4 — «Понимание», латунь)").defineInRange("fullStage", 4, 1, 5);
        ZONES_LAB_COMPOSITION = BUILDER.comment("Сторона лаборатории: «до расстояния=тип|тип» по возрастанию; берётся первая строка,",
                "где расстояние до ядра ≤ числа, иначе последняя").defineListAllowEmpty("labComposition",
                java.util.List.of("1000=infantry_heavy|mech_walker|sniper|tank", "4000=infantry_heavy|infantry_light|sniper",
                        "9500=scout_drone|infantry_light|swarm_unit"), () -> "", o -> o instanceof String);
        ZONES_INFECTION_STUB = BUILDER.comment("Сторона заражения, заглушка до Spore: какие заражённые машины ставить").defineListAllowEmpty("infectionStubTypes",
                java.util.List.of("scout_drone_infected", "infantry_light_infected"), () -> "", o -> o instanceof String);
        BUILDER.pop();

        BUILDER.comment("Защита построек (тест 34): коробка шаблона + отступ").push("protection");
        PROTECTION_ENABLED = BUILDER.comment("Включена ли защита").define("enabled", true);
        PROTECTION_MARGIN = BUILDER.comment("Отступ от коробки шаблона со всех сторон, блоки").defineInRange("margin", 4, 0, 64);
        PROTECTION_OP_LEVEL = BUILDER.comment("Уровень оператора, которому в творческом режиме можно строить в постройках").defineInRange("opPermissionLevel", 2, 0, 4);
        PROTECTION_STRUCTURES = BUILDER.comment("Какие постройки защищены: crash, lab, nexus").defineListAllowEmpty("structures",
                java.util.List.of("crash", "lab", "nexus"), () -> "", o -> o instanceof String);
        PROTECTION_NO_TELEPORT = BUILDER.comment("Внутрь каких построек нельзя эндер-жемчугом и хорусом").defineListAllowEmpty("noTeleportInto",
                java.util.List.of("lab"), () -> "", o -> o instanceof String);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private VivaConfig() {}

    /**
     * Значение конфига или запасное, если серверный конфиг ещё не загружен
     * (команды регистрируются раньше загрузки серверного конфига).
     */
    public static int getOr(ModConfigSpec.IntValue value, Supplier<Integer> fallback) {
        return SPEC.isLoaded() ? value.get() : fallback.get();
    }

    public static int homeMaxMembers() {
        return getOr(HOME_MAX_MEMBERS, HOME_MAX_MEMBERS::getDefault);
    }

    public static int homeCommandPermission() {
        return getOr(HOME_COMMAND_PERMISSION, HOME_COMMAND_PERMISSION::getDefault);
    }

    public static boolean wheelsEnabled() {
        return SPEC.isLoaded() ? WHEELS_ENABLED.get() : WHEELS_ENABLED.getDefault();
    }

    public static int wheelsMaxPerNetwork() {
        return getOr(WHEELS_MAX_PER_NETWORK, WHEELS_MAX_PER_NETWORK::getDefault);
    }

    public static int wheelsCheckInterval() {
        return getOr(WHEELS_CHECK_INTERVAL, WHEELS_CHECK_INTERVAL::getDefault);
    }

    public static int zhivaPhraseCooldownSeconds() {
        return getOr(ZHIVA_PHRASE_COOLDOWN, ZHIVA_PHRASE_COOLDOWN::getDefault);
    }

    public static int zhivaLowHealthPercent() {
        return getOr(ZHIVA_LOW_HEALTH_PERCENT, ZHIVA_LOW_HEALTH_PERCENT::getDefault);
    }

    public static int zhivaHealthCheckTicks() {
        return getOr(ZHIVA_HEALTH_CHECK_TICKS, ZHIVA_HEALTH_CHECK_TICKS::getDefault);
    }

    private static double d(ModConfigSpec.DoubleValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    private static int i(ModConfigSpec.IntValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static boolean armyEnabled() {
        return SPEC.isLoaded() ? ARMY_ENABLED.get() : ARMY_ENABLED.getDefault();
    }

    public static int armyPatrolRadius() { return i(ARMY_PATROL_RADIUS); }
    public static int armyPatrolHeight() { return i(ARMY_PATROL_HEIGHT); }
    public static int armySectorSize() { return i(ARMY_SECTOR_SIZE); }
    public static double armySightRange() { return d(ARMY_SIGHT_RANGE); }
    public static double armyAlarmPerSight() { return d(ARMY_ALARM_PER_SIGHT); }
    public static double armyAlarmPerKill() { return d(ARMY_ALARM_PER_KILL); }
    public static double armyAlarmDecay() { return d(ARMY_ALARM_DECAY); }
    public static int armyWaveThreshold() { return i(ARMY_WAVE_THRESHOLD); }
    public static int armyWaveDelay() { return i(ARMY_WAVE_DELAY); }
    public static int armyWaveWarning() { return i(ARMY_WAVE_WARNING); }
    public static double armyWaveDistance() { return d(ARMY_WAVE_DISTANCE); }
    public static int armyMaxMachines() { return i(ARMY_MAX_MACHINES); }
    public static int armyMaxHeavy() { return i(ARMY_MAX_HEAVY); }
    public static double armyHeavyGroupRadius() { return d(ARMY_HEAVY_GROUP_RADIUS); }
    public static int armyHeavyDespawn() { return i(ARMY_HEAVY_DESPAWN); }

    public static java.util.List<? extends String> armyWave(int level) {
        var v = ARMY_WAVES[Math.max(1, Math.min(4, level))];
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static double machineHealth(dev.rezus.vivacore.army.MachineType t) { return d(MACHINES.get(t).health()); }
    public static double machineSpeed(dev.rezus.vivacore.army.MachineType t) { return d(MACHINES.get(t).speed()); }
    public static double machineArmor(dev.rezus.vivacore.army.MachineType t) { return d(MACHINES.get(t).armor()); }
    public static double machineFireRange(dev.rezus.vivacore.army.MachineType t) { return d(MACHINES.get(t).fireRange()); }
    public static double machineDetectRange(dev.rezus.vivacore.army.MachineType t) { return d(MACHINES.get(t).detectRange()); }
    public static double machineDamage(dev.rezus.vivacore.army.MachineType t) { return d(MACHINES.get(t).damage()); }
    public static int machineCooldown(dev.rezus.vivacore.army.MachineType t) { return i(MACHINES.get(t).cooldown()); }
    public static int machineBurst(dev.rezus.vivacore.army.MachineType t) { return i(MACHINES.get(t).burst()); }
    public static int machineBurstGap(dev.rezus.vivacore.army.MachineType t) { return i(MACHINES.get(t).burstGap()); }
    public static double machineSpread(dev.rezus.vivacore.army.MachineType t) { return d(MACHINES.get(t).spread()); }
    public static double machineProjectileSpeed(dev.rezus.vivacore.army.MachineType t) { return d(MACHINES.get(t).projectileSpeed()); }
    public static double heliRocketDamage() { return d(HELI_ROCKET_DAMAGE); }
    public static int heliRocketCooldown() { return i(HELI_ROCKET_COOLDOWN); }
    public static double heliRocketPower() { return d(HELI_ROCKET_POWER); }
    public static double heliRocketSpeed() { return d(HELI_ROCKET_SPEED); }
    public static double tankShellPower() { return d(TANK_SHELL_POWER); }
    public static double heavyFrontMultiplier() { return d(HEAVY_FRONT_MULTIPLIER); }
    public static double heavyShieldAngle() { return d(HEAVY_SHIELD_ANGLE); }
    public static int sniperLaserTicks() { return i(SNIPER_LASER_TICKS); }
    public static double swarmAuraRadius() { return d(SWARM_AURA_RADIUS); }
    public static int armyPierceBlocks() { return i(ARMY_PIERCE_BLOCKS); }
    public static boolean armyHitscanBullets() { return b(ARMY_HITSCAN_BULLETS); }
    public static double armyHitscanRangeFactor() { return d(ARMY_HITSCAN_RANGE_FACTOR); }
    public static double armyTracerSendRange() { return d(ARMY_TRACER_SEND_RANGE); }
    public static int armyMaxProjectilesPerSector() { return i(ARMY_MAX_PROJECTILES); }
    public static int armyMaxExplosionsPerTick() { return i(ARMY_MAX_EXPLOSIONS); }
    public static double armyInfectionChance() { return d(ARMY_INFECTION_CHANCE); }

    public static java.util.List<? extends String> armyTargets(boolean infected) {
        var v = infected ? ARMY_TARGETS_INFECTED : ARMY_TARGETS_ORION;
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static String orionAccessCode() {
        return SPEC.isLoaded() ? ORION_ACCESS_CODE.get() : ORION_ACCESS_CODE.getDefault();
    }

    public static double orionAttemptCooldownSeconds() {
        return d(ORION_ATTEMPT_COOLDOWN);
    }

    public static boolean orionGiveNote() {
        return SPEC.isLoaded() ? ORION_GIVE_NOTE.get() : ORION_GIVE_NOTE.getDefault();
    }

    public static double orionTerminalReach() {
        return d(ORION_TERMINAL_REACH);
    }

    public static int orionDoorSlideTicks() {
        return i(ORION_DOOR_SLIDE_TICKS);
    }

    public static boolean orionDoorSlidesDown() {
        return SPEC.isLoaded() ? ORION_DOOR_SLIDES_DOWN.get() : ORION_DOOR_SLIDES_DOWN.getDefault();
    }

    public static int emergencyLightLevel() {
        return i(ORION_EMERGENCY_LIGHT);
    }

    private static boolean b(ModConfigSpec.BooleanValue v) { return SPEC.isLoaded() ? v.get() : v.getDefault(); }
    private static <T> T c(ModConfigSpec.ConfigValue<T> v) { return SPEC.isLoaded() ? v.get() : v.getDefault(); }
    public static boolean structuresAutoPlace() { return b(STRUCTURES_AUTO_PLACE); }
    public static double structuresDistance() { return d(STRUCTURES_DISTANCE); }
    public static double structuresAngleShift() { return d(STRUCTURES_ANGLE_SHIFT); }
    public static double structuresAngleStep() { return d(STRUCTURES_ANGLE_STEP); }
    public static double structuresDistanceShift() { return d(STRUCTURES_DISTANCE_SHIFT); }
    public static double structuresDistanceStep() { return d(STRUCTURES_DISTANCE_STEP); }
    public static int structuresLandRadius() { return i(STRUCTURES_LAND_RADIUS); }
    public static int structuresFoundationDepth() { return i(STRUCTURES_FOUNDATION_DEPTH); }
    public static int structuresLandGrid() { return i(STRUCTURES_LAND_GRID); }
    public static double structuresIslandFactor() { return d(STRUCTURES_ISLAND_FACTOR); }
    public static int structuresEdgeBlend() { return i(STRUCTURES_EDGE_BLEND); }
    public static int structuresEdgeSlope() { return i(STRUCTURES_EDGE_SLOPE); }
    public static int structuresEdgeInner() { return i(STRUCTURES_EDGE_INNER); }
    public static String structuresFoundationBlock() { return c(STRUCTURES_FOUNDATION_BLOCK); }
    public static int structuresClearAbove() { return i(STRUCTURES_CLEAR_ABOVE); }
    public static boolean structuresCrashSpawnRadiusZero() { return b(STRUCTURES_CRASH_SPAWN_RADIUS_ZERO); }
    public static String structuresGuardianType() { return c(STRUCTURES_GUARDIAN); }
    public static java.util.List<? extends String> structuresGuardTypes() { return c(STRUCTURES_GUARDS); }
    public static boolean zonesEnabled() { return b(ZONES_ENABLED); }
    public static double zonesCoreRadius() { return d(ZONES_CORE_RADIUS); }
    public static double zonesRadius() { return d(ZONES_RADIUS); }
    public static double zonesDropPer1000() { return d(ZONES_DROP_PER_1000); }
    public static double zonesGuardChance() { return d(ZONES_GUARD_CHANCE); }
    public static int zonesFullStage() { return i(ZONES_FULL_STAGE); }
    public static java.util.List<? extends String> zonesLabComposition() { return c(ZONES_LAB_COMPOSITION); }
    public static java.util.List<? extends String> zonesInfectionStubTypes() { return c(ZONES_INFECTION_STUB); }
    public static boolean protectionEnabled() { return b(PROTECTION_ENABLED); }
    public static int protectionMargin() { return i(PROTECTION_MARGIN); }
    public static int protectionOpLevel() { return i(PROTECTION_OP_LEVEL); }
    public static java.util.List<? extends String> protectionStructures() { return c(PROTECTION_STRUCTURES); }
    public static java.util.List<? extends String> protectionNoTeleportInto() { return c(PROTECTION_NO_TELEPORT); }

    public static double zhivaDeviceReach() {
        return SPEC.isLoaded() ? ZHIVA_DEVICE_REACH.get() : ZHIVA_DEVICE_REACH.getDefault();
    }
}
