package dev.rezus.vivacore;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Клиентский конфиг Viva Core (config/vivacore-client.toml): только то, что видит сам игрок. */
public final class VivaClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SWARM_UNITS;
    public static final ModConfigSpec.DoubleValue TRACER_SPEED;
    public static final ModConfigSpec.DoubleValue TRACER_LENGTH;
    public static final ModConfigSpec.DoubleValue TRACER_WIDTH;
    public static final ModConfigSpec.DoubleValue FLASH_SIZE;
    public static final ModConfigSpec.DoubleValue NEAR_CAMERA;

    static {
        BUILDER.comment("Армия ОРИОНа: отрисовка").push("army");
        SWARM_UNITS = BUILDER
                .comment("Сколько моделек рисуется вокруг одной сущности «рой» (на сервере рой — одна сущность)")
                .defineInRange("swarmUnits", 40, 1, 200);
        TRACER_SPEED = BUILDER
                .comment("Трассер пули (задача 9): скорость полёта черты, блоков за тик")
                .defineInRange("tracerSpeed", 6.0, 0.5, 64.0);
        TRACER_LENGTH = BUILDER
                .comment("Длина черты трассера, блоки")
                .defineInRange("tracerLength", 2.0, 0.1, 16.0);
        TRACER_WIDTH = BUILDER
                .comment("Толщина черты трассера, блоки")
                .defineInRange("tracerWidth", 0.04, 0.005, 0.5);
        FLASH_SIZE = BUILDER
                .comment("Задача 11: полуразмер вспышки выстрела, блоки (было 0.35 — квадрат 0.7 у камеры закрывал обзор)")
                .defineInRange("muzzleFlashSize", 0.15, 0.02, 1.0);
        NEAR_CAMERA = BUILDER
                .comment("Вспышки, трассеры и снаряды ОРИОНа ближе этого к камере не рисуются (иначе заслоняют экран), блоки")
                .defineInRange("hideNearCamera", 1.5, 0.0, 8.0);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private VivaClientConfig() {}

    private static double d(ModConfigSpec.DoubleValue v) {
        return SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static double tracerSpeed() { return d(TRACER_SPEED); }
    public static double tracerLength() { return d(TRACER_LENGTH); }
    public static double tracerWidth() { return d(TRACER_WIDTH); }
    public static double muzzleFlashSize() { return d(FLASH_SIZE); }
    public static double hideNearCamera() { return d(NEAR_CAMERA); }

    public static int swarmUnits() {
        return SPEC.isLoaded() ? SWARM_UNITS.get() : SWARM_UNITS.getDefault();
    }
}
