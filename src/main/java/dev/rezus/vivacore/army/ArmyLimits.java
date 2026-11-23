package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Лимиты боя ОРИОНа (тест 38, задача 9): не больше army.combat.maxProjectilesPerSector снарядов-сущностей
 * одновременно в участке (квадрат army.alarm.sectorSize) и не больше army.combat.maxExplosionsPerTick взрывов ОРИОНа
 * за тик сервера — лишние взрываются «без взрыва»: только урон по сущностям рядом, без блоков, частиц и лучей.
 * Состояние — в памяти сервера (сбрасывается при остановке).
 */
public final class ArmyLimits {
    private ArmyLimits() {}

    private static final Map<String, Integer> PROJECTILES = new HashMap<>();
    private static long explosionTick = Long.MIN_VALUE;
    private static int explosionsThisTick;
    /** Для тестов и замера: сколько взрывов заменено уроном и сколько выстрелов снарядами пропущено. */
    static int explosionsLimited;
    static int projectilesLimited;

    static String sector(ServerLevel level, Vec3 pos) {
        return Alarm.sector(level, net.minecraft.core.BlockPos.containing(pos));
    }

    /** Можно ли выпустить ещё один снаряд-сущность в этом месте. */
    public static boolean canSpawnProjectile(ServerLevel level, Vec3 pos) {
        if (PROJECTILES.getOrDefault(sector(level, pos), 0) < VivaConfig.armyMaxProjectilesPerSector()) return true;
        projectilesLimited++;
        return false;
    }

    static void projectileAdded(String sector) {
        PROJECTILES.merge(sector, 1, Integer::sum);
    }

    static void projectileRemoved(String sector) {
        PROJECTILES.computeIfPresent(sector, (k, v) -> v <= 1 ? null : v - 1);
    }

    /** Живых снарядов ОРИОНа в участке (для тестов). */
    public static int projectilesIn(ServerLevel level, Vec3 pos) {
        return PROJECTILES.getOrDefault(sector(level, pos), 0);
    }

    /** Взять «место» под взрыв в этом тике; false — лимит исчерпан. */
    public static boolean tryExplosion(ServerLevel level) {
        long now = level.getServer().getTickCount();
        if (now != explosionTick) {
            explosionTick = now;
            explosionsThisTick = 0;
        }
        if (explosionsThisTick >= VivaConfig.armyMaxExplosionsPerTick()) {
            explosionsLimited++;
            return false;
        }
        explosionsThisTick++;
        return true;
    }

    public static int explosionsLimited() {
        return explosionsLimited;
    }

    public static int projectilesLimited() {
        return projectilesLimited;
    }

    public static void reset() {
        PROJECTILES.clear();
        explosionTick = Long.MIN_VALUE;
        explosionsThisTick = 0;
    }
}
