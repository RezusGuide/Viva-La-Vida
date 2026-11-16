package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Дрон-разведчик «Мейсса» (заметка «16 Враги-армия», тест 24 — первый шаг армии).
 * Пока только проверка пути «Blockbench → мод → игра»: летает, бесцельно кружит у точки появления,
 * ни на кого не нападает (нет целей, нет атаки). Сам не спавнится — только яйцом или командой.
 * <p>
 * Модель (GeckoLib, assets/vivacore/geo/entity/scout_drone.geo.json) смотрит в −Z — как и сущности Minecraft:
 * GeoEntityRenderer поворачивает модель на (180 − yBodyRot), поэтому −Z модели = направление взгляда сущности.
 * К костям по именам код не обращается (arm_left в модели стоит на +X, то есть справа — имена перепутаны).
 */
public class ScoutDrone extends PathfinderMob implements GeoEntity {
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.scout_drone.fly");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.scout_drone.idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Точка, вокруг которой кружит дрон. Задаётся при появлении, сохраняется в NBT. */
    @Nullable
    private BlockPos home;
    private boolean configApplied;

    public ScoutDrone(EntityType<? extends ScoutDrone> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    /** Базовые атрибуты. Числа из конфига ставятся позже ({@link #applyConfig}) — конфиг сервера ещё не загружен. */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new HoverAroundHomeGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 12.0f));
        // целей (targetSelector) нет — дрон никого не атакует
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        home = blockPosition();
        applyConfig(true);
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            if (!configApplied) applyConfig(false);
            if (home == null) home = blockPosition();
        }
    }

    /** Здоровье и скорость из конфига army.scoutDrone. */
    private void applyConfig(boolean heal) {
        configApplied = true;
        setBase(Attributes.MAX_HEALTH, VivaConfig.scoutDroneMaxHealth());
        setBase(Attributes.FLYING_SPEED, VivaConfig.scoutDroneFlyingSpeed());
        setBase(Attributes.MOVEMENT_SPEED, VivaConfig.scoutDroneFlyingSpeed() / 2);
        if (heal || getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }

    private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) instance.setBaseValue(value);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    // --- полёт: без урона от падения, без шагов по земле ---

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // пока дрон — тестовая сущность: не исчезает. Когда появятся патрули, решим заново
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("vivacore:home", NbtUtils.writeBlockPos(home));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "vivacore:home").ifPresent(p -> home = p);
    }

    // --- GeckoLib ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            // «fly», когда дрон движется, «idle», когда висит. Позиция между тиками надёжнее limbSwing у летающих
            double moved = state.getAnimatable().position().distanceToSqr(
                    state.getAnimatable().xo, state.getAnimatable().yo, state.getAnimatable().zo);
            return state.setAndContinue(moved > 1.0e-4 ? FLY : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** Кружит у точки появления: выбирает случайную точку в радиусе и высоте из конфига и летит туда. */
    static final class HoverAroundHomeGoal extends Goal {
        private final ScoutDrone drone;

        HoverAroundHomeGoal(ScoutDrone drone) {
            this.drone = drone;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return drone.getNavigation().isDone() && drone.getRandom().nextInt(reducedTickDelay(20)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return drone.getNavigation().isInProgress();
        }

        @Override
        public void start() {
            Vec3 target = pickTarget();
            if (target != null) {
                drone.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
            }
        }

        @Nullable
        private Vec3 pickTarget() {
            BlockPos home = drone.home != null ? drone.home : drone.blockPosition();
            int radius = VivaConfig.scoutDroneHoverRadius();
            int height = VivaConfig.scoutDroneHoverHeight();
            for (int attempt = 0; attempt < 10; attempt++) {
                double angle = drone.getRandom().nextDouble() * Math.PI * 2;
                double dist = drone.getRandom().nextDouble() * radius;
                int x = Mth.floor(home.getX() + Math.cos(angle) * dist);
                int z = Mth.floor(home.getZ() + Math.sin(angle) * dist);
                int y = home.getY() + drone.getRandom().nextInt(height * 2 + 1) - height;
                BlockPos pos = new BlockPos(x, y, z);
                if (drone.level().isEmptyBlock(pos) && drone.level().isEmptyBlock(pos.above())) {
                    return Vec3.atCenterOf(pos);
                }
            }
            return null;
        }
    }

    /** Для GameTest: цели нет и быть не может. */
    public boolean hasNoTarget() {
        return getTarget() == null && targetSelector.getAvailableGoals().isEmpty();
    }

}
