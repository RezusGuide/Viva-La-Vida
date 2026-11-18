package dev.rezus.vivacore.army;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Машины ОРИОНа (заметка «16 Враги-армия», раздел «Имена»). Внутренние id — нейтральные (как папки моделей).
 * Здесь — данные модели (хитбокс по габаритам модели, имена анимаций, длина анимации смерти) и значения
 * по умолчанию для конфига army.&lt;id&gt; (здоровье, скорость, броня, дистанция «огня», дальность обнаружения).
 * Баланс живёт в конфиге; эти числа — только стартовые значения.
 */
public enum MachineType {
    //            id                         движение          тяж.   зараж. ширина высота яйцо-основа  яйцо-пятна  move    idle    death   смерть(тики) здоровье скорость броня огонь обнаруж.
    SCOUT_DRONE("scout_drone", Movement.FLYING, false, false, 1.2f, 0.9f, 0xF2C230, 0x111214, "fly", "idle", null, 10, 12, 0.4, 0, 12, 24),
    SCOUT_DRONE_INFECTED("scout_drone_infected", Movement.FLYING, false, true, 1.2f, 0.9f, 0xF2C230, 0xA8494A, "fly", "idle", null, 10, 16, 0.35, 0, 10, 24),
    INFANTRY_LIGHT("infantry_light", Movement.GROUND, false, false, 0.7f, 1.95f, 0x3A3E47, 0x9FD3FF, "walk", "idle", "death", 30, 20, 0.25, 2, 14, 24),
    INFANTRY_LIGHT_INFECTED("infantry_light_infected", Movement.GROUND, false, true, 0.7f, 1.95f, 0x3A3E47, 0xA8494A, "walk", "idle", "death", 30, 26, 0.22, 2, 10, 24),
    INFANTRY_HEAVY("infantry_heavy", Movement.GROUND, false, false, 1.2f, 2.5f, 0xC99A1E, 0x1D1F24, "walk", "idle", "death", 32, 40, 0.2, 8, 12, 24),
    INFANTRY_HEAVY_INFECTED("infantry_heavy_infected", Movement.GROUND, false, true, 1.2f, 2.5f, 0xC99A1E, 0x7A2F2F, "walk", "idle", "death", 32, 50, 0.18, 8, 10, 24),
    SNIPER("sniper", Movement.GROUND, false, false, 0.7f, 1.95f, 0x111214, 0xD8302A, "walk", "idle", "death", 30, 16, 0.25, 1, 32, 40),
    MECH_WALKER("mech_walker", Movement.GROUND, true, false, 2.2f, 3.0f, 0x8A9099, 0xF2C230, "walk", "idle", "death", 44, 120, 0.18, 14, 20, 32),
    MECH_WALKER_INFECTED("mech_walker_infected", Movement.GROUND, true, true, 2.2f, 3.0f, 0x8A9099, 0xA8494A, "walk", "idle", "death", 44, 140, 0.16, 14, 16, 32),
    TANK("tank", Movement.GROUND, true, false, 2.6f, 2.0f, 0x2B2E35, 0x8A9099, "move", "idle", "death", 40, 160, 0.15, 18, 24, 40),
    HELICOPTER("helicopter", Movement.FLYING, true, false, 2.4f, 1.6f, 0x3A3E47, 0xD8302A, "fly", "hover", "death", 50, 80, 0.5, 8, 24, 48),
    SWARM_UNIT("swarm_unit", Movement.FLYING, false, false, 1.5f, 1.5f, 0x111214, 0x45D16B, "fly", "fly", "death", 10, 30, 0.45, 0, 6, 24),
    CORE_GUARDIAN("core_guardian", Movement.STATIC, false, false, 3.0f, 4.2f, 0xD8302A, 0xF2C230, "walk", "idle", "death", 60, 400, 0.0, 20, 0, 32);

    public enum Movement { GROUND, FLYING, STATIC }

    public final String id;
    public final Movement movement;
    /** Тяжёлая техника: лимит на группу игроков, исчезает без игроков рядом. */
    public final boolean heavy;
    /** Заражённая версия: её ведёт заражение — отмена программы ОРИОНа её не усыпляет, тревогу не поднимает. */
    public final boolean infected;
    public final float width, height;
    public final int eggPrimary, eggSecondary;
    public final String animMove, animIdle;
    @Nullable
    public final String animDeath;
    /** Сколько тиков сущность лежит мёртвой (под длину анимации death). */
    public final int deathTicks;
    public final double defaultHealth, defaultSpeed, defaultArmor, defaultFireRange, defaultDetectRange;

    MachineType(String id, Movement movement, boolean heavy, boolean infected, float width, float height,
                int eggPrimary, int eggSecondary, String animMove, String animIdle, @Nullable String animDeath,
                int deathTicks, double health, double speed, double armor, double fireRange, double detectRange) {
        this.id = id;
        this.movement = movement;
        this.heavy = heavy;
        this.infected = infected;
        this.width = width;
        this.height = height;
        this.eggPrimary = eggPrimary;
        this.eggSecondary = eggSecondary;
        this.animMove = animMove;
        this.animIdle = animIdle;
        this.animDeath = animDeath;
        this.deathTicks = deathTicks;
        this.defaultHealth = health;
        this.defaultSpeed = speed;
        this.defaultArmor = armor;
        this.defaultFireRange = fireRange;
        this.defaultDetectRange = detectRange;
    }

    /** Полное имя анимации GeckoLib: animation.&lt;id&gt;.&lt;name&gt;. */
    public String anim(String name) {
        return "animation." + id + "." + name;
    }

    /** Босс: не исчезает, не ходит (пока). */
    public boolean boss() {
        return this == CORE_GUARDIAN;
    }

    @Nullable
    public static MachineType byId(String id) {
        for (MachineType t : values()) {
            if (t.id.equals(id.toLowerCase(Locale.ROOT))) return t;
        }
        return null;
    }
}
