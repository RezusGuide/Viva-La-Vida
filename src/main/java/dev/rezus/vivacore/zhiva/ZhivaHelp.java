package dev.rezus.vivacore.zhiva;

import dev.rezus.vivacore.VivaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Прямая помощь Живы после правды (REVEALED). Заметка «02 Сюжет и Жива», пункт 5 твиста.
 * Все фразы идут через {@link ZhivaVoice}: до REVEALED не звучат, антиспам общий.
 */
public final class ZhivaHelp {
    private ZhivaHelp() {}

    public static final String LOW_HEALTH = "vivacore.zhiva.phrase.low_health";
    public static final String RAID_INCOMING = "vivacore.zhiva.phrase.raid_incoming";

    /**
     * Правило «здоровье ниже X% → фраза про опасность» (X — zhiva.lowHealthPercent).
     * Вызывается из тика игрока раз в zhiva.healthCheckTicks.
     *
     * @return true, если фраза прозвучала
     */
    public static boolean checkHealth(ServerPlayer player, long gameTime) {
        if (!player.isAlive() || player.getMaxHealth() <= 0) {
            return false;
        }
        double percent = player.getHealth() / player.getMaxHealth() * 100.0;
        if (percent >= VivaConfig.zhivaLowHealthPercent()) {
            return false;
        }
        return ZhivaVoice.say(player, gameTime, LOW_HEALTH);
    }

    /**
     * Точка входа: рейд идёт к базе игрока («датчики уловили рейд в нашу сторону»).
     * Вызовет будущая система набегов (заметка «17 Три стороны конфликта», тест 29). Логики пока нет.
     */
    public static void onRaidApproaching(ServerPlayer player, BlockPos raidPos) {
        // TODO(набеги): когда появится система набегов — ZhivaVoice.say(player, RAID_INCOMING, ...)
    }

    /**
     * Точка входа: совет по постройке и починке мехов, самолётов, дирижаблей.
     * Вызовет будущая система советов (Aeronautics / мехи). Логики пока нет.
     *
     * @param topic тема совета (например, id машины или вида поломки) — формат решим вместе с системой советов
     */
    public static void onVehicleAdvice(ServerPlayer player, String topic) {
        // TODO(советы): подобрать фразу по теме и сказать через ZhivaVoice
    }
}
