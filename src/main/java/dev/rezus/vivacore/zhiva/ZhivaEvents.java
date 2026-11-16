package dev.rezus.vivacore.zhiva;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.compat.ChromewareCompat;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** События Живы (игровая шина NeoForge). */
public final class ZhivaEvents {
    private ZhivaEvents() {}

    /**
     * Шаг 2 твиста: игрок сел/лёг на кушетку операций Chromeware. Обычное событие посадки NeoForge,
     * без миксина в Chromeware: сиденье кушетки — сущность chromeware:surgery_seat.
     */
    public static void onMount(EntityMountEvent event) {
        if (event.isMounting()
                && !event.getLevel().isClientSide()
                && event.getEntityMounting() instanceof ServerPlayer player
                && ChromewareCompat.isSurgerySeat(event.getEntityBeingMounted())) {
            Zhiva.onCouch(player);
        }
    }

    /** Правило здоровья — раз в zhiva.healthCheckTicks. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            long now = player.serverLevel().getGameTime();
            if (now % VivaConfig.zhivaHealthCheckTicks() == 0) {
                ZhivaHelp.checkHealth(player, now);
            }
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ZhivaVoice.forget(event.getEntity().getUUID());
    }
}
