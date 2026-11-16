package dev.rezus.vivacore.compat;

import com.chromeware.cyberware.Implant;
import net.minecraft.world.entity.player.Player;

/**
 * Прямые вызовы Chromeware 1.3.0. Загружается только из {@link ChromewareCompat} и только если мод есть.
 * Хрупкие места (при обновлении Chromeware сверить): enum-константа {@code Implant.KIROSHI_OPTICS}
 * и метод {@code Implant#installed(Player)}.
 */
final class ChromewareBridge {
    private ChromewareBridge() {}

    static boolean hasKiroshiOptics(Player player) {
        return Implant.KIROSHI_OPTICS.installed(player);
    }
}
