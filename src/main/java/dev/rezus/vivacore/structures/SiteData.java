package dev.rezus.vivacore.structures;

import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Позиции и состояние трёх построек (тест 31) + флаг «Нексус уничтожен» (тест 32). Один на сервер, в overworld.
 * Позиции лаборатории и Нексуса считаются один раз (угол из сида) и дальше не меняются.
 */
public final class SiteData extends SavedData {
    public static final String ID = "vivacore_structures";

    private static final SavedData.Factory<SiteData> FACTORY = new SavedData.Factory<>(SiteData::new, SiteData::load, null);

    /** Состояние одной постройки. */
    public static final class Entry {
        /** Плановый центр (x, z; y — 0) — из угла сида или «поставить здесь». */
        @Nullable BlockPos planned;
        boolean placed;
        /** Коробка поставленной постройки (по шаблону, без отступа). */
        @Nullable BoundingBox box;
        /** Метка vivacore:core — центр зоны влияния. */
        @Nullable BlockPos core;
        /** Метка vivacore:player_spawn. */
        @Nullable BlockPos spawn;

        @Nullable public BlockPos planned() { return planned; }
        public boolean placed() { return placed; }
        @Nullable public BoundingBox box() { return box; }
        @Nullable public BlockPos core() { return core; }
        @Nullable public BlockPos spawn() { return spawn; }
    }

    private final Map<SiteType, Entry> entries = new EnumMap<>(SiteType.class);
    private boolean planned;
    private double angle;
    private boolean nexusDestroyed;

    public static SiteData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public Entry entry(SiteType type) {
        return entries.computeIfAbsent(type, t -> new Entry());
    }

    public boolean planned() {
        return planned;
    }

    public double angle() {
        return angle;
    }

    void setPlan(double angle, BlockPos lab, BlockPos nexus) {
        this.planned = true;
        this.angle = angle;
        // уже заданную точку (оператор «поставить здесь», тест) план не перезаписывает
        if (entry(SiteType.LAB).planned == null) entry(SiteType.LAB).planned = lab;
        if (entry(SiteType.NEXUS).planned == null) entry(SiteType.NEXUS).planned = nexus;
        changed();
    }

    void setPlanned(SiteType type, BlockPos center) {
        entry(type).planned = center;
        changed();
    }

    void setPlaced(SiteType type, BoundingBox box, @Nullable BlockPos core, @Nullable BlockPos spawn) {
        Entry e = entry(type);
        e.placed = true;
        e.box = box;
        e.core = core;
        e.spawn = spawn;
        changed();
    }

    /** Для тестов и оператора: забыть, что постройка стоит (блоки не трогает). */
    void forget(SiteType type) {
        Entry e = entry(type);
        e.placed = false;
        e.box = null;
        e.core = null;
        e.spawn = null;
        changed();
    }

    void resetAll() {
        entries.clear();
        planned = false;
        angle = 0;
        nexusDestroyed = false;
        changed();
    }

    public boolean nexusDestroyed() {
        return nexusDestroyed;
    }

    void setNexusDestroyed(boolean destroyed) {
        nexusDestroyed = destroyed;
        changed();
    }

    private void changed() {
        setDirty();
        Structures.invalidateCache();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("planned", planned);
        tag.putDouble("angle", angle);
        tag.putBoolean("nexusDestroyed", nexusDestroyed);
        CompoundTag sites = new CompoundTag();
        for (var en : entries.entrySet()) {
            Entry e = en.getValue();
            CompoundTag t = new CompoundTag();
            if (e.planned != null) t.put("planned", NbtUtils.writeBlockPos(e.planned));
            t.putBoolean("placed", e.placed);
            if (e.box != null) t.putIntArray("box", new int[] {e.box.minX(), e.box.minY(), e.box.minZ(), e.box.maxX(), e.box.maxY(), e.box.maxZ()});
            if (e.core != null) t.put("core", NbtUtils.writeBlockPos(e.core));
            if (e.spawn != null) t.put("spawn", NbtUtils.writeBlockPos(e.spawn));
            sites.put(en.getKey().id, t);
        }
        tag.put("sites", sites);
        return tag;
    }

    private static SiteData load(CompoundTag tag, HolderLookup.Provider registries) {
        SiteData d = new SiteData();
        d.planned = tag.getBoolean("planned");
        d.angle = tag.getDouble("angle");
        d.nexusDestroyed = tag.getBoolean("nexusDestroyed");
        CompoundTag sites = tag.getCompound("sites");
        for (SiteType type : SiteType.values()) {
            if (!sites.contains(type.id)) continue;
            CompoundTag t = sites.getCompound(type.id);
            Entry e = d.entry(type);
            e.planned = NbtUtils.readBlockPos(t, "planned").orElse(null);
            e.placed = t.getBoolean("placed");
            int[] b = t.getIntArray("box");
            if (b.length == 6) e.box = new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
            e.core = NbtUtils.readBlockPos(t, "core").orElse(null);
            e.spawn = NbtUtils.readBlockPos(t, "spawn").orElse(null);
        }
        return d;
    }
}
