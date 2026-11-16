package dev.rezus.vivacore.zhiva;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Отправка сообщений игроку. У fake-игроков (других модов, тестов) соединения может не быть — тогда молчим. */
public final class ZhivaMessages {
    private ZhivaMessages() {}

    public static void send(ServerPlayer player, Component message) {
        if (player.connection != null) {
            player.sendSystemMessage(message);
        }
    }
}
