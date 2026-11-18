package dev.rezus.vivacore.client;

import dev.rezus.vivacore.orion.Orion;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Только клиент: открытие экрана терминала и обновление статуса из пакетов. */
public final class CodeTerminalClient {
    private CodeTerminalClient() {}

    public static void open(BlockPos pos, Orion.Status status) {
        Minecraft.getInstance().setScreen(new CodeTerminalScreen(pos, status));
    }

    public static void status(BlockPos pos, Orion.Status status) {
        if (Minecraft.getInstance().screen instanceof CodeTerminalScreen screen && screen.pos().equals(pos)) {
            screen.setStatus(status);
        }
    }
}
