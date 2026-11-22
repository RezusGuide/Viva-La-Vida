package dev.rezus.vivacore.zones;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.army.ArmyEntities;
import dev.rezus.vivacore.army.MachineType;
import dev.rezus.vivacore.army.OrionMachine;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;

/**
 * Сторона лаборатории в зоне влияния (тест 32): машина ОРИОНа вместо пропущенного обычного моба.
 * Состав по расстоянию до ядра — конфиг zones.lab.composition: «до=тип|тип»; дальше от ядра — дроны и пехота,
 * ближе — тяжёлые. Не работает после отмены программы ОРИОНа и при army.enabled = false (это проверяет
 * {@link InfluenceZones#shareAt} через Army.isActive). Лимиты армии те же: всего машин, тяжёлых на группу.
 */
public final class LabZone {
    private LabZone() {}

    /** Тип машины для расстояния до ядра. null — список пуст или кривой. */
    @Nullable
    public static MachineType pick(double distance, RandomSource random) {
        List<? extends String> rows = VivaConfig.zonesLabComposition();
        String chosen = null;
        for (String row : rows) {
            int eq = row.indexOf('=');
            if (eq < 0) continue;
            double upTo;
            try {
                upTo = Double.parseDouble(row.substring(0, eq).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            chosen = row.substring(eq + 1);
            if (distance <= upTo) break;
        }
        if (chosen == null) return null;
        String[] options = chosen.split("\\|");
        return MachineType.byId(options[random.nextInt(options.length)].trim());
    }

    static boolean spawn(ServerLevel level, BlockPos pos, double distance, RandomSource random) {
        if (InfluenceZones.machineCount() >= VivaConfig.armyMaxMachines()) return false;
        MachineType t = pick(distance, random);
        if (t == null || t.infected) return false;
        if (t.heavy) {
            double r = VivaConfig.armyHeavyGroupRadius();
            int heavy = level.getEntitiesOfClass(OrionMachine.class, new AABB(pos).inflate(r),
                    m -> m.isAlive() && m.machine().heavy && !m.machine().infected).size();
            if (heavy >= VivaConfig.armyMaxHeavy()) return false;
        }
        if (!place(level, ArmyEntities.type(t), pos, random)) return false;
        InfluenceZones.countSpawned();
        return true;
    }

    /** Поставить машину на место естественного спавна, если влезает. Общая для обеих сторон. */
    static boolean place(ServerLevel level, EntityType<? extends OrionMachine> type, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        AABB box = type.getDimensions().makeBoundingBox(x, pos.getY(), z);
        if (!level.noCollision(box) || level.containsAnyLiquid(box)) return false;
        OrionMachine m = type.create(level);
        if (m == null) return false;
        m.moveTo(x, pos.getY(), z, random.nextFloat() * 360f, 0f);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
        return level.addFreshEntity(m);
    }
}
