package dev.rezus.vivacore.orion;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Флаг мира «программа ОРИОНа отменена» — один на сервер (хранится в overworld).
 * Заметка «17 Три стороны конфликта»: код в терминале лаборатории отменяет программу армии.
 */
public final class OrionData extends SavedData {
    public static final String ID = "vivacore_orion";

    private static final SavedData.Factory<OrionData> FACTORY = new SavedData.Factory<>(OrionData::new, OrionData::load, null);

    private boolean programCancelled;
    private String cancelledBy = "";
    private long cancelledAt;

    public static OrionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public boolean programCancelled() {
        return programCancelled;
    }

    public String cancelledBy() {
        return cancelledBy;
    }

    public long cancelledAt() {
        return cancelledAt;
    }

    void cancel(String by, long gameTime) {
        programCancelled = true;
        cancelledBy = by;
        cancelledAt = gameTime;
        setDirty();
    }

    void reset() {
        programCancelled = false;
        cancelledBy = "";
        cancelledAt = 0;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("programCancelled", programCancelled);
        tag.putString("cancelledBy", cancelledBy);
        tag.putLong("cancelledAt", cancelledAt);
        return tag;
    }

    public static OrionData load(CompoundTag tag, HolderLookup.Provider registries) {
        OrionData data = new OrionData();
        data.programCancelled = tag.getBoolean("programCancelled");
        data.cancelledBy = tag.getString("cancelledBy");
        data.cancelledAt = tag.getLong("cancelledAt");
        return data;
    }
}
