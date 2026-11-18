package dev.rezus.vivacore.client;

import dev.rezus.vivacore.VivaCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Только для разработки: автоматические скриншоты в dev-клиенте, чтобы агент мог посмотреть модель в игре
 * без ручного управления. Включается системным свойством {@code vivacore.devScreenshots=true}
 * (ставит build.gradle при {@code ./gradlew runClient -PvivaQuickPlay=<мир>}). В обычной игре не работает.
 * <p>
 * После входа в мир делает снимки в run/screenshots на тиках из {@link #SHOTS} и закрывает клиент.
 */
public final class DevScreenshots {
    private DevScreenshots() {}

    private static final int[] SHOTS = {100, 115, 130, 200, 260};
    private static final int LOCK_FROM = 60;
    private static int ticksInWorld;
    private static int shotIndex;
    private static float lockedYaw, lockedPitch;

    public static void initIfEnabled() {
        if (Boolean.getBoolean("vivacore.devScreenshots")) {
            VivaCore.LOGGER.info("Viva Core: dev-скриншоты включены");
            NeoForge.EVENT_BUS.addListener(DevScreenshots::onClientTick);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || (mc.screen != null && ticksInWorld == 0)) {
            return;
        }
        // необязательный шаг: открыть экран для снимка последнего кадра (-PvivaDevScreen=code_terminal)
        if (ticksInWorld == SHOTS[SHOTS.length - 1] - 15 && "code_terminal".equals(System.getProperty("vivacore.devScreen"))) {
            mc.setScreen(new CodeTerminalScreen(mc.player.blockPosition(), dev.rezus.vivacore.orion.Orion.Status.WRONG));
        }
        ticksInWorld++;
        // окно клиента может поймать мышь на рабочем столе — держим поворот камеры, заданный сценой (датапаком)
        if (ticksInWorld == LOCK_FROM) {
            lockedYaw = mc.player.getYRot();
            lockedPitch = mc.player.getXRot();
        } else if (ticksInWorld > LOCK_FROM) {
            mc.player.setYRot(lockedYaw);
            mc.player.setXRot(lockedPitch);
            mc.player.yRotO = lockedYaw;
            mc.player.xRotO = lockedPitch;
        }
        if (shotIndex < SHOTS.length && ticksInWorld == SHOTS[shotIndex]) {
            String name = "vivacore-dev-" + (shotIndex + 1) + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                    msg -> VivaCore.LOGGER.info("Viva Core: скриншот {} — {}", name, msg.getString()));
            shotIndex++;
        } else if (shotIndex >= SHOTS.length && ticksInWorld > SHOTS[SHOTS.length - 1] + 20) {
            VivaCore.LOGGER.info("Viva Core: dev-скриншоты готовы, закрываю клиент");
            mc.stop();
        }
    }
}
