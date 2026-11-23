package dev.rezus.vivacore.army;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Мгновенный выстрел (hitscan; задача 9, тест 38): пули винтовок, пулемётов, очереди меха, снайпер.
 * Одна трассировка блоков ({@link ShotTrace}, видит корабли Sable и правила сквозных pierceable / blast_proof) и
 * один поиск сущностей вдоль луча в момент выстрела; клиенту — пакет трассера ({@link ArmyNetwork}).
 * Сущности-снаряда нет. Своих (та же сторона) не задевает.
 */
public final class Hitscan {
    private Hitscan() {}

    /** Замер (только dev-бенч): время и число мгновенных выстрелов. */
    public static boolean STATS;
    public static long nanos;
    public static long count;

    /** Итог выстрела: конец луча в мире, кого задело (или null), упёрся ли в блок, пробитые блоки. */
    public record Result(Vec3 end, Entity entity, boolean blocked, java.util.Set<net.minecraft.core.BlockPos> pierced) {}

    /**
     * @param dir      направление (не нормированное), разброс уже учтён
     * @param pierce   null — обычная пуля; иначе запас пробития лёгких блоков
     */
    public static Result fire(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, double range, float damage,
                              ShotTrace.Pierce pierce, byte tracerKind) {
        long t0 = STATS ? System.nanoTime() : 0;
        Vec3 to = from.add(dir.normalize().scale(range));
        // сначала сущность на всём луче, потом блоки — только до неё (луч до цели короче полной дальности)
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, shooter, from, to, new AABB(from, to).inflate(1.0),
                e -> canHit(shooter, e));
        Vec3 limit = hit != null ? hit.getLocation() : to;
        BlockHitResult block = ShotTrace.blocks(level, shooter, from, limit, pierce);
        if (block != null) hit = null; // стена раньше сущности
        Vec3 end = block != null ? block.getLocation() : limit;
        byte impact;
        Entity victim = null;
        if (hit != null) {
            victim = hit.getEntity();
            end = hit.getLocation();
            victim.hurt(level.damageSources().mobProjectile(shooter, shooter), damage);
            impact = ArmyNetwork.IMPACT_ENTITY;
        } else {
            impact = block != null ? ArmyNetwork.IMPACT_BLOCK : ArmyNetwork.IMPACT_NONE;
        }
        ArmyNetwork.sendTracer(level, from, end, tracerKind, impact);
        if (STATS) {
            nanos += System.nanoTime() - t0;
            count++;
        }
        return new Result(end, victim, hit == null && block != null, pierce == null ? java.util.Set.of() : java.util.Set.copyOf(pierce.pierced()));
    }

    static boolean canHit(LivingEntity shooter, Entity target) {
        if (target == shooter || !(target instanceof LivingEntity) || !target.isAlive() || target.isSpectator() || !target.isPickable()) {
            return false;
        }
        return !Factions.sameSide(shooter, target);
    }

    /** Направление с разбросом — так же, как {@code Projectile.getMovementToShoot} у ванильных снарядов. */
    public static Vec3 spread(net.minecraft.util.RandomSource random, Vec3 dir, double inaccuracy) {
        return dir.normalize().add(random.triangle(0.0, 0.0172275 * inaccuracy), random.triangle(0.0, 0.0172275 * inaccuracy),
                random.triangle(0.0, 0.0172275 * inaccuracy));
    }
}
