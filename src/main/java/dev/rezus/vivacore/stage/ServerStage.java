package dev.rezus.vivacore.stage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Стадия сервера 1–5 (1 «Рождение» … 4 «Понимание», 5 — Этап 2). Временное хранилище до AStages:
 * когда AStages появится в сборке, {@link #get} будет читать стадию оттуда, а команда /viva стадия — уйдёт.
 * Все проверки стадии в Viva Core — только через {@link #get}. Тесты 31, 32.
 */
public final class ServerStage extends SavedData {
    public static final String ID = "vivacore_stage";
    public static final int MIN = 1;
    public static final int MAX = 5;
    /** «Понимание» (латунь): с неё — полные круги зон влияния, заражение и лаборатория. */
    public static final int UNDERSTANDING = 4;

    private static final SavedData.Factory<ServerStage> FACTORY = new SavedData.Factory<>(ServerStage::new, ServerStage::load, null);

    private int stage = MIN;

    /** Текущая стадия сервера. Единственная точка чтения стадии. */
    public static int get(MinecraftServer server) {
        return data(server).stage;
    }

    public static void set(MinecraftServer server, int stage) {
        ServerStage d = data(server);
        d.stage = Mth.clamp(stage, MIN, MAX);
        d.setDirty();
    }

    private static ServerStage data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("stage", stage);
        return tag;
    }

    private static ServerStage load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerStage d = new ServerStage();
        d.stage = Mth.clamp(tag.getInt("stage"), MIN, MAX);
        return d;
    }
}
