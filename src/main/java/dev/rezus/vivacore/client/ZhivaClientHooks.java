package dev.rezus.vivacore.client;

import dev.rezus.vivacore.zhiva.net.ZhivaNetwork;
import net.minecraft.client.Minecraft;

/** Только клиент. Вызывается из обработчика пакета OpenTerminal. */
public final class ZhivaClientHooks {
    private ZhivaClientHooks() {}

    public static void openTerminal(ZhivaNetwork.OpenTerminal payload) {
        Minecraft.getInstance().setScreen(new ZhivaTerminalScreen(payload));
    }
}
