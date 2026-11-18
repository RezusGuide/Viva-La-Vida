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

    /** Числа одной машины: army.<id>.* */
    public record Machine(ModConfigSpec.DoubleValue health, ModConfigSpec.DoubleValue speed,
                          ModConfigSpec.DoubleValue armor, ModConfigSpec.DoubleValue fireRange,
                          ModConfigSpec.DoubleValue detectRange) {}

    private static final java.util.Map<dev.rezus.vivacore.army.MachineType, Machine> MACHINES =
            new java.util.EnumMap<>(dev.rezus.vivacore.army.MachineType.class);

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

        for (dev.rezus.vivacore.army.MachineType t : dev.rezus.vivacore.army.MachineType.values()) {
            BUILDER.push(t.id);
            MACHINES.put(t, new Machine(
                    BUILDER.comment("Здоровье (полусердца)").defineInRange("maxHealth", t.defaultHealth, 1.0, 4096.0),
                    BUILDER.comment("Скорость (наземные — movement_speed, летающие — flying_speed)").defineInRange("speed", t.defaultSpeed, 0.0, 4.0),
                    BUILDER.comment("Броня (атрибут armor)").defineInRange("armor", t.defaultArmor, 0.0, 30.0),
                    BUILDER.comment("Дистанция «огня»: на неё машина подходит к игроку, блоки").defineInRange("fireRange", t.defaultFireRange, 0.0, 128.0),
                    BUILDER.comment("Дальность обнаружения игрока, блоки").defineInRange("detectRange", t.defaultDetectRange, 0.0, 128.0)));
            BUILDER.pop();
        }
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

    public static double zhivaDeviceReach() {
        return SPEC.isLoaded() ? ZHIVA_DEVICE_REACH.get() : ZHIVA_DEVICE_REACH.getDefault();
    }
}
