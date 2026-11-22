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
    /** Оружие (задача 7). */
    final MachineCombat combat = new MachineCombat(this);
    /** Цель лазера снайпера (id сущности, −1 — нет) — синхронизируется клиенту, рисует луч. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> LASER_TARGET =
            net.minecraft.network.syncher.SynchedEntityData.defineId(OrionMachine.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);

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
        goalSelector.addGoal(1, new ApproachTargetGoal(this));
        goalSelector.addGoal(2, new PatrolGoal(this));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        if (machine.weapon() != MachineType.Weapon.NONE) {
            targetSelector.addGoal(1, new FactionTargetGoal(this));
        }
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LASER_TARGET, -1);
    }

    public int laserTarget() {
        return entityData.get(LASER_TARGET);
    }

    void setLaserTarget(int id) {
        if (entityData.get(LASER_TARGET) != id) entityData.set(LASER_TARGET, id);
    }

    public MachineCombat combat() {
        return combat;
    }

    /** Щит тяжёлого штурмовика: урон спереди (сектор shieldHalfAngle) умножается на frontDamageMultiplier. */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (machine.shielded() && isHitFromFront(source)) {
            amount *= (float) VivaConfig.heavyFrontMultiplier();
        }
        return super.hurt(source, amount);
    }

    public boolean isHitFromFront(net.minecraft.world.damagesource.DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null) return false;
        Vec3 to = from.subtract(position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-6) return false;
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        double cos = forward.dot(to.normalize());
        return cos >= Math.cos(Math.toRadians(VivaConfig.heavyShieldAngle()));
    }

    /**
     * Смерть: обломки и искры; если убийца — моб Spore или заражённая машина, с шансом army.infection.convertChance
     * на месте машины ОРИОНа встаёт её заражённая версия (дрон, лёгкая и тяжёлая пехота, мех).
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel server)) return;
        server.sendParticles(ArmyParticles.DEBRIS.get(), getX(), getY(0.5), getZ(), 14,
                getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.15);
        server.sendParticles(ArmyParticles.SPARK.get(), getX(), getY(0.5), getZ(), 10,
                getBbWidth() * 0.3, getBbHeight() * 0.3, getBbWidth() * 0.3, 0.1);
        MachineType variant = machine.infectedVariant();
        net.minecraft.world.entity.Entity killer = source.getEntity();
        if (variant != null && !machine.infected && (Factions.isSporeMob(killer) || Factions.isInfected(killer))
                && getRandom().nextDouble() < VivaConfig.armyInfectionChance()) {
            OrionMachine infected = ArmyEntities.type(variant).create(server);
            if (infected != null) {
                infected.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
                infected.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.CONVERSION, null);
                server.addFreshEntity(infected);
                lastConversion = infected;
            }
        }
    }

    /** Для тестов: заражённая машина, вставшая на месте этой. */
    @Nullable
    public OrionMachine lastConversion;

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
        if (!isNoAi() && isAlive()) {
            combat.tick();
        } else {
            setLaserTarget(-1);
        }
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
        // выстрелы: сервер вызывает triggerAnim("attack", "shoot" | "rocket"), GeckoLib передаёт клиентам
        AnimationController<OrionMachine> attack = new AnimationController<>(this, "attack", 0, state -> PlayState.STOP);
        if (machine.shootAnim() != null) {
            attack.triggerableAnim(machine.shootAnim(), RawAnimation.begin().thenPlay(machine.anim(machine.shootAnim())));
        }
        if (machine.weapon() == MachineType.Weapon.HELICOPTER) {
            attack.triggerableAnim("rocket", RawAnimation.begin().thenPlay(machine.anim("rocket")));
        }
        controllers.add(attack);
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

    /** Есть цель — повернуться к ней и держаться на дистанции «огня» (подходить, если дальше). */
    static final class ApproachTargetGoal extends Goal {
        private final OrionMachine m;
        private int repath;

        ApproachTargetGoal(OrionMachine m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return m.getTarget() != null && m.getTarget().isAlive();
        }

        @Override
        public void stop() {
            m.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            var target = m.getTarget();
            if (target == null) return;
            m.getLookControl().setLookAt(target, 30f, 30f);
            if (m.machine.movement == MachineType.Movement.GROUND || m.machine.movement == MachineType.Movement.FLYING) {
                m.setYBodyRot(m.yHeadRot);
            }
            double fire = VivaConfig.machineFireRange(m.machine);
            double dist = m.distanceTo(target);
            if (m.machine.weapon() == MachineType.Weapon.SWARM) {
                // рой — облако: летит прямо на цель без поиска пути (путь до тела цели часто не строится)
                m.getNavigation().stop();
                m.getMoveControl().setWantedPosition(target.getX(), target.getY(0.5), target.getZ(), 1.0);
                return;
            }
            if (dist > fire || !m.combat.canEngage(target)) {
                if (--repath <= 0) {
                    repath = 10;
                    // летающие держатся выше цели; рой — прямо на цель (наниты бьют при касании)
                    boolean above = m.machine.movement == MachineType.Movement.FLYING && m.machine.weapon() != MachineType.Weapon.SWARM;
                    double y = above ? target.getY() + VivaConfig.armyPatrolHeight() : target.getY(0.3);
                    m.getNavigation().moveTo(target.getX(), y, target.getZ(), 1.0);
                }
            } else {
                m.getNavigation().stop();
            }
        }
    }

    /** Выбор цели по спискам сторон (army.targets.*): ближайшая видимая в дальности обнаружения. Раз в 10 тиков. */
    static final class FactionTargetGoal extends Goal {
        private final OrionMachine m;

        FactionTargetGoal(OrionMachine m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            // цели Mob оценивает только в тики с чётным (tickCount + id) — проверка с учётом id, иначе у машин
            // с нечётным id выбор цели не срабатывал бы никогда
            if ((m.tickCount + m.getId()) % 10 != 0) return false;
            var current = m.getTarget();
            if (current != null && current.isAlive() && Factions.isTarget(m, current)
                    && m.distanceTo(current) <= VivaConfig.machineDetectRange(m.machine) * 1.25) {
                return false;
            }
            double range = VivaConfig.machineDetectRange(m.machine);
            net.minecraft.world.entity.LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (var e : m.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    m.getBoundingBox().inflate(range), e -> Factions.isTarget(m, e))) {
                double d = m.distanceToSqr(e);
                if (d < bestDist && d <= range * range && (m.getSensing().hasLineOfSight(e) || m.combat.canEngage(e))) {
                    best = e;
                    bestDist = d;
                }
            }
            m.setTarget(best);
            return false;
        }
    }
}
