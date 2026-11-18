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

    // --- army: армия лаборатории (заметка «16 Враги-армия», тест 24) ---
    public static final ModConfigSpec.DoubleValue SCOUT_DRONE_HEALTH;
    public static final ModConfigSpec.DoubleValue SCOUT_DRONE_SPEED;
    public static final ModConfigSpec.IntValue SCOUT_DRONE_HOVER_RADIUS;
    public static final ModConfigSpec.IntValue SCOUT_DRONE_HOVER_HEIGHT;

    static {
        BUILDER.comment("Армия лаборатории").push("army");
        BUILDER.comment("Дрон-разведчик «Мейсса»").push("scoutDrone");
        SCOUT_DRONE_HEALTH = BUILDER
                .comment("Здоровье дрона (полусердца)")
                .defineInRange("maxHealth", 12.0, 1.0, 1024.0);
        SCOUT_DRONE_SPEED = BUILDER
                .comment("Скорость полёта (атрибут flying_speed)")
                .defineInRange("flyingSpeed", 0.4, 0.01, 4.0);
        SCOUT_DRONE_HOVER_RADIUS = BUILDER
                .comment("Радиус (в блоках) вокруг точки появления, в котором дрон кружит")
                .defineInRange("hoverRadius", 8, 1, 64);
        SCOUT_DRONE_HOVER_HEIGHT = BUILDER
                .comment("Насколько блоков выше/ниже точки появления дрон может подняться/опуститься")
                .defineInRange("hoverHeight", 3, 0, 32);
        BUILDER.pop(2);
    }

    // --- orion: лаборатория ОРИОНа и обломки корабля (заметка «17 Три стороны конфликта», тест 34) ---
    public static final ModConfigSpec.ConfigValue<String> ORION_ACCESS_CODE;
    public static final ModConfigSpec.DoubleValue ORION_ATTEMPT_COOLDOWN;
    public static final ModConfigSpec.BooleanValue ORION_GIVE_NOTE;
    public static final ModConfigSpec.DoubleValue ORION_TERMINAL_REACH;
    public static final ModConfigSpec.IntValue ORION_EMERGENCY_LIGHT;

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

    public static double scoutDroneMaxHealth() {
        return SPEC.isLoaded() ? SCOUT_DRONE_HEALTH.get() : SCOUT_DRONE_HEALTH.getDefault();
    }

    public static double scoutDroneFlyingSpeed() {
        return SPEC.isLoaded() ? SCOUT_DRONE_SPEED.get() : SCOUT_DRONE_SPEED.getDefault();
    }

    public static int scoutDroneHoverRadius() {
        return getOr(SCOUT_DRONE_HOVER_RADIUS, SCOUT_DRONE_HOVER_RADIUS::getDefault);
    }

    public static int scoutDroneHoverHeight() {
        return getOr(SCOUT_DRONE_HOVER_HEIGHT, SCOUT_DRONE_HOVER_HEIGHT::getDefault);
    }

    public static String orionAccessCode() {
        return SPEC.isLoaded() ? ORION_ACCESS_CODE.get() : ORION_ACCESS_CODE.getDefault();
    }

    public static double orionAttemptCooldownSeconds() {
        return SPEC.isLoaded() ? ORION_ATTEMPT_COOLDOWN.get() : ORION_ATTEMPT_COOLDOWN.getDefault();
    }

    public static boolean orionGiveNote() {
        return SPEC.isLoaded() ? ORION_GIVE_NOTE.get() : ORION_GIVE_NOTE.getDefault();
    }

    public static double orionTerminalReach() {
        return SPEC.isLoaded() ? ORION_TERMINAL_REACH.get() : ORION_TERMINAL_REACH.getDefault();
    }

    public static int emergencyLightLevel() {
        return getOr(ORION_EMERGENCY_LIGHT, ORION_EMERGENCY_LIGHT::getDefault);
    }

    public static double zhivaDeviceReach() {
        return SPEC.isLoaded() ? ZHIVA_DEVICE_REACH.get() : ZHIVA_DEVICE_REACH.getDefault();
    }
}
