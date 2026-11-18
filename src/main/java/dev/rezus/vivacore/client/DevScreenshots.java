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
 * <p>
 * Необязательные действия ({@code -PvivaDevAction=...}, выполняются на встроенном сервере):
 * <ul>
 *   <li>{@code wave} — на тике 130 волна подкрепления уровня 3 и 4 у игрока (кадры 4–5 — волна);</li>
 *   <li>{@code profile} — с тика 100 по 300 профиль сервера (как /debug start|stop) в run/debug/ и средний MSPT
 *       в лог: «Viva Core: MSPT …». Для замера нагрузки армии (тест 27).</li>
 * </ul>
 */
public final class DevScreenshots {
    private DevScreenshots() {}

    private static final int[] SHOTS = {100, 115, 130, 200, 260};
    private static final int[] PROFILE_SHOTS = {100, 200, 300, 320, 340};
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

    private static void devAction(Minecraft mc) {
        String action = System.getProperty("vivacore.devAction");
        var server = mc.getSingleplayerServer();
        if (action == null || server == null) return;
        java.util.UUID id = mc.player.getUUID();
        if ("wave".equals(action) && ticksInWorld == 130) {
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(id);
                if (sp == null) return;
                int a = dev.rezus.vivacore.army.Waves.spawnWave(sp, 3);
                int b = dev.rezus.vivacore.army.Waves.spawnWave(sp, 4);
                VivaCore.LOGGER.info("Viva Core: волна 3 — {} машин, волна 4 — {} машин", a, b);
            });
        }
        if ("profile".equals(action)) {
            if (ticksInWorld == 100) {
                server.execute(server::startTimeProfiler);
            } else if (ticksInWorld == 300) {
                server.execute(() -> {
                    double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
                    long[] times = server.getTickTimesNanos();
                    long max = 0;
                    for (long t : times) max = Math.max(max, t);
                    int machines = dev.rezus.vivacore.army.Waves.countMachines(server);
                    var results = server.stopTimeProfiler();
                    java.nio.file.Path out = server.getServerDirectory().resolve("debug").resolve("vivacore-profile.txt");
                    try {
                        java.nio.file.Files.createDirectories(out.getParent());
                        results.saveResults(out);
                    } catch (Exception e) {
                        VivaCore.LOGGER.warn("Viva Core: профиль не сохранён: {}", e.toString());
                    }
                    VivaCore.LOGGER.info("Viva Core: MSPT средний {} мс, максимум за 100 тиков {} мс, машин {}, профиль {}",
                            String.format("%.2f", mspt), String.format("%.2f", max / 1_000_000.0), machines, out);
                });
            }
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
        devAction(mc);
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
        int[] shots = "profile".equals(System.getProperty("vivacore.devAction")) ? PROFILE_SHOTS : SHOTS;
        if (shotIndex < shots.length && ticksInWorld == shots[shotIndex]) {
            String name = "vivacore-dev-" + (shotIndex + 1) + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                    msg -> VivaCore.LOGGER.info("Viva Core: скриншот {} — {}", name, msg.getString()));
            shotIndex++;
        } else if (shotIndex >= shots.length && ticksInWorld > shots[shots.length - 1] + 20) {
            VivaCore.LOGGER.info("Viva Core: dev-скриншоты готовы, закрываю клиент");
            mc.stop();
        }
    }
}
