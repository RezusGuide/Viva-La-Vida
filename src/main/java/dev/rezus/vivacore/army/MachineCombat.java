package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Оружие одной машины ОРИОНа (задача 7). Все числа — конфиг army.&lt;id&gt;.*.
 * Пули (очереди, пулемёт вертолёта, снайпер) — мгновенный выстрел {@link Hitscan} без сущности (задача 9, тест 38);
 * сущностями остаются ракеты, снаряд танка и сквозной снаряд тяжёлых — с лимитом {@link ArmyLimits}.
 * Стреляет только по своей цели (getTarget) при прямой видимости; тяжёлые взрывные снаряды при выключенном
 * vivaArmyGriefing становятся сквозными и могут бить через лёгкие блоки (не больше pierceBlocks).
 * <ul>
 *   <li>RIFLE / CANNON / MECH — очереди (у меха — поочерёдно из двух стволов);</li>
 *   <li>SNIPER — за laserTicks до выстрела на цель светит красный луч (синхронизируется клиенту), потом один выстрел;</li>
 *   <li>HELICOPTER — пулемёт очередями + ракеты по своей перезарядке (взрывные или сквозные);</li>
 *   <li>TANK — тяжёлый снаряд (взрывной или сквозной);</li>
 *   <li>SWARM — без снарядов: наниты бьют всех целей, касающихся роя, раз в cooldownTicks.</li>
 * </ul>
 */
public final class MachineCombat {
    private final OrionMachine m;
    private int cooldown;
    private int burstLeft;
    private int burstTimer;
    private int muzzleIndex;
    private int laserTimer;
    private int rocketCooldown;
    private int rocketPod;
    /** Для тестов: сколько выстрелов сделано. */
    int shotsFired;

    MachineCombat(OrionMachine m) {
        this.m = m;
    }

    void tick() {
        MachineType t = m.machine();
        MachineType.Weapon weapon = t.weapon();
        if (weapon == MachineType.Weapon.NONE) return;
        if (cooldown > 0) cooldown--;
        if (rocketCooldown > 0) rocketCooldown--;
        if (weapon == MachineType.Weapon.SWARM) {
            swarmTick(t);
            return;
        }
        LivingEntity target = m.getTarget();
        if (target == null || !target.isAlive() || !Factions.isTarget(m, target)
                || m.distanceTo(target) > VivaConfig.machineFireRange(t) * 1.5 + 4) {
            stopLaser();
            burstLeft = 0;
            return;
        }
        switch (weapon) {
            case SNIPER -> sniperTick(t, target);
            case TANK -> {
                boolean griefing = ArmyRules.griefing(m.level());
                if (cooldown <= 0 && canShoot(target, !griefing)) {
                    fire(target, 0, griefing ? OrionProjectile.Kind.SHELL : OrionProjectile.Kind.PIERCING,
                            (float) VivaConfig.machineDamage(t), (float) VivaConfig.tankShellPower(),
                            VivaConfig.machineProjectileSpeed(t), VivaConfig.machineSpread(t), "shoot");
                    cooldown = VivaConfig.machineCooldown(t);
                }
            }
            case HELICOPTER -> {
                boolean griefing = ArmyRules.griefing(m.level());
                if (rocketCooldown <= 0 && canShoot(target, !griefing)) {
                    rocketPod = rocketPod == 1 ? 2 : 1;
                    fire(target, rocketPod, griefing ? OrionProjectile.Kind.ROCKET : OrionProjectile.Kind.PIERCING,
                            (float) VivaConfig.heliRocketDamage(), (float) VivaConfig.heliRocketPower(),
                            VivaConfig.heliRocketSpeed(), VivaConfig.machineSpread(t), "rocket");
                    rocketCooldown = VivaConfig.heliRocketCooldown();
                }
                burstTick(t, target, 0);
            }
            default -> burstTick(t, target, -1);
        }
    }

    /** Очереди: muzzle ≥ 0 — всегда этот ствол, −1 — по очереди все стволы модели. */
    private void burstTick(MachineType t, LivingEntity target, int muzzle) {
        if (burstLeft > 0) {
            if (--burstTimer <= 0) {
                if (canShoot(target, false)) {
                    int idx = muzzle >= 0 ? muzzle : (muzzleIndex++ % t.muzzles().length);
                    fire(target, idx, OrionProjectile.Kind.BULLET, (float) VivaConfig.machineDamage(t), 0f,
                            VivaConfig.machineProjectileSpeed(t), VivaConfig.machineSpread(t), t.shootAnim());
                }
                burstLeft--;
                burstTimer = VivaConfig.machineBurstGap(t);
            }
        } else if (cooldown <= 0 && canShoot(target, false)) {
            burstLeft = VivaConfig.machineBurst(t);
            burstTimer = 0;
            cooldown = VivaConfig.machineCooldown(t);
        }
    }

    private void sniperTick(MachineType t, LivingEntity target) {
        if (laserTimer > 0) {
            m.setLaserTarget(target.getId());
            if (--laserTimer == 0) {
                if (canShoot(target, false)) {
                    fire(target, 0, OrionProjectile.Kind.BULLET, (float) VivaConfig.machineDamage(t), 0f,
                            VivaConfig.machineProjectileSpeed(t), VivaConfig.machineSpread(t), t.shootAnim());
                }
                cooldown = VivaConfig.machineCooldown(t);
                stopLaser();
            }
        } else if (cooldown <= 0 && canShoot(target, false)) {
            laserTimer = VivaConfig.sniperLaserTicks();
            m.setLaserTarget(target.getId());
        }
    }

    private void stopLaser() {
        laserTimer = 0;
        m.setLaserTarget(-1);
    }

    private void swarmTick(MachineType t) {
        if (cooldown > 0) return;
        cooldown = VivaConfig.machineCooldown(t);
        double r = VivaConfig.swarmAuraRadius();
        for (LivingEntity e : m.level().getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(r),
                e -> Factions.isTarget(m, e))) {
            e.hurt(m.damageSources().mobAttack(m), (float) VivaConfig.machineDamage(t));
            shotsFired++;
        }
    }

    /** Тяжёлые взрывные (танк, ракеты вертолёта) при выключенном vivaArmyGriefing — сквозные: видят цель за лёгкой стеной. */
    public boolean piercingCapable() {
        MachineType.Weapon w = m.machine().weapon();
        return (w == MachineType.Weapon.TANK || w == MachineType.Weapon.HELICOPTER) && !ArmyRules.griefing(m.level());
    }

    /** Цель достижима для оружия: прямая видимость или (для сквозных) только лёгкие блоки между. */
    public boolean canEngage(LivingEntity target) {
        return canShoot(target, piercingCapable());
    }

    /** Прямая видимость; allowPierce — ещё и сквозь лёгкие блоки (не больше pierceBlocks, без blast_proof). */
    boolean canShoot(LivingEntity target, boolean allowPierce) {
        if (m.hasLineOfSight(target)) return true;
        return allowPierce && pierceable(m.level(), m.getEyePosition(), target.getEyePosition(), VivaConfig.armyPierceBlocks());
    }

    /** Между from и to только лёгкие блоки (vivacore:pierceable, не blast_proof), не больше max. */
    static boolean pierceable(BlockGetter level, Vec3 from, Vec3 to, int max) {
        int[] count = {0};
        Boolean blocked = BlockGetter.traverseBlocks(from, to, level, (lvl, pos) -> {
            BlockState state = lvl.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(lvl, pos);
            if (shape.isEmpty() || shape.clip(from, to, pos) == null) return null;
            if (state.is(OrionProjectile.BLAST_PROOF) || !state.is(OrionProjectile.PIERCEABLE)) return Boolean.TRUE;
            return ++count[0] > max ? Boolean.TRUE : null;
        }, lvl -> Boolean.FALSE);
        return !blocked;
    }

    /** Мировая позиция ствола index (модель смотрит в −Z, +X — правая сторона модели). */
    Vec3 muzzle(int index) {
        float[][] all = m.machine().muzzles();
        float[] p = all[Math.max(0, Math.min(all.length - 1, index))];
        float yaw = m.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        double up = (p[1] + m.machine().modelOffsetPx()) / 16.0;
        return m.position().add(right.scale(p[0] / 16.0)).add(0, up, 0).add(forward.scale(-p[2] / 16.0));
    }

    /** Позиция ствола на клиенте с интерполяцией (для луча снайпера). */
    public Vec3 muzzleAt(float partialTick) {
        Vec3 now = muzzle(0);
        return now.subtract(m.position()).add(m.getPosition(partialTick));
    }

    private void fire(LivingEntity target, int muzzleIdx, OrionProjectile.Kind kind, float damage, float power,
                      double speed, double spread, String anim) {
        if (!(m.level() instanceof ServerLevel level)) return;
        Vec3 from = muzzle(muzzleIdx);
        Vec3 aim = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        Vec3 dir = aim.subtract(from);
        if (kind == OrionProjectile.Kind.BULLET && VivaConfig.armyHitscanBullets()) {
            // пули — мгновенный выстрел без сущности (задача 9, тест 38)
            MachineType t = m.machine();
            byte tracer = t.weapon() == MachineType.Weapon.SNIPER ? ArmyNetwork.KIND_SNIPER : ArmyNetwork.KIND_BULLET;
            Hitscan.fire(level, m, from, Hitscan.spread(m.getRandom(), dir, spread),
                    VivaConfig.machineFireRange(t) * VivaConfig.armyHitscanRangeFactor(), damage, null, tracer);
        } else {
            // ракеты, снаряд танка, сквозной снаряд тяжёлых — сущности, но не больше лимита на участок
            if (!ArmyLimits.canSpawnProjectile(level, from)) return;
            OrionProjectile p = OrionProjectile.create(level, m, kind, damage, power);
            p.setPos(from);
            p.shoot(dir.x, dir.y, dir.z, (float) speed, (float) spread);
            level.addFreshEntity(p);
            level.sendParticles(ArmyParticles.MUZZLE_FLASH.get(), from.x, from.y, from.z, 1, 0, 0, 0, 0);
        }
        if (anim != null) {
            m.triggerAnim("attack", anim);
        }
        shotsFired++;
    }

    /** Для тестов. */
    public int shotsFired() {
        return shotsFired;
    }
}
