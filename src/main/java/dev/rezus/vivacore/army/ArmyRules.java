package dev.rezus.vivacore.army;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * Своё игровое правило разрушения армии (заметка «16 Враги-армия», раздел 4). Ванильное mobGriefing не трогаем:
 * оно выключает разрушение сразу всем (криперам, эндерменам, Spore), а это — только для армии ОРИОНа.
 * <ul>
 *   <li>true (по умолчанию): ракеты вертолёта и снаряд танка взрываются и ломают блоки (обычный ванильный взрыв —
 *       его перекрывает приват Open Parties and Claims через ExplosionEvent.Detonate);</li>
 *   <li>false: вместо них — сквозные снаряды: блоки не ломают, проходят сквозь лёгкие блоки (тег vivacore:pierceable).</li>
 * </ul>
 * /gamerule vivaArmyGriefing true|false
 */
public final class ArmyRules {
    private ArmyRules() {}

    public static final GameRules.Key<GameRules.BooleanValue> GRIEFING =
            GameRules.register("vivaArmyGriefing", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));

    public static boolean griefing(Level level) {
        return level.getGameRules().getBoolean(GRIEFING);
    }

    /** Принудительно зарегистрировать правило при загрузке мода. */
    public static void init() {
    }
}
