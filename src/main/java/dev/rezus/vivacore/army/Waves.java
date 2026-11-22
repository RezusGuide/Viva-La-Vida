package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaCore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Волны подкрепления (заметка «16 Враги-армия», раздел 3; тест 24). Волна — на участок, а не на игрока:
 * таймер и предупреждение одни на участок тревоги; когда участок занят дольше army.waves.delaySeconds при тревоге
 * ≥ threshold, приходит одна волна, и её машины распределяются по игрокам участка по кругу.
 * <p>
 * Место: блок под машиной — из тега vivacore:wave_spawn_ground (природные блоки — не постройки игроков), не вода,
 * машина не в блоках, лицом к игроку. Игрок под землёй (не видит неба) — пещерная волна: только машины, которые
 * помещаются в пещеру (дрон, рой, пехота), на высоте игрока ±8, ближе к нему; тяжёлые не идут.
 * Лимиты: машин ОРИОНа ≤ maxMachines, тяжёлых на группу ≤ maxHeavyPerGroup; заражённые в лимитах не считаются.
 */
public final class Waves {
    private Waves() {}

    public static final TagKey<Block> SPAWN_GROUND =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "wave_spawn_ground"));

    public enum Event { NONE, WARNED, SPAWNED, BLOCKED }

    private static final class State {
        int ticks;
        boolean warned;
    }

    /** Таймеры участков. Только серверный поток. */
    private static final Map<String, State> SECTORS = new HashMap<>();

    /** Раз в секунду: сгруппировать игроков по участкам и продвинуть таймер каждого занятого участка. */
    public static void tickAll(MinecraftServer server, Collection<ServerPlayer> players, int elapsedTicks) {
        Map<String, List<ServerPlayer>> bySector = new LinkedHashMap<>();
        for (ServerPlayer p : players) {
            if (!p.isSpectator()) bySector.computeIfAbsent(Alarm.sector(p.level(), p.blockPosition()), k -> new ArrayList<>()).add(p);
        }
        SECTORS.keySet().retainAll(bySector.keySet()); // ушли все — таймер участка сброшен
        for (var e : bySector.entrySet()) {
            tickSector(e.getValue(), elapsedTicks);
        }
    }

    /** Один участок с его игроками (все в одном участке). Тики — явно, для тестов. */
    public static Event tickSector(List<ServerPlayer> playersInSector, int elapsedTicks) {
        if (playersInSector.isEmpty()) return Event.NONE;
        ServerPlayer first = playersInSector.get(0);
        ServerLevel level = first.serverLevel();
        String sector = Alarm.sector(level, first.blockPosition());
        State st = SECTORS.computeIfAbsent(sector, k -> new State());
        if (!Army.isActive(first.server)) {
            st.ticks = 0;
            st.warned = false;
            return Event.NONE;
        }
        st.ticks += elapsedTicks;
        int alarm = Alarm.level(level, first.blockPosition());
        if (alarm < VivaConfig.armyWaveThreshold()) {
            st.warned = false;
            return Event.NONE;
        }
        int delay = VivaConfig.armyWaveDelay() * 20;
        int warnAt = delay - VivaConfig.armyWaveWarning() * 20;
        if (st.ticks >= delay) {
            st.ticks = 0;
            st.warned = false;
            return spawnWave(playersInSector, alarm) > 0 ? Event.SPAWNED : Event.BLOCKED;
        }
        if (!st.warned && st.ticks >= warnAt) {
            st.warned = true;
            for (ServerPlayer p : playersInSector) warn(p);
            return Event.WARNED;
        }
        return Event.NONE;
    }

    private static void warn(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 1.5f, 0.6f);
        player.sendSystemMessage(Component.translatable("vivacore.army.wave.warning"));
    }

    public static void reset() {
        SECTORS.clear();
    }

    /** Состав волны: «id», «id*N», «a|b» (одна из). */
    public static List<MachineType> composition(int alarm, RandomSource random) {
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

    /** Одна волна на игроков участка: машины по очереди к каждому игроку. @return сколько машин появилось */
    public static int spawnWave(List<ServerPlayer> players, int alarm) {
        if (players.isEmpty()) return 0;
        ServerLevel level = players.get(0).serverLevel();
        int spawned = 0;
        int next = 0;
        for (MachineType t : composition(alarm, level.random)) {
            if (countMachines(level.getServer()) >= VivaConfig.armyMaxMachines()) break;
            ServerPlayer target = players.get(next++ % players.size());
            boolean cave = isUnderground(target);
            if (cave && !t.fitsCave()) continue;
            if (t.heavy && countHeavyNear(level, target) >= VivaConfig.armyMaxHeavy()) continue;
            if (spawnNear(target, t) != null) spawned++;
        }
        return spawned;
    }

    /** Совместимость: волна для одного игрока. */
    public static int spawnWave(ServerPlayer player, int alarm) {
        return spawnWave(List.of(player), alarm);
    }

    /**
     * Игрок под землёй: над ним есть твёрдый блок (карта высот MOTION_BLOCKING_NO_LEAVES выше головы; листва не в счёт).
     * Не по освещению (canSeeSky в 1.21 — по небесному свету, он обновляется с задержкой). Под крышей дома игрок
     * тоже считается «под землёй» — тогда приходит лёгкая волна; машины всё равно ставятся только на природный грунт.
     */
    public static boolean isUnderground(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) > pos.getY() + 2;
    }

    /** Живые машины ОРИОНа на сервере (заражённые — не ОРИОН, в лимите не считаются). */
    public static int countMachines(MinecraftServer server) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) {
            for (var e : l.getAllEntities()) {
                if (e instanceof OrionMachine m && m.isAlive() && !m.machine().infected) n++;
            }
        }
        return n;
    }

    /** Тяжёлые машины ОРИОНа в радиусе группы игрока. */
    public static int countHeavyNear(ServerLevel level, ServerPlayer player) {
        double r = VivaConfig.armyHeavyGroupRadius();
        return level.getEntitiesOfClass(OrionMachine.class, player.getBoundingBox().inflate(r),
                m -> m.isAlive() && m.machine().heavy && !m.machine().infected).size();
    }

    /** Машина у игрока: на поверхности — у края видимости перед ним, под землёй — в пещере рядом. */
    @Nullable
    public static OrionMachine spawnNear(ServerPlayer player, MachineType type) {
        ServerLevel level = player.serverLevel();
        EntityType<? extends OrionMachine> entityType = ArmyEntities.type(type);
        boolean cave = isUnderground(player);
        double dist = cave ? 10 : VivaConfig.armyWaveDistance();
        for (int attempt = 0; attempt < (cave ? 60 : 24); attempt++) {
            float yaw = player.getYRot() + (level.random.nextFloat() - 0.5f) * (cave ? 360f : 140f);
            double d = cave ? 4 + level.random.nextDouble() * dist : dist + (level.random.nextDouble() - 0.5) * 8;
            double x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * d;
            double z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * d;
            BlockPos column = BlockPos.containing(x, player.getY(), z);
            if (!level.hasChunkAt(column)) continue;
            Integer ground = cave ? caveFloor(level, column, player.getBlockY()) : surface(level, column);
            if (ground == null) continue;
            BlockPos below = new BlockPos(column.getX(), ground - 1, column.getZ());
            if (!level.getFluidState(below).isEmpty() || !level.getBlockState(below).is(SPAWN_GROUND)) continue;
            double lift = type.movement == MachineType.Movement.FLYING ? (cave ? 1 : VivaConfig.armyPatrolHeight() + 2) : 0;
            double y = ground + lift;
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

    /** Поверхность: высота над верхним твёрдым блоком столбца. */
    @Nullable
    private static Integer surface(ServerLevel level, BlockPos column) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
    }

    /** Пещера: пол на высоте игрока ±8 — твёрдый блок, над ним воздух. */
    @Nullable
    private static Integer caveFloor(ServerLevel level, BlockPos column, int playerY) {
        for (int dy = 0; dy <= 8; dy++) {
            for (int sign : new int[] {-1, 1}) {
                int y = playerY + dy * sign;
                BlockPos at = new BlockPos(column.getX(), y, column.getZ());
                if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                        && level.getBlockState(at.below()).isSolid()) {
                    return y;
                }
                if (dy == 0) break;
            }
        }
        return null;
    }
}
