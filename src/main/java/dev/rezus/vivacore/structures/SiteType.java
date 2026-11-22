package dev.rezus.vivacore.structures;

import dev.rezus.vivacore.VivaCore;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Три постройки (тест 31). id — для команд и SavedData, file — имя шаблона data/vivacore/structure/&lt;file&gt;.nbt. */
public enum SiteType {
    CRASH("crash", "crash_habitation"),
    LAB("lab", "orion_lab"),
    NEXUS("nexus", "nexus_site");

    public final String id;
    public final String file;

    SiteType(String id, String file) {
        this.id = id;
        this.file = file;
    }

    public ResourceLocation template() {
        return ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, file);
    }

    /** Есть ли у постройки ядро — центр зоны влияния (лаборатория и Нексус). */
    public boolean hasCore() {
        return this != CRASH;
    }

    @Nullable
    public static SiteType byId(String id) {
        for (SiteType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
