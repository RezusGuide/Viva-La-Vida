package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.orion.Orion;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Машина ОРИОНа (заметка «16 Враги-армия»). Общая основа всех машин: наземные — этот класс, летающие —
 * {@link FlyingMachine}, страж ядра — этот же класс с движением STATIC (стоит, смотрит на игрока; фазы — позже).
 * <p>
 * Поведение пока простое (без оружия — задача 7): патрулирует вокруг точки появления; заметив игрока (не в
 * творческом режиме и не наблюдателя), поворачивается к нему и подходит на дистанцию «огня» (конфиг), но не атакует.
 * После отмены программы ОРИОНа незаражённые машины «засыпают» (NoAI); заражённых ведёт заражение — они не спят.
 * Тяжёлые без игроков рядом исчезают через army.limits.heavyDespawnSeconds.
 * <p>
 * Анимации по состоянию: смерть → death; сон → стоп; движется → move (walk/move/fly); иначе → idle (idle/hover).
 * К костям модели по именам код не обращается.
 */
public class OrionMachine extends PathfinderMob implements GeoEntity {
    protected final MachineType machine;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation animMove, animIdle;
    @Nullable
    private final RawAnimation animDeath;

    /** Точка, вокруг которой машина патрулирует. Задаётся при появлении, сохраняется в NBT. */
    @Nullable
    protected BlockPos home;
    private boolean configApplied;
    /** Машину усыпила отмена программы ОРИОНа (а не NoAI из команды) — разбудить можно только такую. */
    private boolean sleptByOrion;
    private int noPlayerTicks;

    public OrionMachine(EntityType<? extends OrionMachine> type, Level level, MachineType machine) {
        super(type, level);
        this.machine = machine;
        this.animMove = RawAnimation.begin().thenLoop(machine.anim(machine.animMove));
        this.animIdle = RawAnimation.begin().thenLoop(machine.anim(machine.animIdle));
        this.animDeath = machine.animDeath == null ? null : RawAnimation.begin().thenPlayAndHold(machine.anim(machine.animDeath));
        this.xpReward = 5;
        addMachineGoals();
    }

    public MachineType machine() {
        return machine;
    }

    /** Базовые атрибуты. Числа конфига ставятся при появлении ({@link #applyConfig}) — конфиг сервера ещё не загружен. */
    public static AttributeSupplier.Builder createAttributes(MachineType t) {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, t.defaultHealth)
                .add(Attributes.MOVEMENT_SPEED, Math.max(t.defaultSpeed, 0.01))
                .add(Attributes.FLYING_SPEED, Math.max(t.defaultSpeed, 0.01))
                .add(Attributes.ARMOR, t.defaultArmor)
                .add(Attributes.FOLLOW_RANGE, Math.max(t.defaultDetectRange, 16))
                .add(Attributes.KNOCKBACK_RESISTANCE, t.heavy || t.boss() ? 1.0 : 0.2);
    }

    /** registerGoals вызывается из конструктора Mob раньше, чем присвоено поле machine, — цели добавляем здесь. */
    @Override
    protected void registerGoals() {
    }

    private void addMachineGoals() {
        if (machine.movement == MachineType.Movement.STATIC) {
            goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 24f));
            return;
        }
        goalSelector.addGoal(1, new ApproachPlayerGoal(this));
        goalSelector.addGoal(2, new PatrolGoal(this));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        // целей атаки (targetSelector) нет — оружие появится в задаче 7
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        home = blockPosition();
        applyConfig(true);
        if (machine.boss()) {
            setPersistenceRequired();
        }
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    /** Здоровье, скорость, броня и дальность из конфига army.&lt;id&gt;. */
    protected void applyConfig(boolean heal) {
        configApplied = true;
        setBase(Attributes.MAX_HEALTH, VivaConfig.machineHealth(machine));
        double speed = Math.max(VivaConfig.machineSpeed(machine), 0.0);
        setBase(Attributes.MOVEMENT_SPEED, machine.movement == MachineType.Movement.FLYING ? speed / 2 : speed);
        setBase(Attributes.FLYING_SPEED, speed);
        setBase(Attributes.ARMOR, VivaConfig.machineArmor(machine));
        setBase(Attributes.FOLLOW_RANGE, Math.max(VivaConfig.machineDetectRange(machine), 1));
        if (heal || getHealth() > getMaxHealth()) setHealth(getMaxHealth());
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) instance.setBaseValue(value);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (!configApplied) applyConfig(false);
        if (home == null) home = blockPosition();
        if (tickCount % 20 == 0) {
            updateSleep();
            tickHeavyDespawn();
        }
    }

    /** Отмена программы ОРИОНа усыпляет незаражённые машины; сброс флага (оператор) — будит их. */
    protected void updateSleep() {
        if (machine.infected || !(level() instanceof ServerLevel server)) return;
        boolean cancelled = Orion.isProgramCancelled(server.getServer());
        if (cancelled && !isNoAi()) {
            sleptByOrion = true;
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            setNoAi(true);
        } else if (!cancelled && sleptByOrion) {
            sleptByOrion = false;
            setNoAi(false);
        }
    }

    public boolean isAsleep() {
        return isNoAi();
    }

    private void tickHeavyDespawn() {
        if (!machine.heavy) return;
        boolean player = level().getNearestPlayer(this, 64) != null;
        noPlayerTicks = player ? 0 : noPlayerTicks + 20;
        if (noPlayerTicks >= VivaConfig.armyHeavyDespawn() * 20) {
            discard();
        }
    }

    // ---------------------------------------------------------------- смерть с анимацией

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= machine.deathTicks && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60); // облачко, как у ванильной смерти
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !machine.boss();
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("vivacore:home", NbtUtils.writeBlockPos(home));
        tag.putBoolean("vivacore:slept_by_orion", sleptByOrion);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "vivacore:home").ifPresent(p -> home = p);
        sleptByOrion = tag.getBoolean("vivacore:slept_by_orion");
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    /** Ближайший игрок, которого машина замечает (не творческий режим, не наблюдатель, в дальности обнаружения). */
    @Nullable
    public Player noticedPlayer() {
        double range = VivaConfig.machineDetectRange(machine);
        Player p = level().getNearestPlayer(getX(), getY(), getZ(), range, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        return p != null && getSensing().hasLineOfSight(p) ? p : null;
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            OrionMachine m = state.getAnimatable();
            if (m.isDeadOrDying()) {
                return m.animDeath != null ? state.setAndContinue(m.animDeath) : PlayState.STOP;
            }
            if (m.isNoAi()) {
                return PlayState.STOP;
            }
            double moved = m.position().distanceToSqr(m.xo, m.yo, m.zo);
            return state.setAndContinue(moved > 1.0e-4 ? m.animMove : m.animIdle);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ---------------------------------------------------------------- цели

    /** Целевая высота над землёй для патруля (летающие переопределяют). */
    protected double patrolY(int x, int z) {
        return level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Патруль: случайная точка в радиусе army.patrolRadius вокруг точки появления. */
    static final class PatrolGoal extends Goal {
        private final OrionMachine m;

        PatrolGoal(OrionMachine m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return m.getNavigation().isDone() && m.getRandom().nextInt(reducedTickDelay(40)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return m.getNavigation().isInProgress();
        }

        @Override
        public void start() {
            BlockPos center = m.home != null ? m.home : m.blockPosition();
            int radius = VivaConfig.armyPatrolRadius();
            double angle = m.getRandom().nextDouble() * Math.PI * 2;
            double dist = m.getRandom().nextDouble() * radius;
            int x = Mth.floor(center.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(center.getZ() + Math.sin(angle) * dist);
            m.getNavigation().moveTo(x + 0.5, m.patrolY(x, z), z + 0.5, 0.8);
        }
    }

    /** Заметил игрока — повернуться к нему и подойти на дистанцию «огня». Не атакует. */
    static final class ApproachPlayerGoal extends Goal {
        private final OrionMachine m;
        @Nullable
        private Player player;
        private int repath;

        ApproachPlayerGoal(OrionMachine m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (m.tickCount % 10 != 0) return false;
            player = m.noticedPlayer();
            return player != null;
        }

        @Override
        public boolean canContinueToUse() {
            return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator()
                    && m.distanceTo(player) <= VivaConfig.machineDetectRange(m.machine) * 1.25;
        }

        @Override
        public void stop() {
            player = null;
            m.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (player == null) return;
            m.getLookControl().setLookAt(player, 30f, 30f);
            double fire = VivaConfig.machineFireRange(m.machine);
            double dist = m.distanceTo(player);
            if (dist > fire) {
                if (--repath <= 0) {
                    repath = 10;
                    double y = m.machine.movement == MachineType.Movement.FLYING
                            ? player.getY() + VivaConfig.armyPatrolHeight() : player.getY();
                    m.getNavigation().moveTo(player.getX(), y, player.getZ(), 1.0);
                }
            } else {
                m.getNavigation().stop();
            }
        }
    }
}
