package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Дрон-разведчик «Мейсса» (заметка «16 Враги-армия», тест 24). Летает, патрулирует; пока видит игрока
 * (прямая видимость, army.alarm.droneSightRange), каждую секунду поднимает тревогу участка игрока
 * на army.alarm.perDroneSightSecond. Заражённый дрон тревогу не поднимает (его ведёт заражение).
 * <p>
 * Модель смотрит в −Z, как и сущность в игре (проверено скриншотами в задаче 4).
 */
public class ScoutDrone extends FlyingMachine {
    public ScoutDrone(EntityType<? extends ScoutDrone> type, Level level, MachineType machine) {
        super(type, level, machine);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && tickCount % 20 == 0 && !isNoAi() && !machine.infected) {
            Player seen = seenPlayer();
            if (seen != null) {
                checkSight(seen);
            }
        }
    }

    /** Ближайший игрок в дальности обзора с прямой видимостью (не творческий режим, не наблюдатель). */
    @Nullable
    public Player seenPlayer() {
        Player p = level().getNearestPlayer(getX(), getY(), getZ(), VivaConfig.armySightRange(), EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        return p != null && hasLineOfSight(p) ? p : null;
    }

    /**
     * Дрон видит игрока одну секунду: поднять тревогу участка игрока. Отдельный метод — для GameTest
     * (тестовый игрок не в мире, getNearestPlayer его не находит).
     *
     * @return true, если тревога поднята
     */
    public boolean checkSight(Player player) {
        if (machine.infected || !(level() instanceof ServerLevel server)) return false;
        if (player.distanceTo(this) > VivaConfig.armySightRange() || !hasLineOfSight(player)) return false;
        return Alarm.raise(server, player.blockPosition(), VivaConfig.armyAlarmPerSight());
    }
}
