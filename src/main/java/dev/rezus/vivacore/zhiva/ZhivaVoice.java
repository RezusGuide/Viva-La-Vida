package dev.rezus.vivacore.zhiva;

import dev.rezus.vivacore.VivaConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Канал сообщений Живы игроку. Сейчас — чат с префиксом-заглушкой; позже заменим на HUD
 * (меняется только {@link #deliver}). Антиспам: одна и та же фраза не чаще, чем раз в
 * zhiva.phraseCooldownSeconds (конфиг). Говорит только в REVEALED: в IMPLANTED эти фразы не звучат,
 * в REMOVED Жива молчит совсем.
 */
public final class ZhivaVoice {
    private ZhivaVoice() {}

    /** Когда (игровой тик) игрок последний раз слышал фразу. Только серверный поток; чистится при выходе. */
    private static final Map<UUID, Map<String, Long>> LAST_SPOKEN = new HashMap<>();

    /** Может ли Жива говорить с игроком прямо. */
    public static boolean canSpeak(ServerPlayer player) {
        return Zhiva.state(player) == ZhivaState.REVEALED;
    }

    /** Сказать фразу (lang-ключ). @return true, если фраза прозвучала */
    public static boolean say(ServerPlayer player, String key, Object... args) {
        return say(player, player.serverLevel().getGameTime(), key, args);
    }

    /** То же с явным временем — для тестов антиспама. */
    public static boolean say(ServerPlayer player, long gameTime, String key, Object... args) {
        if (!canSpeak(player)) {
            return false;
        }
        long cooldownTicks = VivaConfig.zhivaPhraseCooldownSeconds() * 20L;
        Map<String, Long> spoken = LAST_SPOKEN.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        Long last = spoken.get(key);
        if (last != null && gameTime - last < cooldownTicks) {
            return false;
        }
        spoken.put(key, gameTime);
        deliver(player, Component.translatable(key, args));
        return true;
    }

    private static void deliver(ServerPlayer player, Component phrase) {
        ZhivaMessages.send(player, Component.translatable("vivacore.zhiva.prefix").append(phrase));
    }

    public static void forget(UUID player) {
        LAST_SPOKEN.remove(player);
    }
}
