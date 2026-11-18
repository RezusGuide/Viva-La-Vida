package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Волны подкрепления (заметка «16 Враги-армия», раздел 3; тест 24).
 * Игрок пробыл в участке army.waves.delaySeconds при тревоге ≥ threshold → волна по уровню тревоги (список из конфига).
 * За warningSeconds до волны — звук и сообщение. Лимиты: всего машин ≤ maxMachines, тяжёлых на группу ≤ maxHeavyPerGroup.
 * Появляются у края видимости игрока (spawnDistance, перед ним), не в блоках и не над водой, лицом к игроку.
 */
public final class Waves {
    private Waves() {}

    public enum Event { NONE, WARNED, SPAWNED, BLOCKED }

    private static final class State {
        String sector = "";
        int ticks;
        boolean warned;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Раз в секунду для каждого игрока. Тики — явно, для тестов. */
    public static Event tickPlayer(ServerPlayer player, int elapsedTicks) {
        MinecraftServer server = player.server;
        State st = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (!Army.isActive(server) || player.isSpectator()) {
            st.ticks = 0;
            st.warned = false;
            return Event.NONE;
        }
        ServerLevel level = player.serverLevel();
        String sector = Alarm.sector(level, player.blockPosition());
        if (!sector.equals(st.sector)) {
            st.sector = sector;
            st.ticks = 0;
            st.warned = false;
        }
        st.ticks += elapsedTicks;
        int alarm = Alarm.level(level, player.blockPosition());
        if (alarm < VivaConfig.armyWaveThreshold()) {
            st.warned = false;
            return Event.NONE;
        }
        int delay = VivaConfig.armyWaveDelay() * 20;
        int warnAt = delay - VivaConfig.armyWaveWarning() * 20;
        if (st.ticks >= delay) {
            st.ticks = 0;
            st.warned = false;
            return spawnWave(player, alarm) > 0 ? Event.SPAWNED : Event.BLOCKED;
        }
        if (!st.warned && st.ticks >= warnAt) {
            st.warned = true;
            warn(player);
            return Event.WARNED;
        }
        return Event.NONE;
    }

    private static void warn(ServerPlayer player) {
        // звук двигателей вдали — пока ванильная заглушка
        player.level().playSound(null, player.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 1.5f, 0.6f);
        player.sendSystemMessage(Component.translatable("vivacore.army.wave.warning"));
    }

    public static void forget(UUID player) {
        STATES.remove(player);
    }

    /** Состав волны: «id», «id*N», «a|b» (одна из). */
    public static List<MachineType> composition(int alarm, net.minecraft.util.RandomSource random) {
        List<MachineType> out = new ArrayList<>();
        for (String entry : VivaConfig.armyWave(alarm)) {
            String spec = entry.trim();
            int count = 1;
            int star = spec.indexOf('*');
            if (star >= 0) {
                try {
                    count = Math.max(0, Integer.parseInt(spec.substring(star + 1).trim()));
                } catch (NumberFormatException ignored) {
                    count = 1;
                }
                spec = spec.substring(0, star);
            }
            String[] options = spec.split("\\|");
            for (int i = 0; i < count; i++) {
                MachineType t = MachineType.byId(options[random.nextInt(options.length)].trim());
                if (t != null) out.add(t);
            }
        }
        return out;
    }

    /** Заспавнить волну у игрока по уровню тревоги. @return сколько машин появилось */
    public static int spawnWave(ServerPlayer player, int alarm) {
        ServerLevel level = player.serverLevel();
        int spawned = 0;
        for (MachineType t : composition(alarm, level.random)) {
            if (countMachines(player.server) >= VivaConfig.armyMaxMachines()) break;
            if (t.heavy && countHeavyNear(level, player) >= VivaConfig.armyMaxHeavy()) continue;
            if (spawnNear(player, t) != null) spawned++;
        }
        return spawned;
    }

    /** Все живые машины ОРИОНа на сервере. */
    public static int countMachines(MinecraftServer server) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) {
            for (var e : l.getAllEntities()) {
                if (e instanceof OrionMachine m && m.isAlive()) n++;
            }
        }
        return n;
    }

    /** Тяжёлые машины в радиусе группы игрока. */
    public static int countHeavyNear(ServerLevel level, ServerPlayer player) {
        double r = VivaConfig.armyHeavyGroupRadius();
        return level.getEntitiesOfClass(OrionMachine.class, player.getBoundingBox().inflate(r),
                m -> m.isAlive() && m.machine().heavy).size();
    }

    /** Машина у края видимости игрока, перед ним, на земле (летающие — выше), не в воде, лицом к игроку. */
    @Nullable
    public static OrionMachine spawnNear(ServerPlayer player, MachineType type) {
        ServerLevel level = player.serverLevel();
        EntityType<? extends OrionMachine> entityType = ArmyEntities.type(type);
        double dist = VivaConfig.armyWaveDistance();
        for (int attempt = 0; attempt < 20; attempt++) {
            float yaw = player.getYRot() + (level.random.nextFloat() - 0.5f) * 140f;
            double d = dist + (level.random.nextDouble() - 0.5) * 8;
            double x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * d;
            double z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * d;
            BlockPos column = BlockPos.containing(x, 0, z);
            if (!level.hasChunkAt(column)) continue;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            BlockPos below = new BlockPos(column.getX(), ground - 1, column.getZ());
            if (!level.getFluidState(below).isEmpty() || level.getBlockState(below).isAir()) continue;
            double y = ground + (type.movement == MachineType.Movement.FLYING ? VivaConfig.armyPatrolHeight() + 2 : 0);
            AABB box = entityType.getDimensions().makeBoundingBox(x, y, z);
            if (!level.noCollision(box) || level.containsAnyLiquid(box)) continue;
            OrionMachine m = entityType.create(level);
            if (m == null) return null;
            float face = (float) (Mth.atan2(player.getZ() - z, player.getX() - x) * Mth.RAD_TO_DEG) - 90f;
            m.moveTo(x, y, z, face, 0f);
            m.setYHeadRot(face);
            m.setYBodyRot(face);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null);
            level.addFreshEntity(m);
            return m;
        }
        return null;
    }
}
