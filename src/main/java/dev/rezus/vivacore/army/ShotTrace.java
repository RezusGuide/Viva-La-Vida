package dev.rezus.vivacore.army;

import dev.rezus.vivacore.compat.SableCompat;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Трассировка выстрела по блокам (задачи 7 и 9; тесты 24, 25): общая для мгновенных выстрелов ({@link Hitscan}) и
 * снарядов-сущностей ({@link OrionProjectile}).
 * <p>
 * Блоки ищет {@code level.clip}: его подменяет Sable, поэтому видны и блоки кораблей. Точка попадания в блок корабля
 * приходит в координатах участка корабля (plot) — она сразу переводится в мир ({@link SableCompat#toWorld});
 * {@code getBlockPos()} остаётся позицией в участке (по ней читается сам блок).
 * Сквозной выстрел (pierce != null) после лёгкого блока (тег vivacore:pierceable, не vivacore:blast_proof) повторяет
 * луч чуть дальше — от мировой точки, — пока есть запас; уже пробитый блок дважды не считается.
 */
public final class ShotTrace {
    private ShotTrace() {}

    /** Запас пробития сквозного выстрела. */
    public static final class Pierce {
        int left;
        final Set<BlockPos> pierced = new HashSet<>();

        public Pierce(int left) {
            this.left = left;
        }

        public Set<BlockPos> pierced() {
            return pierced;
        }
    }

    /** @return первый блок, который останавливает выстрел (точка — в мировых координатах), или null */
    public static BlockHitResult blocks(Level level, Entity context, Vec3 from, Vec3 to, Pierce pierce) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1e-6) return null;
        Vec3 step = dir.scale(0.3 / len);
        Vec3 cur = from;
        for (int i = 0; i < 32; i++) {
            BlockHitResult hit = level.clip(new ClipContext(cur, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
            if (hit.getType() == HitResult.Type.MISS) return null;
            BlockPos pos = hit.getBlockPos();
            Vec3 world = SableCompat.toWorld(level, hit.getLocation());
            if (pierce != null) {
                BlockState state = level.getBlockState(pos);
                boolean light = !state.is(OrionProjectile.BLAST_PROOF) && state.is(OrionProjectile.PIERCEABLE);
                if (light && (pierce.pierced.contains(pos) || pierce.left > 0)) {
                    if (pierce.pierced.add(pos.immutable())) pierce.left--;
                    cur = world.add(step);
                    if (cur.subtract(from).lengthSqr() >= len * len) return null;
                    continue;
                }
            }
            return world == hit.getLocation() ? hit : new BlockHitResult(world, hit.getDirection(), pos, hit.isInside());
        }
        return null;
    }
}
