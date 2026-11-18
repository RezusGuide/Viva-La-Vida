package dev.rezus.vivacore;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Клиентский конфиг Viva Core (config/vivacore-client.toml): только то, что видит сам игрок. */
public final class VivaClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SWARM_UNITS;

    static {
        BUILDER.comment("Армия ОРИОНа: отрисовка").push("army");
        SWARM_UNITS = BUILDER
                .comment("Сколько моделек рисуется вокруг одной сущности «рой» (на сервере рой — одна сущность)")
                .defineInRange("swarmUnits", 40, 1, 200);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private VivaClientConfig() {}

    public static int swarmUnits() {
        return SPEC.isLoaded() ? SWARM_UNITS.get() : SWARM_UNITS.getDefault();
    }
}
