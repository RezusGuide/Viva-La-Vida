package dev.rezus.vivacore.army;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.orion.Orion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Армия ОРИОНа: выключатели и события (тесты 24 и 27).
 * Армия активна, если включена (army.enabled / «/viva армия вкл») и программа ОРИОНа не отменена.
 */
public final class Army {
    private Army() {}

    public static boolean isActive(MinecraftServer server) {
        return VivaConfig.armyEnabled() && !Orion.isProgramCancelled(server);
    }

    /**
     * Игрок убил машину ОРИОНа (не заражённую) → тревога участка игрока (а не машины: волна придёт к нему,
     * и граница участков между игроком и целью не теряет тревогу).
     */
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof OrionMachine m && !m.machine().infected
                && event.getSource().getEntity() instanceof Player killer
                && m.level() instanceof ServerLevel level) {
            Alarm.raise(level, killer.blockPosition(), VivaConfig.armyAlarmPerKill());
        }
    }

    /** Раз в секунду: спад тревоги в пустых участках и таймеры волн игроков. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        var players = server.getPlayerList().getPlayers();
        Alarm.decay(server, Alarm.occupied(players), 1.0);
        for (ServerPlayer p : players) {
            Waves.tickPlayer(p, 20);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Waves.forget(event.getEntity().getUUID());
    }
}
