package dev.rezus.vivacore.army;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Тревога участков (тест 24): ключ участка «измерение|cx|cz» → тревога 0..4 (дробная; уровень — целая часть).
 * Один объект на сервер (хранится в overworld). Участки с нулевой тревогой не хранятся.
 */
public final class AlarmData extends SavedData {
    public static final String ID = "vivacore_army_alarm";
    public static final float MAX = 4f;

    private static final SavedData.Factory<AlarmData> FACTORY = new SavedData.Factory<>(AlarmData::new, AlarmData::load, null);

    private final Map<String, Float> sectors = new HashMap<>();

    public static AlarmData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public float get(String sector) {
        return sectors.getOrDefault(sector, 0f);
    }

    public void set(String sector, float value) {
        float v = Math.max(0f, Math.min(MAX, value));
        if (v <= 0f) {
            if (sectors.remove(sector) != null) setDirty();
        } else if (!Float.valueOf(v).equals(sectors.get(sector))) {
            sectors.put(sector, v);
            setDirty();
        }
    }

    public Map<String, Float> all() {
        return Map.copyOf(sectors);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag map = new CompoundTag();
        sectors.forEach(map::putFloat);
        tag.put("sectors", map);
        return tag;
    }

    public static AlarmData load(CompoundTag tag, HolderLookup.Provider registries) {
        AlarmData data = new AlarmData();
        CompoundTag map = tag.getCompound("sectors");
        for (String key : map.getAllKeys()) {
            data.sectors.put(key, map.getFloat(key));
        }
        return data;
    }
}
