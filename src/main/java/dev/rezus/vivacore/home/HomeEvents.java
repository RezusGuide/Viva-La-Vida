package dev.rezus.vivacore.home;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** События для домов. */
public final class HomeEvents {
    private HomeEvents() {}

    /** Запоминаем ник каждого зашедшего игрока — для «/viva home list» (кто без дома). */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HomeData.get(player.server).rememberName(player.getUUID(), player.getGameProfile().getName());
        }
    }
}
