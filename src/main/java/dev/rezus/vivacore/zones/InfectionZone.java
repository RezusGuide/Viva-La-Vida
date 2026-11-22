package dev.rezus.vivacore.zones;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.army.ArmyEntities;
import dev.rezus.vivacore.army.MachineType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Сторона заражения в зоне влияния (тест 32). Точка входа для Spore.
 * <p>
 * Когда Spore (Spore Inquisition) и Infnexus будут в сборке: здесь при доле заражения share в точке pos надо ставить
 * моба Spore — или, как предлагает заметка 17 («Как сделать»), курган / очаг Spore: заражённые Inquisition растут из
 * курганов, а не спавнятся в темноте. Тогда share — это как часто Viva Core ставит очаг в этой точке. Как это
 * сочетается с распространением Inquisition, проверит тест 32 со Spore.
 * <p>
 * Пока Spore в dev нет — заглушка: заражённые машины из конфига zones.infection.stubTypes
 * (scout_drone_infected, infantry_light_infected). После флага «Нексус уничтожен» зона не работает
 * (это проверяет {@link InfluenceZones#shareAt}).
 */
public final class InfectionZone {
    private InfectionZone() {}

    /**
     * Место для моба заражения: обычный моб в pos пропущен, доля заражения тут — share.
     * @return поставлен ли моб заражения
     */
    static boolean spawn(ServerLevel level, BlockPos pos, double share, RandomSource random) {
        // TODO(Spore): моб Spore или очаг вместо заглушки
        List<? extends String> stub = VivaConfig.zonesInfectionStubTypes();
        if (stub.isEmpty() || InfluenceZones.machineCount() >= VivaConfig.armyMaxMachines()) return false;
        MachineType t = MachineType.byId(stub.get(random.nextInt(stub.size())).trim());
        if (t == null) return false;
        if (!LabZone.place(level, ArmyEntities.type(t), pos, random)) return false;
        InfluenceZones.countSpawned();
        return true;
    }
}
