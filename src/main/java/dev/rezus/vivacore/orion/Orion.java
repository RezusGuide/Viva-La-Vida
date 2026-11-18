package dev.rezus.vivacore.orion;

import dev.rezus.vivacore.VivaConfig;
import dev.rezus.vivacore.VivaRegistries;
import dev.rezus.vivacore.orion.block.CodeTerminalBlock;
import dev.rezus.vivacore.orion.block.OrionBlocks;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Программа ОРИОНа и терминал кода (заметка «17 Три стороны конфликта», тест 34 — часть).
 * Вся проверка — на сервере: блок на месте, игрок рядом, пауза между попытками, сравнение с конфигом.
 */
public final class Orion {
    private Orion() {}

    /** Отменена ли программа ОРИОНа. Позже армия будет проверять это и прекращать набеги. */
    public static boolean isProgramCancelled(MinecraftServer server) {
        return OrionData.get(server).programCancelled();
    }

    /** Для тестов: поставить флаг отмены без терминала. */
    public static void cancel(MinecraftServer server, String by) {
        OrionData.get(server).cancel(by, server.overworld().getGameTime());
    }

    /** Для оператора и тестов: снять флаг отмены. */
    public static void reset(MinecraftServer server) {
        OrionData.get(server).reset();
    }

    public enum Status { READY, WRONG, COOLDOWN, ACCEPTED, ALREADY_CANCELLED, TOO_FAR }

    /** Когда (игровой тик) игрок последний раз вводил код. Только серверный поток. */
    private static final Map<UUID, Long> LAST_ATTEMPT = new HashMap<>();

    public static Status submit(ServerPlayer player, BlockPos pos, String code) {
        return submit(player, pos, code, player.serverLevel().getGameTime());
    }

    /** Ввод кода в терминале pos. Время — явно, для тестов паузы. */
    public static Status submit(ServerPlayer player, BlockPos pos, String code, long gameTime) {
        if (!canUse(player, pos)) {
            return Status.TOO_FAR;
        }
        MinecraftServer server = player.server;
        OrionData data = OrionData.get(server);
        if (data.programCancelled()) {
            return Status.ALREADY_CANCELLED;
        }
        long cooldown = Math.round(VivaConfig.orionAttemptCooldownSeconds() * 20);
        Long last = LAST_ATTEMPT.get(player.getUUID());
        if (last != null && gameTime - last < cooldown) {
            return Status.COOLDOWN;
        }
        LAST_ATTEMPT.put(player.getUUID(), gameTime);
        // строго как в конфиге: регистр и пробелы важны
        if (!VivaConfig.orionAccessCode().equals(code)) {
            return Status.WRONG;
        }
        data.cancel(player.getGameProfile().getName(), gameTime);
        BlockState state = player.level().getBlockState(pos);
        player.level().setBlock(pos, state.setValue(CodeTerminalBlock.UNLOCKED, true), Block.UPDATE_ALL);
        Component message = Component.translatable("vivacore.orion.cancelled.broadcast", player.getGameProfile().getName());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(message);
        }
        return Status.ACCEPTED;
    }

    /** Терминал на месте и игрок рядом. */
    public static boolean canUse(ServerPlayer player, BlockPos pos) {
        return player.level().isLoaded(pos)
                && player.level().getBlockState(pos).is(OrionBlocks.CODE_TERMINAL.get())
                && player.canInteractWithBlock(pos, VivaConfig.orionTerminalReach());
    }

    public static void forget(UUID player) {
        LAST_ATTEMPT.remove(player);
    }

    // ---------------------------------------------------------------- записка

    /**
     * Выдать записку с кодом при первом входе (конфиг orion.giveNoteOnFirstJoin). Отметка — attachment на игроке.
     *
     * @return true, если записка выдана сейчас
     */
    public static boolean giveNoteIfNeeded(ServerPlayer player) {
        if (!VivaConfig.orionGiveNote() || player.getData(VivaRegistries.ORION_NOTE_GIVEN)) {
            return false;
        }
        player.setData(VivaRegistries.ORION_NOTE_GIVEN, true);
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(OrionItems.CODE_NOTE.get()));
        return true;
    }
}
