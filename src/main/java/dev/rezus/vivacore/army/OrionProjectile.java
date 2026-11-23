package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Снаряд машин ОРИОНа (задача 7). Летит по прямой без гравитации, живёт 100 тиков.
 * <ul>
 *   <li>BULLET — пуля: бьёт сущность, о блок гаснет (искры). Блоки не ломает и сквозь них не летит.</li>
 *   <li>PIERCING — сквозная (правило vivaArmyGriefing выключено): проходит до army.griefing.pierceBlocks
 *       лёгких блоков (тег vivacore:pierceable) и бьёт того, кто за стеной; блоки vivacore:blast_proof
 *       и все прочие её останавливают. Блоки не ломает.</li>
 *   <li>SHELL / ROCKET — взрывной (снаряд танка / ракета вертолёта): обычный ванильный взрыв с ломанием блоков
 *       (ExplosionInteraction.TNT — от mobGriefing не зависит; разрешает его наше правило vivaArmyGriefing).
 *       Ванильный взрыв проходит через ExplosionEvent.Detonate — приват OPAC вычищает из него свои блоки.</li>
 * </ul>
 * Своих (та же сторона) не задевает.
 */
public class OrionProjectile extends Projectile {
    public enum Kind { BULLET, PIERCING, SHELL, ROCKET }

    public static final TagKey<Block> PIERCEABLE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "pierceable"));
    public static final TagKey<Block> BLAST_PROOF = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "blast_proof"));

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(OrionProjectile.class, EntityDataSerializers.INT);
    private static final int LIFETIME = 100;

    private float damage;
    private float explosionPower;
    private ShotTrace.Pierce pierce = new ShotTrace.Pierce(0);
    /** Участок, в котором снаряд учтён в лимите ArmyLimits (null — не учтён). */
    private String countedSector;

    public OrionProjectile(EntityType<? extends OrionProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static OrionProjectile create(Level level, LivingEntity owner, Kind kind, float damage, float explosionPower) {
        OrionProjectile p = new OrionProjectile(ArmyEntities.PROJECTILE.get(), level);
        p.setOwner(owner);
        p.entityData.set(KIND, kind.ordinal());
        p.damage = damage;
        p.explosionPower = explosionPower;
        p.pierce = new ShotTrace.Pierce(kind == Kind.PIERCING ? VivaConfig.armyPierceBlocks() : 0);
        return p;
    }

    public Kind kind() {
        return Kind.values()[Math.max(0, Math.min(Kind.values().length - 1, entityData.get(KIND)))];
    }

    public float damage() {
        return damage;
    }

    public Set<BlockPos> piercedBlocks() {
        return Set.copyOf(pierce.pierced());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
    }

    /** Замер (только dev-бенч): суммарное время тиков снарядов. */
    public static long tickNanos;

    @Override
    public void tick() {
        long t0 = Hitscan.STATS ? System.nanoTime() : 0;
        try {
            tickInner();
        } finally {
            if (Hitscan.STATS) tickNanos += System.nanoTime() - t0;
        }
    }

    private void tickInner() {
        super.tick();
        Vec3 from = position();
        Vec3 velocity = getDeltaMovement();
        Vec3 to = from.add(velocity);
        if (!level().isClientSide()) {
            if (tickCount > LIFETIME) {
                discard();
                return;
            }
            BlockHitResult blockHit = traceBlocks(level(), from, to);
            Vec3 end = blockHit != null ? blockHit.getLocation() : to;
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, from, end,
                    getBoundingBox().expandTowards(velocity).inflate(1.0), this::canHitEntity);
            if (entityHit != null) {
                hitEntity(entityHit);
                return;
            }
            if (blockHit != null) {
                hitBlock(blockHit);
                return;
            }
        }
        setPos(to);
        if (velocity.horizontalDistanceSqr() > 1e-7) {
            setYRot((float) (Math.atan2(velocity.x, velocity.z) * (180 / Math.PI)));
        }
    }

    /** Блоки на пути from→to ({@link ShotTrace}: видит корабли Sable, точка — в мире). @return первый стоп-блок или null */
    BlockHitResult traceBlocks(Level level, Vec3 from, Vec3 to) {
        return ShotTrace.blocks(level, this, from, to, kind() == Kind.PIERCING ? pierce : null);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        if (owner != null && (target == owner || Factions.sameSide(owner, target))) return false;
        return super.canHitEntity(target) && target instanceof LivingEntity;
    }

    private void hitEntity(EntityHitResult hit) {
        setPos(hit.getLocation());
        Entity owner = getOwner();
        hit.getEntity().hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), damage);
        if (kind() == Kind.SHELL || kind() == Kind.ROCKET) {
            explode(hit.getLocation());
        } else {
            sparks(hit.getLocation(), 3);
        }
        discard();
    }

    /** Для тестов: блок, о который снаряд остановился (для кораблей Sable — позиция в участке). */
    BlockPos lastBlockHit;

    private void hitBlock(BlockHitResult hit) {
        lastBlockHit = hit.getBlockPos().immutable();
        setPos(hit.getLocation());
        if (kind() == Kind.SHELL || kind() == Kind.ROCKET) {
            explode(hit.getLocation());
        } else {
            sparks(hit.getLocation(), 5);
        }
        discard();
    }

    /** Для тестов: взрыв этого снаряда в точке (с лимитом за тик). */
    void explodeAt(Vec3 at) {
        explode(at);
    }

    private void explode(Vec3 at) {
        if (level() instanceof ServerLevel server && !ArmyLimits.tryExplosion(server)) {
            damageOnly(server, at);
            return;
        }
        boolean griefing = ArmyRules.griefing(level());
        level().explode(this, damageSources().explosion(this, getOwner()), null, at.x, at.y, at.z, explosionPower, false,
                griefing ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    /**
     * Взрыв сверх лимита за тик (ArmyLimits): только урон сущностям в радиусе взрыва (2 × мощность), по ванильной
     * формуле без лучей видимости — без блоков, частиц, звука и отталкивания.
     */
    private void damageOnly(ServerLevel server, Vec3 at) {
        double radius = explosionPower * 2.0;
        if (radius <= 0) return;
        var source = damageSources().explosion(this, getOwner());
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius))) {
            double dist = Math.sqrt(e.distanceToSqr(at)) / radius;
            if (dist > 1.0) continue;
            double impact = 1.0 - dist;
            e.hurt(source, (float) ((impact * impact + impact) / 2.0 * 7.0 * radius + 1.0));
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (level() instanceof ServerLevel server && countedSector == null) {
            countedSector = ArmyLimits.sector(server, position());
            ArmyLimits.projectileAdded(countedSector);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (countedSector != null) {
            ArmyLimits.projectileRemoved(countedSector);
            countedSector = null;
        }
    }

    private void sparks(Vec3 at, int count) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ArmyParticles.SPARK.get(), at.x, at.y, at.z, count, 0.1, 0.1, 0.1, 0.05);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", entityData.get(KIND));
        tag.putFloat("Damage", damage);
        tag.putFloat("ExplosionPower", explosionPower);
        tag.putInt("PierceLeft", pierce.left);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getInt("Kind"));
        damage = tag.getFloat("Damage");
        explosionPower = tag.getFloat("ExplosionPower");
        pierce = new ShotTrace.Pierce(tag.getInt("PierceLeft"));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }
}
