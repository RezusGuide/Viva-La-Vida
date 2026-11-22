package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import java.util.HashSet;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
    private int pierceLeft;
    private final Set<BlockPos> pierced = new HashSet<>();

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
        p.pierceLeft = kind == Kind.PIERCING ? VivaConfig.armyPierceBlocks() : 0;
        return p;
    }

    public Kind kind() {
        return Kind.values()[Math.max(0, Math.min(Kind.values().length - 1, entityData.get(KIND)))];
    }

    public float damage() {
        return damage;
    }

    public Set<BlockPos> piercedBlocks() {
        return Set.copyOf(pierced);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
    }

    @Override
    public void tick() {
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

    /**
     * Блоки на пути from→to через level.clip — его подменяет Sable (clip_overwrite), поэтому снаряд видит и блоки
     * кораблей. Сквозной снаряд после лёгкого блока (pierceable, не blast_proof) повторяет луч чуть дальше, пока есть
     * запас; уже пробитый блок не считается дважды.
     * @return первый блок, который останавливает снаряд, или null
     */
    BlockHitResult traceBlocks(Level level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1e-6) return null;
        Vec3 step = dir.scale(0.3 / len);
        Vec3 cur = from;
        for (int i = 0; i < 32; i++) {
            BlockHitResult hit = level.clip(new ClipContext(cur, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.MISS) return null;
            BlockPos pos = hit.getBlockPos();
            if (kind() == Kind.PIERCING) {
                BlockState state = level.getBlockState(pos);
                boolean light = !state.is(BLAST_PROOF) && state.is(PIERCEABLE);
                if (light && (pierced.contains(pos) || pierceLeft > 0)) {
                    if (pierced.add(pos.immutable())) pierceLeft--;
                    cur = hit.getLocation().add(step);
                    if (cur.subtract(from).lengthSqr() >= len * len) return null;
                    continue;
                }
            }
            return hit;
        }
        return null;
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

    private void hitBlock(BlockHitResult hit) {
        setPos(hit.getLocation());
        if (kind() == Kind.SHELL || kind() == Kind.ROCKET) {
            explode(hit.getLocation());
        } else {
            sparks(hit.getLocation(), 5);
        }
        discard();
    }

    private void explode(Vec3 at) {
        boolean griefing = ArmyRules.griefing(level());
        level().explode(this, damageSources().explosion(this, getOwner()), null, at.x, at.y, at.z, explosionPower, false,
                griefing ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
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
        tag.putInt("PierceLeft", pierceLeft);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getInt("Kind"));
        damage = tag.getFloat("Damage");
        explosionPower = tag.getFloat("ExplosionPower");
        pierceLeft = tag.getInt("PierceLeft");
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }
}
